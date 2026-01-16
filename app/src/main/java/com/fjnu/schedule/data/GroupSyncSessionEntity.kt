package com.example.schedule.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_sync_sessions",
    indices = [Index(value = ["code"], unique = true)]
)
data class GroupSyncSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val semesterId: Long,
    val totalWeeks: Int,
    val periodCount: Int,
    val createdAt: Long
)
