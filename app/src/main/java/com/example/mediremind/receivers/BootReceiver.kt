package com.example.mediremind.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mediremind.util.Scheduler

/** Re-arms every alarm after reboot / app update / clock or timezone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Scheduler.scheduleNextTwoDays(context)
        }
    }
}
