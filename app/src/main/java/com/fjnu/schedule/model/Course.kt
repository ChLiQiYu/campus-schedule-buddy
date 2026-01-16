package com.example.schedule.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val semesterId: Long,
    val name: String,
    val teacher: String?,
    val location: String?,
    val type: String, // "major_required", "major_elective", etc.
    val dayOfWeek: Int, // 1=周一, 7=周日
    val startPeriod: Int, // 1-8
    val endPeriod: Int,   // 1-8
    val weekPattern: List<Int>, // [1,2,3,4,5] 表示第1-5周有课
    val note: String? = null,
    val color: Int? = null
)
