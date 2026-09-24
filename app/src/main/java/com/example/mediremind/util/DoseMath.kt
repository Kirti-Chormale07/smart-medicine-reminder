package com.example.mediremind.util

import java.util.Calendar

/** Pure date/time math for doses – easy to unit test. */
object DoseMath {

    /** Midnight of today + dayOffset in the default timezone. */
    fun dayStartOf(nowMillis: Long, dayOffset: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        c.add(Calendar.DAY_OF_YEAR, dayOffset)
        return c.timeInMillis
    }

    /** Fixed-time dose on the given day. */
    fun doseMillis(dayStartMillis: Long, hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = dayStartMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    /** Meal-relative dose: meal time (minutes of day) + offset. */
    fun mealDoseMillis(dayStartMillis: Long, mealMinutesOfDay: Int, offsetMinutes: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = dayStartMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        c.add(Calendar.MINUTE, mealMinutesOfDay + offsetMinutes)
        return c.timeInMillis
    }
}
