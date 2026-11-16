package com.crinfo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTagsTest {
    private fun trip(
        style: String = "",
        seats: Map<Seat, Pair<SeatStatus, Double?>> = emptyMap(),
    ) = Trip(
        trainCode = "G101",
        trainNo = "no1",
        date = "20261102",
        fromCode = "VNP",
        toCode = "AOH",
        fromName = "北京南",
        toName = "上海虹桥",
        startCode = "VNP",
        endCode = "AOH",
        startName = "北京南",
        endName = "上海虹桥",
        departTime = "06:08",
        arriveTime = "12:04",
        durationText = "05:56",
        canWait = false,
        seats = seats.map { (seat, pair) -> SeatOffer(seat, pair.first, pair.second) },
        trainStyle = style,
    )

    @Test
    fun `CR prefix marks fuxing`() {
        assertEquals(
            listOf(TrainTag.FUXING),
            trip(style = "CR400BF-A").tags,
        )
        // CR200J 动力集中型也属复兴号；CRH 不属于
        assertTrue(trip(style = "CR200J").tags.contains(TrainTag.FUXING))
        assertFalse("CRH380B 不是复兴号", trip(style = "CRH380B_556").tags.contains(TrainTag.FUXING))
        assertTrue("车型未知时不打标签", trip(style = "").tags.isEmpty())
    }

    @Test
    fun `Z suffix marks smart emu`() {
        // 实测车型取值
        assertEquals(
            listOf(TrainTag.FUXING, TrainTag.SMART),
            trip(style = "CR400AF-Z_578").tags,
        )
        assertTrue(trip(style = "CR400BF-AZ").tags.contains(TrainTag.SMART))
        assertTrue(trip(style = "CR400AF-BZ_1283").tags.contains(TrainTag.SMART))
        assertFalse("S 后缀不是智能型", trip(style = "CR400AF-S").tags.contains(TrainTag.SMART))
    }

    @Test
    fun `sleeper seat marks berth train`() {
        val sleeper = trip(
            style = "CR200J",
            seats = mapOf(
                Seat.SECOND to (SeatStatus.Plenty to 367.0),
                Seat.FIRST_SLEEPER to (SeatStatus.Count(3) to 610.0),
                Seat.SECOND_SLEEPER to (SeatStatus.SoldOut to 447.0),
            ),
        )
        assertTrue(sleeper.tags.contains(TrainTag.SLEEPER))
        assertFalse("纯座席车不打铺", trip(style = "CR400BF", seats = mapOf(Seat.SECOND to (SeatStatus.Plenty to 500.0))).tags.contains(TrainTag.SLEEPER))
    }

    @Test
    fun `sold out only when every seat is unavailable`() {
        val allGone = trip(
            seats = mapOf(
                Seat.SECOND to (SeatStatus.SoldOut to 553.0),
                Seat.FIRST to (SeatStatus.SoldOut to 929.0),
                Seat.NO_SEAT to (SeatStatus.SoldOut to 553.0),
            ),
        )
        assertTrue(allGone.soldOut)
        assertEquals(null, allGone.lowestAvailablePrice)

        val oneLeft = trip(
            seats = mapOf(
                Seat.SECOND to (SeatStatus.SoldOut to 553.0),
                Seat.FIRST to (SeatStatus.Count(2) to 929.0),
            ),
        )
        assertFalse(oneLeft.soldOut)
        assertEquals(929.0, oneLeft.lowestAvailablePrice!!, 0.001)

        // 板面模式没有席别数据，不能判成售罄
        assertFalse(trip(style = "CR400BF").soldOut)
    }
}
