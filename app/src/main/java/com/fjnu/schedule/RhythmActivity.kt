package com.fjnu.schedule

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
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
    private var addWidgetButton: MaterialButton? = null
    private lateinit var workloadChart: LinearLayout

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
        workloadChart = findViewById(R.id.container_workload_chart)
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

        findViewById<MaterialButton>(R.id.btn_add_widget)?.setOnClickListener { requestPinWidget() }
    }

    private fun updateDndStatus() {
        val granted = isDndPermissionGranted()
        if (granted) {
            dndStatusLabel.text = "✅ 勿扰权限已开启"
            dndStatusLabel.setTextColor(getColor(R.color.course_public_required))
            requestDndButton.isEnabled = false
        } else {
            dndStatusLabel.text = "⚠️ 勿扰权限未开启"
            dndStatusLabel.setTextColor(getColor(R.color.error))
            requestDndButton.isEnabled = true
        }
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
        renderWorkloadChart(days)
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
        val label = SpannableStringBuilder()
        val headerStart = label.length
        val header = "${formatWeekday(day.dayOfWeek)} ${day.date.format(formatter)}"
        label.append(header)
        label.setSpan(StyleSpan(android.graphics.Typeface.BOLD), headerStart, headerStart + header.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label.append("  ")

        fun appendMetric(icon: String, name: String, value: String, suffix: String = "  ") {
            label.append(icon).append(" ")
            val nameStart = label.length
            label.append(name)
            label.setSpan(RelativeSizeSpan(0.85f), nameStart, nameStart + name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            label.setSpan(
                ForegroundColorSpan(getColor(R.color.text_secondary)),
                nameStart,
                nameStart + name.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            label.append(" ").append(value).append(suffix)
        }

        appendMetric("📚", "课程", "${day.coursePeriods}节")
        appendMetric("📝", "作业", "${day.taskCount}")
        appendMetric("⚡", "指数", "${day.index}", "")
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, dpToPx(6), 0, dpToPx(6))
        }
    }

    private fun renderWorkloadChart(days: List<WorkloadDay>) {
        workloadChart.removeAllViews()
        if (days.isEmpty()) return
        val maxValue = days.maxOfOrNull { it.index }?.coerceAtLeast(1) ?: 1
        val chartHeight = dpToPx(52)
        days.forEach { day ->
            val bar = View(this).apply {
                setBackgroundColor(getColor(R.color.primary))
            }
            val height = (chartHeight * (day.index.toFloat() / maxValue.toFloat())).toInt().coerceAtLeast(dpToPx(6))
            val params = LinearLayout.LayoutParams(0, height, 1f)
            params.marginEnd = dpToPx(6)
            params.marginStart = dpToPx(6)
            bar.layoutParams = params
            workloadChart.addView(bar)
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
