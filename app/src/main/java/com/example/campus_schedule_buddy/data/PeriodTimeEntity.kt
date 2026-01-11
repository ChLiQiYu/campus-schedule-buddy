package com.example.campus_schedule_buddy.data

import androidx.room.Entity
@Entity(tableName = "period_times", primaryKeys = ["semesterId", "period"])
data class PeriodTimeEntity(
    val semesterId: Long,
    val period: Int,
    val startTime: String,
    val endTime: String
)
