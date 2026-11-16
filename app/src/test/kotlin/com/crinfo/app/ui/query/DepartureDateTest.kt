package com.crinfo.app.ui.query

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DepartureDateTest {
    private val today = LocalDate.of(2026, 10, 31)

    @Test
    fun `before 18h defaults to today`() {
        assertEquals(today, defaultDepartureDate(today, 0))
        assertEquals(today, defaultDepartureDate(today, 17))
    }

    @Test
    fun `from 18h defaults to next day`() {
        assertEquals(today.plusDays(1), defaultDepartureDate(today, 18))
        assertEquals(today.plusDays(1), defaultDepartureDate(today, 23))
    }
}
