package com.example.campus_schedule_buddy.util

import com.example.campus_schedule_buddy.data.CourseAttachmentEntity
import com.example.campus_schedule_buddy.model.Course
import com.example.campus_schedule_buddy.model.WorkloadDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object WorkloadCalculator {
    private const val COURSE_WEIGHT = 8
    private const val TASK_WEIGHT = 20
    private const val MAX_INDEX = 100

    fun calculateWeeklyWorkload(
        courses: List<Course>,
        tasks: List<CourseAttachmentEntity>,
        semesterStartDate: LocalDate,
        weekIndex: Int,
        totalWeeks: Int,
        periodCount: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<WorkloadDay> {
        if (weekIndex !in 1..totalWeeks) return emptyList()
        val weekStart = semesterStartDate.plusDays(((weekIndex - 1) * 7).toLong())
        val taskMap = tasks.groupBy { attachment ->
            val dueAt = attachment.dueAt ?: return@groupBy LocalDate.MIN
            Instant.ofEpochMilli(dueAt).atZone(zoneId).toLocalDate()
        }
        val results = mutableListOf<WorkloadDay>()
        for (offset in 0..6) {
            val date = weekStart.plusDays(offset.toLong())
            val dayOfWeek = offset + 1
            val coursePeriods = courses.sumOf { course ->
                if (course.dayOfWeek != dayOfWeek) return@sumOf 0
                if (!course.weekPattern.contains(weekIndex)) return@sumOf 0
                val start = course.startPeriod.coerceAtLeast(1)
                val end = course.endPeriod.coerceAtMost(periodCount)
                if (start > end) return@sumOf 0
                end - start + 1
            }
            val taskCount = taskMap[date]?.count { it.type == CourseAttachmentEntity.TYPE_TASK } ?: 0
            val indexRaw = coursePeriods * COURSE_WEIGHT + taskCount * TASK_WEIGHT
            val index = indexRaw.coerceIn(0, MAX_INDEX)
            results.add(
                WorkloadDay(
                    date = date,
                    weekIndex = weekIndex,
                    dayOfWeek = dayOfWeek,
                    coursePeriods = coursePeriods,
                    taskCount = taskCount,
                    index = index
                )
            )
        }
        return results
    }

    fun calculateTrend(todayIndex: Int, weekIndexes: List<Int>): String {
        if (weekIndexes.isEmpty()) return "平稳"
        val average = weekIndexes.average()
        return when {
            todayIndex >= (average * 1.2f) -> "偏高"
            todayIndex <= (average * 0.8f) -> "偏低"
            else -> "平稳"
        }
    }
}
