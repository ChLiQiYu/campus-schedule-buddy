package com.example.campus_schedule_buddy.model

data class KnowledgeProgress(
    val totalCount: Int,
    val masteredCount: Int,
    val learningCount: Int,
    val notStartedCount: Int,
    val stuckCount: Int,
    val percent: Int
)
