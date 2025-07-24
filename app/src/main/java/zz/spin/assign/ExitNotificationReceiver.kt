package zz.spin.assign

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ExitNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        showExitNotification(context)
    }

    companion object {
        private const val CHANNEL_ID = "exit_reminder_channel"
        private const val NOTIFICATION_ID = 2
        private const val EXIT_ALARM_REQUEST_CODE = 1001

        fun scheduleExitNotification(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ExitNotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                EXIT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Cancel any existing exit notification
            alarmManager.cancel(pendingIntent)

            // Schedule notification for 3 seconds from now (immediate but allowing app to background)
            val triggerTime = System.currentTimeMillis() + 3000

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }

        fun cancelExitNotification(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ExitNotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                EXIT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )

            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }

            // Also dismiss any existing notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        }

        private fun showExitNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create notification channel if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Exit Reminders"
                val descriptionText = "Notifications when you leave without completing assignment"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Create intent to open the app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("Don't forget your assignment!")
                .setContentText("You left without marking your assignment as DONE or SKIPPED today.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }
}