package com.example.campus_schedule_buddy.view
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.example.campus_schedule_buddy.R
import com.example.campus_schedule_buddy.model.Course

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
    private var isPressed = false
    private var originalElevation = 0f
    private var cardWidth = 0
    private var spanCount = 1
    private val spanIndicatorPaint = Paint().apply {
        isAntiAlias = true
        textSize = 24f
        color = Color.WHITE
        textAlign = Paint.Align.RIGHT
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.TOP
        // 优化内边距，确保在小屏幕上也能显示足够的文本
        val horizontalPadding = dpToPx(4) // 使用文档中定义的12dp内边距
        val verticalPadding = dpToPx(8) // 减小垂直内边距，为文本留出更多空间
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

        // 设置初始背景
        setBackgroundResource(android.R.color.transparent)

        // 计算卡片宽度（减去左右内边距）
        post {
            cardWidth = width - horizontalPadding * 2
        }

        // 优化课程名称显示 - 每行3个中文，最多3行
        courseNameTextView = ChineseOptimizedTextView(context).apply {
            setupForChineseText(14f, true, 3, 3) // 14sp, 加粗, 每行3中文, 最多3行
        }

        // 优化教师姓名显示 - 每行3个中文，最多1行
        teacherTextView = ChineseOptimizedTextView(context).apply {
            setupForChineseText(12f, false, 3, 1) // 12sp, 不加粗, 每行3中文, 最多1行
        }

        // 优化地点信息显示 - 每行3个中文，最多1行
        locationTextView = ChineseOptimizedTextView(context).apply {
            setupForChineseText(12f, false, 3, 1) // 12sp, 不加粗, 每行3中文, 最多1行
        }

        // 优化节次和周数信息显示 - 每行3个中文，最多2行
        periodTextView = ChineseOptimizedTextView(context).apply {
            setupForChineseText(10f, false, 3, 2) // 10sp, 不加粗, 每行3中文, 最多2行
            // 确保文本在视图中垂直居下对齐
            gravity = Gravity.BOTTOM or Gravity.END
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
        // 保存原始elevation值
        originalElevation = elevation

        // 添加触摸监听器实现按压效果
        setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    // 减小elevation创建按下效果
                    elevation = originalElevation * 0.3f
                    // 缩小视图创建按下效果
                    scaleX = 0.95f
                    scaleY = 0.95f
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

        // 优化课程名称显示
        courseNameTextView.text = course.name

        // 优化教师姓名显示
        teacherTextView.text = course.teacher

        // 优化地点信息显示
        locationTextView.text = course.location

        // 格式化周数显示，确保在有限空间内完整显示
        val weekText = if (course.weekPattern.size > 1) {
            "第${course.weekPattern.first()}-${course.weekPattern.last()}周"
        } else {
            "第${course.weekPattern.first()}周"
        }

        // 优化节次和周数信息显示
        periodTextView.text = "${course.startPeriod}-${course.endPeriod}节 · $weekText"

        // 设置背景颜色
        setBackgroundColorByCourseType(course.type)

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
    private fun setBackgroundColorByCourseType(type: String) {
        val color = when (type) {
            "major_required" -> ContextCompat.getColor(context, R.color.course_major_required)
            "major_elective" -> ContextCompat.getColor(context, R.color.course_major_elective)
            "public_required" -> ContextCompat.getColor(context, R.color.course_public_required)
            "experiment" -> ContextCompat.getColor(context, R.color.course_experiment)
            "pe" -> ContextCompat.getColor(context, R.color.course_pe)
            else -> ContextCompat.getColor(context, R.color.course_major_required)
        }

        // 创建带圆角和阴影的背景
        val drawable = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpToPx(12).toFloat()
        }

        background = drawable
        elevation = dpToPx(2).toFloat()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * 启动卡片入场动画
     */
    fun startEntranceAnimation(delay: Long = 0) {
        postDelayed({
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
                    
            animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(150)
                .withEndAction {
                    animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }, delay)
    }
        
    /**
     * 设置课程卡片是否为跨越多个节次的卡片
     * @param spanCount 跨越的节次数
     */
    fun setIsSpanning(spanCount: Int) {
        this.spanCount = spanCount
        // 如果是跨越多个节次的卡片，可以在这里添加特殊的样式处理
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cardWidth = w - paddingLeft - paddingRight
        // 重新测量文本
        requestLayout()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 如果是跨节课程，绘制跨节数指示器
        if (spanCount > 1) {
            val text = "↾$spanCount"
            val xPos = width - paddingRight - dpToPx(4)
            val yPos = height - paddingBottom - dpToPx(4)
            canvas.drawText(text, xPos.toFloat(), yPos.toFloat(), spanIndicatorPaint)
        }
    }
    
    /**
     * 设置当前课程高亮效果
     * @param isCurrent 是否为当前课程
     */
    fun setCurrentCourse(isCurrent: Boolean) {
        if (isCurrent) {
            // 增加阴影和边框来突出显示当前课程，使用文档中推荐的8dp
            elevation = dpToPx(8).toFloat()
            
            // 添加边框效果来进一步突出当前课程
            val drawable = background as? GradientDrawable
            drawable?.setStroke(dpToPx(2), Color.WHITE)
        } else {
            // 恢复正常阴影，使用文档中推荐的2dp
            elevation = dpToPx(2).toFloat()
            
            // 移除边框
            val drawable = background as? GradientDrawable
            drawable?.setStroke(0, Color.TRANSPARENT)
        }
    }
}

/**
 * 优化中文显示的TextView
 * 1. 确保每行显示指定数量的中文字符
 * 2. 超出区域的内容直接裁切，不显示省略号
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

    // 重写以实现裁切而非省略号
    private var contentRect = Rect()

    init {
        // 禁用系统省略号
        setHorizontallyScrolling(false)
        setSingleLine(false)
        ellipsize = null

        // 启用硬件加速以提高绘制性能
        setLayerType(LAYER_TYPE_HARDWARE, null)
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
    
        // 要用系统省略号，我们将手动处理裁切
        setSingleLine(false)
        this.maxLines = maxLines
        ellipsize = null
    
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

    override fun onDraw(canvas: Canvas) {
        // 保存canvas状态
        canvas.save()

        // 计算内容区域（考虑padding）
        contentRect.set(
            paddingLeft,
            paddingTop,
            width - paddingRight,
            height - paddingBottom
        )

        // 裁剪绘制区域
        canvas.clipRect(contentRect)

        // 绘制文本
        super.onDraw(canvas)

        // 恢复canvas状态
        canvas.restore()
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
            }
        }

        // 调用父类的测量方法
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        
        // 确保高度正确计算，特别是对于数字和字母
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.bottom - fontMetrics.top
        val desiredHeight = (textHeight + paddingTop + paddingBottom).toInt()
        
        // 获取测量模式
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize // 精确模式，使用指定大小
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize) // 最大模式，取较小值
            MeasureSpec.UNSPECIFIED -> desiredHeight // 未指定模式，使用计算值
            else -> desiredHeight
        }
        
        // 设置测量尺寸
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        // 文本变化后，重新测量
        if (text != null && parent != null) {
            (parent as? View)?.post { requestLayout() }
        }
    }
}