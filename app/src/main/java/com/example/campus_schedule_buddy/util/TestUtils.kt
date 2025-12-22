package com.example.campus_schedule_buddy.util

import com.example.campus_schedule_buddy.model.Course

/**
 * 测试工具类
 */
object TestUtils {
    
    /**
     * 验证跨节课程渲染的测试用例
     */
    fun getTestCourses(): List<Course> {
        return listOf(
            Course(
                id = 1,
                name = "高等数学",
                teacher = "张教授",
                location = "教学楼A-301",
                type = "major_required",
                dayOfWeek = 1, // 周一
                startPeriod = 1, // 第1节
                endPeriod = 2, // 第2节
                weekPattern = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
            ),
            Course(
                id = 2,
                name = "大学英语",
                teacher = "李老师",
                location = "外语楼-205",
                type = "public_required",
                dayOfWeek = 1, // 周一
                startPeriod = 3, // 第3节
                endPeriod = 3, // 第3节（单节课程）
                weekPattern = listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16)
            ),
            Course(
                id = 3,
                name = "数据结构",
                teacher = "王教授",
                location = "计算机楼-401",
                type = "major_required",
                dayOfWeek = 2, // 周二
                startPeriod = 1, // 第1节
                endPeriod = 3, // 第3节
                weekPattern = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
            )
        )
    }
    
    /**
     * 验证课程跨越的节次数计算是否正确
     * @param course 课程对象
     * @param expectedSpan 期望的跨越节次数
     * @return 验证结果
     */
    fun validateCourseSpan(course: Course, expectedSpan: Int): Boolean {
        val actualSpan = CourseUtils.getCourseSpanCount(course)
        return actualSpan == expectedSpan
    }
}