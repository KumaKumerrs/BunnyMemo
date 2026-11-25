package com.example.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val isDailyDigest = intent.action == ReminderScheduler.ACTION_DAILY_DIGEST
        val isClassReminder = intent.action == ReminderScheduler.ACTION_CLASS_REMINDER

        // Always reschedule the next day's gentle daily forget-me-not digest if active
        if (isDailyDigest) {
            ReminderScheduler(context).rescheduleTomorrowFromStoredPrefs()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val itemId = intent.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID)
        if (!isDailyDigest && !isClassReminder && itemId.isNullOrBlank()) return

        val channelId = intent.getStringExtra(ReminderScheduler.EXTRA_CHANNEL_ID)
            ?: if (isDailyDigest) ReminderScheduler.DAILY_DIGEST_CHANNEL_ID else ReminderScheduler.CHANNEL_ID
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE)
            ?: context.getString(R.string.app_name)
        val message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE)
            ?: context.getString(R.string.notif_default_body)
        val notificationId = intent.getIntExtra(
            ReminderScheduler.EXTRA_NOTIFICATION_ID,
            (itemId ?: title).hashCode()
        )

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!itemId.isNullOrBlank()) {
                putExtra(ReminderScheduler.EXTRA_ITEM_ID, itemId)
            }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(ReminderScheduler.FORGET_ME_NOT_ACCENT_COLOR)
            .setSubText(context.getString(R.string.notif_subtext_theme))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notificationId, notification)
    }
}
