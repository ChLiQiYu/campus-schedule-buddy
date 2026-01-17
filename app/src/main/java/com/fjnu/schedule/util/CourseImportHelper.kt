package com.fjnu.schedule.util

import com.fjnu.schedule.model.Course

data class ConflictFilterResult(
    val courses: List<Course>,
    val conflictCount: Int,
    val duplicateCount: Int
)

object CourseImportHelper {

    fun filterConflicts(courses: List<Course>): ConflictFilterResult {
        val accepted = mutableListOf<Course>()
        val signatures = mutableSetOf<String>()
        var conflictCount = 0
        var duplicateCount = 0
        courses.forEach { course ->
            val signature = buildString {
                append(course.name)
                append('|')
                append(course.dayOfWeek)
                append('|')
                append(course.startPeriod)
                append('|')
                append(course.endPeriod)
                append('|')
                append(course.weekPattern.joinToString(","))
            }
            if (!signatures.add(signature)) {
                duplicateCount += 1
                return@forEach
            }
            val conflict = accepted.any { existing ->
                existing.dayOfWeek == course.dayOfWeek &&
                    existing.weekPattern.any { it in course.weekPattern } &&
                    existing.startPeriod <= course.endPeriod &&
                    existing.endPeriod >= course.startPeriod
            }
            if (conflict) {
                conflictCount += 1
            } else {
                accepted.add(course)
            }
        }
        return ConflictFilterResult(accepted, conflictCount, duplicateCount)
    }

    fun totalSkipped(baseSkipped: Int, result: ConflictFilterResult): Int {
        return baseSkipped + result.conflictCount + result.duplicateCount
    }
}
