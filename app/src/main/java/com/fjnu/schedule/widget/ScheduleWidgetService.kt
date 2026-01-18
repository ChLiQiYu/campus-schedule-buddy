package com.fjnu.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.fjnu.schedule.R
import com.fjnu.schedule.data.AppDatabase
import com.fjnu.schedule.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleRemoteViewsFactory(applicationContext, intent)
    }

    private class ScheduleRemoteViewsFactory(
        private val context: Context,
        intent: Intent
    ) : RemoteViewsFactory {
        private val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        private val lines = mutableListOf<WidgetLine>()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            val data = runBlocking(Dispatchers.IO) { loadLines(context, widgetId) }
            lines.clear()
            lines.addAll(data)
        }

        override fun onDestroy() {
            lines.clear()
        }

        override fun getCount(): Int = lines.size

        override fun getViewAt(position: Int): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.item_widget_line)
            val item = lines.getOrNull(position)
            remoteViews.setTextViewText(R.id.tv_widget_course_name, item?.title.orEmpty())
            remoteViews.setTextViewText(R.id.tv_widget_course_meta, item?.meta.orEmpty())
            return remoteViews
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = true

        private suspend fun loadLines(context: Context, widgetId: Int): List<WidgetLine> {
            val db = AppDatabase.getInstance(context)
            val settingsDao = db.settingsDao()
            val semesterDao = db.semesterDao()
            val courseDao = db.courseDao()

            val mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("$PREF_MODE$widgetId", MODE_DAY) ?: MODE_DAY

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
            return if (mode == MODE_WEEK) {
                buildWeekLines(courses, currentWeek, totalWeeks)
            } else {
                buildDayLines(courses, currentWeek, totalWeeks, today.dayOfWeek.value)
            }
        }

        private fun buildDayLines(
            courses: List<Course>,
            weekIndex: Int,
            totalWeeks: Int,
            dayOfWeek: Int
        ): List<WidgetLine> {
            if (weekIndex !in 1..totalWeeks) {
                return listOf(WidgetLine("不在学期内", "请检查学期设置"))
            }
            val items = courses.filter { course ->
                course.dayOfWeek == dayOfWeek && course.weekPattern.contains(weekIndex)
            }.sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))
            if (items.isEmpty()) {
                return listOf(WidgetLine("暂无课程", "今日无安排"))
            }
            return items.map { course ->
                val periodText = if (course.startPeriod == course.endPeriod) {
                    "${course.startPeriod}"
                } else {
                    "${course.startPeriod}-${course.endPeriod}"
                }
                val location = course.location?.let { " · $it" } ?: ""
                val meta = "${periodText}节$location"
                WidgetLine(course.name, meta)
            }
        }

        private fun buildWeekLines(
            courses: List<Course>,
            weekIndex: Int,
            totalWeeks: Int
        ): List<WidgetLine> {
            if (weekIndex !in 1..totalWeeks) {
                return listOf(WidgetLine("不在学期内", "请检查学期设置"))
            }
            val items = courses.filter { it.weekPattern.contains(weekIndex) }
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.endPeriod }))
            if (items.isEmpty()) {
                return listOf(WidgetLine("暂无课程", "本周无安排"))
            }
            return items.map { course ->
                val dayText = formatDay(course.dayOfWeek)
                val periodText = if (course.startPeriod == course.endPeriod) {
                    "${course.startPeriod}"
                } else {
                    "${course.startPeriod}-${course.endPeriod}"
                }
                val location = course.location?.let { " · $it" } ?: ""
                val meta = "${dayText} ${periodText}节${location}"
                WidgetLine(course.name, meta)
            }
        }

        private fun calculateWeekIndex(semesterStart: LocalDate, date: LocalDate): Int {
            val daysDiff = ChronoUnit.DAYS.between(semesterStart, date)
            return (daysDiff / 7).toInt() + 1
        }

        private fun formatDay(day: Int): String {
            return when (day) {
                1 -> "周一"
                2 -> "周二"
                3 -> "周三"
                4 -> "周四"
                5 -> "周五"
                6 -> "周六"
                else -> "周日"
            }
        }

        private data class WidgetLine(
            val title: String,
            val meta: String
        )
    }

    companion object {
        private const val PREFS_NAME = "schedule_widget_prefs"
        private const val PREF_MODE = "schedule_widget_mode_"
        private const val MODE_DAY = "day"
        private const val MODE_WEEK = "week"
        private const val DEFAULT_TOTAL_WEEKS = 20
    }
}
