package com.crinfo.app.ui.query

import com.crinfo.app.data.model.Seat
import com.crinfo.app.data.model.SeatOffer
import com.crinfo.app.data.model.SeatStatus
import com.crinfo.app.data.model.TrainKind
import com.crinfo.app.data.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFilterTest {
    private fun trip(
        code: String,
        depart: String,
        duration: String,
        arrive: String,
        seats: Map<Seat, Pair<SeatStatus, Double?>> = mapOf(Seat.SECOND to (SeatStatus.Plenty to 500.0)),
        fromCode: String = "VNP",
        toCode: String = "AOH",
        startCode: String = "VNP",
        endCode: String = "AOH",
    ) = Trip(
        trainCode = code,
        trainNo = "no$code",
        date = "20261102",
        fromCode = fromCode,
        toCode = toCode,
        fromName = "北京南",
        toName = "上海虹桥",
        startCode = startCode,
        endCode = endCode,
        startName = "北京南",
        endName = "上海虹桥",
        departTime = depart,
        arriveTime = arrive,
        durationText = duration,
        canWait = false,
        seats = seats.map { (seat, pair) -> SeatOffer(seat, pair.first, pair.second) },
    )

    private val morning = trip("G101", "08:00", "05:00", "13:00")
    private val evening = trip("G201", "19:00", "04:00", "23:00")
    private val soldOut = trip(
        "D301", "10:00", "06:00", "16:00",
        seats = mapOf(Seat.SECOND to (SeatStatus.SoldOut to 300.0), Seat.NO_SEAT to (SeatStatus.SoldOut to 300.0)),
    )
    private val nonOrigin = trip("G301", "12:00", "05:00", "17:00", startCode = "QEP", fromCode = "VNP")
    private val noPrice = trip(
        "K401", "21:00", "18:00", "15:00",
        seats = mapOf(Seat.HARD_SEAT to (SeatStatus.Plenty to null)),
    )
    private val all = listOf(morning, evening, soldOut, nonOrigin, noPrice)

    private val offlineSeats = mapOf(
        Seat.SECOND to (SeatStatus.Unknown to null),
        Seat.HARD_SLEEPER to (SeatStatus.Unknown to null),
    )
    private val offlineWithSecond = trip("G501", "09:00", "04:00", "13:00", seats = offlineSeats)
    private val offlineWithoutSecond = trip("G503", "10:00", "04:00", "14:00", seats = mapOf(Seat.BUSINESS to (SeatStatus.Unknown to null)))

    /** 离线态席别只有种类（`Unknown`）：选席别要按「这趟车收录了该席别」筛，而不是按「有票」筛。 */
    @Test
    fun `离线选席别按收录筛选`() {
        assertEquals(
            listOf("G501"),
            TripFilter(seat = Seat.SECOND).apply(listOf(offlineWithSecond, offlineWithoutSecond)).map { it.trainCode },
        )
        assertTrue("离线态不该被判成售罄", !offlineWithSecond.soldOut && !offlineWithSecond.ticketKnown)
    }

    /** 「只看有票」在离线态没有票量可判：置灰之外，过滤器也不许它把结果杀空（VM 在切离线时会复位）。 */
    @Test
    fun `离线只看有票不杀列表`() {
        assertEquals(
            listOf("G501"),
            TripFilter(onlyAvailable = true).apply(listOf(offlineWithSecond)).map { it.trainCode },
        )
        assertEquals(
            "联网态照常杀掉",
            emptyList<String>(),
            TripFilter(onlyAvailable = true).apply(listOf(soldOut)).map { it.trainCode },
        )
    }

    /** 联网态选了席别仍是「该席别有票」，别把离线口径漏进在线路径。 */
    @Test
    fun `联网选席别仍看票量`() {
        val plenty = trip("G505", "11:00", "04:00", "15:00", seats = mapOf(Seat.SECOND to (SeatStatus.Count(3) to 500.0)))
        val sold = trip("G507", "12:00", "04:00", "16:00", seats = mapOf(Seat.SECOND to (SeatStatus.SoldOut to 500.0)))
        assertEquals(listOf("G505"), TripFilter(seat = Seat.SECOND).apply(listOf(plenty, sold)).map { it.trainCode })
    }

    @Test
    fun `default filter keeps everything sorted by departure`() {
        assertEquals(listOf("G101", "D301", "G301", "G201", "K401"), TripFilter().apply(all).map { it.trainCode })
    }

    @Test
    fun `filters by train kind`() {
        // 默认按出发时刻排序：08:00 → 12:00 → 19:00
        assertEquals(listOf("G101", "G301", "G201"), TripFilter(kinds = setOf(TrainKind.G)).apply(all).map { it.trainCode })
    }

    @Test
    fun `filters by departure bucket`() {
        val result = TripFilter(buckets = setOf(TimeBucket.EVENING)).apply(all)
        assertEquals(listOf("G201", "K401"), result.map { it.trainCode })
    }

    @Test
    fun `onlyOrigin drops trains that merely pass through`() {
        val result = TripFilter(onlyOrigin = true).apply(listOf(morning, nonOrigin))
        assertEquals(listOf("G101"), result.map { it.trainCode })
    }

    @Test
    fun `onlyTerminal keeps trains ending at the destination`() {
        val passThrough = trip("G501", "09:00", "04:00", "13:00", endCode = "SZH")
        val result = TripFilter(onlyTerminal = true).apply(listOf(morning, passThrough))
        assertEquals(listOf("G101"), result.map { it.trainCode })
    }

    @Test
    fun `onlyAvailable removes fully sold out trains`() {
        val result = TripFilter(onlyAvailable = true).apply(all)
        assertTrue("无票车次未被剔除", soldOut.trainCode !in result.map { it.trainCode })
    }

    @Test
    fun `choosing a seat alone already filters by that seat`() {
        val withBusiness = trip(
            "G601", "11:00", "05:00", "16:00",
            seats = mapOf(
                Seat.BUSINESS to (SeatStatus.Count(3) to 1700.0),
                Seat.SECOND to (SeatStatus.SoldOut to 500.0),
            ),
        )
        val result = TripFilter(seat = Seat.SECOND).apply(listOf(withBusiness, morning))
        assertEquals("G601 二等无票应被剔除", listOf("G101"), result.map { it.trainCode })

        val byBusiness = TripFilter(seat = Seat.BUSINESS).apply(listOf(withBusiness, morning))
        assertEquals("morning 无商务座应被剔除", listOf("G601"), byBusiness.map { it.trainCode })
    }

    @Test
    fun `price sort puts trains without price last`() {
        val result = TripFilter(sort = TripSort.PRICE).apply(listOf(noPrice, evening, morning))
        assertEquals(listOf("G101", "G201", "K401"), result.map { it.trainCode })
    }

    @Test
    fun `duration sort prefers the fastest`() {
        val result = TripFilter(sort = TripSort.DURATION).apply(listOf(morning, evening))
        assertEquals(listOf("G201", "G101"), result.map { it.trainCode })
    }

    @Test
    fun `arrive sort accounts for overnight arrival`() {
        val overnight = trip("Z99", "21:00", "12:00", "09:00")
        val result = TripFilter(sort = TripSort.ARRIVE).apply(listOf(overnight, morning))
        assertEquals("跨天到达应排在当日之后", listOf("G101", "Z99"), result.map { it.trainCode })
    }

    @Test
    fun `station sets filter departure and arrival sides independently`() {
        // 上海(vvV)→南宁(NZZ)、上海虹桥(AOH)→南宁东(NFZ)、上海→南宁东
        val fromBothCities = listOf(
            trip("G801", "09:00", "10:00", "19:00", fromCode = "VVV", toCode = "NZZ"),
            trip("G802", "10:00", "10:00", "20:00", fromCode = "AOH", toCode = "NFZ"),
            trip("G803", "11:00", "10:00", "21:00", fromCode = "VVV", toCode = "NFZ"),
        )
        val byFrom = TripFilter(fromStations = setOf("AOH")).apply(fromBothCities)
        assertEquals(listOf("G802"), byFrom.map { it.trainCode })

        val byTo = TripFilter(toStations = setOf("NFZ")).apply(fromBothCities)
        assertEquals(listOf("G802", "G803"), byTo.map { it.trainCode })

        // 两侧同时限定即取交集
        val both = TripFilter(fromStations = setOf("VVV", "AOH"), toStations = setOf("NFZ")).apply(fromBothCities)
        assertEquals(listOf("G802", "G803"), both.map { it.trainCode })
    }

    @Test
    fun `activeCount reflects enabled conditions`() {
        assertEquals(0, TripFilter().activeCount)
        assertEquals(1, TripFilter(kinds = setOf(TrainKind.G)).activeCount)
        assertEquals(2, TripFilter(onlyAvailable = true, seat = Seat.SECOND).activeCount)
        assertEquals(2, TripFilter(fromStations = setOf("AOH"), toStations = setOf("NFZ")).activeCount)
        assertTrue(TripFilter().isDefault)
        assertTrue(!TripFilter(sort = TripSort.PRICE).isDefault)
    }
}
