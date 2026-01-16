package com.fjnu.schedule.model

enum class KnowledgeStatus(val level: Int, val label: String) {
    NOT_STARTED(0, "未开始"),
    LEARNING(50, "学习中"),
    MASTERED(100, "已掌握"),
    STUCK(25, "模糊");

    companion object {
        fun fromLevel(level: Int): KnowledgeStatus {
            return when {
                level >= 80 -> MASTERED
                level >= 40 -> LEARNING
                level > 0 -> STUCK
                else -> NOT_STARTED
            }
        }

        fun next(current: KnowledgeStatus): KnowledgeStatus {
            return when (current) {
                NOT_STARTED -> LEARNING
                LEARNING -> MASTERED
                MASTERED -> STUCK
                STUCK -> LEARNING
            }
        }
    }
}
