package com.example.schedule.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "semester_settings")
data class SemesterSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val startDate: String
)
