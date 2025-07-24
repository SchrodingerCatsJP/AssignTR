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
import java.util.Calendar

class AppOpenReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // If the phone was rebooted, reschedule the alarm
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            scheduleDailyAppOpenReminder(context)
            return
        }

        // Check if the app was opened today
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAppOpenTime = prefs.getLong(LAST_APP_OPEN_KEY, 0)

        var hasOpenedToday = false
        if (lastAppOpenTime != 0L) {
            val lastOpenCalendar = Calendar.getInstance().apply { timeInMillis = lastAppOpenTime }
            val todayCalendar = Calendar.getInstance()
            if (lastOpenCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
                lastOpenCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)) {
                hasOpenedToday = true
            }
        }

        // Only show the notification if the app hasn't been opened today
        if (!hasOpenedToday) {
            showAppOpenReminderNotification(context)
        }

        // Always reschedule for the next day
        scheduleDailyAppOpenReminder(context)
    }

    companion object {
        private const val CHANNEL_ID = "app_open_reminder_channel"
        private const val NOTIFICATION_ID = 3
        private const val APP_OPEN_ALARM_REQUEST_CODE = 1002
        const val PREFS_NAME = "AssignmentTrackerPrefs"
        const val LAST_APP_OPEN_KEY = "lastAppOpen"

        fun scheduleDailyAppOpenReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AppOpenReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                APP_OPEN_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Check if we can schedule exact alarms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                return // Cannot proceed without permission
            }

            // Set alarm for 9 AM every day
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 9) // 9 AM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If 9 AM has already passed today, schedule for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }

        private fun showAppOpenReminderNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create notification channel if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Daily App Reminders"
                val descriptionText = "Daily reminders to open the app"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
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
                .setContentTitle("Time to check your assignments!")
                .setContentText("You haven't opened the app today. Don't forget to track your progress!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }
}