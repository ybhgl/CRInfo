package com.crinfo.app.data

import com.crinfo.app.data.model.DelayStop
import com.crinfo.app.data.model.OperatingBureau
import com.crinfo.app.data.model.TrainDelay
import com.crinfo.app.data.model.RunningDays
import com.crinfo.app.data.model.Seat
import com.crinfo.app.data.model.SeatOffer
import com.crinfo.app.data.model.SeatStatus
import com.crinfo.app.data.model.TrainStop
import com.crinfo.app.data.model.TrainTag
import com.crinfo.app.data.model.TrainTimetable
import com.crinfo.app.data.model.TrainDetail
import com.crinfo.app.data.model.chineseDuration
import com.crinfo.app.data.model.mergeSchedule
import com.crinfo.app.data.model.normalizeTerminalTimes
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainDetailTest {

    @Test
    fun `train code input is normalized and validated`() {
        assertEquals("G1", parseTrainCode("g1"))
        assertEquals("G1", parseTrainCode(" G 1 "))
        assertEquals("K516", parseTrainCode("k516"))
        assertEquals("7214", parseTrainCode("7214"))
        assertEquals("G102", parseTrainCode("G-102"))
        assertNull(parseTrainCode(""))
        assertNull(parseTrainCode("abc"))
        assertNull(parseTrainCode("G12345"))
        assertNull(parseTrainCode("G1舱"))
    }

    @Test
    fun `bureau short name gets the 局 suffix once`() {
        assertEquals("上海局", normalizeBureauName("上海"))
        assertEquals("上海局", normalizeBureauName("上海局"))
        assertEquals("", normalizeBureauName("  "))
    }

    @Test
    fun `seats query is skipped outside the presale window`() {
        val now = LocalDateTime.of(2026, 11, 2, 10, 0)
        val today = now.toLocalDate()
        assertFalse(seatsWorthQuerying(today.minusDays(1).toString(), "12:00", now))
        assertFalse(seatsWorthQuerying(today.plusDays(16).toString(), "12:00", now))
        assertTrue(seatsWorthQuerying(today.plusDays(15).toString(), "00:05", now))
        // 非今天只看预售期，凌晨发车的车也不该被「已发车」门槛误拦
        assertTrue(seatsWorthQuerying(today.plusDays(2).toString(), "00:05", now))
    }

    @Test
    fun `today seats query needs the scheduled departure past the guard`() {
        val now = LocalDateTime.of(2026, 11, 2, 10, 0)
        val today = now.toLocalDate().toString()
        assertTrue(seatsWorthQuerying(today, "10:15", now))
        assertFalse(seatsWorthQuerying(today, "10:14", now))
        assertFalse(seatsWorthQuerying(today, "09:50", now))
        assertFalse(seatsWorthQuerying(today, "00:05", now))
    }

    @Test
    fun `today seats query is skipped when the scheduled departure is unknown`() {
        val now = LocalDateTime.of(2026, 11, 2, 10, 0)
        val today = now.toLocalDate().toString()
        assertFalse(seatsWorthQuerying(today, null, now))
        assertFalse(seatsWorthQuerying(today, "", now))
        assertFalse(seatsWorthQuerying(today, "--:--", now))
    }

    @Test
    fun `duration folds cross-day arrivals`() {
        val detail = detail(
            style = "CR400BF-A",
            stops = listOf(stop(order = 1, depart = "22:00"), stop(order = 5, arrive = "06:30", dayDiff = 1)),
        )
        assertEquals(510, detail.durationMinutes)
        assertEquals("8小时30分", detail.durationText)
        assertTrue(detail.crossesDay)
    }

    @Test
    fun `same day trip is not marked as crossing`() {
        val detail = detail(stops = listOf(stop(order = 1, depart = "06:30"), stop(order = 7, arrive = "11:24")))
        assertEquals(294, detail.durationMinutes)
        assertFalse(detail.crossesDay)
        assertEquals("4小时54分", detail.durationText)
    }

    @Test
    fun `tags follow consist and sleeper seats`() {
        val second = SeatOffer(Seat.SECOND, SeatStatus.Plenty, 553.0)
        val sleeper = SeatOffer(Seat.HARD_SLEEPER, SeatStatus.Count(6), 301.5)
        assertEquals(
            listOf(TrainTag.FUXING),
            detail(style = "CR400BF-A", seats = listOf(second), stops = twoStops()).tags,
        )
        assertEquals(
            listOf(TrainTag.FUXING, TrainTag.SMART),
            detail(style = "CR400BF-AZ", seats = listOf(second), stops = twoStops()).tags,
        )
        // CRH 前缀是和谐号，不能算复兴号
        assertEquals(emptyList<TrainTag>(), detail(style = "CRH380B", seats = listOf(second), stops = twoStops()).tags)
        assertEquals(
            listOf(TrainTag.SLEEPER),
            detail(style = "CRH2A", seats = listOf(sleeper), stops = twoStops()).tags,
        )
    }

    @Test
    fun `terminal stop has no scheduled departure`() {
        // 12306 停站接口实测把首末站填成同一个时刻（D8414：北海 19:23/19:23、柳州 22:13/22:13）
        val stops = listOf(
            stop(order = 1, name = "北海", arrive = "19:23", depart = "19:23"),
            stop(order = 2, name = "南宁", arrive = "20:46", depart = "20:56"),
            stop(order = 3, name = "柳州", arrive = "22:13", depart = "22:13"),
        ).normalizeTerminalTimes()
        assertNull("始发站不该有到达", stops.first().arriveTime)
        assertEquals("19:23", stops.first().departTime)
        assertEquals("20:46", stops[1].arriveTime)
        assertEquals("20:56", stops[1].departTime)
        assertEquals("22:13", stops.last().arriveTime)
        assertNull("终到站不该有出发", stops.last().departTime)

        // 终到晚点时副文案只报图定到，不能冒出「图定发」
        val rows = mergeSchedule(
            timetable(stops),
            delay(listOf(delayStop(order = "3", name = "柳州", arriveDelay = 1))),
        )
        assertEquals("22:14", rows.last().displayArrive)
        assertEquals("22:13", rows.last().plannedArriveText)
        assertNull(rows.last().plannedDepartText)
    }

    @Test
    fun `schedule merges live delay by station order`() {
        val timetable = timetable(
            listOf(stop(order = 1, depart = "06:30"), stop(order = 2, name = "沧州西", arrive = "07:18", depart = "07:20")),
        )
        val delay = delay(
            listOf(
                delayStop(order = "2", name = "沧州西", arriveDelay = 12, realArrive = "07:30"),
                delayStop(order = "1", name = "北京南", arriveDelay = 0, realArrive = "06:30"),
            ),
        )
        val rows = mergeSchedule(timetable, delay)
        assertEquals(2, rows.size)
        assertFalse(rows[0].isDelayed)
        assertTrue(rows[1].isDelayed)
        assertEquals(12, rows[1].delayMinutes)
        // 主轴走实时时刻，图定退为次要信息
        assertEquals("07:30", rows[1].displayArrive)
        assertTrue(rows[1].arriveLate)
        assertEquals("07:18", rows[1].plannedArriveText)
        assertEquals("07:18", rows[1].stop.arriveTime)
        // 正点时接口实时值与图定相同，不该再说一遍图定
        assertNull(rows[0].plannedDepartText)
    }

    @Test
    fun `delay minutes alone shift the schedule`() {
        val timetable = timetable(
            listOf(
                stop(order = 1, depart = "06:30"),
                stop(order = 2, name = "沧州西", arrive = "14:16", depart = "14:18"),
                stop(order = 3, name = "廊坊", arrive = "15:00", depart = "15:02"),
            ),
        )
        val rows = mergeSchedule(
            timetable,
            delay(
                listOf(
                    // 只给晚点分钟、不给实时时刻，也要把图定推后
                    delayStop(order = "2", name = "沧州西", arriveDelay = 1, departDelay = 1),
                    // 到与发各自算：到达晚 8 分、出发晚 10 分
                    delayStop(order = "3", name = "廊坊", arriveDelay = 8, departDelay = 10),
                ),
            ),
        )
        assertEquals("14:17", rows[1].displayArrive)
        assertEquals("14:19", rows[1].displayDepart)
        assertEquals(1, rows[1].delayMinutes)
        assertEquals(0, rows[1].dayShift)
        assertEquals("15:08", rows[2].displayArrive)
        assertEquals("15:12", rows[2].displayDepart)
    }

    @Test
    fun `delay past midnight pushes the day badge`() {
        val timetable = timetable(
            listOf(stop(order = 1, depart = "06:30"), stop(order = 9, name = "上海虹桥", arrive = "23:52")),
        )
        val late = mergeSchedule(timetable, delay(listOf(delayStop(order = "9", name = "上海虹桥", arriveDelay = 20))))
        assertEquals("00:12", late[1].displayArrive)
        assertEquals(1, late[1].dayShift)

        val sameDay = mergeSchedule(timetable, delay(listOf(delayStop(order = "9", name = "上海虹桥", arriveDelay = 5))))
        assertEquals("23:57", sameDay[1].displayArrive)
        assertEquals(0, sameDay[1].dayShift)
    }

    @Test
    fun `schedule falls back to station name when order missing`() {
        val timetable = timetable(listOf(stop(order = 3, name = "德州东", arrive = "07:45")))
        val matched = mergeSchedule(timetable, delay(listOf(delayStop(order = "", name = "德州东", realDepart = "07:50"))))
        assertEquals("07:50", matched.single().live?.realDepart)
        assertTrue(mergeSchedule(timetable, null).single().live == null)
    }

    @Test
    fun `chinese duration skips empty hours`() {
        assertEquals("", chineseDuration(0))
        assertEquals("32分", chineseDuration(32))
        assertEquals("1小时0分", chineseDuration(60))
    }

    /* ---------------- 构造辅助 ---------------- */

    private fun delay(stops: List<DelayStop>) = TrainDelay(trainCode = "G1", message = null, stops = stops)

    private fun delayStop(
        order: String,
        name: String,
        arriveDelay: Int = 0,
        departDelay: Int = 0,
        realArrive: String? = null,
        realDepart: String? = null,
    ) = DelayStop(
        stationName = name,
        order = order,
        plannedArrive = null,
        plannedDepart = null,
        realArrive = realArrive,
        realDepart = realDepart,
        dwellText = "",
        departDelayMinutes = departDelay,
        arriveDelayMinutes = arriveDelay,
        dayInterval = 0,
        state = "",
    )

    private fun stop(
        order: Int,
        name: String = "北京南",
        arrive: String? = null,
        depart: String? = null,
        dayDiff: Int = 0,
    ) = TrainStop(
        name = name,
        telecode = "VNP",
        order = order,
        arriveTime = arrive,
        departTime = depart,
        dwellMinutes = 0,
        minutesFromStart = 0,
        dayDiff = dayDiff,
        lat = null,
        lon = null,
        wicket = null,
        waitingRoom = null,
        exit = null,
        stationBureauCode = "P",
        stationCorporation = "P#北京南",
        ticketDelay = null,
    )

    private fun timetable(stops: List<TrainStop>, style: String = "CR400BF-A") = TrainTimetable(
        trainCode = "G1",
        trainNo = "24000000G10L",
        startDate = "20261031",
        fromTelecode = "VNP",
        toTelecode = "AOH",
        startTime = "06:30",
        arriveTime = "11:24",
        trainStyle = style,
        jiaoluTrainStyle = "CR400BF-S",
        jiaoluDept = "上海动车段",
        jiaoluCorporation = "上海客运段",
        corporationCode = "H00",
        statusText = null,
        warmTips = null,
        stops = stops,
    )

    private fun twoStops() = listOf(stop(order = 1, depart = "06:30"), stop(order = 7, arrive = "11:24"))

    private fun detail(
        style: String = "CR400BF-A",
        stops: List<TrainStop>,
        seats: List<SeatOffer>? = null,
    ) = TrainDetail(
        timetable = timetable(stops, style),
        fromName = "北京南",
        toName = "上海虹桥",
        bureauName = "上海局",
        totalKm = 1318,
        rows = mergeSchedule(timetable(stops, style), null),
        runningDays = RunningDays("20261016", OperatingBureau("H", "上海局"), "北京", emptyList()),
        seats = seats,
    )
}
