package com.crinfo.app.data.remote

import com.crinfo.app.core.net.ApiException
import com.crinfo.app.core.net.Http
import com.crinfo.app.core.blankToNull
import com.crinfo.app.core.normalizeTime
import com.crinfo.app.data.model.ArrivalItem
import com.crinfo.app.data.model.DelayStop
import com.crinfo.app.data.model.DepartureItem
import com.crinfo.app.data.model.TrainDelay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val BASE = "https://liechetime.cn/api/"

@Serializable
private data class ScreenRet(val retCode: Int = 0, val message: String = "")

@Serializable
private data class ScreenDto(
    val result: ScreenRet = ScreenRet(),
    val stationWaitingScreens: List<WaitingDto> = emptyList(),
    val stationArrivalScreens: List<ArrivalDto> = emptyList(),
)

@Serializable
private data class WaitingDto(
    val trainNo: String = "",
    val endStationName: String = "",
    val departTime: String = "",
    val departDateTime: String = "",
    val wicket: String = "",
    val waitingRoom: String = "",
    val waitingState: String = "",
    val dayDiff: Int = 0,
)

@Serializable
private data class ArrivalDto(
    val trainNo: String = "",
    val startStationName: String = "",
    val arrivalTime: String = "",
    val exitingPort: String = "",
    val arrivalState: String = "",
    val dayDiff: Int = 0,
)

@Serializable
private data class TimetableDto(
    val result: ScreenRet = ScreenRet(),
    val delayInfo: String = "",
    val trainDelayInfos: List<DelayStopDto> = emptyList(),
)

@Serializable
private data class DelayStopDto(
    val stationName: String = "",
    val stationNo: String = "",
    val startTime: String = "",
    val arrivalTime: String = "",
    val localStartTime: String = "",
    val localArriveTime: String = "",
    val stopMinutes: String = "",
    val delayInfo: String = "",
    val arriveDelayInfo: String = "",
    val dayInterval: Int = 0,
    val delayStateDesc: String = "",
)

/**
 * 大屏备用源（主源是 [CtripApi]，实测两车同源同值），另管车次详情的逐站正晚点——那一档只有它有。
 * station 传站名或电报码都可以（实测均可）。
 */
object LiechetimeApi {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun departures(station: String): List<DepartureItem> {
        val dto = screen(station, screenFlag = 0)
        return dto.stationWaitingScreens.map {
            DepartureItem(
                trainCode = it.trainNo,
                toStation = it.endStationName,
                departTime = normalizeTime(it.departTime),
                departDateTime = blankToNull(it.departDateTime),
                wicket = blankToNull(it.wicket),
                waitingRoom = blankToNull(it.waitingRoom),
                state = it.waitingState,
                dayDiff = it.dayDiff,
            )
        }
    }

    suspend fun arrivals(station: String): List<ArrivalItem> {
        val dto = screen(station, screenFlag = 1)
        return dto.stationArrivalScreens.map {
            ArrivalItem(
                trainCode = it.trainNo,
                fromStation = it.startStationName,
                arriveTime = normalizeTime(it.arrivalTime),
                exitingPort = blankToNull(it.exitingPort),
                state = it.arrivalState,
                dayDiff = it.dayDiff,
            )
        }
    }

    /** 逐站正晚点。接口只给途经站与晚点，不含里程/坐标/车型。 */
    suspend fun trainDelay(trainCode: String, date: String): TrainDelay {
        val body = buildJsonObject {
            put("trainNo", trainCode)
            put("departDate", date)
        }.toString()
        val raw = Http.postJson("${BASE}getTrainTimetableData", body)
        val dto = json.decodeFromString<TimetableDto>(raw)
        if (dto.result.retCode != 1 && dto.trainDelayInfos.isEmpty()) {
            throw ApiException(dto.result.message.ifBlank { "$trainCode 暂无正晚点信息" })
        }
        return TrainDelay(
            trainCode = trainCode,
            message = blankToNull(dto.delayInfo),
            stops = dto.trainDelayInfos.map {
                DelayStop(
                    stationName = it.stationName,
                    order = it.stationNo,
                    plannedArrive = normalizeTime(it.localArriveTime),
                    plannedDepart = normalizeTime(it.localStartTime),
                    realArrive = normalizeTime(it.arrivalTime),
                    realDepart = normalizeTime(it.startTime),
                    dwellText = it.stopMinutes,
                    departDelayMinutes = it.delayInfo.toIntOrNull() ?: 0,
                    arriveDelayMinutes = it.arriveDelayInfo.toIntOrNull() ?: 0,
                    dayInterval = it.dayInterval,
                    state = it.delayStateDesc,
                )
            },
        )
    }

    private suspend fun screen(station: String, screenFlag: Int): ScreenDto {
        val body = buildJsonObject {
            put("stationName", station)
            put("screenFlag", screenFlag)
        }.toString()
        val raw = Http.postJson("${BASE}getStationScreenData", body)
        return json.decodeFromString(raw)
    }
}
