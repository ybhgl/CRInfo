package com.crinfo.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「查询日之后的下一个开行日」的纯逻辑回归。
 * 真实库里的换号车在 11-24 / 11-25 两天的判定由 `OfflineDbRealFileTest` 另测，
 * 这里只管日期推进与边界，不依赖 an.db。
 */
class RunningCheckNextDateTest {

    @Test
    fun `not started returns the first running day`() {
        // 查询日早于图定起始日：下一次就是 startDate
        assertEquals(
            20261125,
            RunningCheck.nextRunningDate(20261120, 20261125, 20991231, RunningRule(1, 1)),
        )
    }

    @Test
    fun `expired returns null`() {
        // 查询日之后的每一天都越过 endDate
        assertNull(RunningCheck.nextRunningDate(20261125, 20260101, 20261124, RunningRule(1, 1)))
    }

    @Test
    fun `end date itself can be the next running day`() {
        assertEquals(
            20261121,
            RunningCheck.nextRunningDate(20261120, 20260101, 20261121, RunningRule(1, 1)),
        )
    }

    @Test
    fun `period bitmap skips closed days`() {
        // 周期 7、只点亮第 0 天：11-02 是开行日，下一次应为 11-09
        val weekly = RunningRule(7, 0b1)
        assertEquals(
            20261109,
            RunningCheck.nextRunningDate(20261102, 20261102, 20261231, weekly),
        )
    }

    @Test
    fun `gives up after the scan budget`() {
        val weekly = RunningRule(7, 0b1)
        assertNull(RunningCheck.nextRunningDate(20261102, 20261102, 20261231, weekly, maxScanDays = 3))
    }

    @Test
    fun `invalid query date does not loop forever`() {
        assertNull(RunningCheck.nextRunningDate(0, 20260101, 20261231, RunningRule(1, 1), maxScanDays = 5))
    }
}
