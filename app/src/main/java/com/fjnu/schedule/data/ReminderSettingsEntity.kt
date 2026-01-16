package com.fjnu.schedule.data

import androidx.room.Entity
@Entity(tableName = "reminder_settings", primaryKeys = ["semesterId"])
data class ReminderSettingsEntity(
    val semesterId: Long,
    val leadMinutes: Int,
    val enableNotification: Boolean,
    val enableVibrate: Boolean
)
