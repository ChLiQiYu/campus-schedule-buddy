package com.fjnu.schedule.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Window
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.fjnu.schedule.R
import com.fjnu.schedule.model.Course

class AddEditCourseDialog(
    context: Context,
    private val course: Course? = null,
    private val semesterId: Long,
    private val onSave: (Course, (SaveFeedback) -> Unit) -> Unit
) : Dialog(context) {

    data class SaveFeedback(
        val success: Boolean,
        val message: String? = null
    )

    private lateinit var etCourseName: EditText
    private lateinit var spCourseType: Spinner
    private lateinit var etTeacher: EditText
    private lateinit var etLocation: EditText
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
    private var colorValues: List<Int?> = emptyList()
    private var customColorValue: Int? = null
    private var isColorSpinnerUpdating = false
    private val customColorSentinel = Int.MIN_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_add_edit_course)

        // 设置对话框样式
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        initViews()
        populateViews()
        setupListeners()
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

        // 设置课程类型下拉列表
        val courseTypes = arrayOf("专业必修", "专业选修", "公共必修", "公共选修", "实验/实践", "体育/艺术")
        val courseTypeAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, courseTypes)
        courseTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCourseType.adapter = courseTypeAdapter

        // 设置星期下拉列表
        val days = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val dayAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, days)
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spDayOfWeek.adapter = dayAdapter

        // 设置节次下拉列表
        val periods = (1..8).map { "第${it}" }.toTypedArray()
        val periodAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, periods)
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spStartPeriod.adapter = periodAdapter
        spEndPeriod.adapter = periodAdapter

        val colorOptions = listOf(
            "按类型自动" to null,
            "蓝色" to ContextCompat.getColor(context, R.color.course_major_required),
            "紫色" to ContextCompat.getColor(context, R.color.course_major_elective),
            "粉色" to ContextCompat.getColor(context, R.color.course_public_required),
            "青绿" to ContextCompat.getColor(context, R.color.course_experiment),
            "天蓝" to ContextCompat.getColor(context, R.color.course_pe),
            "自定义..." to customColorSentinel
        )
        colorValues = colorOptions.map { it.second }
        val colorAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            colorOptions.map { it.first }
        )
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCourseColor.adapter = colorAdapter
    }

    private fun populateViews() {
        if (course != null) {
            // 编辑模式，填充现有数据
            etCourseName.setText(course.name)
            // 设置课程类型
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
            
            etTeacher.setText(course.teacher ?: "")
            etLocation.setText(course.location ?: "")
            spDayOfWeek.setSelection(course.dayOfWeek - 1)
            spStartPeriod.setSelection(course.startPeriod - 1)
            spEndPeriod.setSelection(course.endPeriod - 1)
            val colorIndex = colorValues.indexOf(course.color)
            isColorSpinnerUpdating = true
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

            // 格式化周数显示
            val weeksText = course.weekPattern.joinToString(",")
            etWeeks.setText(weeksText)
            
            etNote.setText(course.note ?: "")
        } else {
            // 添加模式，设置默认值
            spDayOfWeek.setSelection(0)
            spStartPeriod.setSelection(0)
            spEndPeriod.setSelection(0)
            isColorSpinnerUpdating = true
            spCourseColor.setSelection(0)
            isColorSpinnerUpdating = false
            etWeeks.setText("1-16")
            updateColorPreview(null)
        }
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveCourse()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnCustomColor.setOnClickListener {
            openCustomColorPicker()
        }

        spCourseColor.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
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

    private fun saveCourse() {
        val name = etCourseName.text.toString().trim()
        if (name.isEmpty()) {
            etCourseName.error = "请输入课程名称"
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
            // 显示错误信息
            Toast.makeText(context, "开始节次不能晚于结束节次", Toast.LENGTH_SHORT).show()
            return
        }

        val weeksText = etWeeks.text.toString().trim()
        if (weeksText.isEmpty()) {
            etWeeks.error = "请输入周数"
            return
        }

        // 解析周数
        val weekPattern = parseWeeks(weeksText)
        if (weekPattern.isEmpty()) {
            etWeeks.error = "周数格式不正确"
            return
        }

        val note = etNote.text.toString().trim()
        val courseId = course?.id ?: 0L
        val selectedColor = colorValues.getOrNull(spCourseColor.selectedItemPosition)
        if (selectedColor == customColorSentinel && customColorValue == null) {
            Toast.makeText(context, "请先选择自定义颜色", Toast.LENGTH_SHORT).show()
            return
        }
        val color = if (selectedColor == customColorSentinel) customColorValue else selectedColor

        val newCourse = Course(
            id = courseId,
            semesterId = course?.semesterId ?: semesterId,
            name = name,
            teacher = teacher.ifEmpty { null },
            location = location.ifEmpty { null },
            type = type,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekPattern = weekPattern,
            note = note.ifEmpty { null },
            color = color
        )

        btnSave.isEnabled = false
        onSave(newCourse) { feedback ->
            btnSave.isEnabled = true
            if (feedback.success) {
                dismiss()
            } else {
                val message = feedback.message ?: "保存失败"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseWeeks(weeksText: String): List<Int> {
        val weeks = mutableSetOf<Int>()
        val parts = weeksText.split(",")

        for (part in parts) {
            val range = part.trim()
            if (range.contains("-")) {
                val rangeParts = range.split("-")
                if (rangeParts.size == 2) {
                    try {
                        val start = rangeParts[0].toInt()
                        val end = rangeParts[1].toInt()
                        for (i in start..end) {
                            if (i > 0) {
                                weeks.add(i)
                            }
                        }
                    } catch (e: NumberFormatException) {
                        // 忽略无效范围
                    }
                }
            } else {
                try {
                    val value = range.toInt()
                    if (value > 0) {
                        weeks.add(value)
                    }
                } catch (e: NumberFormatException) {
                    // 忽略无效数字
                }
            }
        }

        return weeks.sorted()
    }

    private fun openCustomColorPicker() {
        val initialColor = customColorValue ?: ContextCompat.getColor(context, R.color.primary)
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
        val previewColor = color ?: ContextCompat.getColor(context, R.color.primary)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(4).toFloat()
            setColor(previewColor)
            setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.outline))
        }
        colorPreview.background = drawable
    }

    private fun showColorPickerDialog(initialColor: Int, onSelected: (Int) -> Unit) {
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }

        val preview = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
        }
        container.addView(preview)

        val initialRed = Color.red(initialColor)
        val initialGreen = Color.green(initialColor)
        val initialBlue = Color.blue(initialColor)

        val redLabel = TextView(context)
        val greenLabel = TextView(context)
        val blueLabel = TextView(context)
        val seekRed = SeekBar(context)
        val seekGreen = SeekBar(context)
        val seekBlue = SeekBar(context)
        listOf(seekRed, seekGreen, seekBlue).forEach { it.max = 255 }
        seekRed.progress = initialRed
        seekGreen.progress = initialGreen
        seekBlue.progress = initialBlue

        fun updatePreview() {
            val color = Color.rgb(seekRed.progress, seekGreen.progress, seekBlue.progress)
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(6).toFloat()
                setColor(color)
            }
            redLabel.text = "红: ${seekRed.progress}"
            greenLabel.text = "绿: ${seekGreen.progress}"
            blueLabel.text = "蓝: ${seekBlue.progress}"
        }

        container.addView(redLabel)
        container.addView(seekRed)
        container.addView(greenLabel)
        container.addView(seekGreen)
        container.addView(blueLabel)
        container.addView(seekBlue)

        val changeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        seekRed.setOnSeekBarChangeListener(changeListener)
        seekGreen.setOnSeekBarChangeListener(changeListener)
        seekBlue.setOnSeekBarChangeListener(changeListener)
        updatePreview()

        android.app.AlertDialog.Builder(context)
            .setTitle("自定义颜色")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val color = Color.rgb(seekRed.progress, seekGreen.progress, seekBlue.progress)
                onSelected(color)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
