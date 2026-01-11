package com.example.campus_schedule_buddy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Button
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.campus_schedule_buddy.model.Course
import com.example.campus_schedule_buddy.data.AppDatabase
import com.example.campus_schedule_buddy.data.CourseRepository
import com.example.campus_schedule_buddy.data.CourseTypeReminderEntity
import com.example.campus_schedule_buddy.data.PeriodTimeEntity
import com.example.campus_schedule_buddy.data.ReminderSettingsEntity
import com.example.campus_schedule_buddy.data.SettingsRepository
import com.example.campus_schedule_buddy.data.SemesterEntity
import com.example.campus_schedule_buddy.view.CourseCardView
import com.example.campus_schedule_buddy.view.CourseDetailDialog
import com.example.campus_schedule_buddy.view.AddEditCourseDialog
import android.os.Handler
import android.os.Looper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.enableEdgeToEdge
import com.example.campus_schedule_buddy.reminder.ReminderScheduler
import android.view.GestureDetector
import android.view.MotionEvent
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.example.campus_schedule_buddy.util.CourseExcelImporter
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.database.Cursor
import android.provider.OpenableColumns

class MainActivity : AppCompatActivity() {

    private lateinit var tvCurrentWeek: TextView
    private lateinit var btnPreviousWeek: ImageButton
    private lateinit var btnNextWeek: ImageButton
    private lateinit var btnMore: ImageButton
    private lateinit var scheduleContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var fabAddCourse: FloatingActionButton
    private lateinit var weekHeader: LinearLayout
    private lateinit var timeHeader: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var titleDate: TextView
    private lateinit var titleWeekDay: TextView
    private lateinit var weekDateLabels: List<TextView>
    private lateinit var weekDayLabels: List<TextView>
    private lateinit var dayColumnLayouts: List<LinearLayout>
    private lateinit var gestureDetector: GestureDetector
    private var lastRenderKey = 0
    private var renderPending = false
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            handleImportUri(uri)
        }
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            handleExportUri(uri)
        }
    }

    private var currentWeek = 1
    private val totalWeeks = 20

    // 用于存储课程数据
    private val courseList = mutableListOf<Course>()
    private lateinit var repository: CourseRepository
    private lateinit var settingsRepository: SettingsRepository
    private val reminderScheduler by lazy { ReminderScheduler(this) }
    private var periodTimes: List<PeriodTimeEntity> = emptyList()
    private var semesterStartDate: LocalDate = LocalDate.now()
    private var reminderSettings: ReminderSettingsEntity? = null
    private var typeReminders: List<CourseTypeReminderEntity> = emptyList()
    private var currentSemesterId: Long = 0L
    private var semesters: List<SemesterEntity> = emptyList()
    private var selectedDayOfWeek: Int = LocalDate.now().dayOfWeek.value
    private var viewMode: ViewMode = ViewMode.WEEK

    private enum class ViewMode {
        DAY,
        WEEK,
        MONTH
    }

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

        // 请求通知权限(适用于Android 13+)
        requestNotificationPermission()

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
        btnMore = findViewById(R.id.btn_more)
        scheduleContainer = findViewById(R.id.schedule_container)
        scrollView = findViewById(R.id.scrollView)
        fabAddCourse = findViewById(R.id.fab_add_course)
        weekHeader = findViewById(R.id.week_header)
        timeHeader = findViewById(R.id.tv_time_header)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        titleDate = findViewById(R.id.tv_title_date)
        titleWeekDay = findViewById(R.id.tv_title_week_day)
        weekDateLabels = listOf(
            findViewById(R.id.tv_date_mon),
            findViewById(R.id.tv_date_tue),
            findViewById(R.id.tv_date_wed),
            findViewById(R.id.tv_date_thu),
            findViewById(R.id.tv_date_fri),
            findViewById(R.id.tv_date_sat),
            findViewById(R.id.tv_date_sun)
        )
        weekDayLabels = listOf(
            findViewById(R.id.tv_day_mon),
            findViewById(R.id.tv_day_tue),
            findViewById(R.id.tv_day_wed),
            findViewById(R.id.tv_day_thu),
            findViewById(R.id.tv_day_fri),
            findViewById(R.id.tv_day_sat),
            findViewById(R.id.tv_day_sun)
        )
        dayColumnLayouts = listOf(
            findViewById(R.id.layout_day_mon),
            findViewById(R.id.layout_day_tue),
            findViewById(R.id.layout_day_wed),
            findViewById(R.id.layout_day_thu),
            findViewById(R.id.layout_day_fri),
            findViewById(R.id.layout_day_sat),
            findViewById(R.id.layout_day_sun)
        )
        updateWeekHeaderVisibility()
        updateWeekHeaderLayout()
        setupGestureDetector()
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

        btnMore.setOnClickListener {
            showMoreMenu()
        }

        dayColumnLayouts.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                selectedDayOfWeek = index + 1
                updateSelectedDayHighlight()
                if (viewMode == ViewMode.DAY) {
                    updateWeekHeaderVisibility()
                    updateWeekHeaderLayout()
                    requestRender()
                }
            }
        }

        fabAddCourse.setOnClickListener {
            showAddCourseDialog()
        }

        scrollView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_profile -> true
                else -> true
            }
        }
    }

    private fun updateWeekDisplay() {
        tvCurrentWeek.text = "第${currentWeek}周"
        updateWeekHeaderDates()
        updateTitleInfo()
        updateSelectedDayHighlight()

        // 更新按钮状态
        btnPreviousWeek.isEnabled = currentWeek > 1
        btnNextWeek.isEnabled = currentWeek < totalWeeks
    }

    private fun loadScheduleForWeek(week: Int) {
        // 清空现有的课程视图
        scheduleContainer.removeAllViews()

        // 设置背景色
        updateBackgroundColor()

        when (viewMode) {
            ViewMode.MONTH -> renderMonthView(week)
            ViewMode.DAY -> renderDayView(week)
            ViewMode.WEEK -> renderWeekView(week)
        }
    }

    private fun renderWeekView(week: Int) {
        val courses = courseList.filter { it.weekPattern.contains(week) }
        val scheduleFrameLayout = buildScheduleFrameLayout()
        addGridBackground(scheduleFrameLayout, dayCount = 7)
        courses.forEach { course ->
            addCourseCard(scheduleFrameLayout, course, dayIndexInView = course.dayOfWeek - 1, dayCount = 7)
        }
        scheduleContainer.addView(scheduleFrameLayout)
        highlightCurrentCourse()
    }

    private fun renderDayView(week: Int) {
        val courses = courseList.filter {
            it.weekPattern.contains(week) && it.dayOfWeek == selectedDayOfWeek
        }
        val scheduleFrameLayout = buildScheduleFrameLayout()
        addGridBackground(scheduleFrameLayout, dayCount = 1)
        courses.forEach { course ->
            addCourseCard(scheduleFrameLayout, course, dayIndexInView = 0, dayCount = 1)
        }
        scheduleContainer.addView(scheduleFrameLayout)
        highlightCurrentCourse()
    }

    private fun renderMonthView(week: Int) {
        val weekStart = getWeekStartDate(week)
        val monthStart = weekStart.withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        val listContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }
        var date = monthStart
        while (!date.isAfter(monthEnd)) {
            val dayView = layoutInflater.inflate(R.layout.item_month_day, listContainer, false)
            val dayLabel = dayView.findViewById<TextView>(R.id.tv_month_day)
            val summaryLabel = dayView.findViewById<TextView>(R.id.tv_month_summary)
            val weekIndex = getWeekIndexForDate(date)
            val courses = if (weekIndex > 0) {
                courseList.filter { course ->
                    course.dayOfWeek == date.dayOfWeek.value && course.weekPattern.contains(weekIndex)
                }
            } else {
                emptyList()
            }
            dayLabel.text = "${date.monthValue}/${date.dayOfMonth} ${formatWeekday(date.dayOfWeek.value)}"
            summaryLabel.text = formatMonthSummary(courses)
            listContainer.addView(dayView)
            date = date.plusDays(1)
        }
        scheduleContainer.addView(listContainer)
    }

    private fun buildScheduleFrameLayout(): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                getPeriodRowHeight() * 8
            )
            clipChildren = false
            clipToPadding = false
        }
    }

    /**
     * 根据系统主题更新背景色
     */
    private fun updateBackgroundColor() {
        val backgroundColor = ContextCompat.getColor(this, R.color.background)
        scheduleContainer.setBackgroundColor(backgroundColor)
    }

    /**
     * 添加网格背景（时间列和分隔线）
     */
    private fun addGridBackground(container: FrameLayout, dayCount: Int) {
        container.post {
            val rowHeight = getPeriodRowHeight()
            val metrics = calculateGridMetrics(container.width, dayCount)
            val timeColumnWidth = metrics.timeColumnWidth
            val lineColor = ContextCompat.getColor(this, R.color.grid_line)
            val timeTextColor = ContextCompat.getColor(this, R.color.text_secondary)

            val timeMap = periodTimes.associate { it.period to it }
            val fallbackTimes = mapOf(
                1 to Pair("08:00", "08:45"),
                2 to Pair("08:55", "09:40"),
                3 to Pair("10:00", "10:45"),
                4 to Pair("10:55", "11:40"),
                5 to Pair("14:00", "14:45"),
                6 to Pair("14:55", "15:40"),
                7 to Pair("16:00", "16:45"),
                8 to Pair("16:55", "17:40")
            )

            // 为每个节次创建时间标签
            for (period in 1..8) {
                val periodTime = timeMap[period]
                val (startTime, endTime) = if (periodTime != null) {
                    periodTime.startTime to periodTime.endTime
                } else {
                    fallbackTimes[period] ?: ("" to "")
                }
                val timeLabel = TextView(this).apply {
                    text = "第${period}节\n$startTime\n$endTime"
                    textSize = 10f
                    setLineSpacing(0f, 1.1f)
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
            for (i in 0..dayCount) {
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
            val dayCount = if (viewMode == ViewMode.DAY) 1 else 7
            val metrics = calculateGridMetrics(weekHeader.width, dayCount)
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

    private fun updateWeekHeaderDates() {
        val weekStart = getWeekStartDate(currentWeek)
        weekDateLabels.forEachIndexed { index, textView ->
            val date = weekStart.plusDays(index.toLong())
            textView.text = "${date.monthValue}/${date.dayOfMonth}"
        }
    }

    private fun updateWeekHeaderVisibility() {
        weekHeader.visibility = if (viewMode == ViewMode.MONTH) View.GONE else View.VISIBLE
        dayColumnLayouts.forEachIndexed { index, layout ->
            val shouldShow = viewMode != ViewMode.DAY || index + 1 == selectedDayOfWeek
            layout.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }
    }

    private fun updateSelectedDayHighlight() {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val dayColor = ContextCompat.getColor(this, R.color.text_primary)
        val dateColor = ContextCompat.getColor(this, R.color.text_secondary)
        weekDayLabels.forEachIndexed { index, label ->
            val selected = viewMode == ViewMode.DAY && index + 1 == selectedDayOfWeek
            label.setTextColor(if (selected) primary else dayColor)
            weekDateLabels[index].setTextColor(if (selected) primary else dateColor)
        }
    }

    private fun updateTitleInfo() {
        val today = LocalDate.now()
        val todayDayIndex = today.dayOfWeek.value
        val weekDayName = when (todayDayIndex) {
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            else -> "星期日"
        }
        titleDate.text = "${today.year}年${today.monthValue}月${today.dayOfMonth}日"
        titleWeekDay.text = "第${getCurrentWeek()}周 $weekDayName"
    }

    private fun showMoreMenu() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_main_menu, null)
        val toggleGroup = sheetView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_view_mode_sheet)
        val btnDay = sheetView.findViewById<MaterialButton>(R.id.btn_view_day_sheet)
        val btnWeek = sheetView.findViewById<MaterialButton>(R.id.btn_view_week_sheet)
        val btnMonth = sheetView.findViewById<MaterialButton>(R.id.btn_view_month_sheet)
        val spinner = sheetView.findViewById<Spinner>(R.id.spinner_semester_sheet)
        val addSemesterButton = sheetView.findViewById<ImageButton>(R.id.btn_add_semester_sheet)
        val importButton = sheetView.findViewById<Button>(R.id.btn_import_sheet)
        val exportButton = sheetView.findViewById<Button>(R.id.btn_export_sheet)
        val settingsButton = sheetView.findViewById<Button>(R.id.btn_open_settings_sheet)

        val checkedId = when (viewMode) {
            ViewMode.DAY -> btnDay.id
            ViewMode.MONTH -> btnMonth.id
            ViewMode.WEEK -> btnWeek.id
        }
        toggleGroup.check(checkedId)
        toggleGroup.addOnButtonCheckedListener { _, checkedIdNew, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedIdNew) {
                btnDay.id -> ViewMode.DAY
                btnMonth.id -> ViewMode.MONTH
                else -> ViewMode.WEEK
            }
            if (viewMode != newMode) {
                viewMode = newMode
                updateWeekHeaderVisibility()
                updateWeekHeaderLayout()
                updateSelectedDayHighlight()
                requestRender()
            }
            dialog.dismiss()
        }

        val names = if (semesters.isEmpty()) listOf("暂无学期") else semesters.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.isEnabled = semesters.isNotEmpty()
        val currentIndex = semesters.indexOfFirst { it.id == currentSemesterId }.coerceAtLeast(0)
        var skipSelection = true
        if (currentIndex >= 0 && semesters.isNotEmpty()) {
            spinner.setSelection(currentIndex, false)
        }
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (skipSelection) {
                    skipSelection = false
                    return
                }
                val selected = semesters.getOrNull(position) ?: return
                if (!::settingsRepository.isInitialized) return
                if (selected.id != currentSemesterId) {
                    lifecycleScope.launch {
                        settingsRepository.setCurrentSemester(selected.id)
                    }
                }
                dialog.dismiss()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        addSemesterButton.setOnClickListener {
            dialog.dismiss()
            showCreateSemesterDialog()
        }

        importButton.setOnClickListener {
            dialog.dismiss()
            launchImportPicker()
        }

        exportButton.setOnClickListener {
            dialog.dismiss()
            launchExportPicker()
        }

        settingsButton.setOnClickListener {
            dialog.dismiss()
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun showCreateSemesterDialog() {
        if (!::settingsRepository.isInitialized) {
            Toast.makeText(this, "学期数据尚未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }
        val nameInput = EditText(this).apply {
            hint = "学期名称"
        }
        val dateLabel = TextView(this).apply {
            text = "开始日期：${LocalDate.now()}"
        }
        val pickDateButton = Button(this).apply {
            text = "选择日期"
        }
        var selectedDate = LocalDate.now()
        pickDateButton.setOnClickListener {
            val today = LocalDate.now()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDate = LocalDate.of(year, month + 1, day)
                    dateLabel.text = "开始日期：$selectedDate"
                },
                today.year,
                today.monthValue - 1,
                today.dayOfMonth
            ).show()
        }
        container.addView(nameInput)
        container.addView(dateLabel)
        container.addView(pickDateButton)

        AlertDialog.Builder(this)
            .setTitle("新增学期")
            .setView(container)
            .setPositiveButton("创建") { _, _ ->
                val name = nameInput.text.toString().trim()
                val finalName = if (name.isBlank()) {
                    "学期${selectedDate.year}"
                } else {
                    name
                }
                lifecycleScope.launch {
                    settingsRepository.createSemester(finalName, selectedDate)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && kotlin.math.abs(diffX) > dpToPx(64)) {
                    if (diffX > 0) {
                        handleHorizontalSwipe(-1)
                    } else {
                        handleHorizontalSwipe(1)
                    }
                    return true
                }
                return false
            }
        })
    }

    private fun handleHorizontalSwipe(direction: Int) {
        when (viewMode) {
            ViewMode.DAY -> changeDayBy(direction)
            ViewMode.MONTH -> changeMonthBy(direction)
            ViewMode.WEEK -> changeWeekBy(direction)
        }
    }

    private fun changeWeekBy(delta: Int) {
        val newWeek = (currentWeek + delta).coerceIn(1, totalWeeks)
        if (newWeek == currentWeek) return
        currentWeek = newWeek
        updateWeekDisplay()
        loadScheduleForWeek(currentWeek)
    }

    private fun changeDayBy(delta: Int) {
        var newDay = selectedDayOfWeek + delta
        var newWeek = currentWeek
        if (newDay < 1) {
            newDay = 7
            newWeek -= 1
        } else if (newDay > 7) {
            newDay = 1
            newWeek += 1
        }
        if (newWeek !in 1..totalWeeks) return
        if (newWeek == currentWeek && newDay == selectedDayOfWeek) return
        currentWeek = newWeek
        selectedDayOfWeek = newDay
        updateWeekHeaderVisibility()
        updateWeekDisplay()
        loadScheduleForWeek(currentWeek)
    }

    private fun changeMonthBy(delta: Int) {
        val weekStart = getWeekStartDate(currentWeek)
        val monthStart = weekStart.withDayOfMonth(1)
        val targetMonthStart = monthStart.plusMonths(delta.toLong())
        val weekIndex = getWeekIndexForDate(targetMonthStart)
        if (weekIndex !in 1..totalWeeks) return
        if (weekIndex == currentWeek) return
        currentWeek = weekIndex
        updateWeekDisplay()
        loadScheduleForWeek(currentWeek)
    }

    /**
     * 添加课程卡片（绝对定位，支持跨节课程）
     */
    private fun addCourseCard(
        container: FrameLayout,
        course: Course,
        dayIndexInView: Int,
        dayCount: Int
    ) {
        val rowHeight = getPeriodRowHeight()
        
        // 等待容器测量完成后再计算位置
        container.post {
            val containerWidth = container.width
            if (containerWidth <= 0) return@post

            val metrics = calculateGridMetrics(containerWidth, dayCount)
            
            // 计算课程卡片的位置和尺寸
            val span = course.endPeriod - course.startPeriod + 1
            val cardLeft = metrics.innerPadding + metrics.timeColumnWidth +
                dayIndexInView * metrics.dayColumnWidth + metrics.columnGap
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
        val start = semesterStartDate
        val today = LocalDate.now()
        val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(start, today)
        return ((daysDiff / 7).toInt() + 1).coerceAtLeast(1)
    }

    private fun getWeekIndexForDate(date: LocalDate): Int {
        val daysDiff = ChronoUnit.DAYS.between(semesterStartDate, date)
        if (daysDiff < 0) return -1
        return (daysDiff / 7).toInt() + 1
    }

    private fun getWeekStartDate(week: Int): LocalDate {
        return semesterStartDate.plusDays(((week - 1) * 7).toLong())
    }

    private fun formatWeekday(dayIndex: Int): String {
        return when (dayIndex) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }

    private fun formatMonthSummary(courses: List<Course>): String {
        if (courses.isEmpty()) return "无课程"
        val names = courses.map { it.name }.distinct()
        val preview = names.take(2).joinToString("、")
        val extra = if (names.size > 2) " 等${names.size}门" else ""
        return "课程：$preview$extra"
    }

    private fun launchImportPicker() {
        importLauncher.launch(
            arrayOf(
                "application/vnd.ms-excel",
                "application/octet-stream",
                "application/json",
                "text/json"
            )
        )
    }

    private fun handleImportUri(uri: Uri) {
        val fileName = resolveFileName(uri)
        val lowerName = fileName?.lowercase().orEmpty()
        val mimeType = contentResolver.getType(uri).orEmpty()
        val isJson = lowerName.endsWith(".json") || mimeType.contains("json")
        val isXls = lowerName.endsWith(".xls") || mimeType.contains("ms-excel")
        if (!isJson && !isXls) {
            Toast.makeText(this, "请选择.xls或.json格式的课表文件", Toast.LENGTH_SHORT).show()
            return
        }
        if (isJson) {
            importFromJson(uri)
        } else {
            importFromExcel(uri)
        }
    }

    private fun launchExportPicker() {
        val name = "课表_${LocalDate.now()}.json"
        exportLauncher.launch(name)
    }

    private fun handleExportUri(uri: Uri) {
        val semester = semesters.firstOrNull { it.id == currentSemesterId }
        val json = com.example.campus_schedule_buddy.util.CourseJsonSerializer.toJson(
            semester = semester,
            periodTimes = periodTimes,
            reminderSettings = reminderSettings,
            courseTypeReminders = typeReminders,
            courses = courseList
        )
        lifecycleScope.launch(Dispatchers.IO) {
            val success = contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
                true
            } ?: false
            withContext(Dispatchers.Main) {
                val message = if (success) "导出完成" else "导出失败"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importFromExcel(uri: Uri) {
        if (currentSemesterId <= 0L) {
            Toast.makeText(this, "当前学期未就绪，稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val progressDialog = showImportProgress()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    CourseExcelImporter.importFromUri(contentResolver, uri, currentSemesterId)
                }
                progressDialog.dismiss()
                if (result.courses.isEmpty()) {
                    throw IllegalStateException("未解析到课程数据，请检查课表格式")
                }
                confirmAndImportCourses(
                    semesterId = currentSemesterId,
                    courses = result.courses,
                    skippedCount = result.skippedCount
                )
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(
                    this@MainActivity,
                    "导入失败：${e.message ?: "未知错误"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun importFromJson(uri: Uri) {
        if (currentSemesterId <= 0L) {
            Toast.makeText(this, "当前学期未就绪，稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val progressDialog = showImportProgress()
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: ""
                }
                val payload = com.example.campus_schedule_buddy.util.CourseJsonSerializer.fromJson(json, currentSemesterId)
                progressDialog.dismiss()
                if (payload.courses.isEmpty()) {
                    throw IllegalStateException("未解析到课程数据，请检查导出文件")
                }
                val semesterName = payload.semesterName
                val semesterStart = payload.semesterStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (semesterName != null || semesterStart != null) {
                    showJsonImportTargetDialog(payload, semesterName, semesterStart)
                } else {
                    importJsonPayloadToSemester(payload, currentSemesterId)
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(
                    this@MainActivity,
                    "导入失败：${e.message ?: "未知错误"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showJsonImportTargetDialog(
        payload: com.example.campus_schedule_buddy.util.CourseJsonPayload,
        semesterName: String?,
        semesterStart: LocalDate?
    ) {
        val displayName = semesterName ?: "导入学期"
        val displayDate = semesterStart?.toString() ?: "未提供"
        AlertDialog.Builder(this)
            .setTitle("导入课表")
            .setMessage("检测到学期信息：$displayName（$displayDate）")
            .setPositiveButton("导入为新学期") { _, _ ->
                lifecycleScope.launch {
                    val name = semesterName ?: "导入学期"
                    val startDate = semesterStart ?: LocalDate.now()
                    val semesterId = settingsRepository.createSemester(name, startDate)
                    importJsonPayloadToSemester(payload, semesterId)
                }
            }
            .setNegativeButton("覆盖当前学期") { _, _ ->
                importJsonPayloadToSemester(payload, currentSemesterId)
            }
            .setNeutralButton("取消", null)
            .show()
    }

    private fun importJsonPayloadToSemester(
        payload: com.example.campus_schedule_buddy.util.CourseJsonPayload,
        semesterId: Long
    ) {
        val courses = payload.courses.map { it.copy(semesterId = semesterId) }
        val periodUpdates = payload.periodTimes.map { it.copy(semesterId = semesterId) }
        val reminderUpdate = payload.reminderSettings?.copy(semesterId = semesterId)
        val typeUpdates = payload.courseTypeReminders.map { it.copy(semesterId = semesterId) }
        val startDate = payload.semesterStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        confirmAndImportCourses(
            semesterId = semesterId,
            courses = courses,
            skippedCount = 0
        ) {
            if (semesterId != currentSemesterId) {
                settingsRepository.setCurrentSemester(semesterId)
            }
            if (periodUpdates.isNotEmpty()) {
                settingsRepository.updatePeriodTimes(periodUpdates)
            }
            if (reminderUpdate != null) {
                settingsRepository.updateReminderSettings(reminderUpdate)
            }
            if (typeUpdates.isNotEmpty()) {
                settingsRepository.updateCourseTypeReminders(typeUpdates)
            }
            if (startDate != null) {
                settingsRepository.updateSemesterStart(startDate)
            }
        }
    }

    private data class ConflictFilterResult(
        val courses: List<Course>,
        val conflictCount: Int,
        val duplicateCount: Int
    )

    private fun filterConflicts(courses: List<Course>): ConflictFilterResult {
        val accepted = mutableListOf<Course>()
        val signatures = mutableSetOf<String>()
        var conflictCount = 0
        var duplicateCount = 0
        courses.forEach { course ->
            val signature = buildString {
                append(course.name)
                append('|')
                append(course.dayOfWeek)
                append('|')
                append(course.startPeriod)
                append('|')
                append(course.endPeriod)
                append('|')
                append(course.weekPattern.joinToString(","))
            }
            if (!signatures.add(signature)) {
                duplicateCount += 1
                return@forEach
            }
            val conflict = accepted.any { existing ->
                existing.dayOfWeek == course.dayOfWeek &&
                    existing.weekPattern.any { it in course.weekPattern } &&
                    existing.startPeriod <= course.endPeriod &&
                    existing.endPeriod >= course.startPeriod
            }
            if (conflict) {
                conflictCount += 1
            } else {
                accepted.add(course)
            }
        }
        return ConflictFilterResult(accepted, conflictCount, duplicateCount)
    }

    private fun confirmAndImportCourses(
        semesterId: Long,
        courses: List<Course>,
        skippedCount: Int,
        onBeforeReplace: (suspend () -> Unit)? = null
    ) {
        val filtered = filterConflicts(courses)
        if (filtered.conflictCount > 0 || filtered.duplicateCount > 0) {
            val message = "检测到${filtered.conflictCount}条时间冲突、${filtered.duplicateCount}条重复课程，将自动跳过冲突项。"
            AlertDialog.Builder(this)
                .setTitle("导入冲突提示")
                .setMessage(message)
                .setPositiveButton("继续导入") { _, _ ->
                    applyImportCourses(semesterId, filtered, skippedCount, onBeforeReplace)
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            applyImportCourses(semesterId, filtered, skippedCount, onBeforeReplace)
        }
    }

    private fun applyImportCourses(
        semesterId: Long,
        filtered: ConflictFilterResult,
        skippedCount: Int,
        onBeforeReplace: (suspend () -> Unit)? = null
    ) {
        val progressDialog = showImportProgress()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    onBeforeReplace?.invoke()
                    repository.replaceAll(semesterId, filtered.courses)
                }
                progressDialog.dismiss()
                val message = "导入完成：成功${filtered.courses.size}条，跳过${skippedCount + filtered.conflictCount + filtered.duplicateCount}条"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(
                    this@MainActivity,
                    "导入失败：${e.message ?: "未知错误"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        if (uri.scheme != "content") {
            return uri.lastPathSegment
        }
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return it.getString(nameIndex)
                }
            }
        }
        return null
    }

    private fun showImportProgress(): AlertDialog {
        return AlertDialog.Builder(this)
            .setTitle("导入课表")
            .setMessage("正在导入，请稍候...")
            .setView(android.widget.ProgressBar(this))
            .setCancelable(false)
            .show()
    }

    private fun getCurrentPeriod(time: java.time.LocalTime): Int {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        if (periodTimes.isEmpty()) {
            return -1
        }
        periodTimes.forEach { period ->
            val start = LocalTime.parse(period.startTime, formatter)
            val end = LocalTime.parse(period.endTime, formatter)
            if (!time.isBefore(start) && time.isBefore(end)) {
                return period.period
            }
        }
        return -1
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

    private fun calculateGridMetrics(containerWidth: Int, dayCount: Int): GridMetrics {
        val innerPadding = dpToPx(4)
        val minTimeWidth = dpToPx(42)
        val maxTimeWidth = dpToPx(56)
        val availableWidth = (containerWidth - innerPadding * 2).coerceAtLeast(0)
        val timeColumnWidth = (availableWidth * 0.13f).toInt().coerceIn(minTimeWidth, maxTimeWidth)
        val safeDayCount = dayCount.coerceAtLeast(1)
        val dayColumnWidth = ((availableWidth - timeColumnWidth) / safeDayCount.toFloat())
            .toInt()
            .coerceAtLeast(dpToPx(36))
        val columnGap = dpToPx(2)
        return GridMetrics(timeColumnWidth, dayColumnWidth, columnGap, innerPadding)
    }

    private fun setupRepository() {
        val database = AppDatabase.getInstance(this)
        repository = CourseRepository(database.courseDao())
        settingsRepository = SettingsRepository(database.settingsDao(), database.semesterDao())
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                settingsRepository.ensureDefaults()
            }
            settingsRepository.currentSemesterId
                .flatMapLatest { semesterId ->
                    currentSemesterId = semesterId
                    repository.observeCourses(semesterId)
                }
                .collect { courses ->
                    courseList.clear()
                    courseList.addAll(courses)
                    requestRender()
                    val reminderConfig = reminderSettings
                    if (reminderConfig != null && periodTimes.isNotEmpty()) {
                        withContext(Dispatchers.Default) {
                            reminderScheduler.scheduleUpcomingReminders(
                                courses = courseList,
                                periodTimes = periodTimes,
                                semesterStartDate = semesterStartDate,
                                reminderSettings = reminderConfig,
                                typeReminders = typeReminders
                            )
                        }
                    }
                }
        }

        lifecycleScope.launch {
            combine(
                settingsRepository.periodTimes,
                settingsRepository.reminderSettings,
                settingsRepository.courseTypeReminders,
                settingsRepository.observeSemesterStartDate(),
                settingsRepository.currentSemesterId
            ) { periods, reminder, types, semester, semesterId ->
                periodTimes = periods
                reminderSettings = reminder
                typeReminders = types
                semesterStartDate = semester ?: LocalDate.now()
                currentSemesterId = semesterId
                currentWeek = getCurrentWeek()
                requestRender()
                reminder
            }.collect { reminder ->
                val reminderConfig = reminder ?: return@collect
                withContext(Dispatchers.Default) {
                    reminderScheduler.scheduleUpcomingReminders(
                        courses = courseList,
                        periodTimes = periodTimes,
                        semesterStartDate = semesterStartDate,
                        reminderSettings = reminderConfig,
                        typeReminders = typeReminders
                    )
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.semesters.collect { items ->
                semesters = items
            }
        }
    }

    private fun requestRender() {
        if (renderPending) return
        renderPending = true
        scheduleContainer.post {
            renderPending = false
            val key = computeRenderKey()
            if (key == lastRenderKey) return@post
            lastRenderKey = key
            updateWeekDisplay()
            loadScheduleForWeek(currentWeek)
        }
    }

    private fun computeRenderKey(): Int {
        var result = currentWeek
        result = 31 * result + courseList.hashCode()
        result = 31 * result + periodTimes.hashCode()
        result = 31 * result + semesterStartDate.hashCode()
        result = 31 * result + viewMode.ordinal
        result = 31 * result + selectedDayOfWeek
        result = 31 * result + currentSemesterId.hashCode()
        return result
    }

    private fun showAddCourseDialog() {
        if (currentSemesterId <= 0L) {
            Toast.makeText(this, "当前学期未就绪，稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = AddEditCourseDialog(this, null, currentSemesterId) { course, callback ->
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
        val dialog = AddEditCourseDialog(this, course, currentSemesterId) { updated, callback ->
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

    /**
     * 请求通知权限(Android 13+)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
