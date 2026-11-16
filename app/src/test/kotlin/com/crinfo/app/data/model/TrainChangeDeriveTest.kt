package com.crinfo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 换号点派生：时刻表逐站车次号与前一站不同 = 新号自该站起生效。 */
class TrainChangeDeriveTest {

    private fun stop(name: String, telecode: String, code: String) = TrainStop(
        name = name, telecode = telecode, order = 0,
        arriveTime = if (name == "温州") null else "12:00", departTime = "12:30",
        dwellMinutes = 0, minutesFromStart = 0, dayDiff = 0, lat = null, lon = null,
        wicket = null, waitingRoom = null, exit = null, stationBureauCode = "",
        stationCorporation = "", ticketDelay = null, stationTrainCode = code,
    )

    /** K1276/K1277 实测形态：温州→北京丰台段 K1276，丰台起变 K1277，直到终到包头。 */
    @Test
    fun `与前一站号不同的站标为新号起点`() {
        val stops = listOf(
            stop("温州", "RZH", "K1276"),
            stop("济南", "JNK", "K1276"),
            stop("北京丰台", "PWT", "K1277"),
            stop("包头", "BTC", "K1277"),
        )
        assertEquals(mapOf("PWT" to "K1277"), deriveTrainChanges(stops))
    }

    /** 三段换号（同型车极少但规则必须覆盖）：两个换号点都要出。 */
    @Test
    fun `多次换号逐站都标`() {
        val stops = listOf(
            stop("甲", "A", "K100"),
            stop("乙", "B", "K101"),
            stop("丙", "C", "K101"),
            stop("丁", "D", "K102"),
        )
        assertEquals(mapOf("B" to "K101", "D" to "K102"), deriveTrainChanges(stops))
    }

    /** 离线库没有逐站号（空串）与电报码缺失的站都不给提示。 */
    @Test
    fun `空号或空电报码不算换号点`() {
        val offline = listOf(stop("温州", "RZH", ""), stop("北京丰台", "PWT", ""), stop("包头", "BTC", ""))
        assertEquals(emptyMap<String, String>(), deriveTrainChanges(offline))
        val noTelecode = listOf(stop("甲", "A", "K1"), stop("乙", "", "K2"))
        assertEquals(emptyMap<String, String>(), deriveTrainChanges(noTelecode))
    }
}
