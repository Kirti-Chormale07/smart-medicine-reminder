package com.example.mediremind

import android.app.Application
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Scheduler

class MedRemindApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
        // Re-arm today's and tomorrow's alarms as soon as the process starts.
        try {
            Scheduler.scheduleNextTwoDays(this)
        } catch (_: Exception) {
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ctx = LocaleHelper.wrap(this)
        val nm = getSystemService(NotificationManager::class.java) ?: return

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val doses = android.app.NotificationChannel(
            CHANNEL_DOSES,
            ctx.getString(R.string.notif_channel_name),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ctx.getString(R.string.notif_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            setSound(sound, audio)
        }

        val alerts = android.app.NotificationChannel(
            CHANNEL_ALERTS,
            ctx.getString(R.string.notif_alert_name),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ctx.getString(R.string.notif_alert_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 400, 700)
            setSound(sound, audio)
        }

        nm.createNotificationChannels(listOf(doses, alerts))
    }

    companion object {
        const val CHANNEL_DOSES = "dose_reminders"
        const val CHANNEL_ALERTS = "missed_alerts"
    }
}
