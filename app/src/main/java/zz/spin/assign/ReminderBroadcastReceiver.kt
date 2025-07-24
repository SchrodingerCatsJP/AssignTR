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

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // If the phone was rebooted, we just need to reschedule the alarm.
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            scheduleDailyReminder(context)
            AppOpenReminderReceiver.scheduleDailyAppOpenReminder(context)
            return
        }

        // --- Daily Alarm Logic ---
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastActionTime = prefs.getLong(LAST_ACTION_DATE_KEY, 0)

        // Check if an action has already been logged for today.
        var hasCompletedToday = false
        if (lastActionTime != 0L) {
            val lastActionCalendar = Calendar.getInstance().apply { timeInMillis = lastActionTime }
            val todayCalendar = Calendar.getInstance()
            if (lastActionCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
                lastActionCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)) {
                hasCompletedToday = true
            }
        }

        // Only show the notification if no action was taken today.
        if (!hasCompletedToday) {
            showReminderNotification(context)
        }

        // CRUCIAL: Always reschedule the alarm for the next day to create the daily loop.
        scheduleDailyReminder(context)
    }

    companion object {
        private const val CHANNEL_ID = "assignment_reminder_channel"
        private const val NOTIFICATION_ID = 1
        const val PREFS_NAME = "AssignmentTrackerPrefs"
        const val LAST_ACTION_DATE_KEY = "lastActionDate"

        fun scheduleDailyReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderBroadcastReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                return // Cannot proceed without permission
            }

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 20) // 8 PM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }

        fun showReminderNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Assignment Reminders"
                val descriptionText = "Channel for daily assignment reminders"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply { description = descriptionText }
                notificationManager.createNotificationChannel(channel)
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("Assignment Reminder")
                .setContentText("You haven't logged your assignment yet today!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }
}