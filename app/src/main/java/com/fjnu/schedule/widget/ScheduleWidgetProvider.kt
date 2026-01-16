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
import com.fjnu.schedule.data.AppDatabase
import com.fjnu.schedule.model.Course
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun buildRemoteViews(context: Context, widgetId: Int): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.app_widget_schedule)
        val mode = loadMode(context, widgetId)
        val db = AppDatabase.getInstance(context)
        val settingsDao = db.settingsDao()
        val semesterDao = db.semesterDao()
        val courseDao = db.courseDao()

        val appSettings = settingsDao.getAppSettings()
        val semesterId = appSettings?.currentSemesterId
            ?: semesterDao.getSemesters().firstOrNull()?.id ?: 0L
        val semester = semesterDao.getSemester(semesterId)
        val scheduleSettings = settingsDao.getScheduleSettings(semesterId)
        val totalWeeks = scheduleSettings?.totalWeeks ?: DEFAULT_TOTAL_WEEKS
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val startDate = semester?.startDate?.let { LocalDate.parse(it, formatter) } ?: LocalDate.now()
        val today = LocalDate.now()
        val currentWeek = calculateWeekIndex(startDate, today)

        val courses = courseDao.getAllCourses(semesterId)
        val lines = if (mode == MODE_WEEK) {
            buildWeekLines(courses, currentWeek, totalWeeks)
        } else {
            buildDayLines(courses, currentWeek, totalWeeks, today.dayOfWeek.value)
        }

        val title = if (mode == MODE_WEEK) {
            if (currentWeek in 1..totalWeeks) "Week $currentWeek" else "Week"
        } else {
            "Today"
        }
        remoteViews.setTextViewText(R.id.tv_widget_title, title)
        applyModeStyle(context, remoteViews, mode)
        bindLines(remoteViews, lines)
        bindActions(context, remoteViews, widgetId)

        return remoteViews
    }

    private fun buildDayLines(
        courses: List<Course>,
        weekIndex: Int,
        totalWeeks: Int,
        dayOfWeek: Int
    ): List<String> {
        if (weekIndex !in 1..totalWeeks) {
            return listOf("Out of term")
        }
        val items = courses.filter { course ->
            course.dayOfWeek == dayOfWeek && course.weekPattern.contains(weekIndex)
        }.sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))
        if (items.isEmpty()) {
            return listOf("No courses")
        }
        return items.take(MAX_LINES).map { course ->
            val periodText = if (course.startPeriod == course.endPeriod) {
                "${course.startPeriod}"
            } else {
                "${course.startPeriod}-${course.endPeriod}"
            }
            val location = course.location?.let { " @ $it" } ?: ""
            "$periodText ${course.name}$location"
        }
    }

    private fun buildWeekLines(
        courses: List<Course>,
        weekIndex: Int,
        totalWeeks: Int
    ): List<String> {
        if (weekIndex !in 1..totalWeeks) {
            return listOf("Out of term")
        }
        val items = courses.filter { it.weekPattern.contains(weekIndex) }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.endPeriod }))
        if (items.isEmpty()) {
            return listOf("No courses")
        }
        return items.take(MAX_LINES).map { course ->
            val dayText = formatDay(course.dayOfWeek)
            val periodText = if (course.startPeriod == course.endPeriod) {
                "${course.startPeriod}"
            } else {
                "${course.startPeriod}-${course.endPeriod}"
            }
            "$dayText $periodText ${course.name}"
        }
    }

    private fun bindLines(remoteViews: RemoteViews, lines: List<String>) {
        val lineIds = intArrayOf(
            R.id.tv_widget_line1,
            R.id.tv_widget_line2,
            R.id.tv_widget_line3,
            R.id.tv_widget_line4,
            R.id.tv_widget_line5
        )
        lineIds.forEachIndexed { index, id ->
            val text = lines.getOrNull(index)
            if (text != null) {
                remoteViews.setTextViewText(id, text)
                remoteViews.setViewVisibility(id, android.view.View.VISIBLE)
            } else {
                remoteViews.setViewVisibility(id, android.view.View.GONE)
            }
        }
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
        val unselectedText = ContextCompat.getColor(context, R.color.text_primary)

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

    private fun calculateWeekIndex(semesterStart: LocalDate, date: LocalDate): Int {
        val daysDiff = ChronoUnit.DAYS.between(semesterStart, date)
        return (daysDiff / 7).toInt() + 1
    }

    private fun formatDay(day: Int): String {
        return when (day) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            else -> "Sun"
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
        private const val MAX_LINES = 5
        private const val DEFAULT_TOTAL_WEEKS = 20

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
