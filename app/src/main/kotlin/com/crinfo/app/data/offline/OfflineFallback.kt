package com.crinfo.app.data.offline

import com.crinfo.app.data.model.BoardEntry
import com.crinfo.app.data.model.OperatingBureau
import com.crinfo.app.data.model.RunningDays
import com.crinfo.app.data.model.TrainTimetable
import com.crinfo.app.data.model.Trip

/**
 * 「联网取不到、离线库里补」的共享入口：逐站/全程里程、担当局段、开行日历、车票列表的车型与区间里程。
 *
 * 库里 T 记录的逐站累计里程与 12306 板面 distance 同源
 * （G1 实测 0/210/314/535/1023/1237/1318 与板面逐字一致），
 * 所以板面空窗（凌晨图未灌满、终到站板面捞不到该车、逐站板面返回 0）时，
 * 库值可以直接顶上，不需要第二套口径。
 *
 * 补进来的前提是**首末站名与线上时刻表对得上**：库里是图定，线上可能已改区段或改终到，
 * 对不上就宁可显示缺——错里程比没里程伤害大。跨源一律按站名对齐（库里没有 12306 车站代码）。
 */
object OfflineFallback {

    /** 全程兜底里程；0 表示库没装/没收录/首末站不符，维持在线的空值。 */
    fun totalKm(db: OfflineDb?, record: OfflineTimetable?, timetable: TrainTimetable): Int {
        if (db == null || record == null || record.stops.isEmpty() || timetable.stops.isEmpty()) return 0
        if (!endpointsMatch(db, record, timetable)) return 0
        return record.stops.last().distanceKm
    }

    /**
     * 逐站兜底里程：电报码 → 相对该趟车始发站的累计公里。
     * 线上时刻表没停靠的站不给值；电报码为空的站（库里收录的 12306 站表外小站）单独缺失，不影响其它站。
     */
    fun stopKm(db: OfflineDb?, record: OfflineTimetable?, timetable: TrainTimetable): Map<String, Int> {
        if (db == null || record == null || record.stops.isEmpty() || timetable.stops.isEmpty()) return emptyMap()
        if (!endpointsMatch(db, record, timetable)) return emptyMap()
        val telecodeByName = timetable.stops.associate { it.name.trim() to it.telecode }
        return buildMap {
            record.stops.forEach { row ->
                val name = db.stationNames.getOrElse(row.stationId) { "" }.trim()
                val telecode = telecodeByName[name] ?: return@forEach
                if (telecode.isNotEmpty()) put(telecode, row.distanceKm)
            }
        }
    }

    private fun endpointsMatch(db: OfflineDb, record: OfflineTimetable, timetable: TrainTimetable): Boolean {
        val first = db.stationNames.getOrElse(record.stops.first().stationId) { "" }.trim()
        val last = db.stationNames.getOrElse(record.stops.last().stationId) { "" }.trim()
        return first.isNotEmpty() && first == timetable.stops.first().name.trim() &&
            last.isNotEmpty() && last == timetable.stops.last().name.trim()
    }

    /**
     * 该车所在的担当车底组（jlb）。段内简写（`G82/79`）先补全再对号，
     * 与 [locateJiaolu] 同一套匹配规则；空驶段（前导 0）不参与——那不载客。
     */
    fun rakeOfTrainId(db: OfflineDb, trainId: Int): JiaoluRake? =
        db.rakes.firstOrNull { rake ->
            rake.group.any { part -> expandSegmentCodes(part).any { db.trainIdOf(it) == trainId } }
        }

    /** 担当局段名（extra.dat 企业表，形如「上局上海段」）；库没装/没收录给空串。 */
    fun corporation(db: OfflineDb?, code: String): String {
        if (db == null) return ""
        val trainId = db.trainIdOf(code) ?: return ""
        return corporationOfTrainId(db, trainId)
    }

    /**
     * 车次 ID → 担当局段名。**两级取数**：
     * ① extra.dat 表2 的车次级担当（真库 14753 条，覆盖 14889 趟里的 99%）；
     * ② 再退 jlb 车底组的担当企业（只有 3862 组车底，单靠它大多数车次会空）。
     *
     * 早先只走 ②，所以离线态「担当」行十趟里有七八趟是空的 —— 库里其实有值，
     * 属于「数据在库里、软件没显示」那一类缺口。两级都查不到才给空串。
     */
    fun corporationOfTrainId(db: OfflineDb, trainId: Int): String =
        db.extras.dutyName(db.enterprises, trainId)
            ?: rakeOfTrainId(db, trainId)?.let { db.enterprises.getOrElse(it.enterpriseCode) { "" } }
            .orEmpty()

