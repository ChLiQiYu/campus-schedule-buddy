package com.example.campus_schedule_buddy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "course_type_reminders")
data class CourseTypeReminderEntity(
    @PrimaryKey
    val type: String,
    val leadMinutes: Int,
    val enabled: Boolean
)
