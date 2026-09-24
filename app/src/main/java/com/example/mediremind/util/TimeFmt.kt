package com.example.mediremind.util

import android.content.Context
import com.example.mediremind.R
import com.example.mediremind.data.DoseTime
import java.util.Date

/** Time formatting and human labels, always in the app's chosen language. */
object TimeFmt {

    /** Localised time string, e.g. "1:30 PM" or "13:30". */
    fun time(context: Context, millis: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))

    fun mealName(context: Context, meal: Int): String = when (meal) {
        1 -> context.getString(R.string.meal_breakfast)
        2 -> context.getString(R.string.meal_lunch)
        3 -> context.getString(R.string.meal_snack)
        4 -> context.getString(R.string.meal_dinner)
        else -> context.getString(R.string.meal_fixed)
    }

    /** "30 min after lunch" style label, or null for fixed-time doses. */
    fun relLabel(context: Context, d: DoseTime): String? {
        if (d.meal == 0) return null
        val meal = mealName(context, d.meal)
        return when {
            d.offset == 0 -> context.getString(R.string.rel_at_meal, meal)
            d.offset == 60 -> context.getString(R.string.rel_after_1h, meal)
            d.offset > 0 -> context.getString(R.string.rel_after, d.offset, meal)
            else -> context.getString(R.string.rel_before, -d.offset, meal)
        }
    }

    /** Notification/detail hint: "1:30 PM (after lunch)" or just "1:30 PM". */
    fun hint(context: Context, d: DoseTime, at: Long): String {
        val t = time(context, at)
        val rel = relLabel(context, d)
        return if (rel == null) t else "$t ($rel)"
    }
}
