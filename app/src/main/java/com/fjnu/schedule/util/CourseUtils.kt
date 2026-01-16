package com.example.schedule.util

import com.example.schedule.model.Course

/**
 * 课程工具类
 */
object CourseUtils {
    
    /**
     * 获取课程跨越的节次数
     * @param course 课程对象
     * @return 跨越的节次数
     */
    fun getCourseSpanCount(course: Course): Int {
        return course.endPeriod - course.startPeriod + 1
    }
    
    /**
     * 验证课程时间格式是否有效
     * @param startPeriod 开始节次
     * @param endPeriod 结束节次
     * @return 是否有效
     */
    fun isValidPeriodRange(startPeriod: Int, endPeriod: Int): Boolean {
        return startPeriod in 1..8 && endPeriod in 1..8 && startPeriod <= endPeriod
    }
    
    /**
     * 格式化课程时间显示
     * @param course 课程对象
     * @return 格式化的时间字符串
     */
    fun formatCoursePeriod(course: Course): String {
        return if (course.startPeriod == course.endPeriod) {
            "${course.startPeriod}节"
        } else {
            "${course.startPeriod}-${course.endPeriod}节"
        }
    }
}