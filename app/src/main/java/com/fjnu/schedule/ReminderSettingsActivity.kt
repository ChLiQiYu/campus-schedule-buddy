package com.fjnu.schedule

import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.fjnu.schedule.data.AppDatabase
import com.fjnu.schedule.data.CourseTypeReminderEntity
import com.fjnu.schedule.data.SettingsRepository
import com.fjnu.schedule.viewmodel.SettingsViewModel
import com.fjnu.schedule.viewmodel.SettingsViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ReminderSettingsActivity : AppCompatActivity() {
    private lateinit var viewModel: SettingsViewModel
    private lateinit var enableSwitch: Switch
    private lateinit var vibrateSwitch: Switch
    private lateinit var leadSpinner: Spinner
    private lateinit var permissionHint: TextView
    private lateinit var btnNotification: Button
    private lateinit var btnExactAlarm: Button
    private lateinit var typeReminderContainer: LinearLayout

    private val leadOptions = listOf(5, 10, 15, 20, 30)
    private var isUpdatingUi = false
    private var isUpdatingTypeUi = false
    private val typeLabelMap = mapOf(
        "major_required" to "专业必修",
        "major_elective" to "专业选修",
        "public_required" to "公共必修",
        "public_elective" to "公共选修",
        "experiment" to "实验/实践",
        "pe" to "体育/艺术"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reminder_settings)

        val root = findViewById<LinearLayout>(R.id.reminder_root)
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

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_reminder)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener { finish() }

        enableSwitch = findViewById(R.id.switch_reminder_enable)
        vibrateSwitch = findViewById(R.id.switch_reminder_vibrate)
        leadSpinner = findViewById(R.id.spinner_lead_time)
        permissionHint = findViewById(R.id.tv_permission_hint)
        btnNotification = findViewById(R.id.btn_request_notification)
        btnExactAlarm = findViewById(R.id.btn_request_exact_alarm)
        typeReminderContainer = findViewById(R.id.container_type_reminders)

        val database = AppDatabase.getInstance(this)
        val repository = SettingsRepository(database.settingsDao(), database.semesterDao())
        viewModel = ViewModelProvider(this, SettingsViewModelFactory(repository))[SettingsViewModel::class.java]
        viewModel.ensureDefaults()

        setupLeadSpinner()
        setupListeners()
        observeSettings()
        updatePermissionState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    private fun setupLeadSpinner() {
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            leadOptions.map { "${it}分钟" }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        leadSpinner.adapter = adapter
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

    private fun setupListeners() {
        btnNotification.setOnClickListener {
            requestNotificationPermission()
        }
        btnExactAlarm.setOnClickListener {
            requestExactAlarmPermission()
        }
    }

    private fun observeSettings() {
        viewModel.reminderSettings.observe(this) { settings ->
            if (settings == null) return@observe
            isUpdatingUi = true
            enableSwitch.isChecked = settings.enableNotification
            vibrateSwitch.isChecked = settings.enableVibrate
            leadSpinner.setSelection(leadOptions.indexOf(settings.leadMinutes).coerceAtLeast(0))
            isUpdatingUi = false

            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isUpdatingUi) return@setOnCheckedChangeListener
                if (isChecked) {
                    requestNotificationPermission()
                }
                viewModel.updateReminderSettings(settings.copy(enableNotification = isChecked))
                updatePermissionState()
            }
            vibrateSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isUpdatingUi) return@setOnCheckedChangeListener
                viewModel.updateReminderSettings(settings.copy(enableVibrate = isChecked))
            }
            leadSpinner.onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: android.view.View?,
                        position: Int,
                        id: Long
                    ) {
                        if (isUpdatingUi) return
                        viewModel.updateReminderSettings(settings.copy(leadMinutes = leadOptions[position]))
                    }

                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                }
        }

        viewModel.typeReminders.observe(this) { items ->
            renderTypeReminders(items)
        }
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

    private fun updatePermissionState() {
        val hasNotification = hasNotificationPermission()
        val hasExactAlarm = hasExactAlarmPermission()
        btnNotification.visibility = if (hasNotification) android.view.View.GONE else android.view.View.VISIBLE
        btnExactAlarm.visibility = if (hasExactAlarm) android.view.View.GONE else android.view.View.VISIBLE
        permissionHint.text = when {
            !hasNotification -> "需要通知权限用于发送上课提醒。"
            !hasExactAlarm -> "需要精准提醒权限以保证准时触达。"
            else -> "提醒权限已开启。"
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun requestNotificationPermission() {
        if (hasNotificationPermission()) {
            updatePermissionState()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    private fun requestExactAlarmPermission() {
        if (hasExactAlarmPermission()) {
            updatePermissionState()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            } catch (_: Exception) {
                openAppSettings()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updatePermissionState()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle("需要通知权限")
                    .setMessage("需要访问通知权限来发送上课提醒，可在系统设置中手动开启。")
                    .setPositiveButton("去设置") { _, _ -> openAppSettings() }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1401
    }
}
