package com.example.schedule.model

enum class KnowledgeFilter(val label: String) {
    ALL("全部"),
    NOT_STARTED("未开始"),
    LEARNING("学习中"),
    MASTERED("已掌握"),
    STUCK("模糊"),
    KEYPOINT("重点")
}