    /**
     * 离线开行计划：今天前 90 天起连排到未来 1 年，再按图定起止裁剪，逐天按周期位图判定。
     *
     * 窗口固定而不是铺满整个图定期限 —— `20991231` 这类「长期有效」会拉出几十年月历，
     * 界面翻页既没意义也难看；但 91 天又太短，查不到「这车下次什么时候开」。
     * 裁剪只用图定起止，不猜额外规则；超出期限的天本就不会出现在窗口里。
     */
    fun runningDays(db: OfflineDb, record: OfflineTimetable): RunningDays {
        val rule = db.rules.getOrNull(record.ruleIndex)
        val today = java.time.LocalDate.now()
        val windowStart = today.minusDays(DAYS_BACK)
        val windowEnd = today.plusDays(DAYS_FORWARD)
        val from = maxOf(windowStart, record.startDate.toLocalDateOrNull() ?: windowStart)
        val to = minOf(windowEnd, record.endDate.toLocalDateOrNull() ?: windowEnd)
        val days = buildList {
            var day = from
            while (!day.isAfter(to)) {
                val key = day.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                add(key to RunningCheck.status(key.toInt(), record.startDate, record.endDate, rule).isRunning)
                day = day.plusDays(1)
            }
        }
        // 代码与局段名同源，否则会出现「名字是 extra 的车次级担当、代码还是车底组那套」的错配
        val code = db.extras.dutyCode(record.trainId)
            ?: rakeOfTrainId(db, record.trainId)?.enterpriseCode
            ?: 0
        return RunningDays(
            startDate = "%04d-%02d-%02d".format(record.startDate / 10000, record.startDate / 100 % 100, record.startDate % 100),
            bureau = OperatingBureau(code.toString(), corporationOfTrainId(db, record.trainId)),
            subBureauName = "",
            days = days,
        )
    }

    /**
     * 联网板面缺检票口时，用 `plat.dat` 的图定股道补一行**参考站台**。
     *
     * 口径三条，都是刻意的：
     * 1. 只在 `gateText == null` 时补 —— 实时检票口是当日真值，静态股道不许盖在它上面、也不与它并列显示；
     * 2. 只填 `referencePlatform`，绝不写 `platform` —— 后者会经 `gateText` 冒充检票口；
     * 3. 库里 (车次,站) 没记录就原样留着空，不猜。
     *
     * 联网态的车次号可能是中途换号后的单段号，`trainIdOf` 的分段建键正好接得住。
     */
    fun referencePlatforms(db: OfflineDb?, stationName: String, rows: List<BoardEntry>): List<BoardEntry> {
        if (db == null || rows.none { it.gateText == null && it.referencePlatform == null }) return rows
        val stationId = db.stationIdByName[stationName.trim()] ?: return rows
        val cache = HashMap<String, String?>()
        return rows.map { row ->
            if (row.gateText != null || row.referencePlatform != null) return@map row
            val platform = cache.getOrPut(row.trainCode) {
                db.trainIdOf(row.trainCode)?.let { db.extras.platformOf(it, stationId) }
            }
            if (platform == null) row else row.copy(referencePlatform = platform)
        }
    }

    /**
     * 车票查询列表的车型/区间里程兜底：用离线引擎的同区间产出补**缺项**，
     * 板面已有值一律优先（板面是当日实值，库是图定），库里也没有的原样保留。
     * 库把同车底多车次写成 `G3741/G3744` 一格，分段建键，线上只出现一段也能补上。
     */
    fun enrichTrips(trips: List<Trip>, reference: List<Trip>): List<Trip> {
        if (trips.none { it.trainStyle.isBlank() || it.distanceKm <= 0 }) return trips
        val byCode = buildMap {
            reference.forEach { ref ->
                ref.trainCode.split('/').forEach { putIfAbsent(it.trim().uppercase(), ref) }
            }
        }
        return trips.map { trip ->
            if (trip.trainStyle.isNotBlank() && trip.distanceKm > 0) return@map trip
            val ref = byCode[trip.trainCode.trim().uppercase()] ?: return@map trip
            trip.copy(
                trainStyle = if (trip.trainStyle.isBlank()) ref.trainStyle else trip.trainStyle,
                distanceKm = if (trip.distanceKm <= 0) ref.distanceKm else trip.distanceKm,
            )
        }
    }

    /** 开行计划往前/往后各铺多少天；用图定起止裁剪后才会进日历。 */
    private const val DAYS_BACK = 90L
    private const val DAYS_FORWARD = 365L
}

/** `yyyyMMdd` → LocalDate；库里偶有非法日期，解不出来返回 null，由调用方退回窗口边界。 */
private fun Int.toLocalDateOrNull(): java.time.LocalDate? {
    val y = this / 10000
    val m = this / 100 % 100
    val d = this % 100
    return runCatching { java.time.LocalDate.of(y, m, d) }.getOrNull()
}
