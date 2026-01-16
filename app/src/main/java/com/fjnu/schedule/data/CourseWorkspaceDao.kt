package com.example.schedule.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseWorkspaceDao {
    @Query(
        "SELECT * FROM course_attachments " +
            "WHERE semesterId = :semesterId AND courseId = :courseId " +
            "ORDER BY createdAt DESC"
    )
    suspend fun getAttachments(semesterId: Long, courseId: Long): List<CourseAttachmentEntity>

    @Query(
        "SELECT * FROM course_notes " +
            "WHERE semesterId = :semesterId AND courseId = :courseId " +
            "ORDER BY createdAt DESC"
    )
    suspend fun getNotes(semesterId: Long, courseId: Long): List<CourseNoteEntity>

    @Insert
    suspend fun insertAttachment(entity: CourseAttachmentEntity): Long

    @Query(
        "SELECT COUNT(*) FROM course_attachments " +
            "WHERE uri = :uri AND sourceType = :sourceType"
    )
    suspend fun countAttachmentsByUri(uri: String, sourceType: String): Int

    @Insert
    suspend fun insertNote(entity: CourseNoteEntity): Long

    @Delete
    suspend fun deleteAttachment(entity: CourseAttachmentEntity)

    @Delete
    suspend fun deleteNote(entity: CourseNoteEntity)

    @Query(
        "SELECT * FROM course_attachments " +
            "WHERE semesterId = :semesterId AND type = :type AND dueAt IS NOT NULL"
    )
    fun observeTaskAttachments(semesterId: Long, type: String): Flow<List<CourseAttachmentEntity>>

    @Query(
        "SELECT * FROM course_attachments " +
            "WHERE semesterId = :semesterId AND type = :type AND dueAt IS NOT NULL"
    )
    suspend fun getTaskAttachments(semesterId: Long, type: String): List<CourseAttachmentEntity>

    @Query(
        """
        SELECT courses.id AS courseId,
               COALESCE(attachment_counts.cnt, 0) AS attachmentCount,
               COALESCE(note_counts.cnt, 0) AS noteCount
        FROM courses
        LEFT JOIN (
            SELECT courseId, COUNT(*) AS cnt
            FROM course_attachments
            WHERE semesterId = :semesterId
            GROUP BY courseId
        ) AS attachment_counts
        ON courses.id = attachment_counts.courseId
        LEFT JOIN (
            SELECT courseId, COUNT(*) AS cnt
            FROM course_notes
            WHERE semesterId = :semesterId
            GROUP BY courseId
        ) AS note_counts
        ON courses.id = note_counts.courseId
        WHERE courses.semesterId = :semesterId
        """
    )
    fun observeWorkspaceCounts(semesterId: Long): Flow<List<CourseWorkspaceCount>>
}
