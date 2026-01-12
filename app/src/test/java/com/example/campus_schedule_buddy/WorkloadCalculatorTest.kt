package com.example.campus_schedule_buddy

import com.example.campus_schedule_buddy.data.CourseAttachmentEntity
import com.example.campus_schedule_buddy.model.Course
import com.example.campus_schedule_buddy.util.WorkloadCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkloadCalculatorTest {
    @Test
    fun calculateWeeklyWorkload_respectsCourseAndTaskWeighting() {
        val semesterStart = LocalDate.of(2026, 1, 5) // 周一
        val courses = listOf(
            Course(
                id = 1,
                semesterId = 1,
                name = "课程A",
                teacher = null,
                location = null,
                type = "major_required",
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                weekPattern = listOf(1),
                note = null,
                color = null
            ),
            Course(
                id = 2,
                semesterId = 1,
                name = "课程B",
                teacher = null,
                location = null,
                type = "major_required",
                dayOfWeek = 3,
                startPeriod = 1,
                endPeriod = 4,
                weekPattern = listOf(1),
                note = null,
                color = null
            )
        )
        val zone = ZoneId.of("UTC")
        val mondayDue = semesterStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val wednesdayDue = semesterStart.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            CourseAttachmentEntity(
                id = 1,
                semesterId = 1,
                courseId = 1,
                type = CourseAttachmentEntity.TYPE_TASK,
                title = "作业1",
                dueAt = mondayDue,
                createdAt = mondayDue
            ),
            CourseAttachmentEntity(
                id = 2,
                semesterId = 1,
                courseId = 2,
                type = CourseAttachmentEntity.TYPE_TASK,
                title = "作业2",
                dueAt = wednesdayDue,
                createdAt = wednesdayDue
            ),
            CourseAttachmentEntity(
                id = 3,
                semesterId = 1,
                courseId = 2,
                type = CourseAttachmentEntity.TYPE_TASK,
                title = "作业3",
                dueAt = wednesdayDue,
                createdAt = wednesdayDue
            )
        )

        val results = WorkloadCalculator.calculateWeeklyWorkload(
            courses = courses,
            tasks = tasks,
            semesterStartDate = semesterStart,
            weekIndex = 1,
            totalWeeks = 20,
            periodCount = 8,
            zoneId = zone
        )

        assertEquals(7, results.size)
        val monday = results[0]
        val wednesday = results[2]
        assertEquals(2, monday.coursePeriods)
        assertEquals(1, monday.taskCount)
        assertEquals(4, wednesday.coursePeriods)
        assertEquals(2, wednesday.taskCount)
        assertTrue(wednesday.index > monday.index)
    }
}
