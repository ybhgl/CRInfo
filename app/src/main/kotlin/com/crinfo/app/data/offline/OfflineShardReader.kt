package com.crinfo.app.data.offline

/**
 * 路路通 `t*.dat`（按车次）与 `s*.dat`（按车站）分片的读取层。
 *
 * 字段布局取自路路通自己的实现（`DataMgr`、`QueryBase.getTrainTimeDTO`、`Rule.outOfDate`），
 * 不是从十六进制猜出来的，所以改动这里之前先确认库版本没换格式。
 *
 * 两处反复坑过人的地方，这里按权威实现写死：
 * 1. **分片号在片内，不在文件名上**。车次 ID 存于 `t[(id+1)%20]`、车站 ID 存于 `s[(id+1)%10]`，
 *    片头声明的却是 `id%20`、`id%10`（所以 `t0.dat` 声明 19、`s0.dat` 声明 9）。
 *    按文件名下标建表会整体漂移。
 * 2. **2 字节整数是 255 进制**（`hi*255 + lo`），不是 256 进制。京沪线 曲阜东 535、
 *    南京南 1023、上海虹桥 1318 用 255 进制才和板面逐字一致，用 256 会整体多算几公里。
 */

/** 停站行：时刻是到发的图定时刻，dwell 是停站分钟，distance 是**相对始发站**的累计里程。 */
data class TimetableStop(
    val stationId: Int,
    val arriveHour: Int,
    val arriveMinute: Int,
    val dwellMinutes: Int,
    val distanceKm: Int,
)

/**
 * 一列车在库里的全部信息。
 * startDate/endDate 是 `yyyyMMdd` 整数（endDate 的 20991231/20501231 之类是「长期有效」哨兵）；
 * ruleIndex 指向 [RunningRule] 表的下标，0 表示天天开。
 */
data class OfflineTimetable(
    val trainId: Int,
    val type: Int,
    val priceNo: Int,
    val startDate: Int,
    val endDate: Int,
    val ruleIndex: Int,
    val stops: List<TimetableStop>,
) {
    /** 终到时刻：末站的到达时刻（末站没有到达时退回它的出发时刻）。 */
    val terminalHour: Int get() = stops.lastOrNull()?.arriveHour ?: 0
    val terminalMinute: Int get() = stops.lastOrNull()?.arriveMinute ?: 0
}

/** `t.rule` 的一行：周期天数 + 周期内的开行位图。 */
data class RunningRule(val period: Int, val mask: Long) {
    /** 周期 1（含 0）表示不做周期判断，即天天开。 */
    val isEveryDay: Boolean get() = period <= 1
}

/** 车次/车站 ID → 分片内记录起点偏移。分片内按 ID 升序，所以定位是二分查表；`consumed` 用来断言「走满整片不留残字节」。 */
data class ShardIndex(val offsets: IntArray, val ids: IntArray, val consumed: Int) {
    /** 找不到返回 -1。 */
    fun indexOf(id: Int): Int {
        var lo = 0
        var hi = ids.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                ids[mid] == id -> return mid
                ids[mid] < id -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return -1
    }
}

/**
 * 单片字节流的读取与遍历。
 *
 * 设计上是**按需**的：一次查询只把用到的那一片读进来、只解那一条记录，
 * 常驻内存只有每片千来条偏移表（20 片全建也就几十 KB），不把 14889 趟车的时刻都摊开。
 */
class OfflineShardReader(private val bytes: ByteArray) {

    /**
     * 片头声明的分片号。
     *
     * ⚠️ 片头是 `[1B 0][1B 片号]` **两个单字节字段**，不是 2 字节 255 进制整数——
     * 按 2 字节读 `t0.dat` 会得到 4845 而不是 19。分片记录本身才用 255 进制的 2 字节 ID。
     */
    val declaredShardId: Int get() = if (bytes.size > 1) bytes[1].toInt() and 0xFF else -1

    /**
     * t*.dat 的记录 = `[2B 车次ID][15B 头][N×7B 停站行]`，N 在偏移 +15。
     * 走步一次到底，顺手把每条记录的起点存成索引；走不通就停在真正走完的位置。
     */
    fun trainIndex(): ShardIndex {
        val offsets = ArrayList<Int>(800)
        val ids = ArrayList<Int>(800)
        var p = 0
        while (p + 17 <= bytes.size) {
            val id = int16(p)
            val count = int16(p + 15)
            if (count <= 0 || count > MAX_STOPS || id > MAX_TRAIN_ID) break
            offsets.add(p)
            ids.add(id)
            p += 17 + count * 7
        }
        return ShardIndex(offsets.toIntArray(), ids.toIntArray(), p)
    }

