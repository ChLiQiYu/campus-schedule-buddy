package com.example.campus_schedule_buddy

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.campus_schedule_buddy.model.Course
import com.example.campus_schedule_buddy.data.AppDatabase
import com.example.campus_schedule_buddy.data.CourseRepository
import com.example.campus_schedule_buddy.view.CourseCardView
import com.example.campus_schedule_buddy.view.CourseDetailDialog
import com.example.campus_schedule_buddy.view.AddEditCourseDialog
import android.os.Handler
import android.os.Looper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.enableEdgeToEdge

class MainActivity : AppCompatActivity() {

    private lateinit var tvCurrentWeek: TextView
    private lateinit var btnPreviousWeek: ImageButton
    private lateinit var btnNextWeek: ImageButton
    private lateinit var btnToday: TextView
    private lateinit var scheduleContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var fabAddCourse: FloatingActionButton
    private lateinit var weekHeader: LinearLayout
    private lateinit var timeHeader: TextView

    private var currentWeek = 1
    private val totalWeeks = 20

    // 用于存储课程数据
    private val courseList = mutableListOf<Course>()
    private lateinit var repository: CourseRepository

    // 用于定期更新当前课程高亮状态
    private val handler = Handler(Looper.getMainLooper())
    private val highlightRunnable = object : Runnable {
        override fun run() {
            highlightCurrentCourse()
            // 每分钟检查一次当前课程
            handler.postDelayed(this, 60000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        initViews()
        setupListeners()
        setupRepository()

        // 启动定时器定期更新当前课程高亮状态
        handler.post(highlightRunnable)
    }

    private fun initViews() {
        tvCurrentWeek = findViewById(R.id.tv_current_week)
        btnPreviousWeek = findViewById(R.id.btn_previous_week)
        btnNextWeek = findViewById(R.id.btn_next_week)
        btnToday = findViewById(R.id.btn_today)
        scheduleContainer = findViewById(R.id.schedule_container)
        scrollView = findViewById(R.id.scrollView)
        fabAddCourse = findViewById(R.id.fab_add_course)
        weekHeader = findViewById(R.id.week_header)
        timeHeader = findViewById(R.id.tv_time_header)
        updateWeekHeaderLayout()
    }

    private fun setupListeners() {
        btnPreviousWeek.setOnClickListener {
            if (currentWeek > 1) {
                currentWeek--
                updateWeekDisplay()
                loadScheduleForWeek(currentWeek)
            }
        }

        btnNextWeek.setOnClickListener {
            if (currentWeek < totalWeeks) {
                currentWeek++
                updateWeekDisplay()
                loadScheduleForWeek(currentWeek)
            }
        }

        btnToday.setOnClickListener {
            currentWeek = getCurrentWeek()
            updateWeekDisplay()
            loadScheduleForWeek(currentWeek)
            // 滚动到当前时间
            scrollToCurrentTime()
        }

        fabAddCourse.setOnClickListener {
            showAddCourseDialog()
        }
    }

    private fun updateWeekDisplay() {
        tvCurrentWeek.text = "第${currentWeek}周"

        // 更新按钮状态
        btnPreviousWeek.isEnabled = currentWeek > 1
        btnNextWeek.isEnabled = currentWeek < totalWeeks
    }

    private fun loadScheduleForWeek(week: Int) {
        // 清空现有的课程视图
        scheduleContainer.removeAllViews()

        // 设置背景色
        updateBackgroundColor()

        // 获取该周的课程数据
        val courses = courseList.filter { it.weekPattern.contains(week) }

        // 创建课程表主容器（使用FrameLayout实现绝对定位）
        val scheduleFrameLayout = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                getPeriodRowHeight() * 8 // 8节课的总高度
            )
            clipChildren = false
            clipToPadding = false
        }

        // 先添加网格背景（时间列和分隔线）
        addGridBackground(scheduleFrameLayout)

        // 再添加课程卡片（后添加的在上层，不会被网格遮挡）
        courses.forEach { course ->
            addCourseCard(scheduleFrameLayout, course)
        }

        scheduleContainer.addView(scheduleFrameLayout)

        // 高亮当前时间的课程
        highlightCurrentCourse()
    }

