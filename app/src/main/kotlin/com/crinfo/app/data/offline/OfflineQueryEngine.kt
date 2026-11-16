package com.crinfo.app.data.offline

import com.crinfo.app.data.Station
import com.crinfo.app.data.model.BoardDirection
import com.crinfo.app.data.model.BoardEntry
import com.crinfo.app.data.model.ScheduleRow
import com.crinfo.app.data.model.TrainDetail
import com.crinfo.app.data.model.TrainStop
import com.crinfo.app.data.model.TrainTimetable
import com.crinfo.app.data.model.Trip
import com.crinfo.app.data.model.chineseDuration

/**
 * 离线查询引擎：把 `t*.dat` 的时刻记录拼成与在线链路同形的结果，界面只需要换数据源。
 *
 * 取数一律**按需**：一趟车只读它自己那一条记录，一张板面只读涉及的站；
 * 候选按分片排序后顺序读，让 [OfflineDbRepository] 的两三片缓存就够了，
 * 不把 14889 趟车的时刻摊在内存里。
 *
 * 依赖注入成三个纯函数（读记录 / 读候选 / 站名落地），是为了能在 JVM 单测里直接验算，
 * 不必把 Android Context 拖进来。
 */
class OfflineQueryEngine(
    private val db: OfflineDb,
    private val readTrain: suspend (Int) -> OfflineTimetable?,
    private val readCandidates: suspend (Int) -> IntArray,
    private val stationOf: (String) -> Station?,
) {

    /** 车次查询的结果。`status` 非 [RunningStatus.Running] 时界面要说清「该日不开行」。 */
    data class OfflineTrain(
        val detail: TrainDetail,
        val status: RunningStatus,
        /** 担当企业名（extra.dat 下标表），空串表示库里没给。 */
        val corporation: String,
        /** 餐车（按查询日命中的那条），null 表示库里没有该车的记录。 */
        val foodCoach: String?,
        /** 图定起止日（`yyyyMMdd` 整数），界面用来解释「为什么这天查不到」。 */
        val startDate: Int,
        val endDate: Int,
        /**
         * 查询日之后最近的图定开行日（`yyyyMMdd`）。只在 [status] 非 Running 时有意义，
         * 已过运行期或 366 天内都扫不到时为 null —— 界面据此决定要不要报「图定某日开行」。
         */
        val nextRunningDate: Int?,
    )

    private val carTypeByTrain: Map<Int, String> by lazy {
        // 车底型号来自 jlb 的担当组；组里的号可能是 `G1956/G1957` 这种合并写法
        buildMap {
            db.rakes.forEach { rake ->
                rake.group.forEach { part ->
                    part.split('/').map { it.trim().removePrefix("0") }.forEach { code ->
                        val id = db.trainIdOf(code)
                        if (id != null && !containsKey(id)) put(id, rake.carType)
                    }
                }
            }
        }
    }

    /**
     * 车次查询。查不到车次或库里没有该记录时返回 null（由界面决定报「离线库未收录」）。
     */
    suspend fun train(code: String, date: String): OfflineTrain? {
        val trainId = db.trainIdOf(code) ?: return null
        val record = readTrain(trainId) ?: return null
        val stops = record.stops.mapIndexed { i, row ->
            stopOf(record, i, row)
        }
        val queryDate = date.replace("-", "").toIntOrNull() ?: 0
        val rule = db.rules.getOrNull(record.ruleIndex)
        val status = RunningCheck.status(
            date = queryDate,
            startDate = record.startDate,
            endDate = record.endDate,
            rule = rule,
        )
        val first = stops.first()
        val last = stops.last()
        val style = carTypeByTrain[trainId].orEmpty()
        val timetable = TrainTimetable(
            trainCode = db.trainCodes.getOrElse(trainId) { code },
            // sp.dat 表2 就是「下标即车次 ID → 12306 的 12 位 trainNo」，离线态也能给出真值
            trainNo = db.extras.trainNoOf(trainId).orEmpty(),
            startDate = date,
            fromTelecode = first.telecode,
            toTelecode = last.telecode,
            startTime = first.departTime,
            arriveTime = last.arriveTime,
            trainStyle = style,
            // 「交路车型」那一行是给在线车底接口与交路服务用的；离线只有 jlb 一个车型来源，
            // 填两遍会让担当卡出现一模一样的两行。
            jiaoluTrainStyle = "",
            jiaoluDept = "",
            jiaoluCorporation = "",
            corporationCode = "",
            statusText = status.takeIf { !it.isRunning }?.label,
            warmTips = null,
            stops = stops,
        )
        val detail = TrainDetail(
            timetable = timetable,
            fromName = first.name,
            toName = last.name,
            bureauName = OfflineFallback.corporationOfTrainId(db, trainId),
            // 全程里程直接取末站累计值：库里本来就带，不必再去板面捞
            totalKm = record.stops.lastOrNull()?.distanceKm ?: 0,
            rows = stops.map { ScheduleRow(it, null) },
            // 离线也给开行日历：起止日期 + 周期位图都在库里，逐天判定就是权威口径
            runningDays = OfflineFallback.runningDays(db, record),
            // 席别种类来自 xw 位图（票量与价格库里没有 → Unknown，界面按无票非高亮出字）
            seats = db.extras.seatOffers(trainId),
            carDetail = null,
            emuFallback = null,
        )
        return OfflineTrain(
            detail = detail,
            status = status,
            corporation = detail.bureauName,
            foodCoach = foodCoachOf(trainId, queryDate),
            startDate = record.startDate,
            endDate = record.endDate,
            nextRunningDate = if (status.isRunning) null
            else RunningCheck.nextRunningDate(queryDate, record.startDate, record.endDate, rule),
        )
    }

    /**
     * 车站查询的离线板面。
     *
     * `s*.dat` 给的只是**候选**：城市主站（北京、上海、广州…）和部分县市站挂的是同城合集，
     * 里面混着根本不停这一站的车（实测北京站候选 1389，真正停靠只有 263）。
     * 所以每条候选都要回到 `t*.dat` 的记录里核对「确实有本站这一行」，
     * 时刻、跨天、终到站都以那条记录为准。
     *
     * ⚠️ 「查询日」在这里是**到站日**，不是列车始发日：库里的时刻只以始发日为原点，
     * 所以每条候选要按自己的跨天数把日期折回始发日再判开行（见 [runsOn]）。
     * 早先直接拿查询日当始发日判，结果是这张表演的是「今天始发的车」——
     * 带 `+1/+2` 的那些其实明天、大后天才到站，而真当天到站的长途车（昨天、前天始发）一条都没有。
     */
    suspend fun board(stationName: String, date: String): List<BoardEntry> {
        val stationId = db.stationIdByName[stationName.trim()] ?: return emptyList()
        val queryDate = date.replace("-", "").toIntOrNull() ?: 0
        // 候选按分片排序：同一层的记录连续命中，缓存两三片就够走完一张板面
        val candidates = readCandidates(stationId).distinct().sortedBy { (it + 1) % TRAIN_SHARDS }
        val out = ArrayList<BoardEntry>()
        for (id in candidates) {
            val record = readTrain(id) ?: continue
            val index = record.stops.indexOfFirst { it.stationId == stationId }
            if (index < 0) continue
            val row = record.stops[index]
            val origin = index == 0
            val terminal = index == record.stops.lastIndex
            val arriveAbs = absoluteMinutes(record.stops, index, useDepart = false)
            val departAbs = arriveAbs + row.dwellMinutes
            val code = db.trainCodes.getOrElse(id) { "" }
            val style = carTypeByTrain[id].orEmpty()
            // 终到本站的只进到达屏、始发本站的只进出发屏、经停的两张都进 —— 与在线拆屏同规则。
            // 两张屏各按自己的跨天数折日期：经停车「到 +0 / 发 +1」这种跨零点停靠，两行都属查询日。
            if (!terminal && runsOn(record, queryDate, departAbs / 1440)) {
                out += BoardEntry(
                    direction = BoardDirection.Departure,
                    trainCode = code,
                    trainNo = db.extras.trainNoOf(id).orEmpty(),
                    time = clock(departAbs),
                    // 到站日就是查询日，跨天数没有可演的份：那是「距列车始发日」的信息，车站页不区分
                    dayDiff = 0,
                    otherEnd = db.stationNames.getOrElse(record.stops.last().stationId) { "" },
                    // 检票口离线一律没有（那是实时源字段）；plat.dat 的股道只当参考站台显示
                    platform = null,
                    referencePlatform = db.extras.platformOf(id, stationId),
                    trainStyle = style,
                    className = "",
                    distanceKm = 0,
                    stationOrder = index + 1,
                    live = null,
                )
            }
            if (!origin && runsOn(record, queryDate, arriveAbs / 1440)) {
                out += BoardEntry(
                    direction = BoardDirection.Arrival,
                    trainCode = code,
                    trainNo = db.extras.trainNoOf(id).orEmpty(),
                    time = clock(arriveAbs),
                    dayDiff = 0,
                    otherEnd = db.stationNames.getOrElse(record.stops.first().stationId) { "" },
                    platform = null,
                    referencePlatform = db.extras.platformOf(id, stationId),
                    trainStyle = style,
                    className = "",
                    distanceKm = 0,
                    stationOrder = index + 1,
                    live = null,
                )
            }
        }
        return out.sortedBy { it.sortMinutes }
    }

    /**
     * 这趟车在**查询日当天**是否停靠本站。偏移 k 天到站的车，始发日是查询日减 k，
     * 开行判定（有效期 + 周期位图）要打在那个日期上。
     * 在线板面在服务端做的正是这一步：实测隔日开的 K329 只出现在「始发日 = 到站日前一天」那张板面里，
     * 到站日往前推一天它就没了。
     */
    private fun runsOn(record: OfflineTimetable, queryDate: Int, offsetDays: Int): Boolean =
        RunningCheck
            .status(
                RunningCheck.shift(queryDate, -offsetDays),
                record.startDate,
                record.endDate,
                db.rules.getOrNull(record.ruleIndex),
            )
            .isRunning

    /**
     * 站到站的直达车次。离线**没有余票与票价**（`xw.dat` 只给席别种类，票价族在本版库里是空壳），
     * 席别一律 `SeatStatus.Unknown`：显示成非高亮的「无」，但不算售罄、不参与「只看有票」过滤；
     * 区间里程用两站的累计里程相减，库里给的就是权威值。
     */
    suspend fun trips(fromName: String, toName: String, date: String): List<Trip> =
        tripsBetween(listOf(fromName), listOf(toName), date)

    /**
     * **两侧各允许多站**的直达查询（模糊查询的同城扩站）。
     * 每个车次只出一条：乘车段取「先停靠的出发侧站 → 最后停靠的到达侧站」，
     * `fromCode/toCode` 落**实际乘车站** —— 关掉模糊时界面按所选两站精确过滤，
     * 与余票模式的客户端过滤同口径；时刻与里程都按这一乘车段算。
     */
    suspend fun tripsBetween(fromNames: List<String>, toNames: List<String>, date: String): List<Trip> {
        val fromIds = fromNames.mapNotNull { db.stationIdByName[it.trim()] }.toSet()
        val toIds = toNames.mapNotNull { db.stationIdByName[it.trim()] }.toSet()
        if (fromIds.isEmpty() || toIds.isEmpty()) return emptyList()
        val queryDate = date.replace("-", "").toIntOrNull() ?: 0
        // 候选剪枝 = (出发侧各站并集) ∩ (到达侧各站并集)。
        // 绝不能把两侧所有站一起求交 —— 车只要停每侧任意一站即可，
        // 全站求交会把「只停上海虹桥、不停上海西」的 G1 这类正主错杀（真库测试抓出来的）。
        // 并集里混着 s 片同城合集的不停车，最终仍靠逐条回 T 记录核对「真的停到两侧各一站」。
        val fromUnion = HashSet<Int>()
        fromIds.forEach { fromUnion.addAll(readCandidates(it).toList()) }
        val toUnion = HashSet<Int>()
        toIds.forEach { toUnion.addAll(readCandidates(it).toList()) }
        val candidates = fromUnion.intersect(toUnion)
        val out = ArrayList<Trip>()
        for (id in candidates.orEmpty().sortedBy { (it + 1) % TRAIN_SHARDS }) {
            val record = readTrain(id) ?: continue
            val i = record.stops.indexOfFirst { it.stationId in fromIds }
            val j = record.stops.indexOfLast { it.stationId in toIds }
            if (i < 0 || j < 0 || i >= j) continue
            if (!RunningCheck.status(queryDate, record.startDate, record.endDate, db.rules.getOrNull(record.ruleIndex)).isRunning) {
                continue
            }
            val aRow = record.stops[i]
            val bRow = record.stops[j]
            val departAbs = absoluteMinutes(record.stops, i, useDepart = false) + aRow.dwellMinutes
            val arriveAbs = absoluteMinutes(record.stops, j, useDepart = false)
            val duration = arriveAbs - departAbs
            if (duration <= 0) continue
            val code = db.trainCodes.getOrElse(id) { "" }
            val actualFrom = db.stationNames.getOrElse(aRow.stationId) { "" }
            val actualTo = db.stationNames.getOrElse(bRow.stationId) { "" }
            out += Trip(
                trainCode = code,
                trainNo = db.extras.trainNoOf(id).orEmpty(),
                date = date,
                fromCode = stationOf(actualFrom)?.code.orEmpty(),
                toCode = stationOf(actualTo)?.code.orEmpty(),
                fromName = actualFrom,
                toName = actualTo,
                startCode = stationOf(db.stationNames.getOrElse(record.stops.first().stationId) { "" })?.code.orEmpty(),
                endCode = stationOf(db.stationNames.getOrElse(record.stops.last().stationId) { "" })?.code.orEmpty(),
                startName = db.stationNames.getOrElse(record.stops.first().stationId) { "" },
                endName = db.stationNames.getOrElse(record.stops.last().stationId) { "" },
                departTime = clock(departAbs),
                arriveTime = clock(arriveAbs),
                durationText = chineseDuration(duration),
                canWait = true,
                // 离线只有席别种类、没有余票：给 Unknown 样式而不是空表，
                // 「铺」徽标与席别区都靠它，而售罄判定与「只看有票」会因 ticketKnown=false 自动让路
                seats = db.extras.seatOffers(id).orEmpty(),
                trainStyle = carTypeByTrain[id].orEmpty(),
                distanceKm = (bRow.distanceKm - aRow.distanceKm).coerceAtLeast(0),
            )
        }
        return out.sortedBy { clockToMinutes(it.departTime) + it.arrivesNextDayToInt() * 1440 }
    }

    /**
     * 库里有没有这一站。**必须先问它**：`board/trips` 对没收录的站也返回空列表，
     * 直接把空当「今天没车」会把「库里没这个站」说成「无车次」（项目站表有 440 个站是库里没有的）。
     */
    fun knowsStation(name: String): Boolean = db.stationIdByName.containsKey(name.trim())

    /** 站名 → 项目站表；库里 364 个站是 12306 站表没有的小站，落不到就只给站名。 */
    /**
     * 停站行的电报码。先问 12306 站表（与在线同一口径），站表没有再退库里那份
     * （routes §1 节点略号 + sn.dat 表3 的 364 条补充）。
     *
     * 换号提示与逐站里程都以电报码为键，所以这里给不出码就等于那一行的信息整块消失 ——
     * 只问站表时项目站表缺的那些站（实测有 440 个站库里没、反之也有库里站名与站表写法不一致的）
     * 会白丢，故加这层回退。
     */
    private fun telecodeOf(stationId: Int): String {
        val name = db.stationNames.getOrElse(stationId) { "" }
        if (name.isBlank()) return ""
        return stationOf(name)?.code.orEmpty().ifBlank { db.telecodeOfStation(name).orEmpty() }
    }

    private fun foodCoachOf(trainId: Int, date: Int): String? = db.foodCoachOf(trainId, date)

    /**
     * 停站行 → 时刻表一行。
     *
     * 库里的时刻只有「到达时分 + 停站分」，所以：始发站的到达位其实是发车时刻（给 departTime），
     * 终到站不给 departTime，中间站的出发时刻 = 到达 + 停站分。
     * 跨天靠累计分钟不回退来推，不猜日期。
     *
     * 逐站车次号来自 `xw.dat` 的「按里程切段」表（官方 `get_exact_train_code` 同一算法）：
     * 拿本站的累计里程去比段表阈值，落在哪段就显示那一段的号。没有段表的车原样返回整串，
     * 于是每一站都相同 → 换号提示自然不出现，不会凭空造出换号站。
     */
    private fun stopOf(record: OfflineTimetable, index: Int, row: TimetableStop): TrainStop {
        val arriveAbs = absoluteMinutes(record.stops, index, useDepart = false)
        val departAbs = arriveAbs + row.dwellMinutes
        val origin = index == 0
        val terminal = index == record.stops.lastIndex
        val name = db.stationNames.getOrElse(row.stationId) { "" }
        return TrainStop(
            name = name,
            telecode = telecodeOf(row.stationId),
            order = index,
            arriveTime = if (origin) null else clock(arriveAbs),
            departTime = if (terminal) null else clock(departAbs),
            dwellMinutes = row.dwellMinutes,
            minutesFromStart = arriveAbs - absoluteMinutes(record.stops, 0, useDepart = false),
            dayDiff = arriveAbs / 1440,
            lat = null,
            lon = null,
            wicket = null,
            waitingRoom = null,
            exit = null,
            stationBureauCode = "",
            stationCorporation = "",
            ticketDelay = null,
            stationTrainCode = db.extras.exactCode(record.trainId, db.trainCodes.getOrElse(record.trainId) { "" }, row.distanceKm),
        )
    }

    private companion object {
        const val TRAIN_SHARDS = 20
    }
}

/**
 * 第 [index] 站相对始发日的绝对分钟（可能已跨过 24:00）。
 *
 * 库里每行只存「时、分」两个字节，跨天不落字段，只能顺着站序推进：时刻比前一站小就是过零点。
 * 末站 `useDepart` 用它的到达时刻即可，出发时刻 = 到达 + 停站分 由调用方加。
 */
internal fun absoluteMinutes(rows: List<TimetableStop>, index: Int, useDepart: Boolean): Int {
    var prev = -1
    var current = 0
    for (i in 0..index) {
        val row = rows[i]
        var minutes = row.arriveHour * 60 + row.arriveMinute
        if (useDepart && i == index) minutes += row.dwellMinutes
        if (prev >= 0) {
            while (minutes < prev) minutes += 1440
        }
        current = minutes
        prev = minutes
    }
    return current
}

internal fun clock(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

private fun clockToMinutes(text: String): Int {
    val parts = text.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return -1
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return -1
    return h * 60 + m
}

/** Trip 只给了「是否跨天」的布尔，排序时折回整数。 */
private fun Trip.arrivesNextDayToInt(): Int = if (arrivesNextDay) 1 else 0
