package com.crinfo.app.data.offline

import com.crinfo.app.data.model.TrainStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方径路 → 经由与逐站里程。里程一律按**本线节点里程差**累加，
 * 所以「换线点落不到本线节点」时只丢里程、不丢线路名，且绝不允许累计值倒退。
 */
class OfflineJourneyTest {

    /** 线路节点：`Pair<电报码, 站名 to 里程>`。径路表按站名落线，电报码要和停站对上才给得出里程。 */
    private fun line(id: Int, name: String, vararg stops: Triple<String, String, Int>) = RouteLine(
        id,
        name,
        stops.map { (tele, n, km) -> LineStop(tele, n, km) },
    )

    private fun stop(name: String, tele: String, arrive: String? = null, depart: String? = "08:00") = TrainStop(
        name = name,
        telecode = tele,
        order = 0,
        arriveTime = arrive,
        departTime = depart,
        dwellMinutes = 0,
        minutesFromStart = 0,
        dayDiff = 0,
        lat = null,
        lon = null,
        wicket = null,
        waitingRoom = null,
        exit = null,
        stationBureauCode = "",
        stationCorporation = "",
        ticketDelay = null,
    )

    /** 甲线：始发 A(0) → B(100) → 丙线起点 C；丙线：C(0) → D(50)。 */
    private val lines = mapOf(
        1 to line(1, "甲线", Triple("A01", "A", 0), Triple("B02", "B", 100), Triple("C03", "C", 180)),
        2 to line(2, "丙线", Triple("C03", "C", 0), Triple("D04", "D", 50)),
    )

    private val route = TrainRoute(
        terminus = "D",
        legs = listOf(RouteLeg("A", 1), RouteLeg("C", 2)),
    )

    private val stops = listOf(
        stop("A", "A01", depart = "08:00"),
        stop("B", "B02", arrive = "09:00", depart = "09:02"),
        stop("C", "C03", arrive = "10:00", depart = "10:05"),
        stop("D", "D04", arrive = "11:00", depart = null),
    )

    @Test
    fun `里程按本线里程差累加并给出经过的线路`() {
        val out = OfflineJourney.build(route, lines, stops)
        assertNotNull(out)
        // A→C 走甲线 180km，C→D 走丙线 50km
        assertEquals(180, out!!.mileages["C03"]?.km)
        assertEquals(230, out.mileages["D04"]?.km)
        assertEquals(listOf("甲线"), out.mileages["B02"]?.lines)
        assertEquals(listOf("甲线", "丙线"), out.mileages["D04"]?.lines)
    }

    @Test
    fun `经由含区间内的线路所节点`() {
        val out = OfflineJourney.build(route, lines, stops)!!
        assertEquals(listOf("A", "B", "C", "D"), out.nodes.map { it.name })
        assertEquals("", out.nodes.first().line)
        assertEquals("丙线", out.nodes.last().line)
        assertTrue(out.nodes.last().isStop)
    }

    /**
     * 换线点（半边山所这类线路所）不在被换入的那条线上时：这一段不再展开中间站、
     * 也不给里程（拿别线节点硬凑会算出倒退值），但换入的线路名照给。
     */
    @Test
    fun `换线点不在本线时只丢里程不丢线路`() {
        // C 是甲线上的中间站、J 是甲线末端附近的线路所；乙线里既没有 J 也没有 C
        val withJunction = mapOf(
            1 to line(1, "甲线", Triple("A01", "A", 0), Triple("C03", "C", 60), Triple("J05", "J", 120)),
            2 to line(2, "乙线", Triple("X09", "X", 0), Triple("D04", "D", 50)),
        )
        val junction = TrainRoute(
            terminus = "D",
            legs = listOf(RouteLeg("A", 1), RouteLeg("J", 2)),
        )
        val out = OfflineJourney.build(junction, withJunction, stops)
        assertNotNull(out)
        // A→J 两端都在甲线上：中间站 C 落在这段里，里程按本线差给出
        assertEquals(60, out!!.mileages["C03"]?.km)
        // J 落不到乙线上，之后的 D 不给里程
        assertNull(out.mileages["D04"]?.km)
        assertTrue("换线后的节点应带上被换入的线路名", out.nodes.any { it.line == "乙线" })
    }

    /**
     * 库里的线路里程轴不保证与行车同向（G1958 走石太客专线是 232→0 逆着走的）。
     * 逆序区间必须按绝对跨度累加，绝不能被当成解析失败把整趟车的里程清零。
     */
    @Test
    fun `线路里程轴反向时按绝对跨度累加`() {
        val reversed = mapOf(
            1 to line(1, "甲线", Triple("A01", "A", 200), Triple("B02", "B", 150), Triple("C03", "C", 100)),
            2 to line(2, "丙线", Triple("C03", "C", 0), Triple("D04", "D", 50)),
        )
        val out = OfflineJourney.build(route, reversed, stops)!!
        // 甲线上 A=200 → B=150 → C=100（逆着里程轴走）：距始发 50 / 100，接丙线再 +50
        assertEquals(50, out.mileages["B02"]?.km)
        assertEquals(100, out.mileages["C03"]?.km)
        assertEquals(150, out.mileages["D04"]?.km)
        val seq = listOf("A01", "B02", "C03", "D04").mapNotNull { out.mileages[it]?.km }
        assertTrue("累计里程必须单调：$seq", seq == seq.sorted())
    }

    /** 图搜索会给跨线车算出 741 → 738 这种倒退值；官方径路的累计里程必须单调。 */
    @Test
    fun `累计里程不会倒退`() {
        val out = OfflineJourney.build(route, lines, stops)!!
        val seq = out.mileages.values.map { it.km }
        assertTrue("里程序列必须递增：$seq", seq.sorted() == seq)
    }

    @Test
    fun `没有径路或没有停站时不给出结果`() {
        assertNull(OfflineJourney.build(TrainRoute("D", emptyList()), lines, stops))
        assertNull(OfflineJourney.build(route, lines, emptyList()))
    }
}
