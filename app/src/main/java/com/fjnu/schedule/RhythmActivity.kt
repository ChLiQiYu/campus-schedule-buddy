package com.fjnu.schedule

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.fjnu.schedule.data.AppDatabase
import com.fjnu.schedule.data.PeriodTimeEntity
import com.fjnu.schedule.data.RhythmRepository
import com.fjnu.schedule.data.SettingsRepository
import com.fjnu.schedule.focus.FocusModeReceiver
import com.fjnu.schedule.focus.FocusModeScheduler
import com.fjnu.schedule.model.WorkloadDay
import com.fjnu.schedule.viewmodel.RhythmViewModel
import com.fjnu.schedule.viewmodel.RhythmViewModelFactory
import com.fjnu.schedule.widget.ProgressBeaconWidgetProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RhythmActivity : AppCompatActivity() {
    private lateinit var viewModel: RhythmViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var rhythmRepository: RhythmRepository
    private lateinit var workloadContainer: LinearLayout
    private lateinit var autoFocusSwitch: SwitchMaterial
    private lateinit var dndStatusLabel: TextView
    private lateinit var requestDndButton: MaterialButton
    private lateinit var addWidgetButton: MaterialButton

    private var currentSemesterId: Long = 0L
    private var periodTimes: List<PeriodTimeEntity> = emptyList()
    private var semesterStartDate: LocalDate = LocalDate.now()
    private var suppressToggle = false

    private val focusScheduler by lazy { FocusModeScheduler(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rhythm)
        setupToolbar()
        initViews()
        initRepositories()
        bindViewModel()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateDndStatus()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_rhythm)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initRepositories() {
        val database = AppDatabase.getInstance(this)
        settingsRepository = SettingsRepository(database.settingsDao(), database.semesterDao())
        rhythmRepository =
            RhythmRepository(database.courseDao(), database.workspaceDao(), database.settingsDao())
        val factory = RhythmViewModelFactory(rhythmRepository, settingsRepository)
        viewModel = ViewModelProvider(this, factory)[RhythmViewModel::class.java]
        viewModel.ensureDefaults()
        lifecycleScope.launch {
            settingsRepository.ensureDefaults()
        }

        lifecycleScope.launch {
            settingsRepository.currentSemesterId.collectLatest { semesterId ->
                currentSemesterId = semesterId
                if (autoFocusSwitch.isChecked) {
                    updateFocusSchedule(true)
                }
            }
        }
        lifecycleScope.launch {
            settingsRepository.periodTimes.collectLatest { times ->
                periodTimes = times
                if (autoFocusSwitch.isChecked && periodTimes.isNotEmpty()) {
                    updateFocusSchedule(true)
                }
            }
        }
        lifecycleScope.launch {
            settingsRepository.observeSemesterStartDate().collectLatest { date ->
                semesterStartDate = date ?: LocalDate.now()
            }
        }
    }

    private fun initViews() {
        workloadContainer = findViewById(R.id.container_workload)
        autoFocusSwitch = findViewById(R.id.switch_auto_focus)
        dndStatusLabel = findViewById(R.id.tv_dnd_status)
        requestDndButton = findViewById(R.id.btn_request_dnd)
        addWidgetButton = findViewById(R.id.btn_add_widget)
    }

    private fun bindViewModel() {
        viewModel.workloadDays.observe(this) { days ->
            renderWorkload(days)
        }
        viewModel.autoFocusEnabled.observe(this) { enabled ->
            suppressToggle = true
            autoFocusSwitch.isChecked = enabled
            suppressToggle = false
            if (enabled) {
                updateFocusSchedule(true)
            }
        }
    }

    private fun setupListeners() {
        autoFocusSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToggle) return@setOnCheckedChangeListener
            if (isChecked && !isDndPermissionGranted()) {
                Toast.makeText(this, "请先开启勿扰权限", Toast.LENGTH_SHORT).show()
                suppressToggle = true
                autoFocusSwitch.isChecked = false
                suppressToggle = false
                openDndSettings()
                return@setOnCheckedChangeListener
            }
            viewModel.setAutoFocusEnabled(isChecked)
            updateFocusSchedule(isChecked)
            if (!isChecked) {
                FocusModeReceiver.setDndEnabled(this, false)
            }
        }

        requestDndButton.setOnClickListener {
            openDndSettings()
        }

        addWidgetButton.setOnClickListener {
            requestPinWidget()
        }
    }

    private fun updateDndStatus() {
        val granted = isDndPermissionGranted()
        dndStatusLabel.text = if (granted) "勿扰权限已开启" else "勿扰权限未开启"
        requestDndButton.isEnabled = !granted
    }

    private fun updateFocusSchedule(enabled: Boolean) {
        if (currentSemesterId <= 0L) return
        lifecycleScope.launch {
            val courses = withContext(Dispatchers.IO) {
                rhythmRepository.getCourses(currentSemesterId)
            }
            val times = periodTimes
            val startDate = semesterStartDate
            withContext(Dispatchers.IO) {
                focusScheduler.scheduleUpcomingFocus(courses, times, startDate, enabled)
            }
        }
    }

    private fun renderWorkload(days: List<WorkloadDay>) {
        workloadContainer.removeAllViews()
        if (days.isEmpty()) {
            workloadContainer.addView(buildHint("暂无数据"))
            return
        }
        val formatter = DateTimeFormatter.ofPattern("MM/dd")
        days.forEach { day ->
            workloadContainer.addView(buildWorkloadItem(day, formatter))
        }
    }

    private fun buildWorkloadItem(day: WorkloadDay, formatter: DateTimeFormatter): TextView {
        val label = buildString {
            append(formatWeekday(day.dayOfWeek))
            append(" ")
            append(day.date.format(formatter))
            append(" · 课程 ")
            append(day.coursePeriods)
            append("节 · 作业 ")
            append(day.taskCount)
            append(" · 指数 ")
            append(day.index)
        }
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, dpToPx(6), 0, dpToPx(6))
        }
    }

    private fun buildHint(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dpToPx(6), 0, dpToPx(6))
        }
    }

    private fun isDndPermissionGranted(): Boolean {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return manager.isNotificationPolicyAccessGranted
    }

    private fun openDndSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    private fun requestPinWidget() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "请长按桌面空白处 → 小部件 → 学业节律", Toast.LENGTH_LONG).show()
            return
        }
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        if (appWidgetManager == null || !appWidgetManager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, "当前启动器不支持一键添加，请手动添加：桌面空白处 → 小部件 → 学业节律", Toast.LENGTH_LONG).show()
            return
        }
        val provider = ComponentName(this, ProgressBeaconWidgetProvider::class.java)
        val requested = appWidgetManager.requestPinAppWidget(provider, null, null)
        if (!requested) {
            Toast.makeText(this, "未能唤起添加流程，请手动添加：桌面空白处 → 小部件 → 学业节律", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "已发起添加，请在桌面确认", Toast.LENGTH_SHORT).show()
        }
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

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }
}
