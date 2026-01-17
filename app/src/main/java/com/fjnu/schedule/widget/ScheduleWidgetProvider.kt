package com.fjnu.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.fjnu.schedule.MainActivity
import com.fjnu.schedule.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            updateWidgetAsync(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_SET_MODE -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_DAY
                    saveMode(context, widgetId, mode)
                    updateWidgetAsync(context, AppWidgetManager.getInstance(context), widgetId)
                }
            }
            ACTION_UPDATE_ALL,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
                ids.forEach { updateWidgetAsync(context, manager, it) }
            }
        }
    }

    private fun updateWidgetAsync(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remoteViews = buildRemoteViews(context, widgetId)
                manager.updateAppWidget(widgetId, remoteViews)
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.list_widget_lines)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun buildRemoteViews(context: Context, widgetId: Int): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.app_widget_schedule)
        val mode = loadMode(context, widgetId)
        val adapterIntent = Intent(context, ScheduleWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        remoteViews.setRemoteAdapter(R.id.list_widget_lines, adapterIntent)
        remoteViews.setEmptyView(R.id.list_widget_lines, R.id.tv_widget_empty)
        applyModeStyle(context, remoteViews, mode)
        bindActions(context, remoteViews, widgetId)

        return remoteViews
    }

    private fun bindActions(context: Context, remoteViews: RemoteViews, widgetId: Int) {
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.widget_schedule_root, openPendingIntent)

        val dayIntent = Intent(context, ScheduleWidgetProvider::class.java).apply {
            action = ACTION_SET_MODE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(EXTRA_MODE, MODE_DAY)
        }
        val weekIntent = Intent(context, ScheduleWidgetProvider::class.java).apply {
            action = ACTION_SET_MODE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(EXTRA_MODE, MODE_WEEK)
        }
        val dayPending = PendingIntent.getBroadcast(
            context,
            widgetId,
            dayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val weekPending = PendingIntent.getBroadcast(
            context,
            widgetId + 1000,
            weekIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.btn_widget_day, dayPending)
        remoteViews.setOnClickPendingIntent(R.id.btn_widget_week, weekPending)
    }

    private fun applyModeStyle(context: Context, remoteViews: RemoteViews, mode: String) {
        val selected = R.drawable.widget_tab_selected
        val unselected = R.drawable.widget_tab_unselected
        val selectedText = ContextCompat.getColor(context, android.R.color.white)
        val unselectedText = ContextCompat.getColor(context, R.color.primary)

        if (mode == MODE_WEEK) {
            remoteViews.setInt(R.id.btn_widget_day, "setBackgroundResource", unselected)
            remoteViews.setInt(R.id.btn_widget_week, "setBackgroundResource", selected)
            remoteViews.setTextColor(R.id.btn_widget_day, unselectedText)
            remoteViews.setTextColor(R.id.btn_widget_week, selectedText)
        } else {
            remoteViews.setInt(R.id.btn_widget_day, "setBackgroundResource", selected)
            remoteViews.setInt(R.id.btn_widget_week, "setBackgroundResource", unselected)
            remoteViews.setTextColor(R.id.btn_widget_day, selectedText)
            remoteViews.setTextColor(R.id.btn_widget_week, unselectedText)
        }
    }

    private fun saveMode(context: Context, widgetId: Int, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$PREF_MODE$widgetId", mode)
            .apply()
    }

    private fun loadMode(context: Context, widgetId: Int): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$PREF_MODE$widgetId", MODE_DAY) ?: MODE_DAY
    }

    companion object {
        private const val PREFS_NAME = "schedule_widget_prefs"
        private const val PREF_MODE = "schedule_widget_mode_"
        private const val MODE_DAY = "day"
        private const val MODE_WEEK = "week"

        private const val ACTION_SET_MODE = "com.fjnu.schedule.widget.ACTION_SET_MODE"
        private const val ACTION_UPDATE_ALL = "com.fjnu.schedule.widget.ACTION_UPDATE_ALL"
        private const val EXTRA_MODE = "extra_mode"

        fun requestUpdate(context: Context) {
            val intent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_ALL
            }
            context.sendBroadcast(intent)
        }
    }
}
