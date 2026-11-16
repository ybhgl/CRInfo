package com.crinfo.app.data.model

/** wxxcx 车站大屏里的一条车次（含通过、临客、担当信息）。 */
data class ScreenTrain(
    val trainCode: String,
    val trainNo: String,
    val fromStation: String,
    val toStation: String,
    val fromTelecode: String,
    val toTelecode: String,
    val arriveTime: String?,
    val departTime: String?,
    val runningTimeText: String,
    val distanceKm: Int,
    val platform: String?,
    val trainStyle: String,
    val className: String,
    val typeName: String,
    val stationBureauCode: String,
    val corporationCode: String,
    val stationCorporation: String,
    val jiaoluCorporation: String,
    val jiaoluDept: String,
    val jiaoluStyle: String,
    val stationOrder: String,
)

/** 出发大屏条目：带实时候车状态与检票口。携程与 liechetime 都归一到这个形状。 */
data class DepartureItem(
    val trainCode: String,
    val toStation: String,
    val departTime: String?,
    val departDateTime: String?,
    val wicket: String?,
    val waitingRoom: String?,
    val state: String,
    val dayDiff: Int,
)

/** 到达大屏条目。 */
data class ArrivalItem(
    val trainCode: String,
    val fromStation: String,
    val arriveTime: String?,
    val exitingPort: String?,
    val state: String,
    val dayDiff: Int,
)

/**
 * liechetime 逐站正晚点。接口里 `localXxx` 与 `xxx` 在正点时完全相同，
 * 按图定/实时二分处理（晚点时实时值才会偏离）。
 */
data class DelayStop(
    val stationName: String,
    val order: String,
    val plannedArrive: String?,
    val plannedDepart: String?,
    val realArrive: String?,
    val realDepart: String?,
    val dwellText: String,
    val departDelayMinutes: Int,
    val arriveDelayMinutes: Int,
    val dayInterval: Int,
    val state: String,
)

data class TrainDelay(val trainCode: String, val message: String?, val stops: List<DelayStop>)
