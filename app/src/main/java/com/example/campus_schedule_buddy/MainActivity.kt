package com.example.campus_schedule_buddy

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.campus_schedule_buddy.model.Course
import com.example.campus_schedule_buddy.util.MockData
import com.example.campus_schedule_buddy.view.CourseCardView
import com.example.campus_schedule_buddy.view.CourseDetailDialog
import com.example.campus_schedule_buddy.view.AddEditCourseDialog
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var tvCurrentWeek: TextView
    private lateinit var btnPreviousWeek: ImageButton
    private lateinit var btnNextWeek: ImageButton
    private lateinit var btnToday: TextView
    private lateinit var scheduleContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    
    private var currentWeek = 1
    private val totalWeeks = 20
    
    // 用于存储课程数据
    private val courseList = mutableListOf<Course>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        initViews()
        setupListeners()
        loadScheduleForWeek(currentWeek)
    }
    
    private fun initViews() {
        tvCurrentWeek = findViewById(R.id.tv_current_week)
        btnPreviousWeek = findViewById(R.id.btn_previous_week)
        btnNextWeek = findViewById(R.id.btn_next_week)
        btnToday = findViewById(R.id.btn_today)
        scheduleContainer = findViewById(R.id.schedule_container)
        scrollView = findViewById(R.id.scrollView)
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
        
        // 获取该周的课程数据
        val courses = if (courseList.isEmpty()) {
            // 如果没有自定义课程，使用假数据
            MockData.getMockCourses(week)
        } else {
            // 使用自定义课程数据
            courseList.filter { it.weekPattern.contains(week) }
        }
        
        // 按天分组课程
        val coursesByDay = mutableMapOf<Int, MutableList<Course>>()
        for (i in 1..7) {
            coursesByDay[i] = mutableListOf()
        }
        
        courses.forEach { course ->
            coursesByDay[course.dayOfWeek]?.add(course)
        }
        
        // 创建8个时间段的视图（1-8节），同时处理跨越多节的课程
        var currentPeriod = 1
        while (currentPeriod <= 8) {
            // 检查是否有课程从当前节次开始且跨越多个节次
            val startingCourses = mutableMapOf<Int, Course>()
            for (day in 1..7) {
                val course = coursesByDay[day]?.find { it.startPeriod == currentPeriod }
                if (course != null) {
                    startingCourses[day] = course
                }
            }
            
            // 获取在当前节次的课程的最大跨度
            var maxSpan = 1 // 至少是单节课
            for (day in 1..7) {
                val course = coursesByDay[day]?.find { it.startPeriod <= currentPeriod && it.endPeriod >= currentPeriod }
                if (course != null) {
                    val span = course.endPeriod - course.startPeriod + 1
                    val startPeriodInSpan = currentPeriod - course.startPeriod + 1
                    if (startPeriodInSpan <= span) {
                        maxSpan = maxOf(maxSpan, span - startPeriodInSpan + 1)
                    }
                }
            }
            
            // 创建该节次行（或跨越多节的行）
            val periodRow = createPeriodRow(currentPeriod, coursesByDay, maxSpan)
            scheduleContainer.addView(periodRow)
            
            // 根据最大跨度跳过已经显示的节次
            currentPeriod += maxSpan
        }
        
        // 高亮当前时间的课程
        highlightCurrentCourse()
    }
    
    private fun createPeriodRow(startPeriod: Int, coursesByDay: Map<Int, List<Course>>, spanCount: Int = 1): View {
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(80 * spanCount) // 根据跨越节次数计算高度
            )
        }
        
        // 时间列 - 只显示起始节次，其他节次留空
        val timeColumn = TextView(this).apply {
            text = "第${startPeriod}节"
            textSize = 12f
            gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
            setPadding(0, 0, dpToPx(4), 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }
        rowLayout.addView(timeColumn)
        
        // 为周一到周日创建列
        for (day in 1..7) {
            val cellLayout = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
            }
            
            // 查找当天该节次（或跨越该节次）的课程
            val courseForThisSlot = coursesByDay[day]?.find { 
                startPeriod >= it.startPeriod && startPeriod <= it.endPeriod
            }
            
            if (courseForThisSlot != null && startPeriod == courseForThisSlot.startPeriod) {
                // 在课程的起始节次添加卡片
                val courseCard = CourseCardView(this)
                courseCard.setCourse(courseForThisSlot)
                
                // 计算课程跨越的节次数
                val span = courseForThisSlot.endPeriod - courseForThisSlot.startPeriod + 1
                
                // 设置卡片布局参数
                courseCard.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(70 * span + 10 * (span - 1)) // 卡片高度
                ).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                }
                
                // 设置是否为跨越多个节次的卡片
                courseCard.setIsSpanning(span > 1)
                
                // 添加点击事件
                courseCard.setOnClickListener {
                    showCourseDetailDialog(courseForThisSlot)
                }
                
                cellLayout.addView(courseCard)
                
                // 启动入场动画
                val delay = ((day - 1) * 50L + (courseForThisSlot.startPeriod - 1) * 30L)
                courseCard.startEntranceAnimation(delay)
            } else if (courseForThisSlot == null) {
                // 无课程时段
                val noCourseText = TextView(this).apply {
                    text = if (spanCount == 1) "无课程" else ""
                    textSize = 12f
                    setTextColor(android.graphics.Color.GRAY)
                    gravity = android.view.Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                cellLayout.addView(noCourseText)
            }
            // 如果courseForThisSlot != null 但 startPeriod != courseForThisSlot.startPeriod，
            // 说明这个位置在课程卡片内，不显示任何内容
            
            rowLayout.addView(cellLayout)
        }
        
        return rowLayout
    }
    
    private fun highlightCurrentCourse() {
        // 获取当前时间
        val now = java.time.LocalTime.now()
        val currentPeriod = getCurrentPeriod(now)
        
        // 如果在课程时间范围内，则高亮显示
        if (currentPeriod > 0) {
            // TODO: 实现具体的高亮逻辑
        }
    }
    
    private fun scrollToCurrentTime() {
        // 获取当前时间
        val now = java.time.LocalTime.now()
        val currentPeriod = getCurrentPeriod(now)
            
        // 如果在课程时间范围内，则滚动到对应位置
        if (currentPeriod > 0) {
            // 计算滚动位置
            val scrollY = dpToPx(80 * (currentPeriod - 1))
            scrollView.post {
                scrollView.smoothScrollTo(0, scrollY)
            }
        }
    }
    
    private fun getCurrentWeek(): Int {
        // 获取当前周数（模拟）
        return 1
    }
    
    // 添加 FloatingActionButton 用于添加课程
    private fun addFloatingActionButton() {
        // 在实际应用中，您可能需要在布局中添加 FAB
        // 这里我们简化处理，通过菜单或其他方式触发
    }
    
    // 显示添加课程对话框
    private fun showAddCourseDialog() {
        val dialog = AddEditCourseDialog(this) { newCourse ->
            // 添加新课程到列表
            courseList.add(newCourse)
            // 重新加载当前周的课程
            loadScheduleForWeek(currentWeek)
        }
        dialog.show()
    }
    
    // 显示编辑课程对话框
    private fun showEditCourseDialog(course: Course) {
        val dialog = AddEditCourseDialog(this, course) { updatedCourse ->
            // 更新课程信息
            val index = courseList.indexOfFirst { it.id == updatedCourse.id }
            if (index != -1) {
                courseList[index] = updatedCourse
                // 重新加载当前周的课程
                loadScheduleForWeek(currentWeek)
            }
        }
        dialog.show()
    }
    
    // 删除课程
    private fun deleteCourse(course: Course) {
        courseList.remove(course)
        // 重新加载当前周的课程
        loadScheduleForWeek(currentWeek)
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
    
    private fun showCourseDetailDialog(course: Course) {
        val dialog = CourseDetailDialog(this, course)
        dialog.show()
    }
}