package com.example.schedule.util

import com.example.schedule.model.Course

object MockData {
    fun getMockCourses(currentWeek: Int = 1): List<Course> {
        return listOf(
            Course(1L, 1L, "高等数学", "张教授", "教学楼A-301", "major_required", 1, 1, 2, listOf(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16)),
            Course(2L, 1L, "大学英语", "李老师", "外语楼-205", "public_required", 1, 3, 4, listOf(1,2,3,4,5,6,7,8,10,11,12,13,14,15,16)),
            Course(3L, 1L, "数据结构", "王教授", "计算机楼-401", "major_required", 2, 1, 2, listOf(1,2,3,4,5,6,7,8,9,10,11,12)),
            Course(4L, 1L, "Android开发", "赵老师", "创新实验室", "major_elective", 2, 3, 4, listOf(5,6,7,8,9,10,11,12,13,14,15,16)),
            Course(5L, 1L, "线性代数", "陈教授", "教学楼B-203", "major_required", 3, 1, 2, listOf(1,2,3,4,5,6,7,8,9,10,11)),
            Course(6L, 1L, "体育(篮球)", "刘教练", "体育馆-西场", "pe", 3, 3, 4, listOf(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16)),
            Course(7L, 1L, "计算机网络", "钱教授", "计算机楼-305", "major_required", 4, 1, 2, listOf(1,2,3,4,5,6,7,8,9,10,11,12,13)),
            Course(8L, 1L, "人工智能导论", "孙博士", "AI实验室", "major_elective", 4, 3, 4, listOf(8,9,10,11,12,13,14,15,16)),
            Course(9L, 1L, "马克思主义原理", "周教授", "人文楼-101", "public_required", 5, 1, 2, listOf(1,2,3,4,5,6,7,8,9,10,11,12,13,14)),
            Course(10L, 1L, "移动应用设计", "郑老师", "设计工坊", "major_elective", 5, 3, 4, listOf(5,6,7,8,9,10,11,12,13,14,15,16))
        ).filter { it.weekPattern.contains(currentWeek) }
    }
}
