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
import com.example.campus_schedule_buddy.view.CourseCardView
import com.example.campus_schedule_buddy.view.CourseDetailDialog
import com.example.campus_schedule_buddy.view.AddEditCourseDialog
import android.os.Handler
import android.os.Looper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.enableEdgeToEdge
import com.example.campus_schedule_buddy.reminder.ReminderScheduler
import android.view.GestureDetector
import android.view.MotionEvent
import android.app.AlertDialog
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.example.campus_schedule_buddy.util.CourseExcelImporter
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.database.Cursor
import android.provider.OpenableColumns

class MainActivity : AppCompatActivity() {

    private lateinit var tvCurrentWeek: TextView
    private lateinit var btnPreviousWeek: ImageButton
    private lateinit var btnNextWeek: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnImport: ImageButton
    private lateinit var scheduleContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var fabAddCourse: FloatingActionButton
    private lateinit var weekHeader: LinearLayout
    private lateinit var timeHeader: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var titleDate: TextView
    private lateinit var titleWeekDay: TextView
    private lateinit var weekDateLabels: List<TextView>
    private lateinit var gestureDetector: GestureDetector
    private var lastRenderKey = 0
    private var renderPending = false
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            handleImportUri(uri)
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
        btnSettings = findViewById(R.id.btn_settings)
        btnImport = findViewById(R.id.btn_import)
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

        btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        btnImport.setOnClickListener {
            launchImportPicker()
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
        val backgroundColor = ContextCompat.getColor(this, R.color.background)
        scheduleContainer.setBackgroundColor(backgroundColor)
    }

    /**
     * 添加网格背景（时间列和分隔线）
     */
    private fun addGridBackground(container: FrameLayout) {
        container.post {
            val rowHeight = getPeriodRowHeight()
            val metrics = calculateGridMetrics(container.width)
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

    private fun updateWeekHeaderDates() {
        val weekStart = getWeekStartDate(currentWeek)
        weekDateLabels.forEachIndexed { index, textView ->
            val date = weekStart.plusDays(index.toLong())
            textView.text = "${date.monthValue}/${date.dayOfMonth}"
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
                        changeWeekBy(-1)
                    } else {
                        changeWeekBy(1)
                    }
                    return true
                }
                return false
            }
        })
    }

    private fun changeWeekBy(delta: Int) {
        val newWeek = (currentWeek + delta).coerceIn(1, totalWeeks)
        if (newWeek == currentWeek) return
        currentWeek = newWeek
        updateWeekDisplay()
        loadScheduleForWeek(currentWeek)
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
        val start = semesterStartDate
        val today = LocalDate.now()
        val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(start, today)
        return ((daysDiff / 7).toInt() + 1).coerceAtLeast(1)
    }

    private fun getWeekStartDate(week: Int): LocalDate {
        return semesterStartDate.plusDays(((week - 1) * 7).toLong())
    }

    private fun launchImportPicker() {
        importLauncher.launch(arrayOf("application/vnd.ms-excel", "application/octet-stream"))
    }

    private fun handleImportUri(uri: Uri) {
        val fileName = resolveFileName(uri)
        if (fileName != null && !fileName.lowercase().endsWith(".xls")) {
            Toast.makeText(this, "请选择.xls格式的课表文件", Toast.LENGTH_SHORT).show()
            return
        }
        val progressDialog = showImportProgress()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    CourseExcelImporter.importFromUri(contentResolver, uri)
                }
                if (result.courses.isEmpty()) {
                    throw IllegalStateException("未解析到课程数据，请检查课表格式")
                }
                withContext(Dispatchers.IO) {
                    repository.replaceAll(result.courses)
                }
                progressDialog.dismiss()
                val message = "导入完成：成功${result.courses.size}条，跳过${result.skippedCount}条"
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
        settingsRepository = SettingsRepository(database.settingsDao())
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                settingsRepository.ensureDefaults()
            }
            repository.coursesFlow.collect { courses ->
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
                settingsRepository.observeSemesterStartDate()
            ) { periods, reminder, types, semester ->
                periodTimes = periods
                reminderSettings = reminder
                typeReminders = types
                semesterStartDate = semester ?: LocalDate.now()
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
        return result
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
