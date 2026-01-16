package com.example.schedule.view
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.schedule.R
import com.example.schedule.model.Course

@SuppressLint("ClickableViewAccessibility")
class CourseCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var course: Course
    private val courseNameTextView: ChineseOptimizedTextView
    private val teacherTextView: ChineseOptimizedTextView
    private val locationTextView: ChineseOptimizedTextView
    private val periodTextView: ChineseOptimizedTextView
    private var backgroundDrawable: GradientDrawable? = null
    private var isCurrentCourse = false
    private val recordTextPaint = Paint().apply {
        isAntiAlias = true
        textSize = dpToPx(9).toFloat()
        color = Color.WHITE
        textAlign = Paint.Align.RIGHT
    }
    private val recordDotPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#FF4D4F")
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.TOP
        // 优化内边距，确保在小屏幕上也能显示足够的文本
        val horizontalPadding = dpToPx(6)
        val verticalPadding = dpToPx(8)
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

        // 设置初始背景
        setBackgroundResource(android.R.color.transparent)

        // 优化课程名称显示 - 每行3个中文，最多4行
        courseNameTextView = ChineseOptimizedTextView(context).apply {
            setupForChineseText(8.0f, true, 3, 4)
        }

        // 优化教师姓名显示 - 每行3个中文，最多2行
        teacherTextView = ChineseOptimizedTextView(context).apply {
            setupForChineseText(8.0f, false, 3, 2)
        }

        // 优化地点信息显示 - 每行3个中文，最多2行
        locationTextView = ChineseOptimizedTextView(context).apply {
            setupForChineseText(8.5f, false, 3, 2)
        }

        // 保留节次信息的占位（不显示）
        periodTextView = ChineseOptimizedTextView(context).apply {
            setupForChineseText(8f, false, 3, 1)
            visibility = View.GONE
        }

        // 添加子视图
        addView(courseNameTextView)
        addView(teacherTextView)
        addView(locationTextView)
        addView(periodTextView)

        // 为所有TextView设置自适应高度的布局参数，使其高度能根据实际内容自动调整
        val textViewList = listOf(
            courseNameTextView,
            teacherTextView,
            locationTextView,
            periodTextView
        )
        
        textViewList.forEach { textView ->
            val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            textView.layoutParams = params
        }
        // 添加触摸监听器实现按压效果
        setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(90)
                        .start()
                    ViewCompat.setTranslationZ(this, dpToPx(6).toFloat())
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    animate()
                        .scaleX(if (isCurrentCourse) 1.02f else 1.0f)
                        .scaleY(if (isCurrentCourse) 1.02f else 1.0f)
                        .setDuration(120)
                        .start()
                    ViewCompat.setTranslationZ(this, 0f)
                }
            }
            false // 返回false以允许点击事件继续传播
        }

        // 设置初始状态用于入场动画
        alpha = 0f
        translationY = dpToPx(16).toFloat()
        scaleX = 0.98f
        scaleY = 0.98f
    }

    fun setCourse(course: Course) {
        this.course = course

        // 优化课程名称显示
        courseNameTextView.text = course.name

        // 优化教师姓名显示
        teacherTextView.text = course.teacher ?: ""

        // 优化地点信息显示
        locationTextView.text = course.location ?: ""

        // 设置背景颜色
        setBackgroundColorByCourse(course)

        // 根据系统主题更新文字颜色
        updateTextColors()

        // 重新测量文本，确保正确显示
        post { requestLayout() }
    }

    /**
     * 根据系统主题更新文字颜色
     */
    private fun updateTextColors() {
        val isDarkMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    
        // 在浅色模式下，将所有文本设置为白色以确保良好的可读性
        val whiteColor = ContextCompat.getColor(context, R.color.white)
        val primaryTextColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.dark_text_primary)
        } else {
            whiteColor
        }
    
        val secondaryTextColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.dark_text_secondary)
        } else {
            whiteColor
        }
    
        courseNameTextView.setTextColor(primaryTextColor)
        teacherTextView.setTextColor(secondaryTextColor)
        locationTextView.setTextColor(secondaryTextColor)
        periodTextView.setTextColor(secondaryTextColor)
    }
    /**
     * 设置背景颜色
     */
    private fun setBackgroundColorByCourse(course: Course) {
        val fallbackColor = when (course.type) {
            "major_required" -> ContextCompat.getColor(context, R.color.course_major_required)
            "major_elective" -> ContextCompat.getColor(context, R.color.course_major_elective)
            "public_required" -> ContextCompat.getColor(context, R.color.course_public_required)
            "experiment" -> ContextCompat.getColor(context, R.color.course_experiment)
            "pe" -> ContextCompat.getColor(context, R.color.course_pe)
            else -> ContextCompat.getColor(context, R.color.course_major_required)
        }
        val color = course.color ?: fallbackColor

        // 创建带圆角和阴影的背景
        val strokeWidth = if (isCurrentCourse) dpToPx(2) else dpToPx(1)
        val strokeColor = if (isCurrentCourse) {
            ContextCompat.getColor(context, R.color.card_border_current)
        } else {
            ContextCompat.getColor(context, R.color.card_border)
        }
        val drawable = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpToPx(14).toFloat()
            setStroke(strokeWidth, strokeColor)
        }

        backgroundDrawable = drawable
        background = drawable
        elevation = dpToPx(3).toFloat()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * 启动卡片入场动画
     */
    fun startEntranceAnimation(delay: Long = 0) {
        postDelayed({
            animate().cancel()
            animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(260)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }, delay)
    }
        
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 重新测量文本
        requestLayout()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isCurrentCourse) {
            val dotRadius = dpToPx(3)
            val dotX = width - paddingRight - dpToPx(6)
            val dotY = paddingTop + dpToPx(6)
            canvas.drawCircle(dotX.toFloat(), dotY.toFloat(), dotRadius.toFloat(), recordDotPaint)
            val textX = dotX - dpToPx(10)
            val textY = dotY + dpToPx(3)
            canvas.drawText("REC", textX.toFloat(), textY.toFloat(), recordTextPaint)
        }
    }
    
    /**
     * 设置当前课程高亮效果
     * @param isCurrent 是否为当前课程
     */
    fun setCurrentCourse(isCurrent: Boolean) {
        isCurrentCourse = isCurrent
        if (isCurrent) {
            elevation = dpToPx(10).toFloat()
            backgroundDrawable?.setStroke(dpToPx(2), ContextCompat.getColor(context, R.color.card_border_current))
            animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(140)
                .start()
        } else {
            elevation = dpToPx(3).toFloat()
            backgroundDrawable?.setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.card_border))
            animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(140)
                .start()
        }
        invalidate()
    }

}

