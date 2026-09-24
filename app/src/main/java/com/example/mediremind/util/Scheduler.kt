package com.example.mediremind.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.mediremind.data.Db
import com.example.mediremind.data.DoseTime
import com.example.mediremind.data.Medicine
import com.example.mediremind.data.Prefs
import com.example.mediremind.receivers.DayRolloverReceiver
import com.example.mediremind.receivers.DoseAlarmReceiver
import com.example.mediremind.receivers.MissedCheckReceiver
import kotlin.math.max

/**
 * Owns every AlarmManager schedule:
 *  - a DOSE alarm at each reminder time (rings notification + full-screen reminder)
 *  - a MISSED alarm grace-period later (escalates to an SMS to the guardian)
 *  - a daily midnight alarm that rolls the schedule forward
 */
object Scheduler {

    const val ACTION_DOSE = "com.example.mediremind.action.DOSE"
    const val ACTION_MISSED = "com.example.mediremind.action.MISSED"
    const val ACTION_ROLL = "com.example.mediremind.action.ROLL"
    const val ACTION_TAKEN = "com.example.mediremind.action.TAKEN"
    const val ACTION_SNOOZE = "com.example.mediremind.action.SNOOZE"

    const val EXTRA_MED_ID = "med_id"
    const val EXTRA_AT = "at"

    const val SNOOZE_MS = 10 * 60 * 1000L

    private fun am(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || am(context).canScheduleExactAlarms()

    /**
     * Deterministic request code: unique for (medicine, moment) inside a multi-day window.
     * Missed-check alarms additionally use a different Intent action/component.
     */
    fun requestCode(medId: Long, at: Long): Int {
        val minutes = at / 60_000L
        return ((medId % 512) * 4_194_304L + (minutes % 2_097_152L)).toInt()
    }

    /** Schedules today + tomorrow for every medicine; safe to call any time. */
    fun scheduleNextTwoDays(context: Context) {
        val prefs = Prefs(context)
        val meds = Db(context).allMedicines()
        val now = System.currentTimeMillis()
        val grace = prefs.graceMin * 60_000L

        for (offset in 0..1) {
            val dayStart = DoseMath.dayStartOf(now, offset)
            for (med in meds) {
                for (d in med.doses) {
                    val at = instantOf(prefs, dayStart, d)
                    when {
                        // Still in the future – normal schedule.
                        at > now -> {
                            scheduleDose(context, med.id, at, at)
                            scheduleMissedCheck(context, med.id, at, at + grace)
                        }
                        // Recent past still inside the grace window – remind late,
                        // escalation stays anchored to the original time.
                        now - at < grace -> {
                            scheduleDose(context, med.id, at, now + 1200)
                            scheduleMissedCheck(context, med.id, at, at + grace)
                        }
                        // Long overdue with no answer yet (e.g. phone was off) – escalate.
                        else -> {
                            scheduleMissedCheck(context, med.id, at, now + 1200)
                        }
                    }
                }
            }
        }
        scheduleDayRollover(context)
    }

    /** Epoch millis of one dose on the day starting at dayStart. */
    fun instantOf(prefs: Prefs, dayStart: Long, d: DoseTime): Long =
        if (d.meal == 0) DoseMath.doseMillis(dayStart, d.hour, d.minute)
        else DoseMath.mealDoseMillis(dayStart, prefs.mealMinutes(d.meal), d.offset)

    fun scheduleDose(context: Context, medId: Long, at: Long, triggerAt: Long) {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            action = ACTION_DOSE
            putExtra(EXTRA_MED_ID, medId)
            putExtra(EXTRA_AT, at)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode(medId, at), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val trigger = max(triggerAt, System.currentTimeMillis() + 500)
        if (canExact(context)) {
            am(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            am(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun scheduleMissedCheck(context: Context, medId: Long, at: Long, triggerAt: Long) {
        val intent = Intent(context, MissedCheckReceiver::class.java).apply {
            action = ACTION_MISSED
            putExtra(EXTRA_MED_ID, medId)
            putExtra(EXTRA_AT, at)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode(medId, at), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val trigger = max(triggerAt, System.currentTimeMillis() + 500)
        if (canExact(context)) {
            am(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            am(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun cancelMissedCheck(context: Context, medId: Long, at: Long) {
        val intent = Intent(context, MissedCheckReceiver::class.java).apply {
            action = ACTION_MISSED
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode(medId, at), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am(context).cancel(pi)
    }

    /** Cancels every pending alarm of one medicine (today/tomorrow window). */
    fun cancelMedicine(context: Context, med: Medicine) {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        for (offset in 0..1) {
            val dayStart = DoseMath.dayStartOf(now, offset)
            for (d in med.doses) {
                val at = instantOf(prefs, dayStart, d)
                val doseIntent = Intent(context, DoseAlarmReceiver::class.java).apply {
                    action = ACTION_DOSE
                }
                am(context).cancel(
                    PendingIntent.getBroadcast(
                        context, requestCode(med.id, at), doseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                cancelMissedCheck(context, med.id, at)
            }
        }
    }

    private fun scheduleDayRollover(context: Context) {
        val at = DoseMath.dayStartOf(System.currentTimeMillis(), 1) + 60_000L
        val intent = Intent(context, DayRolloverReceiver::class.java).apply {
            action = ACTION_ROLL
        }
        val pi = PendingIntent.getBroadcast(
            context, 777, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canExact(context)) {
            am(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
