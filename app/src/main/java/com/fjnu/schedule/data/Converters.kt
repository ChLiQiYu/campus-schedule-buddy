package com.example.schedule.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromWeekPattern(value: List<Int>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toWeekPattern(value: String): List<Int> {
        if (value.isBlank()) return emptyList()
        return value.split(",")
            .mapNotNull { part ->
                part.trim().toIntOrNull()
            }
            .distinct()
            .sorted()
    }
}
