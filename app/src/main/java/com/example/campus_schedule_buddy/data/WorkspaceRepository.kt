package com.example.campus_schedule_buddy.data

import kotlinx.coroutines.flow.Flow

class WorkspaceRepository(private val workspaceDao: CourseWorkspaceDao) {
    fun observeCounts(semesterId: Long): Flow<List<CourseWorkspaceCount>> {
        return workspaceDao.observeWorkspaceCounts(semesterId)
    }

    suspend fun getAttachments(semesterId: Long, courseId: Long): List<CourseAttachmentEntity> {
        return workspaceDao.getAttachments(semesterId, courseId)
    }

    suspend fun getNotes(semesterId: Long, courseId: Long): List<CourseNoteEntity> {
        return workspaceDao.getNotes(semesterId, courseId)
    }

    suspend fun addAttachment(entity: CourseAttachmentEntity): Long {
        return workspaceDao.insertAttachment(entity)
    }

    suspend fun hasAttachmentUri(uri: String, sourceType: String): Boolean {
        return workspaceDao.countAttachmentsByUri(uri, sourceType) > 0
    }

    suspend fun addNote(entity: CourseNoteEntity): Long {
        return workspaceDao.insertNote(entity)
    }

    suspend fun deleteAttachment(entity: CourseAttachmentEntity) {
        workspaceDao.deleteAttachment(entity)
    }

    suspend fun deleteNote(entity: CourseNoteEntity) {
        workspaceDao.deleteNote(entity)
    }
}
