package com.example.campus_schedule_buddy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "period_times")
data class PeriodTimeEntity(
    @PrimaryKey
    val period: Int,
    val startTime: String,
    val endTime: String
)
