package com.crinfo.app.data.model

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainProgressTest {
    private fun stop(
        name: String,
        arrive: String?,
        depart: String?,
        dayDiff: Int = 0,
    ) = TrainStop(
        name = name,
        telecode = name,
        order = 0,
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
        stationCorporation = "",
        ticketDelay = null,
    )

    private fun row(stop: TrainStop, live: DelayStop? = null) = ScheduleRow(stop, live)

    private fun delay(arriveDelay: Int = 0, departDelay: Int = 0) = DelayStop(
        stationName = "",
        order = "",
        plannedArrive = null,
        plannedDepart = null,
        realArrive = null,
        realDepart = null,
        dwellText = "",
        departDelayMinutes = departDelay,
        arriveDelayMinutes = arriveDelay,
        dayInterval = 0,
        state = "",
    )

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): LocalDateTime =
        LocalDateTime.of(y, mo, d, h, mi)

    // 甲 06:00 发 → 乙 07:00 到 07:05 发 → 丙 08:00 到（终到）
    private val rows = listOf(
        row(stop("甲", null, "06:00")),
        row(stop("乙", "07:00", "07:05")),
        row(stop("丙", "08:00", null)),
    )
    private val date = "2026-11-01"

    @Test
    fun `未到始发时刻不算在运行`() {
        assertNull(trainProgress(rows, date, at(2026, 11, 1, 5, 59)))
    }

    @Test
    fun `停站中判给该站并标记为在站`() {
        val p = trainProgress(rows, date, at(2026, 11, 1, 7, 2))!!
        assertEquals(1, p.reachedIndex)
        assertTrue("07:02 正停在乙站办理客运", p.atStation)
    }

    @Test
    fun `区间内不给在站标记`() {
        val p = trainProgress(rows, date, at(2026, 11, 1, 7, 30))!!
        assertEquals(1, p.reachedIndex)
        assertFalse(p.atStation)
    }

    @Test
    fun `终到站到达即全程结束`() {
        val p = trainProgress(rows, date, at(2026, 11, 1, 8, 30))!!
        assertEquals(rows.lastIndex, p.reachedIndex)
        assertTrue(p.atStation)
    }

    @Test
    fun `晚点要压过图定时刻`() {
        // 乙站晚 40 分：07:40 到、07:45 发。07:30 时车还没到乙，进度应停在始发站之后
        val late = listOf(
            rows[0],
            row(stop("乙", "07:00", "07:05"), delay(arriveDelay = 40, departDelay = 40)),
            rows[2],
        )
        val p = trainProgress(late, date, at(2026, 11, 1, 7, 30))!!
        assertEquals(0, p.reachedIndex)
        assertFalse(p.atStation)
    }

    @Test
    fun `跨零点车按绝对分钟比较而不是时钟差`() {
        // 甲 22:00 发 → 乙 次日 01:00 到 01:05 发 → 丙 次日 03:00 到
        val night = listOf(
            row(stop("甲", null, "22:00")),
            row(stop("乙", "01:00", "01:05", dayDiff = 1)),
            row(stop("丙", "03:00", null, dayDiff = 1)),
        )
        // 现在已是次日 00:30：只比时钟会得到负数、判成"未出发"；按绝对分钟则正确落在甲乙之间
        val crossing = trainProgress(night, date, at(2026, 11, 2, 0, 30))!!
        assertEquals(0, crossing.reachedIndex)
        assertFalse(crossing.atStation)
        // 次日 01:02 已过乙站到达、未到出发，正是停站中
        val later = trainProgress(night, date, at(2026, 11, 2, 1, 2))!!
        assertEquals(1, later.reachedIndex)
        assertTrue(later.atStation)
    }

    @Test
    fun `运行日期不是今天时不给进度`() {
        // 由界面侧决定何时调用；这里确认解析失败安全返回 null
        assertNull(trainProgress(rows, "不是日期", at(2026, 11, 1, 7, 2)))
    }
}
