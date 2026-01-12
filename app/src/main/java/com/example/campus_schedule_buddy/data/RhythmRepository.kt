package com.example.campus_schedule_buddy.data

import com.example.campus_schedule_buddy.model.Course
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RhythmRepository(
    private val courseDao: CourseDao,
    private val workspaceDao: CourseWorkspaceDao,
    private val settingsDao: SettingsDao
) {
    fun observeCourses(semesterId: Long): Flow<List<Course>> {
        return courseDao.observeCourses(semesterId)
    }

    fun observeTaskAttachments(semesterId: Long): Flow<List<CourseAttachmentEntity>> {
        return workspaceDao.observeTaskAttachments(semesterId, CourseAttachmentEntity.TYPE_TASK)
    }

    suspend fun getTaskAttachments(semesterId: Long): List<CourseAttachmentEntity> {
        return workspaceDao.getTaskAttachments(semesterId, CourseAttachmentEntity.TYPE_TASK)
    }

    suspend fun getCourses(semesterId: Long): List<Course> {
        return courseDao.getAllCourses(semesterId)
    }

    fun observeAutoFocusEnabled(): Flow<Boolean> {
        return settingsDao.observeRhythmSettings().map { it?.autoFocusEnabled ?: false }
    }

    suspend fun setAutoFocusEnabled(enabled: Boolean) {
        settingsDao.upsertRhythmSettings(RhythmSettingsEntity(autoFocusEnabled = enabled))
    }

    suspend fun ensureDefaults() {
        if (settingsDao.getRhythmSettings() == null) {
            settingsDao.upsertRhythmSettings(RhythmSettingsEntity(autoFocusEnabled = false))
        }
    }
}
