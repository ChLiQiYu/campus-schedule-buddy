package com.example.campus_schedule_buddy.view

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.campus_schedule_buddy.R
import com.example.campus_schedule_buddy.model.Course

class AddEditCourseDialog(
    context: Context,
    private val course: Course? = null,
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
    private var colorValues: List<Int?> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_add_edit_course)

        // 设置对话框样式
        window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
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
            "天蓝" to ContextCompat.getColor(context, R.color.course_pe)
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
            spCourseColor.setSelection(if (colorIndex >= 0) colorIndex else 0)

            // 格式化周数显示
            val weeksText = course.weekPattern.joinToString(",")
            etWeeks.setText(weeksText)
            
            etNote.setText(course.note ?: "")
        } else {
            // 添加模式，设置默认值
            spDayOfWeek.setSelection(0)
            spStartPeriod.setSelection(0)
            spEndPeriod.setSelection(0)
            spCourseColor.setSelection(0)
            etWeeks.setText("1-16")
        }
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveCourse()
        }

        btnCancel.setOnClickListener {
            dismiss()
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
            android.widget.Toast.makeText(context, "开始节次不能晚于结束节次", android.widget.Toast.LENGTH_SHORT).show()
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
        val color = colorValues.getOrNull(spCourseColor.selectedItemPosition)

        val newCourse = Course(
            id = courseId,
            name = name,
            teacher = if (teacher.isNotEmpty()) teacher else null,
            location = if (location.isNotEmpty()) location else null,
            type = type,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekPattern = weekPattern,
            note = if (note.isNotEmpty()) note else null,
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
}
