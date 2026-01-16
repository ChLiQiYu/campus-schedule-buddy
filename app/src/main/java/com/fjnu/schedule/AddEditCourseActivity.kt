package com.fjnu.schedule

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.fjnu.schedule.data.AppDatabase
import com.fjnu.schedule.data.CourseRepository
import com.fjnu.schedule.data.SettingsRepository
import com.fjnu.schedule.model.Course
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddEditCourseActivity : AppCompatActivity() {
    private lateinit var repository: CourseRepository
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var etCourseName: AutoCompleteTextView
    private lateinit var spCourseType: Spinner
    private lateinit var etTeacher: AutoCompleteTextView
    private lateinit var etLocation: AutoCompleteTextView
    private lateinit var spDayOfWeek: Spinner
    private lateinit var spStartPeriod: Spinner
    private lateinit var spEndPeriod: Spinner
    private lateinit var spCourseColor: Spinner
    private lateinit var etWeeks: EditText
    private lateinit var etNote: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var colorPreview: View
    private lateinit var btnCustomColor: Button

    private var semesterId: Long = 0L
    private var editingCourse: Course? = null
    private var selectedWeeks: MutableSet<Int> = mutableSetOf()
    private var totalWeeks: Int = DEFAULT_TOTAL_WEEKS
    private var periodCount: Int = DEFAULT_PERIOD_COUNT

    private var colorValues: List<Int?> = emptyList()
    private var customColorValue: Int? = null
    private var isColorSpinnerUpdating = false
    private val customColorSentinel = Int.MIN_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_edit_course)

        val root = findViewById<LinearLayout>(R.id.add_edit_root)
        val initialTop = root.paddingTop
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + initialTop,
                view.paddingRight,
                systemBars.bottom + initialBottom
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_add_edit)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener { finish() }

        initViews()

        semesterId = intent.getLongExtra(EXTRA_SEMESTER_ID, 0L)
        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, 0L)
        toolbar.title = if (courseId > 0L) "编辑课程" else "新增课程"
        if (semesterId <= 0L) {
            Toast.makeText(this, "学期信息无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val database = AppDatabase.getInstance(this)
        repository = CourseRepository(database.courseDao())
        settingsRepository = SettingsRepository(database.settingsDao(), database.semesterDao())

        setupSpinners()
        setupColorOptions()
        setupListeners()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                settingsRepository.ensureDefaults()
            }
            loadSettingsAndSuggestions()
            if (courseId > 0L) {
                loadCourse(courseId)
            } else {
                applyDefaultValues()
            }
        }
    }

    private fun initViews() {
        etCourseName = findViewById(R.id.et_course_name)
        spCourseType = findViewById(R.id.sp_course_type)
        etTeacher = findViewById(R.id.et_teacher)
        etLocation = findViewById(R.id.et_location)
        spDayOfWeek = findViewById(R.id.sp_day_of_week)
        spStartPeriod = findViewById(R.id.sp_start_period)
        spEndPeriod = findViewById(R.id.sp_end_period)
        spCourseColor = findViewById(R.id.sp_course_color)
        colorPreview = findViewById(R.id.view_color_preview)
        btnCustomColor = findViewById(R.id.btn_custom_color)
        etWeeks = findViewById(R.id.et_weeks)
        etNote = findViewById(R.id.et_note)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
    }

    private fun setupSpinners() {
        val courseTypes = arrayOf("专业必修", "专业选修", "公共必修", "公共选修", "实验/实践", "体育/艺术")
        val courseTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courseTypes)
        courseTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCourseType.adapter = courseTypeAdapter

        val days = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val dayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spDayOfWeek.adapter = dayAdapter
    }

    private fun setupColorOptions() {
        val colorOptions = listOf(
            "按类型自动" to null,
            "蓝色" to ContextCompat.getColor(this, R.color.course_major_required),
            "紫色" to ContextCompat.getColor(this, R.color.course_major_elective),
            "粉色" to ContextCompat.getColor(this, R.color.course_public_required),
            "青绿" to ContextCompat.getColor(this, R.color.course_experiment),
            "天蓝" to ContextCompat.getColor(this, R.color.course_pe),
            "自定义..." to customColorSentinel
        )
        colorValues = colorOptions.map { it.second }
        val colorAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            colorOptions.map { it.first }
        )
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCourseColor.adapter = colorAdapter
    }

    private fun setupListeners() {
        btnSave.setOnClickListener { saveCourse() }
        btnCancel.setOnClickListener { finish() }
        etWeeks.setOnClickListener { showWeekPicker() }
        btnCustomColor.setOnClickListener { openCustomColorPicker() }

        spCourseColor.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (isColorSpinnerUpdating) return
                val selected = colorValues.getOrNull(position)
                if (selected == customColorSentinel) {
                    openCustomColorPicker()
                } else {
                    updateColorPreview(selected)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private suspend fun loadSettingsAndSuggestions() {
        val database = AppDatabase.getInstance(this)
        val courseDao = database.courseDao()
        val settingsDao = database.settingsDao()
        val periodTimes = settingsDao.getPeriodTimes(semesterId)
        val scheduleSettings = settingsDao.getScheduleSettings(semesterId)
        periodCount = if (periodTimes.isNotEmpty()) {
            periodTimes.maxOf { it.period }.coerceAtLeast(1)
        } else {
            scheduleSettings?.periodCount ?: DEFAULT_PERIOD_COUNT
        }
        totalWeeks = scheduleSettings?.totalWeeks ?: DEFAULT_TOTAL_WEEKS

        val names = courseDao.getCourseNames(semesterId)
        val teachers = courseDao.getTeachers(semesterId)
        val locations = courseDao.getLocations(semesterId)

        withContext(Dispatchers.Main) {
            setupPeriodSpinners(periodCount)
            setupAutoComplete(etCourseName, names)
            setupAutoComplete(etTeacher, teachers)
            setupAutoComplete(etLocation, locations)
        }
    }

    private fun setupAutoComplete(view: AutoCompleteTextView, items: List<String>) {
        if (items.isEmpty()) return
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items.distinct())
        view.setAdapter(adapter)
        view.threshold = 1
    }

    private fun setupPeriodSpinners(count: Int) {
        val periods = (1..count).map { "第${it}" }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spStartPeriod.adapter = adapter
        spEndPeriod.adapter = adapter
    }

    private suspend fun loadCourse(courseId: Long) {
        val database = AppDatabase.getInstance(this)
        val course = withContext(Dispatchers.IO) {
            database.courseDao().getCourse(courseId)
        }
        if (course == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AddEditCourseActivity, "课程不存在", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }
        editingCourse = course
        withContext(Dispatchers.Main) {
            bindCourse(course)
        }
    }

    private fun applyDefaultValues() {
        spDayOfWeek.setSelection(0)
        spStartPeriod.setSelection(0)
        spEndPeriod.setSelection(0)
        isColorSpinnerUpdating = true
        spCourseColor.setSelection(0)
        isColorSpinnerUpdating = false
        updateColorPreview(null)
        val defaultWeeks = (1..totalWeeks.coerceAtMost(16)).toSet()
        selectedWeeks = defaultWeeks.toMutableSet()
        updateWeeksText()
    }

    private fun bindCourse(course: Course) {
        etCourseName.setText(course.name)
        val courseTypeIndex = when (course.type) {
            "major_required" -> 0
            "major_elective" -> 1
            "public_required" -> 2
            "public_elective" -> 3
            "experiment" -> 4
            "pe" -> 5
            else -> 0
        }
        spCourseType.setSelection(courseTypeIndex)
        etTeacher.setText(course.teacher.orEmpty())
        etLocation.setText(course.location.orEmpty())
        spDayOfWeek.setSelection(course.dayOfWeek - 1)
        val maxIndex = (periodCount - 1).coerceAtLeast(0)
        spStartPeriod.setSelection((course.startPeriod - 1).coerceIn(0, maxIndex))
        spEndPeriod.setSelection((course.endPeriod - 1).coerceIn(0, maxIndex))
        isColorSpinnerUpdating = true
        val colorIndex = colorValues.indexOf(course.color)
        if (colorIndex >= 0) {
            spCourseColor.setSelection(colorIndex)
            updateColorPreview(course.color)
        } else if (course.color != null) {
            customColorValue = course.color
            val customIndex = colorValues.indexOf(customColorSentinel)
            spCourseColor.setSelection(customIndex)
            updateColorPreview(course.color)
        } else {
            spCourseColor.setSelection(0)
            updateColorPreview(null)
        }
        isColorSpinnerUpdating = false
        etNote.setText(course.note.orEmpty())
        selectedWeeks = course.weekPattern.toMutableSet()
        updateWeeksText()
    }

    private fun saveCourse() {
        val name = etCourseName.text.toString().trim()
        if (name.isEmpty()) {
            etCourseName.error = "请输入课程名称"
            return
        }

        if (selectedWeeks.isEmpty()) {
            Toast.makeText(this, "请选择周数", Toast.LENGTH_SHORT).show()
            return
        }

        val typeIndex = spCourseType.selectedItemPosition
        val type = when (typeIndex) {
            0 -> "major_required"
            1 -> "major_elective"
            2 -> "public_required"
            3 -> "public_elective"
            4 -> "experiment"
            5 -> "pe"
            else -> "major_required"
        }

        val teacher = etTeacher.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val dayOfWeek = spDayOfWeek.selectedItemPosition + 1
        val startPeriod = spStartPeriod.selectedItemPosition + 1
        val endPeriod = spEndPeriod.selectedItemPosition + 1
        if (startPeriod > endPeriod) {
            Toast.makeText(this, "开始节次不能晚于结束节次", Toast.LENGTH_SHORT).show()
            return
        }

        val note = etNote.text.toString().trim()
        val selectedColor = colorValues.getOrNull(spCourseColor.selectedItemPosition)
        if (selectedColor == customColorSentinel && customColorValue == null) {
            Toast.makeText(this, "请先选择自定义颜色", Toast.LENGTH_SHORT).show()
            return
        }
        val color = if (selectedColor == customColorSentinel) customColorValue else selectedColor

        val newCourse = Course(
            id = editingCourse?.id ?: 0L,
            semesterId = editingCourse?.semesterId ?: semesterId,
            name = name,
            teacher = if (teacher.isNotEmpty()) teacher else null,
            location = if (location.isNotEmpty()) location else null,
            type = type,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekPattern = selectedWeeks.toList().sorted(),
            note = if (note.isNotEmpty()) note else null,
            color = color
        )

        btnSave.isEnabled = false
        lifecycleScope.launch {
            val result = if (editingCourse == null) {
                repository.addCourse(newCourse)
            } else {
                repository.updateCourse(newCourse)
            }
            withContext(Dispatchers.Main) {
                btnSave.isEnabled = true
                when (result) {
                    is CourseRepository.SaveResult.Success -> finish()
                    is CourseRepository.SaveResult.Error -> {
                        Toast.makeText(this@AddEditCourseActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showWeekPicker() {
        val tempSelected = selectedWeeks.toMutableSet()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }
        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnAll = MaterialButton(this).apply { text = "全周" }
        val btnOdd = MaterialButton(this).apply { text = "单周" }
        val btnEven = MaterialButton(this).apply { text = "双周" }
        listOf(btnAll, btnOdd, btnEven).forEach { button ->
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            params.marginEnd = dpToPx(6)
            button.layoutParams = params
            quickRow.addView(button)
        }
        container.addView(quickRow)

        val grid = android.widget.GridLayout(this).apply {
            columnCount = 5
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val buttons = mutableMapOf<Int, MaterialButton>()
        for (week in 1..totalWeeks) {
            val button = MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = week.toString()
                isCheckable = true
                layoutParams = ViewGroup.LayoutParams(dpToPx(56), ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    if (tempSelected.contains(week)) {
                        tempSelected.remove(week)
                        setWeekButtonStyle(this, false)
                    } else {
                        tempSelected.add(week)
                        setWeekButtonStyle(this, true)
                    }
                }
            }
            setWeekButtonStyle(button, tempSelected.contains(week))
            grid.addView(button)
            buttons[week] = button
        }
        container.addView(grid)

        btnAll.setOnClickListener {
            tempSelected.clear()
            tempSelected.addAll(1..totalWeeks)
            buttons.forEach { (week, button) -> setWeekButtonStyle(button, tempSelected.contains(week)) }
        }
        btnOdd.setOnClickListener {
            tempSelected.clear()
            (1..totalWeeks step 2).forEach { tempSelected.add(it) }
            buttons.forEach { (week, button) -> setWeekButtonStyle(button, tempSelected.contains(week)) }
        }
        btnEven.setOnClickListener {
            tempSelected.clear()
            (2..totalWeeks step 2).forEach { tempSelected.add(it) }
            buttons.forEach { (week, button) -> setWeekButtonStyle(button, tempSelected.contains(week)) }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("选择周数")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                selectedWeeks = tempSelected
                updateWeeksText()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setWeekButtonStyle(button: MaterialButton, selected: Boolean) {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val onPrimary = ContextCompat.getColor(this, R.color.on_primary)
        val outline = ContextCompat.getColor(this, R.color.outline)
        if (selected) {
            button.setBackgroundColor(primary)
            button.setTextColor(onPrimary)
            button.strokeWidth = 0
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.setTextColor(primary)
            button.strokeWidth = dpToPx(1)
            button.strokeColor = ColorStateList.valueOf(outline)
        }
    }

    private fun updateWeeksText() {
        val text = formatWeekPattern(selectedWeeks.toList().sorted())
        etWeeks.setText(text)
    }

    private fun formatWeekPattern(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.sorted()
        val ranges = mutableListOf<String>()
        var start = sorted.first()
        var prev = start
        for (i in 1 until sorted.size) {
            val current = sorted[i]
            if (current == prev + 1) {
                prev = current
            } else {
                ranges.add(if (start == prev) "$start" else "$start-$prev")
                start = current
                prev = current
            }
        }
        ranges.add(if (start == prev) "$start" else "$start-$prev")
        return ranges.joinToString(",")
    }

    private fun openCustomColorPicker() {
        val initialColor = customColorValue ?: ContextCompat.getColor(this, R.color.primary)
        showColorPickerDialog(initialColor) { color ->
            customColorValue = color
            val customIndex = colorValues.indexOf(customColorSentinel).coerceAtLeast(0)
            isColorSpinnerUpdating = true
            spCourseColor.setSelection(customIndex)
            isColorSpinnerUpdating = false
            updateColorPreview(color)
        }
    }

    private fun updateColorPreview(color: Int?) {
        val previewColor = color ?: ContextCompat.getColor(this, R.color.primary)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(4).toFloat()
            setColor(previewColor)
            setStroke(dpToPx(1), ContextCompat.getColor(this@AddEditCourseActivity, R.color.outline))
        }
        colorPreview.background = drawable
    }

    private fun showColorPickerDialog(initialColor: Int, onSelected: (Int) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }

        val preview = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
        }
        val hexLabel = TextView(this).apply {
            ellipsize = TextUtils.TruncateAt.END
        }
        container.addView(preview)
        container.addView(hexLabel)

        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        val initialAlpha = Color.alpha(initialColor)

        val hueLabel = TextView(this)
        val satLabel = TextView(this)
        val valLabel = TextView(this)
        val alphaLabel = TextView(this)
        val seekHue = android.widget.SeekBar(this).apply { max = 360 }
        val seekSat = android.widget.SeekBar(this).apply { max = 100 }
        val seekVal = android.widget.SeekBar(this).apply { max = 100 }
        val seekAlpha = android.widget.SeekBar(this).apply { max = 255 }

        seekHue.progress = hsv[0].toInt()
        seekSat.progress = (hsv[1] * 100).toInt()
        seekVal.progress = (hsv[2] * 100).toInt()
        seekAlpha.progress = initialAlpha

        fun updatePreview() {
            val hue = seekHue.progress.toFloat()
            val sat = seekSat.progress / 100f
            val value = seekVal.progress / 100f
            val alpha = seekAlpha.progress
            val color = Color.HSVToColor(alpha, floatArrayOf(hue, sat, value))
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(color)
            }
            hexLabel.text = "当前颜色：${String.format("#%08X", color)}"
            hueLabel.text = "色相: ${seekHue.progress}"
            satLabel.text = "饱和度: ${seekSat.progress}%"
            valLabel.text = "明度: ${seekVal.progress}%"
            alphaLabel.text = "透明度: ${seekAlpha.progress}"
        }

        container.addView(hueLabel)
        container.addView(seekHue)
        container.addView(satLabel)
        container.addView(seekSat)
        container.addView(valLabel)
        container.addView(seekVal)
        container.addView(alphaLabel)
        container.addView(seekAlpha)

        val changeListener = object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        }
        seekHue.setOnSeekBarChangeListener(changeListener)
        seekSat.setOnSeekBarChangeListener(changeListener)
        seekVal.setOnSeekBarChangeListener(changeListener)
        seekAlpha.setOnSeekBarChangeListener(changeListener)
        updatePreview()

        MaterialAlertDialogBuilder(this)
            .setTitle("自定义颜色")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val hue = seekHue.progress.toFloat()
                val sat = seekSat.progress / 100f
                val value = seekVal.progress / 100f
                val alpha = seekAlpha.progress
                val color = Color.HSVToColor(alpha, floatArrayOf(hue, sat, value))
                onSelected(color)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    companion object {
        const val EXTRA_SEMESTER_ID = "extra_semester_id"
        const val EXTRA_COURSE_ID = "extra_course_id"
        private const val DEFAULT_TOTAL_WEEKS = 20
        private const val DEFAULT_PERIOD_COUNT = 8
    }
}
