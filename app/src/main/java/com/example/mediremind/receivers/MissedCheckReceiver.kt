package com.example.mediremind.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mediremind.data.Db
import com.example.mediremind.data.Status
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Notifs
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.SmsHelper

/**
 * Grace period expired with no answer:
 * mark the dose missed, notify on screen and SMS the guardian (in the app's language).
 */
class MissedCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(Scheduler.EXTRA_MED_ID, -1)
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)
        if (medId < 0 || at <= 0) return

        val db = Db(context)
        val status = db.logStatus(medId, at)
        if (status == null || status != Status.PENDING) return // answered already

        val med = db.getMedicine(medId)
        val medName = med?.name
            ?: db.logsBetween(at, at + 1).firstOrNull()?.medName
            ?: return

        db.updateLog(medId, at, Status.MISSED, guardianNotified = 1)

        val wrapped = LocaleHelper.wrap(context)
        Notifs.showMissed(wrapped, medId, medName)

        // Keep the process alive until the (network/SIM) SMS has been delivered.
        val pending = goAsync()
        Thread {
            try {
                SmsHelper.sendMissedBlocking(context, medName, med?.dose ?: "", at)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
