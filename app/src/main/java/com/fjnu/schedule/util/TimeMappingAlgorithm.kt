package com.example.schedule.util

import com.example.schedule.data.PeriodTimeEntity
import com.example.schedule.model.Course
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeMappingAlgorithm {
    private const val BUFFER_MINUTES = 10L
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun matchCoursesForMedia(
        mediaTime: Instant,
        courses: List<Course>,
        periodTimes: List<PeriodTimeEntity>,
        semesterStartDate: LocalDate
    ): List<Course> {
        if (courses.isEmpty() || periodTimes.isEmpty()) return emptyList()
        val dateTime = LocalDateTime.ofInstant(mediaTime, ZoneId.systemDefault())
        val date = dateTime.toLocalDate()
        val weekIndex = calculateWeekIndex(semesterStartDate, date)
        if (weekIndex <= 0) return emptyList()
        val dayOfWeek = date.dayOfWeek.value
        val periodMap = periodTimes.associateBy { it.period }

        return courses.filter { course ->
            course.dayOfWeek == dayOfWeek && course.weekPattern.contains(weekIndex)
        }.filter { course ->
            val start = periodMap[course.startPeriod]?.let { parseTime(it.startTime) }
            val end = periodMap[course.endPeriod]?.let { parseTime(it.endTime) }
            if (start == null || end == null) return@filter false
            val startWindow = LocalDateTime.of(date, start).minusMinutes(BUFFER_MINUTES)
            val endWindow = LocalDateTime.of(date, end).plusMinutes(BUFFER_MINUTES)
            !dateTime.isBefore(startWindow) && !dateTime.isAfter(endWindow)
        }
    }

    private fun parseTime(value: String): LocalTime? {
        return runCatching { LocalTime.parse(value, timeFormatter) }.getOrNull()
    }

    private fun calculateWeekIndex(semesterStartDate: LocalDate, date: LocalDate): Int {
        val daysDiff = ChronoUnit.DAYS.between(semesterStartDate, date)
        return if (daysDiff < 0) -1 else (daysDiff / 7).toInt() + 1
    }
}
