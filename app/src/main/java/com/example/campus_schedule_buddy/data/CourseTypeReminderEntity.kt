package com.example.campus_schedule_buddy.data

import androidx.room.Entity
@Entity(tableName = "course_type_reminders", primaryKeys = ["semesterId", "type"])
data class CourseTypeReminderEntity(
    val semesterId: Long,
    val type: String,
    val leadMinutes: Int,
    val enabled: Boolean
)
