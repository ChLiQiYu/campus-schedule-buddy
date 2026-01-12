package com.example.campus_schedule_buddy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_sync_shares",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "memberName"], unique = true)
    ]
)
data class GroupSyncShareEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val memberName: String,
    val freeSlots: String,
    val createdAt: Long
)