    /** s*.dat 的记录 = `[2B 车站ID][2B 车次个数][个数×2B 车次ID]`。 */
    fun stationIndex(): ShardIndex {
        val offsets = ArrayList<Int>(400)
        val ids = ArrayList<Int>(400)
        var p = 0
        while (p + 4 <= bytes.size) {
            val id = int16(p)
            val count = int16(p + 2)
            if (count <= 0 || count > MAX_BOARD_SIZE || id > MAX_STATION_ID) break
            offsets.add(p)
            ids.add(id)
            p += 4 + count * 2
        }
        return ShardIndex(offsets.toIntArray(), ids.toIntArray(), p)
    }

    /** 解一条车次记录；结构不合返回 null，交给调用方回落。 */
    fun readTrain(offset: Int): OfflineTimetable? {
        if (offset < 0 || offset + 17 > bytes.size) return null
        val id = int16(offset)
        val count = int16(offset + 15)
        if (count <= 0 || count > MAX_STOPS || offset + 17 + count * 7 > bytes.size) return null
        val stops = ArrayList<TimetableStop>(count)
        var i = offset + 17
        repeat(count) {
            val stationId = int16(i)
            // 里程同样是 255 进制：字节 (5,6) = (4,43) 要解成 1318，不是 1067
            val kmHi = bytes[i + 5].toInt() and 0xFF
            val kmLo = bytes[i + 6].toInt() and 0xFF
            stops.add(
                TimetableStop(
                    stationId = stationId,
                    arriveHour = bytes[i + 2].toInt() and 0xFF,
                    arriveMinute = bytes[i + 3].toInt() and 0xFF,
                    dwellMinutes = bytes[i + 4].toInt() and 0xFF,
                    distanceKm = kmHi * 255 + kmLo,
                )
            )
            i += 7
        }
        return OfflineTimetable(
            trainId = id,
            type = bytes[offset + 2].toInt() and 0xFF,
            priceNo = int16(offset + 3),
            startDate = int32(offset + 5),
            endDate = int32(offset + 9),
            ruleIndex = int16(offset + 13),
            stops = stops,
        )
    }

    /** 解一条车站记录，得到「在这个站有板面」的车次 ID 列表。 */
    fun readStationBoard(offset: Int): IntArray {
        if (offset < 0 || offset + 4 > bytes.size) return IntArray(0)
        val count = int16(offset + 2)
        if (count <= 0 || count > MAX_BOARD_SIZE || offset + 4 + count * 2 > bytes.size) return IntArray(0)
        return Array(count) { int16(offset + 4 + it * 2) }.toIntArray()
    }

    /**
     * 255 进制的 2 字节整数。库里到处用这个权重而不是 256，
     * 上限 255*255+255 = 65280，正好盖住 14889 个车次与 3331 个车站。
     */
    private fun int16(at: Int): Int {
        if (at < 0 || at + 1 >= bytes.size) return -1
        return ((bytes[at].toInt() and 0xFF) * 255) + (bytes[at + 1].toInt() and 0xFF)
    }

    /** 4 字节的 `yyyyMMdd`：128 进制小端（b0 + b1·128 + b2·16384 + b3·2097152）。 */
    private fun int32(at: Int): Int {
        if (at < 0 || at + 3 >= bytes.size) return 0
        return (bytes[at].toInt() and 0xFF) +
            ((bytes[at + 1].toInt() and 0xFF) shl 7) +
            ((bytes[at + 2].toInt() and 0xFF) shl 14) +
            ((bytes[at + 3].toInt() and 0xFF) shl 21)
    }

    private companion object {
        const val MAX_STOPS = 800
        const val MAX_BOARD_SIZE = 20000
        const val MAX_TRAIN_ID = 20000
        const val MAX_STATION_ID = 5000
    }
}

