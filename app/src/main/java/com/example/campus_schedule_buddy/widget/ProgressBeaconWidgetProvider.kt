package com.example.campus_schedule_buddy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.campus_schedule_buddy.R
import com.example.campus_schedule_buddy.RhythmActivity
import com.example.campus_schedule_buddy.data.AppDatabase
import com.example.campus_schedule_buddy.util.WorkloadCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ProgressBeaconWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.app_widget_progress_beacon)
            views.setTextViewText(R.id.tv_widget_task, "加载中…")
            views.setTextViewText(R.id.tv_widget_load, "今日负荷：--")
            val intent = Intent(context, RhythmActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tv_widget_title, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            CoroutineScope(Dispatchers.IO).launch {
                val database = AppDatabase.getInstance(context)
                val settingsDao = database.settingsDao()
                val semesterDao = database.semesterDao()
                val courseDao = database.courseDao()
                val workspaceDao = database.workspaceDao()

                val settings = settingsDao.getAppSettings()
                val semesterId = settings?.currentSemesterId ?: 0L
                if (semesterId <= 0L) {
                    val fallbackViews = RemoteViews(context.packageName, R.layout.app_widget_progress_beacon)
                    fallbackViews.setTextViewText(R.id.tv_widget_task, "暂无任务")
                    fallbackViews.setTextViewText(R.id.tv_widget_load, "今日负荷：--")
                    fallbackViews.setOnClickPendingIntent(R.id.tv_widget_title, pendingIntent)
                    appWidgetManager.updateAppWidget(appWidgetId, fallbackViews)
                    return@launch
                }
                val semester = semesterDao.getSemester(semesterId)
                val semesterStart = semester?.startDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
                val courses = courseDao.getAllCourses(semesterId)
                val tasks = workspaceDao.getTaskAttachments(
                    semesterId,
                    com.example.campus_schedule_buddy.data.CourseAttachmentEntity.TYPE_TASK
                )
                val periodTimes = settingsDao.getPeriodTimes(semesterId)
                val periodCount = periodTimes.maxOfOrNull { it.period }?.coerceAtLeast(1) ?: 8
                val currentWeek = calculateCurrentWeek(semesterStart)

                val weekWorkloads = WorkloadCalculator.calculateWeeklyWorkload(
                    courses = courses,
                    tasks = tasks,
                    semesterStartDate = semesterStart,
                    weekIndex = currentWeek,
                    totalWeeks = DEFAULT_TOTAL_WEEKS,
                    periodCount = periodCount
                )
                val todayIndex = LocalDate.now().dayOfWeek.value - 1
                val todayLoad = weekWorkloads.getOrNull(todayIndex)?.index ?: 0
                val trend = WorkloadCalculator.calculateTrend(
                    todayLoad,
                    weekWorkloads.map { it.index }
                )

                val taskText = buildTaskSummary(tasks)
                val loadText = "今日负荷：$todayLoad · 趋势：$trend"

                val updatedViews = RemoteViews(context.packageName, R.layout.app_widget_progress_beacon)
                updatedViews.setTextViewText(R.id.tv_widget_task, taskText)
                updatedViews.setTextViewText(R.id.tv_widget_load, loadText)
                updatedViews.setOnClickPendingIntent(R.id.tv_widget_title, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, updatedViews)
            }
        }

        private fun buildTaskSummary(tasks: List<com.example.campus_schedule_buddy.data.CourseAttachmentEntity>): String {
            val now = System.currentTimeMillis()
            val upcoming = tasks.filter { it.dueAt != null && it.dueAt >= now }
            val nearest = upcoming.minByOrNull { it.dueAt ?: Long.MAX_VALUE }
            if (nearest?.dueAt == null) {
                return "暂无任务"
            }
            val dueDate = Instant.ofEpochMilli(nearest.dueAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), dueDate)
            val suffix = when {
                daysLeft < 0 -> "已过期"
                daysLeft == 0L -> "今日截止"
                else -> "${daysLeft}天后截止"
            }
            return "最近任务：$suffix"
        }

        private fun calculateCurrentWeek(semesterStart: LocalDate): Int {
            val daysDiff = ChronoUnit.DAYS.between(semesterStart, LocalDate.now())
            return ((daysDiff / 7).toInt() + 1).coerceAtLeast(1)
        }

        private const val DEFAULT_TOTAL_WEEKS = 20
    }
}
