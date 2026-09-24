package com.example.mediremind.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mediremind.MedRemindApp
import com.example.mediremind.R
import com.example.mediremind.data.DoseTime
import com.example.mediremind.data.Medicine
import com.example.mediremind.receivers.ActionReceiver
import com.example.mediremind.ui.MainActivity
import com.example.mediremind.ui.ReminderActivity

/** All notification building/posting lives here, always localised. */
object Notifs {

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** High-priority "time for your medicine" notification with big buttons. */
    fun showDose(context: Context, med: Medicine, dose: DoseTime, at: Long) {
        if (!canPost(context)) return
        val ctx = LocaleHelper.wrap(context)

        val timeStr = TimeFmt.time(ctx, at)
        val hint = TimeFmt.hint(ctx, dose, at)
        val title = ctx.getString(R.string.notif_title, med.name)
        val body = ctx.getString(R.string.notif_body, med.dose.ifBlank { " " }, hint)

        val open = Intent(ctx, ReminderActivity::class.java).apply {
            putExtra(Scheduler.EXTRA_MED_ID, med.id)
            putExtra(Scheduler.EXTRA_AT, at)
        }
        val openPi = PendingIntent.getActivity(
            ctx, Scheduler.requestCode(med.id, at), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val took = actionPi(ctx, Scheduler.ACTION_TAKEN, med.id, at)
        val snooze = actionPi(ctx, Scheduler.ACTION_SNOOZE, med.id, at)

        val notification = NotificationCompat.Builder(ctx, MedRemindApp.CHANNEL_DOSES)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n$timeStr"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .addAction(R.drawable.ic_stat_pill, ctx.getString(R.string.action_took), took)
            .addAction(R.drawable.ic_stat_pill, ctx.getString(R.string.action_snooze), snooze)
            .build()

        NotificationManagerCompat.from(ctx).notify(med.id.toInt(), notification)
    }

    /** Red "dose missed – guardian informed" alert. */
    fun showMissed(context: Context, medId: Long, medName: String) {
        if (!canPost(context)) return
        val ctx = LocaleHelper.wrap(context)

        val open = Intent(ctx, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            ctx, (medId % Int.MAX_VALUE).toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, MedRemindApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(ctx.getString(R.string.notif_missed_title))
            .setContentText(ctx.getString(R.string.notif_missed_body, medName))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(ctx.getString(R.string.notif_missed_body, medName)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .build()

        NotificationManagerCompat.from(ctx).notify((1_000_000 + medId).toInt(), notification)
    }

    fun cancel(context: Context, medId: Long) {
        NotificationManagerCompat.from(context).cancel(medId.toInt())
        NotificationManagerCompat.from(context).cancel((1_000_000 + medId).toInt())
    }

    private fun actionPi(context: Context, action: String, medId: Long, at: Long): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java).apply {
            this.action = action
            putExtra(Scheduler.EXTRA_MED_ID, medId)
            putExtra(Scheduler.EXTRA_AT, at)
        }
        return PendingIntent.getBroadcast(
            context, Scheduler.requestCode(medId, at), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
