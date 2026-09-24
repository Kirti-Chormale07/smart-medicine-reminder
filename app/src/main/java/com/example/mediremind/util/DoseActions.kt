package com.example.mediremind.util

import android.content.Context
import com.example.mediremind.data.Db
import com.example.mediremind.data.Prefs
import com.example.mediremind.data.Status

/**
 * The three answers to a reminder. Shared by the full-screen ReminderActivity
 * and the notification buttons (ActionReceiver).
 */
object DoseActions {

    fun taken(context: Context, medId: Long, at: Long) {
        val db = Db(context)
        val med = db.getMedicine(medId)
        if (med != null) db.ensureLog(med, at)
        db.updateLog(medId, at, Status.TAKEN, takenAt = System.currentTimeMillis())
        db.adjustStock(medId, -1)
        Scheduler.cancelMissedCheck(context, medId, at)
        Notifs.cancel(context, medId)
    }

    fun snooze(context: Context, medId: Long, at: Long) {
        val db = Db(context)
        val med = db.getMedicine(medId)
        if (med != null) db.ensureLog(med, at)
        db.updateLog(medId, at, Status.SNOOZED)
        Scheduler.cancelMissedCheck(context, medId, at)
        Notifs.cancel(context, medId)

        // Fresh reminder in 10 minutes – DoseAlarmReceiver re-creates the log row
        // and re-arms the guardian escalation for the new time.
        val newAt = System.currentTimeMillis() + Scheduler.SNOOZE_MS
        Scheduler.scheduleDose(context, medId, newAt, newAt)
    }

    fun skip(context: Context, medId: Long, at: Long) {
        val db = Db(context)
        val med = db.getMedicine(medId) ?: return
        db.ensureLog(med, at)
        db.updateLog(medId, at, Status.MISSED, guardianNotified = 1)
        Scheduler.cancelMissedCheck(context, medId, at)
        Notifs.cancel(context, medId)

        if (Prefs(context).guardian.isNotBlank()) {
            SmsHelper.sendSkip(context, medId, at)
        }
        Notifs.showMissed(context, medId, med.name)
    }
}
