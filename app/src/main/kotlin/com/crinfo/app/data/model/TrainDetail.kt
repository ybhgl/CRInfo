package com.crinfo.app.data.model

/** `5小时54分` / `32分`，全程时长与逐站累计运行时分共用。 */
fun chineseDuration(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes / 60 == 0 -> "${minutes}分"
    else -> "${minutes / 60}小时${minutes % 60}分"
}

/**
 * 时刻表一行：图定到发来自 wxxcx，实时正晚点来自 liechetime（可为空）。
 * 两个源的停靠站顺序实测一致，但 liechetime 只给途经站、不给坐标与检票口。
 * 界面主时刻走实时口径——正晚点既然拿到了，时刻表就该报列车实际几点到发；
 * 图定退到副文案，正点时两者相同则不重复。进度判定共用同一份晚点分钟，见 `trainProgress`。
 */
data class ScheduleRow(
    val stop: TrainStop,
    val live: DelayStop?,
) {
    /** 到/发取较大者；正点、通过或未开行时为 0。 */
    val delayMinutes: Int get() = maxOf(live?.arriveDelayMinutes ?: 0, live?.departDelayMinutes ?: 0)

    val isDelayed: Boolean get() = delayMinutes > 0

    /** 到/发的显示时刻：接口实时值优先，只给分钟数时按图定推算。 */
    val displayArrive: String? get() = liveTime(stop.arriveTime, live?.realArrive, live?.arriveDelayMinutes ?: 0)
    val displayDepart: String? get() = liveTime(stop.departTime, live?.realDepart, live?.departDelayMinutes ?: 0)

    val arriveLate: Boolean get() = displayArrive != null && displayArrive != stop.arriveTime
    val departLate: Boolean get() = displayDepart != null && displayDepart != stop.departTime

    /** 主时刻换成实际值之后，图定才需要另说一句；正点时说出来只是重复。 */
    val plannedArriveText: String? get() = stop.arriveTime.takeIf { arriveLate }
    val plannedDepartText: String? get() = stop.departTime.takeIf { departLate }

    /** 晚点把时刻推过零点时，跨天徽标跟着进一天。 */
    val dayShift: Int get() = maxOf(midnightShift(stop.arriveTime, displayArrive), midnightShift(stop.departTime, displayDepart))
}

/**
 * 单个时刻的实时口径。正点时接口的 `arrivalTime` 与图定完全相同，所以「接口值与图定不同」
 * 就足以判定要用接口值；接口只给晚点分钟、不给时刻时按分钟推，推不出合法时刻就退回图定。
 */
private fun liveTime(planned: String?, real: String?, delayMinutes: Int): String? {
    if (planned == null) return null
    if (real != null && real != planned) return real
    if (delayMinutes == 0) return planned
    return addMinutes(planned, delayMinutes) ?: planned
}

/** 只在 0..1439 里打转：跨零点后的分钟数，界面上就是同一天的凌晨。 */
private fun addMinutes(time: String, minutes: Int): String? {
    val base = timeToMinutes(time)
    if (base < 0) return null
    val shifted = ((base + minutes) % 1440 + 1440) % 1440
    return "%02d:%02d".format(shifted / 60, shifted % 60)
}

/**
 * 显示时刻数值上比图定更早，只可能是推过了零点。正晚点只会出现「晚」，
 * 不存在把时刻往前拽的早点，所以这里不必区分方向。
 */
private fun midnightShift(planned: String?, displayed: String?): Int {
    if (planned == null || displayed == null || displayed == planned) return 0
    val base = timeToMinutes(planned)
    val shown = timeToMinutes(displayed)
    if (base < 0 || shown < 0) return 0
    return if (shown < base) 1 else 0
}

/** 车次查询的完整结果：路线、时刻表、交路、担当、路局、票价与开行计划。 */
data class TrainDetail(
    val timetable: TrainTimetable,
    val fromName: String,
    val toName: String,
    /** 担当路局名，接口只给「上海」这样的短名，界面补「局」。 */
    val bureauName: String,
    /** 全程里程（公里），0 表示板面没捞到。 */
    val totalKm: Int,
    val rows: List<ScheduleRow>,
    val runningDays: RunningDays? = null,
    /**
     * 全程座席与票价。只有联网查、查询日在预售期内、且（查询日就是今天时）图定发车
     * 不早于当前时刻 +15 分钟，余票接口又命中本车次时才有值；
     * 离线态与已临近发车的当日车次恒为 null，界面据此整块不显示席别区。
     */
    val seats: List<SeatOffer>? = null,
    /** 车底详情（编组、定员、设施、图片），接口没给就为空。 */
    val carDetail: CarDetail? = null,
    /** 12306 车底接口空响应时，第三方交路服务补的车底号。 */
    val emuFallback: EmuFallback? = null,
    /** 路路通车型参考图；官方车底存在时也会查，供「车辆图片」弹窗里切换图片源。 */
    val lltskb: LltskbCarImages? = null,
) {
    val trainCode: String get() = timetable.trainCode
    val stops: List<TrainStop> get() = timetable.stops
    val stopCount: Int get() = timetable.stopCount

    /** 终到站在次日：概览卡的到达时刻要挂「+1」。 */
    val crossesDay: Boolean get() = (timetable.stops.lastOrNull()?.dayDiff ?: 0) > 0

    val departTime: String? get() = timetable.stops.firstOrNull()?.departTime ?: timetable.startTime
    val arriveTime: String? get() = timetable.stops.lastOrNull()?.arriveTime ?: timetable.arriveTime

    /** 图定全程时长（分钟）：跨天用终到站的 dayDifference 折算。 */
    val durationMinutes: Int
        get() {
            val head = timetable.stops.firstOrNull() ?: return -1
            val tail = timetable.stops.lastOrNull() ?: return -1
            val depart = timeToMinutes(head.departTime ?: head.arriveTime ?: return -1)
            val arrive = timeToMinutes(tail.arriveTime ?: tail.departTime ?: return -1)
            if (depart < 0 || arrive < 0) return -1
            return arrive + tail.dayDiff * 1440 - depart
        }

    val durationText: String get() = chineseDuration(durationMinutes)

    /** 「上海局 · CR400BF-A」这样的副标题，缺一块就只显示另一块。 */
    val subtitle: String
        get() = listOf(bureauName, timetable.trainStyle)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

    val tags: List<TrainTag>
        get() = trainTags(timetable.trainStyle, seats?.any { it.seat.isSleeper } == true)
}

/**
 * 合并图定与实时。liechetime 的车站顺序与 wxxcx 一致，优先按站序配对，
 * 站序缺失或数量对不上时退回站名——普速车两源都可能有「同名不同站」的极小概率，
 * 但站名比坐标可靠，且失败只是少显示晚点，不会显示错误数据。
 */
fun mergeSchedule(timetable: TrainTimetable, delay: TrainDelay?): List<ScheduleRow> {
    if (delay == null || delay.stops.isEmpty()) return timetable.stops.map { ScheduleRow(it, null) }
    val byOrder = delay.stops.mapNotNull { s -> s.order.toIntOrNull()?.let { it to s } }.toMap()
    val byName = delay.stops.associateBy { it.stationName }
    return timetable.stops.map { stop ->
        ScheduleRow(stop, byOrder[stop.order] ?: byName[stop.name])
    }
}
