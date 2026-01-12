package com.example.campus_schedule_buddy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rhythm_settings")
data class RhythmSettingsEntity(
    @PrimaryKey
    val id: Long = 1,
    val autoFocusEnabled: Boolean
)
