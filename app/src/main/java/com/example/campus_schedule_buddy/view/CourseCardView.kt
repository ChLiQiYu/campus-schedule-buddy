package com.example.campus_schedule_buddy.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.campus_schedule_buddy.R
import com.example.campus_schedule_buddy.model.Course

class CourseCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var course: Course
    private val courseNameTextView: TextView
    private val teacherTextView: TextView
    private val locationTextView: TextView
    private val periodTextView: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        setPadding(
            dpToPx(6),
            dpToPx(4),
            dpToPx(6),
            dpToPx(4)
        )

        // 设置圆角和阴影效果
        setBackgroundResource(R.drawable.course_card_background)

        // 初始化子视图
        courseNameTextView = TextView(context).apply {
            setTextSize(12f)  // 适中的字体大小
            setTextColor(Color.WHITE)
            setSingleLine(false)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        teacherTextView = TextView(context).apply {
            setTextSize(10f)  // 适中的字体大小
            setTextColor(Color.WHITE)
            setSingleLine(false)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        locationTextView = TextView(context).apply {
            setTextSize(9f)  // 适中的字体大小
            setTextColor(Color.WHITE)
            setSingleLine(false)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        periodTextView = TextView(context).apply {
            setTextSize(12f)
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }

        // 添加子视图
        addView(courseNameTextView)
        addView(teacherTextView)
        addView(locationTextView)
        addView(periodTextView)
    }

    fun setCourse(course: Course) {
        this.course = course
        
        courseNameTextView.text = course.name
        teacherTextView.text = course.teacher
        locationTextView.text = course.location
        
        // 格式化周数显示
        val weekText = if (course.weekPattern.size > 1) {
            "第${course.weekPattern.first()}-${course.weekPattern.last()}周"
        } else {
            "第${course.weekPattern.first()}周"
        }
        
        periodTextView.text = "${course.startPeriod}-${course.endPeriod}节 · $weekText"
        
        // 设置背景颜色
        setBackgroundColorByCourseType(course.type)
    }
    
    /**
     * 设置课程卡片是否为跨越多个节次的卡片
     * @param isSpanning 是否跨越多个节次
     */
    fun setIsSpanning(isSpanning: Boolean) {
        // 如果是跨越多个节次的卡片，调整底部圆角
        if (isSpanning) {
            // 可以在这里添加特殊的样式处理
        }
    }

    private fun setBackgroundColorByCourseType(type: String) {
        val color = when (type) {
            "major_required" -> Color.parseColor("#4cc9f0") // 专业必修
            "major_elective" -> Color.parseColor("#7209b7") // 专业选修
            "public_required" -> Color.parseColor("#f72585") // 公共必修
            "public_elective" -> Color.parseColor("#4895ef") // 公共选修
            "experiment" -> Color.parseColor("#72efdd") // 实验/实践
            "pe" -> Color.parseColor("#4361ee") // 体育/艺术
            else -> Color.parseColor("#4cc9f0") // 默认颜色
        }

        // 创建带圆角的背景
        val drawable = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpToPx(12).toFloat()
        }
        
        background = drawable
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}