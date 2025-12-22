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
    private var isPressed = false
    private var originalElevation = 0f

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        setPadding(
            dpToPx(6),
            dpToPx(4),
            dpToPx(6),
            dpToPx(4)
        )

        // 设置初始背景
        setBackgroundResource(android.R.color.transparent)

        // 初始化子视图
        courseNameTextView = TextView(context).apply {
            setTextSize(14f)  // 增大课程名称字体
            setTextColor(Color.WHITE)
            setSingleLine(false)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            // 添加字体粗细和字母间距
            setLetterSpacing(0.01f)
        }

        teacherTextView = TextView(context).apply {
            setTextSize(12f)  // 增大教师姓名字体
            setTextColor(Color.WHITE)
            setSingleLine(false)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            // 添加轻微的字母间距
            setLetterSpacing(0.005f)
        }

        locationTextView = TextView(context).apply {
            setTextSize(11f)  // 增大地点信息字体
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
        
        // 保存原始elevation值
        originalElevation = elevation
        
        // 添加触摸监听器实现按压效果
        setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    // 减小elevation创建按下效果
                    elevation = originalElevation * 0.5f
                    // 缩小视图创建按下效果
                    scaleX = 0.98f
                    scaleY = 0.98f
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    // 恢复elevation
                    elevation = originalElevation
                    // 恢复视图大小
                    scaleX = 1.0f
                    scaleY = 1.0f
                }
            }
            false // 返回false以允许点击事件继续传播
        }
        
        // 设置初始状态用于入场动画
        alpha = 0f
        translationY = dpToPx(20).toFloat()
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
    
    /**
     * 启动卡片入场动画
     */
    fun startEntranceAnimation(delay: Long = 0) {
        postDelayed({
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300) // 300ms动画时长
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
        }, delay)
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

        // 创建带圆角和阴影的背景
        val drawable = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpToPx(16).toFloat() // 增加圆角半径到16dp
        }
        
        background = drawable
        
        // 添加阴影效果
        elevation = dpToPx(3).toFloat()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}