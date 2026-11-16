package com.crinfo.app.data.remote

import com.crinfo.app.core.net.Http
import com.crinfo.app.core.normalizeTime
import com.crinfo.app.data.model.ArrivalItem
import com.crinfo.app.data.model.DepartureItem
import com.crinfo.app.data.model.splitCheckingPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val SCREEN_URL = "https://m.ctrip.com/restapi/soa2/22867/json/screenStation"

/**
 * 两个方向共用 `checkingPort` + `waitingState` 两个字段名：出发是「候车区/检票口」，
 * 到达是出站口整串。与 liechetime 同源同值，差异只在 liechetime 已替我们拆好。
 */
@Serializable
private data class CtripScreenDto(
    val stationWaitingScreens: List<CtripWaitingDto> = emptyList(),
    val stationArrivalScreens: List<CtripArrivalDto> = emptyList(),
)

@Serializable
private data class CtripWaitingDto(
    val trainNo: String = "",
    val endStationName: String = "",
    val departTime: String = "",
    val checkingPort: String = "",
    val waitingState: String = "",
)

@Serializable
private data class CtripArrivalDto(
    val trainNo: String = "",
    val startStationName: String = "",
    val arrivalTime: String = "",
    val checkingPort: String = "",
    val waitingState: String = "",
)

/**
 * 车站大屏主源。匿名可用（实测无 Cookie、无 `head` 也能取到全量数据），
 * station 传车站表名（「北京南」）—— 带「站」字实测为 0 条。
 * 站名不认识或此刻无车都回 `resultCode=1` + 空列表，不报错：判空即判缺，交由调用方回落。
 */
object CtripApi {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun departures(station: String): List<DepartureItem> =
        screen(station, screenFlag = 0).stationWaitingScreens.map {
            val (waitingRoom, wicket) = splitCheckingPort(it.checkingPort)
            DepartureItem(
                trainCode = it.trainNo,
                toStation = it.endStationName,
                departTime = normalizeTime(it.departTime),
                departDateTime = null,
                wicket = wicket,
                waitingRoom = waitingRoom,
                state = it.waitingState,
                dayDiff = 0,
            )
        }

    suspend fun arrivals(station: String): List<ArrivalItem> =
        screen(station, screenFlag = 1).stationArrivalScreens.map {
            ArrivalItem(
                trainCode = it.trainNo,
                fromStation = it.startStationName,
                arriveTime = normalizeTime(it.arrivalTime),
                exitingPort = it.checkingPort.ifBlank { null },
                state = it.waitingState,
                dayDiff = 0,
            )
        }

    private suspend fun screen(station: String, screenFlag: Int): CtripScreenDto {
        val body = buildJsonObject {
            put("stationName", station)
            put("screenFlag", screenFlag)
        }.toString()
        return json.decodeFromString(Http.postJson(SCREEN_URL, body))
    }
}
