package com.crinfo.app.data.remote

import com.crinfo.app.data.model.Seat
import com.crinfo.app.data.model.SeatStatus
import com.crinfo.app.data.model.Trip
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 钉住 leftTicket 的列序与票价解码：数据漂移必须在这里先炸，而不是在 UI 上显示错票价。 */
class LeftTicketParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val trips: List<Trip> by lazy {
        val text = javaClass.getResourceAsStream("/fixtures/leftticket_bjp_aoh.json")!!
            .reader(Charsets.UTF_8).readText()
        val result = json.parseToJsonElement(text).jsonObject["data"]!!
            .jsonObject["result"]!!.jsonArray.map { it.jsonPrimitive.content }
        LeftTicketParser.parse(result) { null }
    }

    private fun trip(code: String): Trip = trips.first { it.trainCode == code }

    @Test
    fun `parses every row of the real response`() {
        assertEquals(54, trips.size)
    }

    @Test
    fun `maps times and duration for a daytime high speed train`() {
        val g531 = trip("G531")
        assertEquals("06:08", g531.departTime)
        assertEquals("12:04", g531.arriveTime)
        assertEquals("05:56", g531.durationText)
        assertFalse("06:08 + 5h56m 仍在当天", g531.arrivesNextDay)
        assertEquals("240000G53106", g531.trainNo)
        // 接口的日期列是 20261102，模型里必须是 ISO，否则跨页传日期会解析失败
        assertEquals("2026-11-02", g531.date)
    }

    @Test
    fun `marks overnight trains as arriving next day`() {
        assertTrue(trip("D17").arrivesNextDay)
        assertTrue(trip("Z281").arrivesNextDay)
    }

    @Test
    fun `decodes availability and price per seat for high speed`() {
        val g531 = trip("G531")
        assertEquals(SeatStatus.Count(5), g531.seat(Seat.BUSINESS)?.status)
        assertEquals(1744.0, g531.seat(Seat.BUSINESS)?.price!!, 0.001)
        assertEquals(SeatStatus.Plenty, g531.seat(Seat.FIRST)?.status)
        assertEquals(836.0, g531.seat(Seat.FIRST)?.price!!, 0.001)
        assertEquals(SeatStatus.Count(4), g531.seat(Seat.SECOND)?.status)
        assertEquals(498.0, g531.seat(Seat.SECOND)?.price!!, 0.001)
        // 无座票价的来源是 seat_types 里对应的那一组，所以等于二等座价
        assertEquals(SeatStatus.SoldOut, g531.seat(Seat.NO_SEAT)?.status)
        assertEquals(498.0, g531.seat(Seat.NO_SEAT)?.price!!, 0.001)
        assertEquals(498.0, g531.lowestAvailablePrice!!, 0.001)
    }

    @Test
    fun `starting price ignores sold out seats`() {
        // G7 的二等座与无座 692 元已售罄，起售价必须取有票席别里最低的一等座 1107 元
        val g7 = trip("G7")
        assertEquals(SeatStatus.SoldOut, g7.seat(Seat.SECOND)?.status)
        assertEquals(692.0, g7.seat(Seat.SECOND)?.price!!, 0.001)
        assertEquals(1107.0, g7.lowestAvailablePrice!!, 0.001)
    }

    @Test
    fun `only emits seats actually present in seat_types`() {
        assertNull("G531 无优选一等", trip("G531").seat(Seat.PREMIUM_FIRST))
        val g3 = trip("G3")
        assertEquals(1468.0, g3.seat(Seat.PREMIUM_FIRST)?.price!!, 0.001)
        assertEquals(SeatStatus.Count(9), g3.seat(Seat.PREMIUM_FIRST)?.status)
    }

    @Test
    fun `shares one column between sleeper classes without duplicating rows`() {
        val d17 = trip("D17")
        assertEquals(610.0, d17.seat(Seat.FIRST_SLEEPER)?.price!!, 0.001)
        assertEquals(447.0, d17.seat(Seat.SECOND_SLEEPER)?.price!!, 0.001)
        assertNull("卧铺动车没有软卧码 4", d17.seat(Seat.SOFT_SLEEPER))
        assertNull("卧铺动车没有硬卧码 3", d17.seat(Seat.HARD_SLEEPER))

        val z281 = trip("Z281")
        assertEquals(304.5, z281.seat(Seat.HARD_SLEEPER)?.price!!, 0.001)
        assertEquals(476.5, z281.seat(Seat.SOFT_SLEEPER)?.price!!, 0.001)
        assertEquals(177.5, z281.seat(Seat.HARD_SEAT)?.price!!, 0.001)
        assertEquals(177.5, z281.seat(Seat.NO_SEAT)?.price!!, 0.001)
    }

    @Test
    fun `no trip ever lists the same seat twice`() {
        trips.forEach { trip ->
            val labels = trip.seats.map { it.seat }
            assertEquals("${trip.trainCode} 席别重复", labels.size, labels.toSet().size)
        }
    }

    @Test
    fun `empty availability cell does not crash the parser`() {
        // 回归："".all { isDigit } 恒为 true，曾让 "".toInt() 抛 NumberFormatException
        assertEquals(SeatStatus.SoldOut, SeatStatus.parse(""))
        assertEquals(SeatStatus.SoldOut, SeatStatus.parse("-"))
        assertEquals(SeatStatus.SoldOut, SeatStatus.parse("*"))
        assertEquals(SeatStatus.SoldOut, SeatStatus.parse("0"))
        assertEquals(SeatStatus.Count(5), SeatStatus.parse("5"))
    }

    @Test
    fun `station names fall back to telecode when resolver returns null`() {
        val g531 = trip("G531")
        assertEquals("VNP", g531.fromName)
        assertEquals("AOH", g531.toName)
        assertTrue(g531.hasAnyTicket)
    }
}
