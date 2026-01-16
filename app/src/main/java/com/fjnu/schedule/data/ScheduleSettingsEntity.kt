package com.fjnu.schedule.data

import androidx.room.Entity

@Entity(tableName = "schedule_settings", primaryKeys = ["semesterId"])
data class ScheduleSettingsEntity(
    val semesterId: Long,
    val periodCount: Int,
    val periodMinutes: Int,
    val breakMinutes: Int,
    val totalWeeks: Int
)
