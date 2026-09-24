package com.example.mediremind.data

import android.content.Context
import java.util.Locale

/** Small wrapper around SharedPreferences with sensible defaults. */
class Prefs(context: Context) {

    private val sp =
        context.applicationContext.getSharedPreferences("mediremind_prefs", Context.MODE_PRIVATE)

    var patientName: String
        get() = sp.getString("patient", "") ?: ""
        set(value) {
            sp.edit().putString("patient", value).apply()
        }

    var guardian: String
        get() = sp.getString("guardian", "") ?: ""
        set(value) {
            sp.edit().putString("guardian", value).apply()
        }

    /** Twilio cloud SMS (works even when the phone has no SIM). */
    var twilioEnabled: Boolean
        get() = sp.getBoolean("tw_on", false)
        set(value) {
            sp.edit().putBoolean("tw_on", value).apply()
        }

    var twilioSid: String
        get() = sp.getString("tw_sid", "") ?: ""
        set(value) {
            sp.edit().putString("tw_sid", value).apply()
        }

    var twilioToken: String
        get() = sp.getString("tw_token", "") ?: ""
        set(value) {
            sp.edit().putString("tw_token", value).apply()
        }

    var twilioFrom: String
        get() = sp.getString("tw_from", "") ?: ""
        set(value) {
            sp.edit().putString("tw_from", value).apply()
        }

    /** "" = follow system language (Hindi/Marathi/Gujarati if available, else English). */
    var lang: String
        get() = sp.getString("lang", "") ?: ""
        set(value) {
            sp.edit().putString("lang", value).apply()
        }

    /** Minutes the elder has to answer before the guardian gets an SMS. */
    var graceMin: Int
        get() = sp.getInt("grace", 15)
        set(value) {
            sp.edit().putInt("grace", value).apply()
        }

    /** Meal times stored as minutes of day. */
    var breakfast: Int
        get() = sp.getInt("bfast", 8 * 60)
        set(value) {
            sp.edit().putInt("bfast", value).apply()
        }

    var lunch: Int
        get() = sp.getInt("lunch", 13 * 60)
        set(value) {
            sp.edit().putInt("lunch", value).apply()
        }

    var snack: Int
        get() = sp.getInt("snack", 17 * 60)
        set(value) {
            sp.edit().putInt("snack", value).apply()
        }

    var dinner: Int
        get() = sp.getInt("dinner", 19 * 60 + 30)
        set(value) {
            sp.edit().putInt("dinner", value).apply()
        }

    var askedAlarmPerm: Boolean
        get() = sp.getBoolean("asked_alarm", false)
        set(value) {
            sp.edit().putBoolean("asked_alarm", value).apply()
        }

    /** Meal id: 1 breakfast, 2 lunch, 3 snack, 4 dinner. Returns minutes of day. */
    fun mealMinutes(meal: Int): Int = when (meal) {
        1 -> breakfast
        2 -> lunch
        3 -> snack
        4 -> dinner
        else -> 0
    }

    /** Resolved app language code: one of en / hi / mr / gu. */
    fun langCode(): String {
        val stored = lang
        if (stored.isNotEmpty()) return stored
        val system = Locale.getDefault().language
        return if (system == "hi" || system == "mr" || system == "gu") system else "en"
    }
}
