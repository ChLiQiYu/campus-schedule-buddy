package com.example.schedule.focus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.schedule.R

class FocusModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val enable = intent.getBooleanExtra(EXTRA_ENABLE, false)
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME).orEmpty()
        setDndEnabled(context, enable)
        showStatusNotification(context, enable, courseName)
    }

    private fun showStatusNotification(context: Context, enabled: Boolean, courseName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.isNotificationPolicyAccessGranted) return
        ensureChannel(manager)
        val title = if (enabled) "已进入专注模式" else "已恢复正常模式"
        val message = if (enabled && courseName.isNotBlank()) {
            "当前课程：$courseName"
        } else if (enabled) {
            "课程开始，专注已开启"
        } else {
            "课程结束，专注已关闭"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "专注模式提醒",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_ENABLE = "extra_enable"
        const val EXTRA_COURSE_NAME = "extra_course_name"
        private const val CHANNEL_ID = "focus_mode"
        private const val NOTIFICATION_ID = 3101

        fun setDndEnabled(context: Context, enabled: Boolean) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.isNotificationPolicyAccessGranted) return
            val filter = if (enabled) {
                NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            manager.setInterruptionFilter(filter)
        }
    }
}
