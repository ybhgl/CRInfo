package com.crinfo.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteGraphTest {
    private fun stops(vararg pairs: Pair<String, Int>) =
        pairs.mapIndexed { i, (tele, km) -> LineStop(tele, "站$i", km) }

    private val jinghu = RouteLine(
        1,
        "京沪高速线",
        stops("AAA" to 0, "BBB" to 60, "CCC" to 110, "DDD" to 210),
    )

    /** 与 京沪高速线 共享 CCC，但将 CCC→DDD 拉得很近：纯最短距离会诱惑换线。 */
    private val parallel = RouteLine(
        2,
        "并线",
        stops("CCC" to 0, "DDD" to 10),
    )

    @Test
    fun `单线累计里程等于线路内里程差`() {
        val graph = RouteGraph(listOf(jinghu))
        val route = graph.route(listOf("AAA", "BBB", "CCC", "DDD"))
        assertEquals(listOf(0, 60, 110, 210), route?.map { it.km })
        assertEquals(listOf("京沪高速线"), route?.first { it.km == 60 }?.lines)
    }

    @Test
    fun `代价先压换线次数而不是纯最短距离`() {
        val graph = RouteGraph(listOf(jinghu, parallel))
        val route = graph.route(listOf("AAA", "DDD"))!!
        // 换线可省 100km，但图定径路以顺跑为主，应当仍走 京沪高速线 的 210km
        assertEquals(210, route.last().km)
        assertEquals(listOf("京沪高速线"), route.last().lines)
    }

    @Test
    fun `跨线车按经过的线路顺序给出经由`() {
        val hebang = RouteLine(3, "合蚌高速线", stops("EEE" to 0, "FFF" to 43, "AAA" to 132))
        val graph = RouteGraph(listOf(jinghu, hebang))
        val route = graph.route(listOf("EEE", "AAA", "BBB"))!!
        assertEquals(listOf(0, 132, 192), route.map { it.km })
        assertEquals(listOf("合蚌高速线"), route[1].lines)
        assertEquals(listOf("合蚌高速线", "京沪高速线"), route[2].lines)
    }

    @Test
    fun `任一站不在库里就整体放弃，交回板面兜底`() {
        val graph = RouteGraph(listOf(jinghu))
        assertNull(graph.route(listOf("AAA", "ZZZ")))
        assertNull(graph.route(listOf("AAA")))
    }
}
