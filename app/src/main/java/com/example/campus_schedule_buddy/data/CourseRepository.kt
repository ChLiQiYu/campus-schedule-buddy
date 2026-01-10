package com.example.campus_schedule_buddy.data

import com.example.campus_schedule_buddy.model.Course
import kotlinx.coroutines.flow.Flow

class CourseRepository(private val courseDao: CourseDao) {

    sealed class SaveResult {
        data class Success(val id: Long) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    val coursesFlow: Flow<List<Course>> = courseDao.observeCourses()

    suspend fun addCourse(course: Course): SaveResult {
        val validation = validateCourse(course)
        if (validation != null) {
            return SaveResult.Error(validation)
        }
        val conflict = findConflict(course)
        if (conflict != null) {
            return SaveResult.Error(conflict)
        }
        val id = courseDao.insert(course.copy(id = 0))
        return SaveResult.Success(id)
    }

    suspend fun updateCourse(course: Course): SaveResult {
        if (course.id <= 0) {
            return SaveResult.Error("无效课程ID")
        }
        val validation = validateCourse(course)
        if (validation != null) {
            return SaveResult.Error(validation)
        }
        val conflict = findConflict(course)
        if (conflict != null) {
            return SaveResult.Error(conflict)
        }
        courseDao.update(course)
        return SaveResult.Success(course.id)
    }

    suspend fun deleteCourse(course: Course) {
        courseDao.delete(course)
    }

    private suspend fun findConflict(course: Course): String? {
        val existingCourses = courseDao.getAllCourses()
        val conflictCourse = existingCourses.firstOrNull { existing ->
            if (existing.id == course.id) return@firstOrNull false
            if (existing.dayOfWeek != course.dayOfWeek) return@firstOrNull false
            val weekOverlap = existing.weekPattern.any { it in course.weekPattern }
            if (!weekOverlap) return@firstOrNull false
            val periodOverlap = existing.startPeriod <= course.endPeriod &&
                existing.endPeriod >= course.startPeriod
            periodOverlap
        }
        return conflictCourse?.let {
            "与《${it.name}》在同一天的节次冲突"
        }
    }

    private fun validateCourse(course: Course): String? {
        if (course.name.isBlank()) {
            return "课程名称不能为空"
        }
        if (course.dayOfWeek !in 1..7) {
            return "星期选择不正确"
        }
        if (course.startPeriod !in 1..8 || course.endPeriod !in 1..8) {
            return "节次范围不正确"
        }
        if (course.startPeriod > course.endPeriod) {
            return "开始节次不能晚于结束节次"
        }
        if (course.weekPattern.isEmpty()) {
            return "周数不能为空"
        }
        if (course.weekPattern.any { it <= 0 }) {
            return "周数必须为正数"
        }
        return null
    }
}
