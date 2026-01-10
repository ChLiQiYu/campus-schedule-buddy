package com.example.campus_schedule_buddy.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.campus_schedule_buddy.data.CourseTypeReminderEntity
import com.example.campus_schedule_buddy.data.PeriodTimeEntity
import com.example.campus_schedule_buddy.data.ReminderSettingsEntity
import com.example.campus_schedule_buddy.model.Course
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)

    fun scheduleUpcomingReminders(
        courses: List<Course>,
        periodTimes: List<PeriodTimeEntity>,
        semesterStartDate: LocalDate,
        reminderSettings: ReminderSettingsEntity,
        typeReminders: List<CourseTypeReminderEntity>,
        daysAhead: Long = 14
    ) {
        cancelAllScheduled()
        if (!reminderSettings.enableNotification) {
            return
        }
        val periodMap = periodTimes.associate { it.period to it.startTime }
        val typeMap = typeReminders.associateBy { it.type }
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
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
                val startTimeText = periodMap[course.startPeriod] ?: return@forEach
                val startTime = LocalTime.parse(startTimeText, formatter)
                val leadMinutes = resolveLeadMinutes(course, reminderSettings, typeMap) ?: return@forEach
                val triggerTime = LocalDateTime.of(date, startTime).minusMinutes(leadMinutes.toLong())
                if (triggerTime.isAfter(now)) {
                    val requestCode = buildRequestCode(course.id, date, course.startPeriod)
                    scheduleReminder(course, triggerTime, requestCode, reminderSettings)
                    scheduledCodes.add(requestCode.toString())
                }
            }
            date = date.plusDays(1)
        }

        prefs.edit().putStringSet(KEY_REQUEST_CODES, scheduledCodes).apply()
    }

    private fun scheduleReminder(
        course: Course,
        triggerTime: LocalDateTime,
        requestCode: Int,
        reminderSettings: ReminderSettingsEntity
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_COURSE_ID, course.id)
            putExtra(ReminderReceiver.EXTRA_COURSE_NAME, course.name)
            putExtra(ReminderReceiver.EXTRA_TEACHER, course.teacher ?: "")
            putExtra(ReminderReceiver.EXTRA_LOCATION, course.location ?: "")
            putExtra(ReminderReceiver.EXTRA_DAY_OF_WEEK, course.dayOfWeek)
            putExtra(ReminderReceiver.EXTRA_START_PERIOD, course.startPeriod)
            putExtra(ReminderReceiver.EXTRA_END_PERIOD, course.endPeriod)
            putExtra(ReminderReceiver.EXTRA_ENABLE_VIBRATE, reminderSettings.enableVibrate)
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

    private fun resolveLeadMinutes(
        course: Course,
        reminderSettings: ReminderSettingsEntity,
        typeMap: Map<String, CourseTypeReminderEntity>
    ): Int? {
        val override = typeMap[course.type]
        return if (override != null) {
            if (override.enabled) override.leadMinutes else null
        } else {
            reminderSettings.leadMinutes
        }
    }

    fun cancelAllScheduled() {
        val codes = prefs.getStringSet(KEY_REQUEST_CODES, emptySet()) ?: emptySet()
        codes.forEach { code ->
            val requestCode = code.toIntOrNull() ?: return@forEach
            val intent = Intent(context, ReminderReceiver::class.java)
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

    private fun buildRequestCode(courseId: Long, date: LocalDate, startPeriod: Int): Int {
        val dateValue = date.toString().replace("-", "").toIntOrNull() ?: 0
        return (courseId.toInt() * 100000) + (dateValue % 100000) + startPeriod
    }

    companion object {
        private const val KEY_REQUEST_CODES = "scheduled_codes"
    }
}
