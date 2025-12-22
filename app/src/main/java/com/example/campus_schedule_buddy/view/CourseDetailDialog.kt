package com.example.campus_schedule_buddy.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.example.campus_schedule_buddy.R
import com.example.campus_schedule_buddy.model.Course

class CourseDetailDialog(context: Context, private val course: Course) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_course_detail)

        // 设置对话框样式
        window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        initView()
    }

    private fun initView() {
        // 获取视图组件
        val courseNameTextView = findViewById<TextView>(R.id.tv_course_name)
        val courseTypeBadge = findViewById<TextView>(R.id.tv_course_type_badge)
        val teacherTextView = findViewById<TextView>(R.id.tv_teacher)
        val locationTextView = findViewById<TextView>(R.id.tv_location)
        val timeTextView = findViewById<TextView>(R.id.tv_time)
        val weekPatternTextView = findViewById<TextView>(R.id.tv_week_pattern)
        val noteTextView = findViewById<TextView>(R.id.tv_note)
        val noteLabel = findViewById<TextView>(R.id.tv_note_label)
        val editButton = findViewById<Button>(R.id.btn_edit)
        val deleteButton = findViewById<Button>(R.id.btn_delete)
        val cancelButton = findViewById<Button>(R.id.btn_cancel)

        // 设置课程信息
        courseNameTextView.text = course.name
        teacherTextView.text = course.teacher
        locationTextView.text = course.location
        
        // 设置课程类型徽章
        val (typeName, typeColor) = getCourseTypeInfo(course.type)
        courseTypeBadge.text = typeName
        val badgeDrawable = GradientDrawable().apply {
            setColor(typeColor)
            cornerRadius = dpToPx(12).toFloat()
        }
        courseTypeBadge.background = badgeDrawable

        // 设置上课时间
        val weekdays = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
        timeTextView.text = "${weekdays[course.dayOfWeek]} 第${course.startPeriod}-${course.endPeriod}节"

        // 设置周数模式
        weekPatternTextView.text = formatWeekPattern(course.weekPattern)

        // 设置备注
        if (course.note.isNullOrEmpty()) {
            noteTextView.visibility = android.view.View.GONE
            noteLabel.visibility = android.view.View.GONE
        } else {
            noteTextView.text = course.note
        }

        // 设置按钮点击事件
        editButton.setOnClickListener {
            // 启动编辑功能
            startEditCourse()
        }

        deleteButton.setOnClickListener {
            // 删除课程
            deleteCourse()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun getCourseTypeInfo(type: String): Pair<String, Int> {
        return when (type) {
            "major_required" -> Pair("必修", Color.parseColor("#4cc9f0"))
            "major_elective" -> Pair("专选", Color.parseColor("#7209b7"))
            "public_required" -> Pair("公必", Color.parseColor("#f72585"))
            "public_elective" -> Pair("公选", Color.parseColor("#4895ef"))
            "experiment" -> Pair("实验", Color.parseColor("#72efdd"))
            "pe" -> Pair("体育", Color.parseColor("#4361ee"))
            else -> Pair("未知", Color.parseColor("#4cc9f0"))
        }
    }

    private fun formatWeekPattern(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        
        // 简单格式化周数显示
        return if (weeks.size > 1) {
            "第${weeks.first()}-${weeks.last()}周"
        } else {
            "第${weeks.first()}周"
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    private fun startEditCourse() {
        // 关闭当前对话框
        dismiss()
        
        // TODO: 通知 MainActivity 启动编辑
        // 在实际应用中，您可能需要通过接口回调或其他方式通知 MainActivity
    }
    
    private fun deleteCourse() {
        // 关闭当前对话框
        dismiss()
        
        // TODO: 通知 MainActivity 删除课程
        // 在实际应用中，您可能需要通过接口回调或其他方式通知 MainActivity
    }
}