/** `t.rule`：每行 `<周期> <开行位图>`，**按下标**取用（ruleIndex 就是行号，不是查表键）。 */
object RunningRuleTable {
    fun parse(text: String): List<RunningRule> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .mapNotNull { line ->
                val parts = line.split(' ', '\t').filter { it.isNotBlank() }
                if (parts.size < 2) return@mapNotNull null
                val period = parts[0].toLongOrNull() ?: return@mapNotNull null
                val mask = parts[1].toLongOrNull() ?: return@mapNotNull null
                RunningRule(period.toInt(), mask)
            }
            .toList()
}

/**
 * 某天到底开不开。名字与界面模型 `data.model.RunningDays`（那张三个月开行日历）刻意分开：
 * 这里只回答判定，那张是渲染用的数据，两者同名会在同一文件里互相挡路。
 *
 * 语义照路路通 `Rule.outOfDate()`：
 * 先卡起止日期，再做「以 startDate 为原点、每 period 天一循环、mask 标记循环内哪几天开行」的位判断。
 */
enum class RunningStatus(val label: String) {
    Running("开行"),
    NotStarted("未到开行期"),
    Expired("已过运行期"),
    OutOfRule("当天不开行"),
    ;

    val isRunning: Boolean get() = this == Running
}

object RunningCheck {

    /** `date`/`startDate`/`endDate` 都是 `yyyyMMdd` 整数。rule 为空（或周期 ≤1）时不做周期判断。 */
    fun status(date: Int, startDate: Int, endDate: Int, rule: RunningRule?): RunningStatus {
        if (date < startDate) return RunningStatus.NotStarted
        if (date > endDate) return RunningStatus.Expired
        val r = rule ?: return RunningStatus.Running
        if (r.isEveryDay) return RunningStatus.Running
        val day = dayDiff(startDate, date)
        if (day < 0) return RunningStatus.Running
        // 路路通的原式：1 << (距始发日天数 % 周期) 再与位图相与，位为 0 就是这一天不开
        val bit = 1L shl (day % r.period)
        return if ((bit and r.mask) == 0L) RunningStatus.OutOfRule else RunningStatus.Running
    }

    /** 两个 `yyyyMMdd` 之间的天数（date2 - date1）。 */
    fun dayDiff(date1: Int, date2: Int): Int {
        val d1 = date1.toEpochDaySafe()
        val d2 = date2.toEpochDaySafe()
        return if (d1 < 0 || d2 < 0) 0 else (d2 - d1).toInt()
    }

    /**
     * [afterDate] 之后最近的一个开行日（`yyyyMMdd`），扫不到返回 null。
     *
     * 只往后看，最多 [maxScanDays] 天、且不越过 [endDate] —— `20991231` 这类长期哨兵
     * 不会让这里扫几十年。`NotStarted` 时通常正好落在 startDate，但 startDate 当天也可能
     * 被周期位图排除，所以仍然逐日判定，不直接返回 startDate。
     */
    fun nextRunningDate(
        afterDate: Int,
        startDate: Int,
        endDate: Int,
        rule: RunningRule?,
        maxScanDays: Int = 366,
    ): Int? {
        var cursor = afterDate
        repeat(maxScanDays) {
            val next = shift(cursor, 1)
            // 日期解不出来时 shift 原样返回，继续扫只会空转，直接放弃
            if (next == cursor || next > endDate) return null
            if (status(next, startDate, endDate, rule).isRunning) return next
            cursor = next
        }
        return null
    }

    /**
     * `yyyyMMdd` 平移 [days] 天（可为负）。库里有非法日期，解不出来就原样返回，
     * 让调用方的判定退回到「按既有日期判」而不是凭空造一个日。
     */
    fun shift(date: Int, days: Int): Int {
        val epoch = date.toEpochDaySafe()
        if (epoch < 0) return date
        val moved = java.time.LocalDate.ofEpochDay(epoch + days)
        return moved.year * 10000 + moved.monthValue * 100 + moved.dayOfMonth
    }

    /** 不引 java.time 的日历换算（库里有非法日期，解不出来就返回 -1 表示「判不了，按开行处理」）。 */
    private fun Int.toEpochDaySafe(): Long {
        val y = this / 10000
        val m = (this / 100) % 100
        val d = this % 100
        if (y < 1900 || m !in 1..12 || d !in 1..31) return -1
        return java.time.LocalDate.of(y, m, d).toEpochDay()
    }
}
