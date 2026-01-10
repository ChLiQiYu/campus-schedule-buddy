package com.example.campus_schedule_buddy.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.campus_schedule_buddy.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "课程提醒"
        val teacher = intent.getStringExtra(EXTRA_TEACHER) ?: ""
        val location = intent.getStringExtra(EXTRA_LOCATION) ?: ""
        val startPeriod = intent.getIntExtra(EXTRA_START_PERIOD, 0)
        val endPeriod = intent.getIntExtra(EXTRA_END_PERIOD, 0)
        val enableVibrate = intent.getBooleanExtra(EXTRA_ENABLE_VIBRATE, true)

        val contentText = buildString {
            if (teacher.isNotBlank()) append("教师：").append(teacher).append("  ")
            if (location.isNotBlank()) append("地点：").append(location).append("  ")
            if (startPeriod > 0 && endPeriod > 0) {
                append("第").append(startPeriod).append("-").append(endPeriod).append("节")
            }
        }.ifBlank { "课程即将开始" }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_schedule)
            .setContentTitle(courseName)
            .setContentText(contentText)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(if (enableVibrate) longArrayOf(0, 200, 100, 200) else longArrayOf(0))
            .build()

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(notificationId, notification)
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "课程提醒",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "课程开始前提醒通知"
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "course_reminder_channel"
        const val EXTRA_COURSE_ID = "extra_course_id"
        const val EXTRA_COURSE_NAME = "extra_course_name"
        const val EXTRA_TEACHER = "extra_teacher"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val EXTRA_START_PERIOD = "extra_start_period"
        const val EXTRA_END_PERIOD = "extra_end_period"
        const val EXTRA_ENABLE_VIBRATE = "extra_enable_vibrate"
    }
}
