package com.example.mediremind.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mediremind.data.Db
import com.example.mediremind.data.Prefs
import com.example.mediremind.data.Status
import com.example.mediremind.util.Notifs
import com.example.mediremind.util.Scheduler

/** Alarm time reached: ring the notification and arm the guardian escalation. */
class DoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(Scheduler.EXTRA_MED_ID, -1)
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)
        if (medId < 0 || at <= 0) return

        val db = Db(context)
        val med = db.getMedicine(medId) ?: return
        val status = db.ensureLog(med, at)
        if (status != Status.PENDING) return // already answered (taken/snoozed/missed)

        val dose = med.doses.minByOrNull {
            kotlin.math.abs(Scheduler.instantOf(Prefs(context), at, it) - at)
        } ?: return

        Notifs.showDose(context, med, dose, at)

        // Nobody answers within the grace period → guardian gets an SMS.
        val grace = Prefs(context).graceMin * 60_000L
        var checkAt = at + grace
        val now = System.currentTimeMillis()
        if (checkAt < now + 2000) checkAt = now + 2000
        Scheduler.scheduleMissedCheck(context, medId, at, checkAt)
    }
}
