package com.fjnu.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.fjnu.schedule.data.AppDatabase
import com.fjnu.schedule.data.CourseTypeReminderEntity
import com.fjnu.schedule.data.PeriodTimeEntity
import com.fjnu.schedule.data.ScheduleSettingsEntity
import com.fjnu.schedule.data.SettingsRepository
import com.fjnu.schedule.viewmodel.SettingsViewModel
import com.fjnu.schedule.viewmodel.SettingsViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SettingsActivity : AppCompatActivity() {
    private lateinit var viewModel: SettingsViewModel
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var semesterDateText: TextView
    private lateinit var periodContainer: LinearLayout
    private lateinit var typeReminderContainer: LinearLayout
    private lateinit var reminderEnableSwitch: Switch
    private lateinit var reminderVibrateSwitch: Switch
    private lateinit var defaultLeadSpinner: Spinner
    private lateinit var etPeriodCount: EditText
    private lateinit var etPeriodMinutes: EditText
    private lateinit var etBreakMinutes: EditText
    private lateinit var etTotalWeeks: EditText
    private lateinit var btnApplyScheduleSettings: Button
    private lateinit var btnOpenReminderSettings: Button

    private val leadOptions = listOf(5, 10, 15, 20, 30)
    private val typeLabelMap = mapOf(
        "major_required" to "专业必修",
        "major_elective" to "专业选修",
        "public_required" to "公共必修",
        "public_elective" to "公共选修",
        "experiment" to "实验/实践",
        "pe" to "体育/艺术"
    )
    private var isUpdatingReminderUi = false
    private var isUpdatingTypeUi = false
    private var latestPeriodTimes: List<PeriodTimeEntity> = emptyList()
    private var latestScheduleSettings: ScheduleSettingsEntity? = null
    private var currentSemesterId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val root = findViewById<LinearLayout>(R.id.settings_root)
        val initialPaddingTop = root.paddingTop
        val initialPaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + initialPaddingTop,
                view.paddingRight,
                systemBars.bottom + initialPaddingBottom
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_settings)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener { finish() }

        semesterDateText = findViewById(R.id.tv_semester_date)
        periodContainer = findViewById(R.id.container_period_times)
        typeReminderContainer = findViewById(R.id.container_type_reminders)
        reminderEnableSwitch = findViewById(R.id.switch_reminder_enable)
        reminderVibrateSwitch = findViewById(R.id.switch_reminder_vibrate)
        defaultLeadSpinner = findViewById(R.id.spinner_default_lead)
        etPeriodCount = findViewById(R.id.et_period_count)
        etPeriodMinutes = findViewById(R.id.et_period_minutes)
        etBreakMinutes = findViewById(R.id.et_break_minutes)
        etTotalWeeks = findViewById(R.id.et_total_weeks)
        btnApplyScheduleSettings = findViewById(R.id.btn_apply_schedule_settings)
        btnOpenReminderSettings = findViewById(R.id.btn_open_reminder_settings)

        val database = AppDatabase.getInstance(this)
        val repository = SettingsRepository(database.settingsDao(), database.semesterDao())
        viewModel = ViewModelProvider(this, SettingsViewModelFactory(repository))[SettingsViewModel::class.java]
        viewModel.ensureDefaults()

        setupLeadSpinner(defaultLeadSpinner)
        setupTemplateButtons()
        setupSemesterDatePicker()
        setupScheduleSettings()
        btnOpenReminderSettings.setOnClickListener {
            startActivity(Intent(this, ReminderSettingsActivity::class.java))
        }
        observeSettings()
    }

    private fun setupLeadSpinner(spinner: Spinner) {
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            leadOptions.map { "${it}分钟" }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun setupTemplateButtons() {
        findViewById<Button>(R.id.btn_template_45).setOnClickListener {
            viewModel.updatePeriodTimes(default45Template())
        }
        findViewById<Button>(R.id.btn_template_90).setOnClickListener {
            viewModel.updatePeriodTimes(default90Template())
        }
    }

    private fun setupScheduleSettings() {
        btnApplyScheduleSettings.setOnClickListener {
            val periodCount = etPeriodCount.text.toString().trim().toIntOrNull()
            val periodMinutes = etPeriodMinutes.text.toString().trim().toIntOrNull()
            val breakMinutes = etBreakMinutes.text.toString().trim().toIntOrNull()
            val totalWeeks = etTotalWeeks.text.toString().trim().toIntOrNull()

            if (periodCount == null || periodCount <= 0) {
                Toast.makeText(this, "请输入有效的每日节数", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (periodCount > MAX_PERIOD_COUNT) {
                Toast.makeText(this, "每日节数建议不超过$MAX_PERIOD_COUNT", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (periodMinutes == null || periodMinutes <= 0) {
                Toast.makeText(this, "请输入有效的每节课时长", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (breakMinutes == null || breakMinutes < 0) {
                Toast.makeText(this, "请输入有效的课间时间", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (totalWeeks == null || totalWeeks <= 0) {
                Toast.makeText(this, "请输入有效的学期周数", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (totalWeeks > MAX_TOTAL_WEEKS) {
                Toast.makeText(this, "学期周数建议不超过$MAX_TOTAL_WEEKS", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val settings = ScheduleSettingsEntity(
                semesterId = currentSemesterId,
                periodCount = periodCount,
                periodMinutes = periodMinutes,
                breakMinutes = breakMinutes,
                totalWeeks = totalWeeks
            )
            viewModel.updateScheduleSettings(settings)

            val startTime = latestPeriodTimes.firstOrNull { it.period == 1 }?.startTime ?: DEFAULT_START_TIME
            val generated = generatePeriodTimes(periodCount, periodMinutes, breakMinutes, startTime)
            viewModel.updatePeriodTimes(generated)
            Toast.makeText(this, "已应用上课时间设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSemesterDatePicker() {
        findViewById<Button>(R.id.btn_pick_semester).setOnClickListener {
            val today = LocalDate.now()
            val dialog = DatePickerDialog(
                this,
                { _, year, month, day ->
                    val date = LocalDate.of(year, month + 1, day)
                    viewModel.updateSemesterStart(date)
                },
                today.year,
                today.monthValue - 1,
                today.dayOfMonth
            )
            dialog.show()
        }
    }

    private fun observeSettings() {
        viewModel.currentSemesterId.observe(this) { semesterId ->
            currentSemesterId = semesterId
        }

        viewModel.semesterStartDate.observe(this) { date ->
            semesterDateText.text = date?.toString() ?: "未设置"
        }

        viewModel.periodTimes.observe(this) { times ->
            latestPeriodTimes = times
            renderPeriodTimes(times)
        }

        viewModel.scheduleSettings.observe(this) { settings ->
            if (settings == null) return@observe
            latestScheduleSettings = settings
            etPeriodCount.setText(settings.periodCount.toString())
            etPeriodMinutes.setText(settings.periodMinutes.toString())
            etBreakMinutes.setText(settings.breakMinutes.toString())
            etTotalWeeks.setText(settings.totalWeeks.toString())
        }

        viewModel.reminderSettings.observe(this) { settings ->
            if (settings == null) return@observe
            isUpdatingReminderUi = true
            reminderEnableSwitch.isChecked = settings.enableNotification
            reminderVibrateSwitch.isChecked = settings.enableVibrate
            val index = leadOptions.indexOf(settings.leadMinutes).coerceAtLeast(0)
            defaultLeadSpinner.setSelection(index)
            isUpdatingReminderUi = false
            reminderEnableSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isUpdatingReminderUi) return@setOnCheckedChangeListener
                handleNotificationPermission(isChecked)
                viewModel.updateReminderSettings(
                    settings.copy(enableNotification = isChecked)
                )
            }
            reminderVibrateSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isUpdatingReminderUi) return@setOnCheckedChangeListener
                viewModel.updateReminderSettings(
                    settings.copy(enableVibrate = isChecked)
                )
            }
            defaultLeadSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (isUpdatingReminderUi) return
                    viewModel.updateReminderSettings(
                        settings.copy(leadMinutes = leadOptions[position])
                    )
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }

        viewModel.typeReminders.observe(this) { items ->
            renderTypeReminders(items)
        }
    }

    private fun renderPeriodTimes(times: List<PeriodTimeEntity>) {
        periodContainer.removeAllViews()
        val displayTimes = if (times.isEmpty()) {
            val settings = latestScheduleSettings
            if (settings != null) {
                generatePeriodTimes(
                    settings.periodCount,
                    settings.periodMinutes,
                    settings.breakMinutes,
                    DEFAULT_START_TIME
                )
            } else {
                default45Template()
            }
        } else {
            times
        }
        displayTimes.forEach { period ->
            val row = layoutInflater.inflate(R.layout.item_period_time, periodContainer, false)
            val label = row.findViewById<TextView>(R.id.tv_period_label)
            val startBtn = row.findViewById<Button>(R.id.btn_start_time)
            val endBtn = row.findViewById<Button>(R.id.btn_end_time)
            label.text = "第${period.period}节"
            startBtn.text = period.startTime
            endBtn.text = period.endTime

            startBtn.setOnClickListener {
                pickTime(period.startTime) { time ->
                    updatePeriodTime(period.period, time, period.endTime, displayTimes)
                }
            }
            endBtn.setOnClickListener {
                pickTime(period.endTime) { time ->
                    updatePeriodTime(period.period, period.startTime, time, displayTimes)
                }
            }
            periodContainer.addView(row)
        }
    }

    private fun updatePeriodTime(
        period: Int,
        startTime: String,
        endTime: String,
        currentTimes: List<PeriodTimeEntity>
    ) {
        val updated = currentTimes.map {
            if (it.period == period) {
                it.copy(startTime = startTime, endTime = endTime)
            } else {
                it
            }
        }
        viewModel.updatePeriodTimes(updated)
    }

    private fun renderTypeReminders(items: List<CourseTypeReminderEntity>) {
        typeReminderContainer.removeAllViews()
        if (items.isEmpty()) return
        isUpdatingTypeUi = true
        items.forEach { item ->
            val row = layoutInflater.inflate(R.layout.item_type_reminder, typeReminderContainer, false)
            val label = row.findViewById<TextView>(R.id.tv_type_label)
            val leadSpinner = row.findViewById<Spinner>(R.id.spinner_type_lead)
            val enableSwitch = row.findViewById<Switch>(R.id.switch_type_enable)
            label.text = typeLabelMap[item.type] ?: item.type
            setupLeadSpinner(leadSpinner)
            leadSpinner.setSelection(leadOptions.indexOf(item.leadMinutes).coerceAtLeast(0))
            enableSwitch.isChecked = item.enabled

            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isUpdatingTypeUi) return@setOnCheckedChangeListener
                updateTypeReminder(item, item.leadMinutes, isChecked)
            }
            leadSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (isUpdatingTypeUi) return
                    updateTypeReminder(item, leadOptions[position], enableSwitch.isChecked)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
            typeReminderContainer.addView(row)
        }
        isUpdatingTypeUi = false
    }

    private fun updateTypeReminder(item: CourseTypeReminderEntity, leadMinutes: Int, enabled: Boolean) {
        val updated = item.copy(leadMinutes = leadMinutes, enabled = enabled)
        val current = viewModel.typeReminders.value?.toMutableList() ?: mutableListOf()
        val index = current.indexOfFirst { it.type == item.type }
        if (index >= 0) {
            current[index] = updated
            viewModel.updateTypeReminders(current)
        }
    }

    private fun pickTime(initial: String, onSelected: (String) -> Unit) {
        val time = LocalTime.parse(initial, timeFormatter)
        val dialog = TimePickerDialog(
            this,
            { _, hour, minute ->
                val newTime = LocalTime.of(hour, minute).format(timeFormatter)
                onSelected(newTime)
            },
            time.hour,
            time.minute,
            true
        )
        dialog.show()
    }

    private fun handleNotificationPermission(enable: Boolean) {
        if (!enable) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    private fun generatePeriodTimes(
        periodCount: Int,
        periodMinutes: Int,
        breakMinutes: Int,
        startTimeText: String
    ): List<PeriodTimeEntity> {
        val safeStart = try {
            LocalTime.parse(startTimeText, timeFormatter)
        } catch (_: Exception) {
            LocalTime.of(8, 0)
        }
        val times = mutableListOf<PeriodTimeEntity>()
        var currentStart = safeStart
        for (period in 1..periodCount) {
            val endTime = currentStart.plusMinutes(periodMinutes.toLong())
            times.add(
                PeriodTimeEntity(
                    semesterId = 0L,
                    period = period,
                    startTime = currentStart.format(timeFormatter),
                    endTime = endTime.format(timeFormatter)
                )
            )
            currentStart = endTime.plusMinutes(breakMinutes.toLong())
        }
        return times
    }

    private fun default45Template(): List<PeriodTimeEntity> {
        return listOf(
            PeriodTimeEntity(0, 1, "08:00", "08:45"),
            PeriodTimeEntity(0, 2, "08:55", "09:40"),
            PeriodTimeEntity(0, 3, "10:00", "10:45"),
            PeriodTimeEntity(0, 4, "10:55", "11:40"),
            PeriodTimeEntity(0, 5, "14:00", "14:45"),
            PeriodTimeEntity(0, 6, "14:55", "15:40"),
            PeriodTimeEntity(0, 7, "16:00", "16:45"),
            PeriodTimeEntity(0, 8, "16:55", "17:40")
        )
    }

    private fun default90Template(): List<PeriodTimeEntity> {
        return listOf(
            PeriodTimeEntity(0, 1, "08:00", "08:45"),
            PeriodTimeEntity(0, 2, "08:45", "09:30"),
            PeriodTimeEntity(0, 3, "09:50", "10:35"),
            PeriodTimeEntity(0, 4, "10:35", "11:20"),
            PeriodTimeEntity(0, 5, "14:00", "14:45"),
            PeriodTimeEntity(0, 6, "14:45", "15:30"),
            PeriodTimeEntity(0, 7, "15:50", "16:35"),
            PeriodTimeEntity(0, 8, "16:35", "17:20")
        )
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1201
        private const val DEFAULT_START_TIME = "08:00"
        private const val MAX_PERIOD_COUNT = 20
        private const val MAX_TOTAL_WEEKS = 20
    }
}
