package com.crinfo.app.data.offline

import com.crinfo.app.data.offline.OfflineBinary.date128
import com.crinfo.app.data.offline.OfflineBinary.id255
import com.crinfo.app.data.offline.OfflineBinary.u8
import com.crinfo.app.data.offline.OfflineBinary.utf8

/** 站级担当细分的一个生效区间。`text` 是路路通 `getHcdd` 取回的那段文字（本版本没有样本）。 */
data class DutyPeriod(val startDate: Int, val endDate: Int, val text: String)

/** 某车次在某一站的担当细分（按日期区间分多段）。 */
data class StationDuty(val stationId: Int, val periods: List<DutyPeriod>)

/**
 * extra.dat 表2 到表7。表1（企业名表）仍由 `OfflineDbParser.parseEnterprises` 负责，
 * 这里按同样的走步**跳过后**再接着解，所以两份解的是同一段字节、不会互相错开。
 *
 * 本版本（20261108）真库实测：
 * - 表2 有 14753 条「车次 → 担当企业代码」，但 `stationCnt` **全为 0** —— 也就是担当是车次级、
 *   没有站级细分。旧记忆里「库里建不出担当」因此不成立，`dutyCodeByTrainId` 就是答案。
 * - 表3/4/5 是三档复兴号名单（4085 / 333 / 795），官方 `getFuXingHaoType` 按 1→2→3 依次 put，
 *   **靠后的名单覆盖靠前的**（真库 `C101` 同时出现在表3 和表5，最终算 3 档）。这里保持同一口径。
 * - 表6 是 1073 条「车次 → 调向站 ID」，111 条有多个站，正是车型备注里 `石家庄调向` 的数据源。
 * - 表7 的 122 个 ID 全是 S 字头（市域/市郊），对应官方 `isSTrain`。
 */
class ExtraData private constructor(
    val declared: Int,
    val dutyCodeByTrainId: IntArray,
    val stationDuties: Map<Int, List<StationDuty>>,
    val fuXingLevelByTrainId: IntArray,
    val turnStationIds: Map<Int, IntArray>,
    val suburbanTrainIds: Set<Int>,
    /** 走完全部表剩下的字节数。真库实测 0。 */
    val residual: Int,
) {
    /** 车次 ID → 担当企业代码，没有记录返回 null（和「代码 0」区分开）。 */
    fun dutyCode(trainId: Int): Int? = dutyCodeByTrainId.getOrNull(trainId)?.takeIf { it >= 0 }

    fun fuXingLevel(trainId: Int): Int = fuXingLevelByTrainId.getOrElse(trainId) { 0 }

    fun isSTrain(trainId: Int): Boolean = trainId in suburbanTrainIds

    /**
     * 某车某站的担当文字，按查询日命中区间；没有区间命中时官方退回**最后一段**
     * （`getHcdd` 的循环就是这么写的），这里保持同一行为，免得两边解出来的结果不一致。
     */
    fun dutyText(trainId: Int, stationId: Int, date: Int): String? =
        stationDuties[trainId]?.firstOrNull { it.stationId == stationId }?.periods?.let { periods ->
            periods.firstOrNull { date in it.startDate..it.endDate }?.text ?: periods.last().text
        }

    companion object {
        private const val NO_DUTY = -1

        val EMPTY = ExtraData(0, IntArray(0), emptyMap(), IntArray(0), emptyMap(), emptySet(), 0)

        /**
         * [trainCount] 取自 t.i，用来把「按车次 ID 索引」的几张表铺成定长整型数组 ——
         * 14753 条用装箱 Map 存就是十几 MB，铺成 IntArray 只要 60KB。
         */
        fun parse(b: ByteArray, trainCount: Int): ExtraData {
            var p = skipLengthPrefixedStrings(b, id255(b, 0))
            if (trainCount <= 0) return ExtraData(0, IntArray(0), emptyMap(), IntArray(0), emptyMap(), emptySet(), (b.size - p).coerceAtLeast(0))
            val duty = IntArray(trainCount) { NO_DUTY }
            val level = IntArray(trainCount)
            val stationDuties = HashMap<Int, List<StationDuty>>()
            val count = id255(b, p)
            if (count !in 0..60000) return ExtraData(count.coerceAtLeast(0), duty, stationDuties, level, emptyMap(), emptySet(), b.size - p)
            p += 2
            var read = 0
            while (read < count && p + 5 <= b.size) {
                val trainId = id255(b, p)
                val code = id255(b, p + 2)
                val stations = u8(b, p + 4)
                p += 5
                read++
                if (trainId in duty.indices) duty[trainId] = code
                if (stations == 0) continue
                val perStation = ArrayList<StationDuty>(stations)
                var broken = false
                repeat(stations) {
                    if (broken) return@repeat
                    val stationId = id255(b, p)
                    val units = u8(b, p + 2)
                    if (stationId < 0 || units < 0) {
                        broken = true
                        return@repeat
                    }
                    p += 3
                    val periods = ArrayList<DutyPeriod>(if (units in 1..64) units else 0)
                    repeat(units) {
                        if (broken) return@repeat
                        val start = date128(b, p)
                        val end = date128(b, p + 4)
                        val len = u8(b, p + 8)
                        if (len < 0 || p + 9 + len > b.size) {
                            broken = true
                            return@repeat
                        }
                        periods.add(DutyPeriod(start, end, utf8(b, p + 9, len).orEmpty()))
                        p += 9 + len
                    }
                    perStation.add(StationDuty(stationId, periods))
                }
                if (broken) break
                stationDuties[trainId] = perStation
            }
            val turn = HashMap<Int, IntArray>()
            val suburban = HashSet<Int>()
            // 表3/4/5：三档名单，按官方顺序 1→2→3 依次覆盖
            for (tier in 1..3) {
                val n = id255(b, p)
                if (n !in 0..60000) return ExtraData(count, duty, stationDuties, level, turn, suburban, b.size - p)
                p += 2
                for (i in 0 until n) {
                    val id = id255(b, p)
                    if (id < 0) break
                    p += 2
                    if (id in level.indices) level[id] = tier
                }
            }
            val nTurn = id255(b, p)
            if (nTurn in 0..60000) {
                p += 2
                var i = 0
                while (i < nTurn && p + 3 <= b.size) {
                    val trainId = id255(b, p)
                    val n = u8(b, p + 2)
                    p += 3
                    if (n < 0 || p + n * 2 > b.size) break
                    turn[trainId] = IntArray(n) { id255(b, p + it * 2) }
                    p += n * 2
                    i++
                }
            }
            val nS = id255(b, p)
            if (nS in 0..60000) {
                p += 2
                for (i in 0 until nS) {
                    val id = id255(b, p)
                    if (id < 0) break
                    p += 2
                    suburban.add(id)
                }
            }
            return ExtraData(count, duty, stationDuties, level, turn, suburban, b.size - p)
        }

        /**
         * 表1 的走步：`[2B(255 进制) 条数]` + `count` 条 `[1B 长度][UTF-8]`，只跳不解。
         * **起点必须是 2**（跳过那 2 个条数字节）—— 早先从 0 起走会把条数字节当成长度，
         * 整张表偏移一条，真库上正好表现为「残留 1 字节」，很不容易看出来。
         */
        private fun skipLengthPrefixedStrings(b: ByteArray, count: Int): Int {
            if (count !in 0..60000) return 2
            var p = 2
            repeat(count) {
                val len = u8(b, p)
                if (len < 0 || p + 1 + len > b.size) return p
                p += 1 + len
            }
            return p
        }
    }
}
