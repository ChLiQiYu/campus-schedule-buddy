package com.example.campus_schedule_buddy.model

import java.time.LocalDate

data class WorkloadDay(
    val date: LocalDate,
    val weekIndex: Int,
    val dayOfWeek: Int,
    val coursePeriods: Int,
    val taskCount: Int,
    val index: Int
)
