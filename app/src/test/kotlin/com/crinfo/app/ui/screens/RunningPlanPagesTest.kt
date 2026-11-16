package com.crinfo.app.ui.screens

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 开行计划分页回归：星期对齐、每页 5 行 35 天连排，初始页第一行必须包含本月 1 号；
 * 页与页首尾相接、绝不重叠，否则就会出现「上一页最后一行又出现在下一页第一行」。
 */
class RunningPlanPagesTest {

    /** 实测 D8414 的开行计划：2026-10-19 起连排 91 天。 */
    private fun runningOf(head: LocalDate, days: Int): Map<String, Boolean> =
        (0 until days).associate {
            head.plusDays(it.toLong()).format(DateTimeFormatter.BASIC_ISO_DATE) to true
        }

    @Test
    fun `initial page first row contains the first of this month`() {
        // 2026-11-01 是周日，锚点就是 11-01；数据 10-19 起、早于锚点，要向前整页平铺
        val pages = planPages(runningOf(LocalDate.of(2026, 10, 19), 91), LocalDate.of(2026, 11, 3))!!
        assertEquals(LocalDate.of(2026, 9, 27), pages.start)
        assertEquals(DayOfWeek.SUNDAY, pages.start.dayOfWeek)
        assertEquals(4, pages.pageCount)
        assertEquals(1, pages.initialPage)
        // 初始页第一格 = 11-01，1 号落在第一行
        assertEquals(LocalDate.of(2026, 11, 1), pages.start.plusDays(pages.initialPage.toLong() * 35))

        // 1 号不是周日：2026-08-01 是周六，锚点回退到所在周的周日 07-26
        val aug = planPages(runningOf(LocalDate.of(2026, 7, 26), 60), LocalDate.of(2026, 8, 10))!!
        assertEquals(LocalDate.of(2026, 7, 26), aug.start)
        assertEquals(0, aug.initialPage)
        assertEquals(LocalDate.of(2026, 8, 1), aug.start.plusDays(aug.initialPage.toLong() * 35).plusDays(6))
    }

    @Test
    fun `pages are continuous slices without overlap`() {
        val pages = planPages(runningOf(LocalDate.of(2026, 10, 19), 91), LocalDate.of(2026, 11, 3))!!
        val days = (0 until pages.pageCount * 35).map { pages.start.plusDays(it.toLong()) }
        assertEquals(days.size, days.toSet().size)
        // 相邻页首尾相接：第 N 页最后一天 +1 = 第 N+1 页第一天
        for (page in 1 until pages.pageCount) {
            assertEquals(days[(page - 1) * 35 + 34].plusDays(1), days[page * 35])
        }
    }

    @Test
    fun `initial page follows today`() {
        // 2026-08-01 是周六、锚点 07-26；08-31 距锚点 36 天，落在第二页
        val running = runningOf(LocalDate.of(2026, 7, 26), 200)
        assertEquals(1, planPages(running, LocalDate.of(2026, 8, 31))!!.initialPage)
        // 今天已过计划末日时停在最后一页
        val tail = planPages(runningOf(LocalDate.of(2026, 10, 19), 91), LocalDate.of(2027, 3, 1))!!
        assertEquals(tail.pageCount - 1, tail.initialPage)
    }
}
