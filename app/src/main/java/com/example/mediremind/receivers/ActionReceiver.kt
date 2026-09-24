package com.example.mediremind.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mediremind.util.DoseActions
import com.example.mediremind.util.Scheduler

/** Buttons on the notification: "Took it" / "10 min later". */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(Scheduler.EXTRA_MED_ID, -1)
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)
        if (medId < 0 || at <= 0) return

        when (intent.action) {
            Scheduler.ACTION_TAKEN -> DoseActions.taken(context, medId, at)
            Scheduler.ACTION_SNOOZE -> DoseActions.snooze(context, medId, at)
        }
    }
}
