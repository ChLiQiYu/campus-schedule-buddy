package com.fjnu.schedule.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "course_attachments",
    indices = [
        Index(value = ["semesterId", "courseId"]),
        Index(value = ["semesterId", "dueAt"])
    ]
)
data class CourseAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val semesterId: Long,
    val courseId: Long,
    val type: String,
    val title: String,
    val uri: String? = null,
    val url: String? = null,
    val dueAt: Long? = null,
    val sourceType: String = SOURCE_MANUAL,
    val createdAt: Long
) {
    companion object {
        const val TYPE_PDF = "pdf"
        const val TYPE_LINK = "link"
        const val TYPE_TASK = "task"
        const val TYPE_MEDIA = "media"
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_CHRONICLE_MEDIA = "CHRONICLE_MEDIA"
    }
}