/**
 * 优化中文显示的TextView
 * 1. 确保每行显示指定数量的中文字符
 * 2. 超出区域的内容显示省略号
 * 3. 智能调整字体大小以适应空间
 * 4. 继承AppCompatTextView确保兼容性
 */
class ChineseOptimizedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {  // 修复#2: 使用AppCompatTextView

    private var minChineseCharsPerLine = 3  // 每行最少显示的中文字符数
    private var maxLines = 2                // 最大行数
    private var isBold = false              // 是否加粗

    private var baseTextSizePx = 0f

    init {
        // 使用系统省略号
        setHorizontallyScrolling(false)
        setSingleLine(false)
        ellipsize = TextUtils.TruncateAt.END

    }

    /**
     * 配置TextView以优化中文显示
     * @param textSizeSp 字体大小（SP单位）
     * @param bold 是否加粗
     * @param minChineseChars 每行最小中文字符数
     * @param maxLines 最大行数
     */
    fun setupForChineseText(textSizeSp: Float, bold: Boolean, minChineseChars: Int, maxLines: Int) {
        this.minChineseCharsPerLine = minChineseChars
        this.maxLines = maxLines
        this.isBold = bold
    
        // 设置基础属性
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        gravity = Gravity.TOP or Gravity.START
        baseTextSizePx = textSize
    
        // 使用系统省略号并限制最大行数
        setSingleLine(false)
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
    
        // 设置合适的行间距，特别优化数字和字母的显示
        val density = context.resources.displayMetrics.density
        val extraSpacing = (2 * density).toInt() // 减小行间距以适应数字和字母
        setLineSpacing(extraSpacing.toFloat(), 1.0f)
            
        // 确保视图能够正确测量高度
        includeFontPadding = false
    }

    @Suppress("DEPRECATION")
    private fun setupLineBreakStrategy() {
        // 修复#1: 根据API级别设置合适的换行策略
        // API 23+ 使用 Layout.BREAK_STRATEGY
//        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        // API < 23 不设置，使用默认换行策略
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 获取可用宽度
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        if (availableWidth <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // 获取文本
        val text = text.toString()
        if (text.isNotEmpty()) {
            // 计算一个中文字符的平均宽度
            val paint = paint
            val sampleText = "中"
            val chineseCharWidth = paint.measureText(sampleText)

            // 计算每行可用的最小宽度（确保能显示指定数量的中文）
            val minWidthForChineseChars = (chineseCharWidth * minChineseCharsPerLine).toInt()

            // 如果可用宽度小于最小需求，动态调整字体大小
            if (availableWidth < minWidthForChineseChars && textSize > 10f) {
                // 计算新的字体大小比例
                val scaleRatio = availableWidth.toFloat() / minWidthForChineseChars

                // 应用新的字体大小（不低于10sp）
                val newTextSize = maxOf(textSize * scaleRatio, 10f)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, newTextSize)
            } else if (baseTextSizePx > 0f && textSize != baseTextSizePx) {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, baseTextSizePx)
            }
        }

        // 调用父类的测量方法，保留多行文本高度
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        // 文本变化后，重新测量
        if (text != null && parent != null) {
            (parent as? View)?.post { requestLayout() }
        }
    }
}
