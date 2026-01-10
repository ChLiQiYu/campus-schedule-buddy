package com.example.campus_schedule_buddy.view

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.TextView
import com.example.campus_schedule_buddy.R
import com.example.campus_schedule_buddy.model.Course

class CourseDetailDialog(
    context: Context,
    private val course: Course,
    private val onEdit: (Course) -> Unit,
    private val onDelete: (Course) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_course_detail)

        // 设置对话框样式
        window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // 根据系统主题设置对话框背景
        val isDarkMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) {
            window?.setBackgroundDrawableResource(R.drawable.dialog_background)
        } else {
            // 在浅色模式下使用白色背景
            val whiteBackground = GradientDrawable().apply {
                setColor(context.getColor(R.color.white))
                cornerRadius = dpToPx(12).toFloat()
            }
            window?.setBackgroundDrawable(whiteBackground)
        }
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
        val colorLabel = findViewById<TextView>(R.id.tv_color_label)
        val colorValue = findViewById<TextView>(R.id.tv_color_value)
        val noteTextView = findViewById<TextView>(R.id.tv_note)
        val noteLabel = findViewById<TextView>(R.id.tv_note_label)
        val editButton = findViewById<Button>(R.id.btn_edit)
        val deleteButton = findViewById<Button>(R.id.btn_delete)
        val cancelButton = findViewById<Button>(R.id.btn_cancel)

        // 设置课程信息
        courseNameTextView.text = course.name
        teacherTextView.text = "教师：${course.teacher?.ifBlank { "未填写" } ?: "未填写"}"
        locationTextView.text = "地点：${course.location?.ifBlank { "未填写" } ?: "未填写"}"
        
        // 设置课程类型徽章
        val (typeName, typeColor) = getCourseTypeInfo(course.type)
        courseTypeBadge.text = typeName
        val badgeDrawable = GradientDrawable().apply {
            setColor(typeColor)
            cornerRadius = dpToPx(12).toFloat()
        }
        courseTypeBadge.background = badgeDrawable
        
        // 根据系统主题设置文字颜色
        updateTextColors(
            courseNameTextView,
            teacherTextView,
            locationTextView,
            timeTextView,
            weekPatternTextView,
            noteTextView,
            noteLabel,
            colorLabel,
            colorValue
        )

        // 设置上课时间
        val weekdays = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
        timeTextView.text = "${weekdays[course.dayOfWeek]} 第${course.startPeriod}-${course.endPeriod}节"

        // 设置周数模式
        weekPatternTextView.text = formatWeekPattern(course.weekPattern)

        // 设置颜色显示
        val (colorName, colorInt) = getCourseColorInfo(course.color)
        colorValue.text = colorName
        val colorDrawable = GradientDrawable().apply {
            setColor(colorInt)
            cornerRadius = dpToPx(10).toFloat()
        }
        colorValue.background = colorDrawable

        // 设置备注
        if (course.note.isNullOrEmpty()) {
            noteTextView.visibility = android.view.View.GONE
            noteLabel.visibility = android.view.View.GONE
        } else {
            noteTextView.text = course.note
        }

        // 设置按钮点击事件
        editButton.setOnClickListener {
            // 添加按钮点击动画效果
            addButtonClickEffect(editButton)
            // 启动编辑功能
            dismiss()
            onEdit(course)
        }

        deleteButton.setOnClickListener {
            // 添加按钮点击动画效果
            addButtonClickEffect(deleteButton)
            // 删除课程
            confirmDelete()
        }

        cancelButton.setOnClickListener {
            // 添加按钮点击动画效果
            addButtonClickEffect(cancelButton)
            dismiss()
        }
        
        // 添加弹窗显示动画
        addDialogShowAnimation()
    }
    
    /**
     * 为按钮添加点击动画效果
     */
    private fun addButtonClickEffect(button: Button) {
        val scaleAnimation = ScaleAnimation(
            1f, 0.95f, 1f, 0.95f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnimation.duration = 100
        scaleAnimation.fillAfter = false
        
        val restoreAnimation = ScaleAnimation(
            0.95f, 1f, 0.95f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        restoreAnimation.duration = 100
        restoreAnimation.fillAfter = false
        
        scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            
            override fun onAnimationRepeat(animation: Animation?) {}
            
            override fun onAnimationEnd(animation: Animation?) {
                button.startAnimation(restoreAnimation)
            }
        })
        
        button.startAnimation(scaleAnimation)
    }
    
    /**
     * 为弹窗添加显示动画
     */
    private fun addDialogShowAnimation() {
        val rootView = findViewById<View>(android.R.id.content)
        if (rootView != null) {
            val scaleAnimation = ScaleAnimation(
                0.8f, 1f, 0.8f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            )
            scaleAnimation.duration = 300
            rootView.startAnimation(scaleAnimation)
        }
    }
    
    /**
     * 根据系统主题更新文字颜色
     */
    private fun updateTextColors(
        courseNameTextView: TextView,
        teacherTextView: TextView,
        locationTextView: TextView,
        timeTextView: TextView,
        weekPatternTextView: TextView,
        noteTextView: TextView?,
        noteLabel: TextView?,
        colorLabel: TextView,
        colorValue: TextView
    ) {
        val isDarkMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // 在浅色模式下，将所有文本设置为黑色以确保良好的可读性
        val blackColor = context.getColor(R.color.black)
        val whiteColor = context.getColor(R.color.white)
        
        val primaryTextColor = if (isDarkMode) {
            whiteColor
        } else {
            blackColor
        }
        
        val secondaryTextColor = if (isDarkMode) {
            context.getColor(R.color.dark_text_secondary)
        } else {
            blackColor
        }
        
        courseNameTextView.setTextColor(primaryTextColor)
        teacherTextView.setTextColor(primaryTextColor)
        locationTextView.setTextColor(secondaryTextColor)
        timeTextView.setTextColor(secondaryTextColor)
        weekPatternTextView.setTextColor(secondaryTextColor)
        noteTextView?.setTextColor(secondaryTextColor)
        noteLabel?.setTextColor(primaryTextColor)
        colorLabel.setTextColor(primaryTextColor)
        colorValue.setTextColor(whiteColor)
    }

    private fun getCourseTypeInfo(type: String): Pair<String, Int> {
        // 使用文档中定义的课程类型色彩映射
        return when (type) {
            "pe" -> Pair("体育", Color.parseColor("#4cc9f0"))       // 天蓝：活力/放松
            "major_required" -> Pair("专业必修", Color.parseColor("#4361ee")) // 蓝色：重要/严肃
            "major_elective" -> Pair("专业选修", Color.parseColor("#7209b7")) // 紫色：探索/兴趣
            "public_required" -> Pair("公共必修", Color.parseColor("#f72585")) // 粉红：基础/普及
            "experiment" -> Pair("实验课", Color.parseColor("#72efdd"))   // 青绿：实践/动手
            else -> Pair("其他", context.getColor(R.color.course_major_required))
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
    
    private fun getCourseColorInfo(color: Int?): Pair<String, Int> {
        return if (color == null) {
            Pair("按类型自动", context.getColor(R.color.course_major_required))
        } else {
            val mapping = mapOf(
                context.getColor(R.color.course_major_required) to "蓝色",
                context.getColor(R.color.course_major_elective) to "紫色",
                context.getColor(R.color.course_public_required) to "粉色",
                context.getColor(R.color.course_experiment) to "青绿",
                context.getColor(R.color.course_pe) to "天蓝"
            )
            Pair(mapping[color] ?: "自定义", color)
        }
    }
    
    private fun confirmDelete() {
        AlertDialog.Builder(context)
            .setTitle("删除课程")
            .setMessage("确定要删除《${course.name}》吗？")
            .setPositiveButton("删除") { _, _ ->
                dismiss()
                onDelete(course)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
