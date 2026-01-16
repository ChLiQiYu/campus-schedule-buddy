package com.fjnu.schedule.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "course_notes",
    indices = [Index(value = ["semesterId", "courseId"])]
)
data class CourseNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val semesterId: Long,
    val courseId: Long,
    val content: String,
    val createdAt: Long
)