    /**
     * 根据系统主题更新背景色
     */
    private fun updateBackgroundColor() {
        val isDarkMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = if (isDarkMode) {
            ContextCompat.getColor(this, R.color.dark_background)
        } else {
            ContextCompat.getColor(this, R.color.light_background)
        }
        scheduleContainer.setBackgroundColor(backgroundColor)
    }

    /**
     * 添加网格背景（时间列和分隔线）
     */
    private fun addGridBackground(container: FrameLayout) {
        val isDarkMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        container.post {
            val rowHeight = getPeriodRowHeight()
            val metrics = calculateGridMetrics(container.width)
            val timeColumnWidth = metrics.timeColumnWidth
            val lineColor = if (isDarkMode) {
                ContextCompat.getColor(this, R.color.grid_line_dark)
            } else {
                ContextCompat.getColor(this, R.color.grid_line)
            }
            val timeTextColor = if (isDarkMode) {
                ContextCompat.getColor(this, R.color.dark_text_secondary)
            } else {
                ContextCompat.getColor(this, R.color.light_text_secondary)
            }

            // 为每个节次创建时间标签
            for (period in 1..8) {
                val timeLabel = TextView(this).apply {
                    text = "第${period}节"
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(timeTextColor)
                }

                val params = FrameLayout.LayoutParams(timeColumnWidth, rowHeight)
                params.topMargin = rowHeight * (period - 1)
                params.leftMargin = metrics.innerPadding
                timeLabel.layoutParams = params

                container.addView(timeLabel)
            }

            // 添加水平分隔线
            for (i in 1..8) {
                val divider = View(this).apply {
                    setBackgroundColor(lineColor)
                }
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(1)
                )
                params.topMargin = rowHeight * i
                divider.layoutParams = params
                container.addView(divider)
            }

            // 添加垂直分隔线
            val totalHeight = rowHeight * 8
            for (i in 0..7) {
                val divider = View(this).apply {
                    setBackgroundColor(lineColor)
                }
                val params = FrameLayout.LayoutParams(
                    dpToPx(1),
                    totalHeight
                )
                params.leftMargin = metrics.innerPadding + timeColumnWidth + metrics.dayColumnWidth * i
                divider.layoutParams = params
                container.addView(divider)
            }
        }
    }

    private fun updateWeekHeaderLayout() {
        weekHeader.post {
            val metrics = calculateGridMetrics(weekHeader.width)
            val params = timeHeader.layoutParams as LinearLayout.LayoutParams
            params.width = metrics.timeColumnWidth + metrics.innerPadding
            timeHeader.layoutParams = params
            weekHeader.setPadding(
                weekHeader.paddingLeft,
                weekHeader.paddingTop,
                metrics.innerPadding,
                weekHeader.paddingBottom
            )
        }
    }

    /**
     * 添加课程卡片（绝对定位，支持跨节课程）
     */
    private fun addCourseCard(container: FrameLayout, course: Course) {
        val rowHeight = getPeriodRowHeight()
        
        // 等待容器测量完成后再计算位置
        container.post {
            val containerWidth = container.width
            if (containerWidth <= 0) return@post

            val metrics = calculateGridMetrics(containerWidth)
            
            // 计算课程卡片的位置和尺寸
            val span = course.endPeriod - course.startPeriod + 1
            val cardLeft = metrics.innerPadding + metrics.timeColumnWidth +
                (course.dayOfWeek - 1) * metrics.dayColumnWidth + metrics.columnGap
            val cardTop = (course.startPeriod - 1) * rowHeight + metrics.columnGap
            val cardWidth = metrics.dayColumnWidth - metrics.columnGap * 2
            val cardHeight = rowHeight * span - metrics.columnGap * 2
            
            val courseCard = CourseCardView(this).apply {
                setCourse(course)
                setIsSpanning(span)
                tag = course
                
                layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight).apply {
                    leftMargin = cardLeft
                    topMargin = cardTop
                }
                
                // 设置较高的elevation确保课程卡片在网格之上
                elevation = dpToPx(4).toFloat()
                
                setOnClickListener {
                    showCourseDetailDialog(course)
                }
            }
            
            container.addView(courseCard)
            
            // 启动入场动画
            val delay = ((course.dayOfWeek - 1) * 50L + (course.startPeriod - 1) * 30L)
            courseCard.startEntranceAnimation(delay)
        }
    }

    private fun highlightCurrentCourse() {
        // 获取当前时间
        val now = java.time.LocalTime.now()
        val currentPeriod = getCurrentPeriod(now)
        val currentDay = java.time.LocalDate.now().dayOfWeek.value

        // 遍历scheduleContainer中的所有子视图
        for (i in 0 until scheduleContainer.childCount) {
            val child = scheduleContainer.getChildAt(i)
            // 新布局结构：课程表主容器是FrameLayout
            if (child is FrameLayout) {
                // 遍历FrameLayout中的所有课程卡片
                for (j in 0 until child.childCount) {
                    val cardView = child.getChildAt(j)
                    if (cardView is CourseCardView) {
                        val course = getCourseFromCard(cardView)
                        if (course != null) {
                            val isCurrent = currentPeriod > 0 &&
                                currentDay in 1..7 &&
                                course.dayOfWeek == currentDay &&
                                currentPeriod >= course.startPeriod &&
                                currentPeriod <= course.endPeriod
                            cardView.setCurrentCourse(isCurrent)
                        }
                    }
                }
            }
        }
    }

    private fun scrollToCurrentTime() {
        // 获取当前时间
        val now = java.time.LocalTime.now()
        val currentPeriod = getCurrentPeriod(now)

        // 如果在课程时间范围内，则滚动到对应位置
        if (currentPeriod > 0) {
            // 计算滚动位置 - 增加额外空间确保当前课程完全可见
            val scrollY = getPeriodRowHeight() * (currentPeriod - 1) - dpToPx(20)
            scrollView.post {
                scrollView.smoothScrollTo(0, scrollY.coerceAtLeast(0))
            }
        }
    }

    private fun getCurrentWeek(): Int {
        // 获取当前周数（模拟）
        return 1
    }

    private fun getCurrentPeriod(time: java.time.LocalTime): Int {
        // 根据时间判断当前是第几节课
        return when {
            time.isAfter(java.time.LocalTime.of(8, 0)) && time.isBefore(java.time.LocalTime.of(8, 45)) -> 1
            time.isAfter(java.time.LocalTime.of(8, 55)) && time.isBefore(java.time.LocalTime.of(9, 40)) -> 2
            time.isAfter(java.time.LocalTime.of(10, 0)) && time.isBefore(java.time.LocalTime.of(10, 45)) -> 3
            time.isAfter(java.time.LocalTime.of(10, 55)) && time.isBefore(java.time.LocalTime.of(11, 40)) -> 4
            time.isAfter(java.time.LocalTime.of(14, 0)) && time.isBefore(java.time.LocalTime.of(14, 45)) -> 5
            time.isAfter(java.time.LocalTime.of(14, 55)) && time.isBefore(java.time.LocalTime.of(15, 40)) -> 6
            time.isAfter(java.time.LocalTime.of(16, 0)) && time.isBefore(java.time.LocalTime.of(16, 45)) -> 7
            time.isAfter(java.time.LocalTime.of(16, 55)) && time.isBefore(java.time.LocalTime.of(17, 40)) -> 8
            else -> -1 // 不在课程时间内
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * 根据屏幕密度和设备尺寸计算每节课的行高
     * 确保在不同设备上都有良好的显示效果
     */
    private fun getPeriodRowHeight(): Int {
        val density = resources.displayMetrics.density
        val screenHeight = resources.displayMetrics.heightPixels

        // 根据屏幕密度调整基础高度
        val baseHeight = when {
            density <= 1.5 -> 70  // ldpi
            density <= 2.0 -> 75   // mdpi, hdpi
            density <= 3.0 -> 80   // xhdpi
            density <= 4.0 -> 85   // xxhdpi
            else -> 90            // xxxhdpi
        }

        // 根据屏幕高度调整高度，确保在小屏幕上也能显示足够的内容
        val screenAdjustedHeight = if (screenHeight < 1280) {
            // 小屏幕设备适当减小高度
            (baseHeight * 0.9).toInt()
        } else {
            baseHeight
        }

        return dpToPx(screenAdjustedHeight)
    }

    private fun showCourseDetailDialog(course: Course) {
        val dialog = CourseDetailDialog(
            this,
            course,
            onEdit = { selected -> showEditCourseDialog(selected) },
            onDelete = { selected -> deleteCourse(selected) }
        )
        dialog.show()
    }

    /**
     * 通过反射获取CourseCardView中的课程对象
     */
    private fun getCourseFromCard(courseCardView: CourseCardView): Course? {
        val taggedCourse = courseCardView.tag as? Course
        if (taggedCourse != null) {
            return taggedCourse
        }
        return try {
            val field = CourseCardView::class.java.getDeclaredField("course")
            field.isAccessible = true
            field.get(courseCardView) as? Course
        } catch (e: Exception) {
            null
        }
    }

    private data class GridMetrics(
        val timeColumnWidth: Int,
        val dayColumnWidth: Int,
        val columnGap: Int,
        val innerPadding: Int
    )

    private fun calculateGridMetrics(containerWidth: Int): GridMetrics {
        val innerPadding = dpToPx(4)
        val minTimeWidth = dpToPx(42)
        val maxTimeWidth = dpToPx(56)
        val availableWidth = (containerWidth - innerPadding * 2).coerceAtLeast(0)
        val timeColumnWidth = (availableWidth * 0.13f).toInt().coerceIn(minTimeWidth, maxTimeWidth)
        val dayColumnWidth = ((availableWidth - timeColumnWidth) / 7f).toInt().coerceAtLeast(dpToPx(36))
        val columnGap = dpToPx(2)
        return GridMetrics(timeColumnWidth, dayColumnWidth, columnGap, innerPadding)
    }

    private fun setupRepository() {
        val database = AppDatabase.getInstance(this)
        repository = CourseRepository(database.courseDao())
        lifecycleScope.launch {
            repository.coursesFlow.collect { courses ->
                courseList.clear()
                courseList.addAll(courses)
                updateWeekDisplay()
                loadScheduleForWeek(currentWeek)
            }
        }
    }

    private fun showAddCourseDialog() {
        val dialog = AddEditCourseDialog(this, null) { course, callback ->
            lifecycleScope.launch {
                val result = repository.addCourse(course)
                withContext(Dispatchers.Main) {
                    when (result) {
                        is CourseRepository.SaveResult.Success -> {
                            callback(AddEditCourseDialog.SaveFeedback(true))
                        }
                        is CourseRepository.SaveResult.Error -> {
                            callback(AddEditCourseDialog.SaveFeedback(false, result.message))
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showEditCourseDialog(course: Course) {
        val dialog = AddEditCourseDialog(this, course) { updated, callback ->
            lifecycleScope.launch {
                val result = repository.updateCourse(updated)
                withContext(Dispatchers.Main) {
                    when (result) {
                        is CourseRepository.SaveResult.Success -> {
                            callback(AddEditCourseDialog.SaveFeedback(true))
                        }
                        is CourseRepository.SaveResult.Error -> {
                            callback(AddEditCourseDialog.SaveFeedback(false, result.message))
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun deleteCourse(course: Course) {
        lifecycleScope.launch {
            repository.deleteCourse(course)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "课程已删除", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理定时器
        handler.removeCallbacks(highlightRunnable)
    }
}
