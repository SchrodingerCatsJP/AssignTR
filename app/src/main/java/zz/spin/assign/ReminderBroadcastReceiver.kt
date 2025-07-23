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
        // If the phone was rebooted, just reschedule the alarm
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            scheduleDailyReminder(context)
            return
        }

        // --- Daily Alarm Logic ---
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastActionTime = prefs.getLong(LAST_ACTION_DATE_KEY, 0)

        var shouldNotify = true
        if (lastActionTime != 0L) {
            val lastActionCalendar = Calendar.getInstance().apply { timeInMillis = lastActionTime }
            val todayCalendar = Calendar.getInstance()
            val isSameDay = lastActionCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
                    lastActionCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)
            if (isSameDay) {
                shouldNotify = false
            }
        }

        if (shouldNotify) {
            showReminderNotification(context)
        }

        // CRUCIAL: Reschedule the alarm for the next day to create the loop
        scheduleDailyReminder(context)
    }

    companion object {
        private const val CHANNEL_ID = "assignment_reminder_channel"
        private const val PREFS_NAME = "AssignmentTrackerPrefs"
        private const val LAST_ACTION_DATE_KEY = "lastActionDate"

        fun scheduleDailyReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderBroadcastReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            // On Android 12+, we need permission to schedule exact alarms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    // In a real app, you might redirect the user to settings.
                    // For now, we just won't schedule if permission is missing.
                    return
                }
            }

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 20) // 8 PM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

            // If it's already past 8 PM today, schedule for 8 PM tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            // Use a precise alarm
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        fun showReminderNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Assignment Reminders"
                val descriptionText = "Channel for daily assignment reminders"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("Assignment Reminder")
                .setContentText("You haven't logged your assignment yet today!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            notificationManager.notify(1, builder.build())
        }
    }
}
