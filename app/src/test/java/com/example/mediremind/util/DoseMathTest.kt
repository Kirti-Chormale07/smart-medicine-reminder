package com.example.mediremind.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DoseMathTest {

    private fun dayStart(september: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun fixedDose_isAtTheRightTime() {
        val at = DoseMath.doseMillis(dayStart(9, 23), 13, 30)
        val c = Calendar.getInstance().apply { timeInMillis = at }
        assertEquals(13, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(Calendar.MINUTE))
    }

    @Test
    fun lunchRelativeDose_addsOffset() {
        // Lunch at 13:00, medicine 30 minutes after lunch.
        val at = DoseMath.mealDoseMillis(dayStart(9, 23), 13 * 60, 30)
        val c = Calendar.getInstance().apply { timeInMillis = at }
        assertEquals(13, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(Calendar.MINUTE))
    }

    @Test
    fun beforeMealDose_subtractsOffset() {
        // Breakfast at 08:00, medicine 30 minutes before.
        val at = DoseMath.mealDoseMillis(dayStart(9, 23), 8 * 60, -30)
        val c = Calendar.getInstance().apply { timeInMillis = at }
        assertEquals(7, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(Calendar.MINUTE))
    }

    @Test
    fun dayStartOf_tomorrow_isNextMidnight() {
        val now = dayStart(9, 23) + 10 * 60 * 60 * 1000L // 10:00 today
        val tomorrow = DoseMath.dayStartOf(now, 1)
        val today = DoseMath.dayStartOf(now, 0)
        assertEquals(24L * 60 * 60 * 1000L, tomorrow - today)
    }
}
