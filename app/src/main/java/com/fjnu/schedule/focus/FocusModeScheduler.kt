package com.fjnu.schedule.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fjnu.schedule.data.PeriodTimeEntity
import com.fjnu.schedule.model.Course
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FocusModeScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun scheduleUpcomingFocus(
        courses: List<Course>,
        periodTimes: List<PeriodTimeEntity>,
        semesterStartDate: LocalDate,
        enabled: Boolean,
        daysAhead: Long = 14
    ) {
        cancelAllScheduled()
        if (!enabled) return
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val periodMap = periodTimes.associate { it.period to it }
        val now = LocalDateTime.now()
        val endDate = LocalDate.now().plusDays(daysAhead)
        val scheduledCodes = mutableSetOf<String>()

        var date = LocalDate.now()
        while (!date.isAfter(endDate)) {
            val weekNumber = calculateWeekNumber(semesterStartDate, date)
            val dayOfWeek = date.dayOfWeek.value
            courses.filter { course ->
                course.dayOfWeek == dayOfWeek && course.weekPattern.contains(weekNumber)
            }.forEach { course ->
                val startPeriod = periodMap[course.startPeriod]
                val endPeriod = periodMap[course.endPeriod]
                if (startPeriod == null || endPeriod == null) return@forEach
                val startTime = LocalTime.parse(startPeriod.startTime, formatter)
                val endTime = LocalTime.parse(endPeriod.endTime, formatter)
                val startDateTime = LocalDateTime.of(date, startTime)
                val endDateTime = LocalDateTime.of(date, endTime)
                if (startDateTime.isAfter(now)) {
                    val requestCode = buildRequestCode(course.id, date, course.startPeriod, true)
                    scheduleFocusToggle(course, startDateTime, true, requestCode)
                    scheduledCodes.add(requestCode.toString())
                }
                if (endDateTime.isAfter(now)) {
                    val requestCode = buildRequestCode(course.id, date, course.endPeriod, false)
                    scheduleFocusToggle(course, endDateTime, false, requestCode)
                    scheduledCodes.add(requestCode.toString())
                }
            }
            date = date.plusDays(1)
        }

        prefs.edit().putStringSet(KEY_REQUEST_CODES, scheduledCodes).apply()
    }

    private fun scheduleFocusToggle(
        course: Course,
        triggerTime: LocalDateTime,
        enable: Boolean,
        requestCode: Int
    ) {
        val intent = Intent(context, FocusModeReceiver::class.java).apply {
            putExtra(FocusModeReceiver.EXTRA_ENABLE, enable)
            putExtra(FocusModeReceiver.EXTRA_COURSE_NAME, course.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerMillis = triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
    }

    fun cancelAllScheduled() {
        val codes = prefs.getStringSet(KEY_REQUEST_CODES, emptySet()) ?: emptySet()
        codes.forEach { code ->
            val requestCode = code.toIntOrNull() ?: return@forEach
            val intent = Intent(context, FocusModeReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        prefs.edit().remove(KEY_REQUEST_CODES).apply()
    }

    private fun calculateWeekNumber(semesterStart: LocalDate, date: LocalDate): Int {
        val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(semesterStart, date)
        return (daysDiff / 7).toInt() + 1
    }

    private fun buildRequestCode(courseId: Long, date: LocalDate, period: Int, isStart: Boolean): Int {
        val dateValue = date.toString().replace("-", "").toIntOrNull() ?: 0
        val typeFlag = if (isStart) 1 else 2
        return (courseId.toInt() * 100000) + (dateValue % 100000) + period + typeFlag
    }

    companion object {
        private const val PREFS_NAME = "focus_mode_prefs"
        private const val KEY_REQUEST_CODES = "focus_scheduled_codes"
    }
}
