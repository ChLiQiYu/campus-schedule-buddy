package com.example.campus_schedule_buddy

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campus_schedule_buddy.data.AppDatabase
import com.example.campus_schedule_buddy.data.CourseDao
import com.example.campus_schedule_buddy.data.GroupSyncRepository
import com.example.campus_schedule_buddy.data.GroupSyncSessionEntity
import com.example.campus_schedule_buddy.data.GroupSyncShareEntity
import com.example.campus_schedule_buddy.data.PeriodTimeEntity
import com.example.campus_schedule_buddy.data.SettingsRepository
import com.example.campus_schedule_buddy.util.GroupSyncPayload
import com.example.campus_schedule_buddy.util.GroupSyncSerializer
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Random

class GroupSyncActivity : AppCompatActivity() {

    private lateinit var courseDao: CourseDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var groupSyncRepository: GroupSyncRepository

    private lateinit var codeInput: TextInputEditText
    private lateinit var sessionLabel: TextView
    private lateinit var memberCountLabel: TextView
    private lateinit var exportButton: MaterialButton
    private lateinit var importButton: MaterialButton
    private lateinit var computeButton: MaterialButton
    private lateinit var weekSpinner: Spinner
    private lateinit var resultsContainer: LinearLayout

    private var activeSession: GroupSyncSessionEntity? = null
    private var activeShares: List<GroupSyncShareEntity> = emptyList()
    private var currentIntersection: String? = null
    private var currentSemesterId: Long = 0L
    private var periodTimes: List<PeriodTimeEntity> = emptyList()
    private var semesterStartDate: LocalDate = LocalDate.now()
    private var currentWeek: Int = 1
    private var selectedWeek: Int = 1
    private var shareJob: Job? = null
    private var pendingExportPayload: GroupSyncPayload? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExportPayload
        if (uri == null || payload == null) {
            pendingExportPayload = null
            return@registerForActivityResult
        }
        lifecycleScope.launch(Dispatchers.IO) {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(GroupSyncSerializer.toJson(payload).toByteArray(Charsets.UTF_8))
                output.flush()
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@GroupSyncActivity, "导出完成", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportPayload = null
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importShareFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_sync)
        setupToolbar()
        initViews()
        setupRepositories()
        setupListeners()
        updateSessionUi(null)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_group_sync)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initViews() {
        codeInput = findViewById(R.id.et_sync_code)
        sessionLabel = findViewById(R.id.tv_session_code)
        memberCountLabel = findViewById(R.id.tv_member_count)
        exportButton = findViewById(R.id.btn_export_share)
        importButton = findViewById(R.id.btn_import_share)
        computeButton = findViewById(R.id.btn_compute_intersection)
        weekSpinner = findViewById(R.id.spinner_week)
        resultsContainer = findViewById(R.id.container_results)
    }

    private fun setupRepositories() {
        val database = AppDatabase.getInstance(this)
        courseDao = database.courseDao()
        settingsRepository = SettingsRepository(database.settingsDao(), database.semesterDao())
        groupSyncRepository = GroupSyncRepository(database.groupSyncDao())

        lifecycleScope.launch {
            settingsRepository.currentSemesterId.collectLatest { semesterId ->
                currentSemesterId = semesterId
            }
        }
        lifecycleScope.launch {
            settingsRepository.periodTimes.collectLatest { times ->
                periodTimes = times
            }
        }
        lifecycleScope.launch {
            settingsRepository.observeSemesterStartDate().collectLatest { date ->
                semesterStartDate = date ?: LocalDate.now()
                currentWeek = getCurrentWeek()
                if (activeSession != null) {
                    updateWeekOptions(activeSession!!.totalWeeks)
                }
            }
        }
    }

    private fun setupListeners() {
        findViewById<MaterialButton>(R.id.btn_create_session).setOnClickListener {
            if (currentSemesterId <= 0L) {
                Toast.makeText(this, "学期信息未就绪", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val code = generateSyncCode()
            val totalWeeks = DEFAULT_TOTAL_WEEKS
            val periodCount = derivePeriodCount()
            lifecycleScope.launch {
                val session = withContext(Dispatchers.IO) {
                    groupSyncRepository.createSession(code, currentSemesterId, totalWeeks, periodCount)
                }
                setActiveSession(session)
                Toast.makeText(this@GroupSyncActivity, "已创建组队：$code", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.btn_join_session).setOnClickListener {
            val code = codeInput.text?.toString()?.trim()?.uppercase().orEmpty()
            if (code.isBlank()) {
                Toast.makeText(this, "请输入Sync Code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentSemesterId <= 0L) {
                Toast.makeText(this, "学期信息未就绪", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val periodCount = derivePeriodCount()
            lifecycleScope.launch {
                val session = withContext(Dispatchers.IO) {
                    groupSyncRepository.getSessionByCode(code)
                        ?: groupSyncRepository.createSession(code, currentSemesterId, DEFAULT_TOTAL_WEEKS, periodCount)
                }
                setActiveSession(session)
                Toast.makeText(this@GroupSyncActivity, "已加入组队：$code", Toast.LENGTH_SHORT).show()
            }
        }

        exportButton.setOnClickListener {
            val session = activeSession
            if (session == null) {
                Toast.makeText(this, "请先创建或加入组队", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showExportDialog(session)
        }

        importButton.setOnClickListener {
            if (currentSemesterId <= 0L) {
                Toast.makeText(this, "学期信息未就绪", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            importLauncher.launch(arrayOf("application/json"))
        }

        computeButton.setOnClickListener {
            computeIntersection()
        }

        weekSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                selectedWeek = position + 1
                renderIntersection()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setActiveSession(session: GroupSyncSessionEntity) {
        activeSession = session
        currentIntersection = null
        updateSessionUi(session)
        updateWeekOptions(session.totalWeeks)
        observeShares(session.id)
    }

    private fun observeShares(sessionId: Long) {
        shareJob?.cancel()
        shareJob = lifecycleScope.launch {
            groupSyncRepository.observeShares(sessionId).collectLatest { shares ->
                activeShares = shares
                memberCountLabel.text = "成员数：${shares.size}"
                currentIntersection = null
                renderIntersection()
            }
        }
    }

    private fun updateSessionUi(session: GroupSyncSessionEntity?) {
        if (session == null) {
            sessionLabel.text = "当前组队：未选择"
            memberCountLabel.text = "成员数：0"
            exportButton.isEnabled = false
            importButton.isEnabled = true
            computeButton.isEnabled = false
            resultsContainer.removeAllViews()
            return
        }
        sessionLabel.text = "当前组队：${session.code}"
        exportButton.isEnabled = true
        importButton.isEnabled = true
        computeButton.isEnabled = true
    }

    private fun updateWeekOptions(totalWeeks: Int) {
        val weeks = (1..totalWeeks).map { "第${it}周" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, weeks)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        weekSpinner.adapter = adapter
        val defaultWeek = currentWeek.coerceIn(1, totalWeeks)
        selectedWeek = defaultWeek
        weekSpinner.setSelection(defaultWeek - 1, false)
    }

    private fun showExportDialog(session: GroupSyncSessionEntity) {
        val input = android.widget.EditText(this).apply {
            hint = "成员名称"
            setText("我")
        }
        AlertDialog.Builder(this)
            .setTitle("导出我的空闲")
            .setView(input)
            .setPositiveButton("导出") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "我" }
                exportMyAvailability(session, name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportMyAvailability(session: GroupSyncSessionEntity, memberName: String) {
        lifecycleScope.launch {
            val freeSlots = withContext(Dispatchers.IO) {
                buildFreeSlots(session)
            }
            withContext(Dispatchers.IO) {
                groupSyncRepository.upsertShare(session.id, memberName, freeSlots)
            }
            val payload = GroupSyncPayload(
                code = session.code,
                memberName = memberName,
                totalWeeks = session.totalWeeks,
                periodCount = session.periodCount,
                freeSlots = freeSlots,
                createdAt = System.currentTimeMillis()
            )
            pendingExportPayload = payload
            val fileName = "sync_${session.code}_${memberName}.json"
            exportLauncher.launch(fileName)
        }
    }

    private fun importShareFromUri(uri: Uri) {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: ""
            }
            val payload = GroupSyncSerializer.fromJson(json)
            if (payload == null) {
                Toast.makeText(this@GroupSyncActivity, "导入失败：文件格式不正确", Toast.LENGTH_SHORT).show()
                return@launch
            }
            handleImportedPayload(payload)
        }
    }

    private suspend fun handleImportedPayload(payload: GroupSyncPayload) {
        if (currentSemesterId <= 0L) {
            Toast.makeText(this, "学期信息未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        val session = activeSession
        val resolvedSession = if (session == null) {
            withContext(Dispatchers.IO) {
                groupSyncRepository.getSessionByCode(payload.code)
                    ?: groupSyncRepository.createSession(
                        code = payload.code,
                        semesterId = currentSemesterId,
                        totalWeeks = payload.totalWeeks,
                        periodCount = payload.periodCount
                    )
            }
        } else {
            session
        }
        if (resolvedSession.code != payload.code) {
            Toast.makeText(this, "Sync Code不一致，请切换组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (resolvedSession.totalWeeks != payload.totalWeeks ||
            resolvedSession.periodCount != payload.periodCount
        ) {
            Toast.makeText(this, "导入失败：成员课表配置不一致", Toast.LENGTH_SHORT).show()
            return
        }
        val expectedLength = resolvedSession.totalWeeks * 7 * resolvedSession.periodCount
        if (payload.freeSlots.length != expectedLength) {
            Toast.makeText(this, "导入失败：空闲数据长度不匹配", Toast.LENGTH_SHORT).show()
            return
        }
        withContext(Dispatchers.IO) {
            groupSyncRepository.upsertShare(
                sessionId = resolvedSession.id,
                memberName = payload.memberName,
                freeSlots = payload.freeSlots
            )
        }
        if (activeSession == null) {
            setActiveSession(resolvedSession)
        }
        Toast.makeText(this, "已导入成员空闲", Toast.LENGTH_SHORT).show()
    }

    private fun computeIntersection() {
        val session = activeSession
        if (session == null) {
            Toast.makeText(this, "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (activeShares.isEmpty()) {
            Toast.makeText(this, "请先导入成员空闲", Toast.LENGTH_SHORT).show()
            return
        }
        val length = activeShares.first().freeSlots.length
        if (activeShares.any { it.freeSlots.length != length }) {
            Toast.makeText(this, "成员数据长度不一致", Toast.LENGTH_SHORT).show()
            return
        }
        val result = CharArray(length) { '1' }
        activeShares.forEach { share ->
            val slots = share.freeSlots
            for (i in 0 until length) {
                if (slots[i] != '1') {
                    result[i] = '0'
                }
            }
        }
        currentIntersection = String(result)
        renderIntersection()
    }

    private fun renderIntersection() {
        val session = activeSession ?: return
        val intersection = currentIntersection
        resultsContainer.removeAllViews()
        if (intersection.isNullOrBlank()) {
            resultsContainer.addView(buildHintText("请先计算共同空闲"))
            return
        }
        val expectedLength = session.totalWeeks * 7 * session.periodCount
        if (intersection.length != expectedLength) {
            resultsContainer.addView(buildHintText("空闲数据异常"))
            return
        }
        val items = buildSlotSummary(intersection, session.periodCount, selectedWeek)
        if (items.isEmpty()) {
            resultsContainer.addView(buildHintText("本周暂无共同空闲时段"))
            return
        }
        val maxItems = 60
        items.take(maxItems).forEach { text ->
            resultsContainer.addView(buildResultItem(text))
        }
        if (items.size > maxItems) {
            val moreText = "还有 ${items.size - maxItems} 个时段未展示"
            resultsContainer.addView(buildHintText(moreText))
        }
    }

    private fun buildSlotSummary(
        intersection: String,
        periodCount: Int,
        weekFilter: Int
    ): List<String> {
        val results = mutableListOf<String>()
        val periodMap = periodTimes.associateBy { it.period }
        val fallbackTimes = defaultPeriodTimes()
        val blockSize = 7 * periodCount
        for (index in intersection.indices) {
            if (intersection[index] != '1') continue
            val week = index / blockSize + 1
            if (week != weekFilter) continue
            val offset = index % blockSize
            val day = offset / periodCount + 1
            val period = offset % periodCount + 1
            val label = buildString {
                append(formatWeekday(day))
                append(" 第")
                append(period)
                append("节")
                val time = periodMap[period]
                val fallback = fallbackTimes[period]
                val start = time?.startTime ?: fallback?.first
                val end = time?.endTime ?: fallback?.second
                if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
                    append(" (")
                    append(start)
                    append("-")
                    append(end)
                    append(")")
                }
            }
            results.add(label)
        }
        return results
    }

    private fun buildResultItem(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, dpToPx(6), 0, dpToPx(6))
        }
    }

    private fun buildHintText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dpToPx(6), 0, dpToPx(6))
        }
    }

    private suspend fun buildFreeSlots(session: GroupSyncSessionEntity): String {
        val totalWeeks = session.totalWeeks
        val periodCount = session.periodCount
        val size = totalWeeks * 7 * periodCount
        val busy = BooleanArray(size)
        val courses = courseDao.getAllCourses(session.semesterId)
        courses.forEach { course ->
            val day = course.dayOfWeek
            if (day !in 1..7) return@forEach
            val startPeriod = course.startPeriod.coerceAtLeast(1)
            val endPeriod = course.endPeriod.coerceAtMost(periodCount)
            if (startPeriod > endPeriod) return@forEach
            course.weekPattern.forEach { week ->
                if (week !in 1..totalWeeks) return@forEach
                for (period in startPeriod..endPeriod) {
                    val index = indexForSlot(week, day, period, periodCount)
                    if (index in 0 until size) {
                        busy[index] = true
                    }
                }
            }
        }
        val builder = StringBuilder(size)
        for (i in 0 until size) {
            builder.append(if (busy[i]) '0' else '1')
        }
        return builder.toString()
    }

    private fun indexForSlot(week: Int, day: Int, period: Int, periodCount: Int): Int {
        return ((week - 1) * 7 + (day - 1)) * periodCount + (period - 1)
    }

    private fun generateSyncCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = Random()
        return buildString {
            repeat(6) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    private fun derivePeriodCount(): Int {
        if (periodTimes.isEmpty()) {
            return DEFAULT_PERIOD_COUNT
        }
        return periodTimes.maxOf { it.period }.coerceAtLeast(1)
    }

    private fun defaultPeriodTimes(): Map<Int, Pair<String, String>> {
        return mapOf(
            1 to ("08:00" to "08:45"),
            2 to ("08:55" to "09:40"),
            3 to ("10:00" to "10:45"),
            4 to ("10:55" to "11:40"),
            5 to ("14:00" to "14:45"),
            6 to ("14:55" to "15:40"),
            7 to ("16:00" to "16:45"),
            8 to ("16:55" to "17:40")
        )
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

    private fun getCurrentWeek(): Int {
        val today = LocalDate.now()
        val daysDiff = ChronoUnit.DAYS.between(semesterStartDate, today)
        return ((daysDiff / 7).toInt() + 1).coerceAtLeast(1)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    companion object {
        private const val DEFAULT_TOTAL_WEEKS = 20
        private const val DEFAULT_PERIOD_COUNT = 8
    }
}
