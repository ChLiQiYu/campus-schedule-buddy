package com.example.campus_schedule_buddy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_settings")
data class ReminderSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val leadMinutes: Int,
    val enableNotification: Boolean,
    val enableVibrate: Boolean
)
