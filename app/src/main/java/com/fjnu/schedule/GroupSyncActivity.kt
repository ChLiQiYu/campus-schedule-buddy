package com.fjnu.schedule

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fjnu.schedule.data.AppDatabase
import com.fjnu.schedule.data.CourseDao
import com.fjnu.schedule.data.PeriodTimeEntity
import com.fjnu.schedule.data.SettingsRepository
import com.fjnu.schedule.util.GroupSyncPayload
import com.fjnu.schedule.util.GroupSyncSerializer
import com.google.android.gms.tasks.Tasks
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Random

class GroupSyncActivity : AppCompatActivity() {

    private lateinit var courseDao: CourseDao
    private lateinit var settingsRepository: SettingsRepository
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var codeInput: TextInputEditText
    private lateinit var sessionLabel: TextView
    private lateinit var memberCountLabel: TextView
    private lateinit var memberListLabel: TextView
    private lateinit var exportButton: MaterialButton
    private lateinit var importButton: MaterialButton
    private lateinit var computeButton: MaterialButton
    private lateinit var weekSpinner: Spinner
    private lateinit var resultsContainer: LinearLayout

    private var activeSession: GroupSyncSession? = null
    private var activeMembers: List<GroupSyncMember> = emptyList()
    private var externalShares: List<GroupSyncExternalShare> = emptyList()
    private var currentIntersection: String? = null
    private var currentSemesterId: Long = 0L
    private var periodTimes: List<PeriodTimeEntity> = emptyList()
    private var semesterStartDate: LocalDate = LocalDate.now()
    private var currentWeek: Int = 1
    private var selectedWeek: Int = 1
    private var totalWeeks: Int = DEFAULT_TOTAL_WEEKS
    private var sessionListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null
    private var externalSharesListener: ListenerRegistration? = null
    private var pendingExportPayload: GroupSyncPayload? = null
    private var lastMemberNames: Set<String> = emptySet()
    private var hasLoadedShares = false
    private var authInProgress = false

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
        ensureAuthenticated()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_group_sync)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.group_sync_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_browse_public -> {
                ensureAuthenticated { showPublicSessionsDialog() }
                return true
            }
            R.id.action_invite_member -> {
                ensureAuthenticated { showInviteDialog() }
                return true
            }
            R.id.action_change_visibility -> {
                ensureAuthenticated { showVisibilityDialog() }
                return true
            }
            R.id.action_leave_session -> {
                ensureAuthenticated { leaveSession() }
                return true
            }
            R.id.action_disband_session -> {
                ensureAuthenticated { disbandSession() }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initViews() {
        codeInput = findViewById(R.id.et_sync_code)
        sessionLabel = findViewById(R.id.tv_session_code)
        memberCountLabel = findViewById(R.id.tv_member_count)
        memberListLabel = findViewById(R.id.tv_member_list)
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
            settingsRepository.scheduleSettings.collectLatest { settings ->
                totalWeeks = settings?.totalWeeks ?: DEFAULT_TOTAL_WEEKS
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
        findViewById<MaterialButton>(R.id.btn_join_session).setOnClickListener {
            val code = codeInput.text?.toString()?.trim()?.uppercase().orEmpty()
            if (code.isBlank()) {
                Toast.makeText(this, "请输入Sync Code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isValidSyncCode(code)) {
                Toast.makeText(this, "Sync Code格式不正确", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureAuthenticated {
                joinOrCreateSession(code)
            }
        }

        exportButton.setOnClickListener {
            val session = activeSession
            if (session == null) {
                Toast.makeText(this, "请先创建或加入组队", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureAuthenticated { showExportDialog(session) }
        }

        importButton.setOnClickListener {
            if (currentSemesterId <= 0L) {
                Toast.makeText(this, "学期信息未就绪", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureAuthenticated { importLauncher.launch(arrayOf("application/json")) }
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

    override fun onDestroy() {
        sessionListener?.remove()
        membersListener?.remove()
        externalSharesListener?.remove()
        super.onDestroy()
    }

    private fun setActiveSession(session: GroupSyncSession) {
        activeSession = session
        currentIntersection = null
        lastMemberNames = emptySet()
        hasLoadedShares = false
        updateSessionUi(session)
        updateWeekOptions(session.totalWeeks)
        observeSession(session.code)
        observeMembers(session.code)
        observeExternalShares(session.code)
    }

    private fun observeSession(code: String) {
        sessionListener?.remove()
        sessionListener = sessionDoc(code).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "同步失败：${error.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            val session = snapshot?.let { parseSession(it) } ?: return@addSnapshotListener
            val previous = activeSession
            activeSession = session
            updateSessionUi(session)
            if (previous?.status == STATUS_ACTIVE && session.status != STATUS_ACTIVE) {
                Toast.makeText(this, "组队已解散", Toast.LENGTH_SHORT).show()
            }
            if (previous?.totalWeeks != session.totalWeeks) {
                updateWeekOptions(session.totalWeeks)
            }
        }
    }

    private fun observeMembers(code: String) {
        membersListener?.remove()
        membersListener = membersCollection(code)
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "成员同步失败：${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                val members = snapshot?.documents?.mapNotNull { parseMember(it) } ?: emptyList()
                activeMembers = members
                val currentNames = members.map { it.displayName }.toSet()
                val newMembers = currentNames - lastMemberNames
                lastMemberNames = currentNames
                memberCountLabel.text = "成员数：${members.size}"
                memberListLabel.text = formatMemberList(currentNames)
                if (hasLoadedShares && newMembers.isNotEmpty()) {
                    val sessionIdSeed = (activeSession?.code?.hashCode() ?: 0).toLong()
                    notifyNewMembers(sessionIdSeed, newMembers.toList())
                }
                hasLoadedShares = true
                currentIntersection = null
                renderIntersection()
            }
    }

    private fun observeExternalShares(code: String) {
        externalSharesListener?.remove()
        externalSharesListener = externalSharesCollection(code)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "外部成员同步失败：${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                externalShares = snapshot?.documents?.mapNotNull { parseExternalShare(it) } ?: emptyList()
                currentIntersection = null
                renderIntersection()
            }
    }

    private fun updateSessionUi(session: GroupSyncSession?) {
        if (session == null) {
            sessionLabel.text = "当前组队：未选择"
            memberCountLabel.text = "成员数：0"
            memberListLabel.text = "成员：暂无"
            exportButton.isEnabled = false
            importButton.isEnabled = true
            computeButton.isEnabled = false
            resultsContainer.removeAllViews()
            return
        }
        val active = session.status == STATUS_ACTIVE
        val statusSuffix = if (active) "" else "（已解散）"
        sessionLabel.text = "当前组队：${session.code}$statusSuffix"
        exportButton.isEnabled = active
        importButton.isEnabled = active
        computeButton.isEnabled = active
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

    private fun joinOrCreateSession(code: String) {
        if (currentSemesterId <= 0L) {
            Toast.makeText(this, "学期信息未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        ensureDisplayName { displayName ->
            lifecycleScope.launch {
                val resolution = withContext(Dispatchers.IO) {
                    resolveSessionForCode(code, displayName)
                }
                when (resolution) {
                    is SessionResolution.Success -> {
                        setActiveSession(resolution.session)
                        Toast.makeText(this@GroupSyncActivity, resolution.message, Toast.LENGTH_SHORT).show()
                    }
                    is SessionResolution.Error -> {
                        Toast.makeText(this@GroupSyncActivity, resolution.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showPublicSessionsDialog() {
        lifecycleScope.launch {
            val sessions = withContext(Dispatchers.IO) { fetchPublicSessions() }
            if (sessions.isEmpty()) {
                Toast.makeText(this@GroupSyncActivity, "暂无公开组队", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = sessions.map { session ->
                val visibilityLabel = when (session.visibility) {
                    VISIBILITY_PUBLIC -> "公开"
                    VISIBILITY_PRIVATE -> "私有"
                    else -> "邀请制"
                }
                "${session.code} · $visibilityLabel"
            }
            AlertDialog.Builder(this@GroupSyncActivity)
                .setTitle("公开组队")
                .setItems(labels.toTypedArray()) { _, index ->
                    joinOrCreateSession(sessions[index].code)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun showInviteDialog() {
        val session = activeSession
        val uid = auth.currentUser?.uid
        if (session == null || uid == null) {
            Toast.makeText(this, "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (session.status != STATUS_ACTIVE) {
            Toast.makeText(this, "当前组队已解散", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val inviteCode = generateInviteCode()
            val now = System.currentTimeMillis()
            val inviteData = mapOf(
                "code" to inviteCode,
                "sessionCode" to session.code,
                "createdBy" to uid,
                "createdAt" to now,
                "expiresAt" to now + INVITE_EXPIRES_MS,
                "usedBy" to null,
                "usedAt" to null
            )
            withContext(Dispatchers.IO) {
                Tasks.await(invitesCollection().document(inviteCode).set(inviteData))
            }
            AlertDialog.Builder(this@GroupSyncActivity)
                .setTitle("邀请成员")
                .setMessage("邀请码：$inviteCode\n有效期24小时，可分享给队友加入。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun showVisibilityDialog() {
        val session = activeSession
        val uid = auth.currentUser?.uid
        if (session == null || uid == null) {
            Toast.makeText(this, "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (session.ownerUid != uid) {
            Toast.makeText(this, "只有队长可以修改可见性", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf("公开", "私有", "邀请制")
        AlertDialog.Builder(this)
            .setTitle("队伍可见性")
            .setItems(options) { _, index ->
                val newVisibility = when (index) {
                    0 -> VISIBILITY_PUBLIC
                    1 -> VISIBILITY_PRIVATE
                    else -> VISIBILITY_INVITE
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    Tasks.await(
                        sessionDoc(session.code).set(
                            mapOf(
                                "visibility" to newVisibility,
                                "updatedAt" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        )
                    )
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun leaveSession() {
        val session = activeSession
        val uid = auth.currentUser?.uid
        if (session == null || uid == null) {
            Toast.makeText(this, "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (session.ownerUid == uid) {
            Toast.makeText(this, "队长不能直接退出，请先解散组队", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("退出组队")
            .setMessage("确定要退出当前组队吗？")
            .setPositiveButton("退出") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    Tasks.await(membersCollection(session.code).document(uid).delete())
                    withContext(Dispatchers.Main) {
                        clearActiveSession()
                        Toast.makeText(this@GroupSyncActivity, "已退出组队", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun disbandSession() {
        val session = activeSession
        val uid = auth.currentUser?.uid
        if (session == null || uid == null) {
            Toast.makeText(this, "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (session.ownerUid != uid) {
            Toast.makeText(this, "只有队长可以解散组队", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("解散组队")
            .setMessage("解散后成员将无法继续访问该组队，确定解散？")
            .setPositiveButton("解散") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    Tasks.await(
                        sessionDoc(session.code).set(
                            mapOf(
                                "status" to STATUS_DISBANDED,
                                "updatedAt" to System.currentTimeMillis(),
                                "disbandedAt" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        )
                    )
                    withContext(Dispatchers.Main) {
                        clearActiveSession()
                        Toast.makeText(this@GroupSyncActivity, "已解散组队", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearActiveSession() {
        activeSession = null
        activeMembers = emptyList()
        externalShares = emptyList()
        currentIntersection = null
        sessionListener?.remove()
        membersListener?.remove()
        externalSharesListener?.remove()
        updateSessionUi(null)
        resultsContainer.removeAllViews()
    }

    private fun ensureAuthenticated(onReady: (() -> Unit)? = null) {
        if (auth.currentUser != null) {
            onReady?.invoke()
            return
        }
        if (authInProgress) return
        authInProgress = true
        signInAnonymously(onReady)
    }

    private fun signInAnonymously(onReady: (() -> Unit)? = null) {
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                authInProgress = false
                if (task.isSuccessful) {
                    Toast.makeText(this, "已使用匿名身份连接", Toast.LENGTH_SHORT).show()
                    onReady?.invoke()
                } else {
                    Toast.makeText(this, "匿名登录失败", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun ensureDisplayName(onReady: (String) -> Unit) {
        val saved = getSavedDisplayName()
        if (!saved.isNullOrBlank()) {
            onReady(saved)
            return
        }
        val input = EditText(this).apply {
            hint = "匿名昵称"
            setText("我")
        }
        AlertDialog.Builder(this)
            .setTitle("设置匿名昵称")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "我" }
                saveDisplayName(name)
                onReady(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getSavedDisplayName(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_MEMBER_NAME, null)
    }

    private fun saveDisplayName(name: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_MEMBER_NAME, name)
            .apply()
    }

    private suspend fun resolveSessionForCode(
        code: String,
        displayName: String
    ): SessionResolution {
        val uid = auth.currentUser?.uid ?: return SessionResolution.Error("未登录")
        return if (isInviteCode(code)) {
            resolveInvite(code, uid, displayName)
        } else {
            resolveSessionCode(code, uid, displayName)
        }
    }

    private suspend fun resolveInvite(
        inviteCode: String,
        uid: String,
        displayName: String
    ): SessionResolution {
        val inviteSnapshot = Tasks.await(invitesCollection().document(inviteCode).get())
        if (!inviteSnapshot.exists()) {
            return SessionResolution.Error("邀请码无效或已过期")
        }
        val sessionCode = inviteSnapshot.getString("sessionCode").orEmpty()
        val usedBy = inviteSnapshot.getString("usedBy")
        val expiresAt = inviteSnapshot.getLong("expiresAt") ?: 0L
        if (sessionCode.isBlank()) {
            return SessionResolution.Error("邀请码数据异常")
        }
        if (!usedBy.isNullOrBlank()) {
            return SessionResolution.Error("邀请码已被使用")
        }
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            return SessionResolution.Error("邀请码已过期")
        }
        val session = fetchSession(sessionCode) ?: return SessionResolution.Error("组队不存在")
        if (session.status != STATUS_ACTIVE) {
            return SessionResolution.Error("该组队已解散")
        }
        val joinResult = joinSession(session, uid, displayName, fromInvite = true)
        if (!joinResult) {
            return SessionResolution.Error("加入组队失败")
        }
        Tasks.await(
            invitesCollection().document(inviteCode).set(
                mapOf(
                    "usedBy" to uid,
                    "usedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        )
        return SessionResolution.Success(session, "已通过邀请加入组队：$sessionCode")
    }

    private suspend fun resolveSessionCode(
        code: String,
        uid: String,
        displayName: String
    ): SessionResolution {
        val existing = fetchSession(code)
        val periodCount = derivePeriodCount()
        val session = if (existing == null) {
            if (!isSessionCode(code)) {
                return SessionResolution.Error("Sync Code格式不正确")
            }
            createSession(
                code = code,
                semesterId = currentSemesterId,
                totalWeeks = totalWeeks,
                periodCount = periodCount,
                visibility = VISIBILITY_INVITE
            )
        } else {
            existing
        } ?: return SessionResolution.Error("创建组队失败")
        if (session.status != STATUS_ACTIVE) {
            return SessionResolution.Error("该组队已解散")
        }
        if (session.visibility == VISIBILITY_PRIVATE && session.ownerUid != uid) {
            return SessionResolution.Error("该组队为私有，仅队长可访问")
        }
        if (session.visibility == VISIBILITY_INVITE && session.ownerUid != uid) {
            return SessionResolution.Error("该组队为邀请制，请使用邀请码加入")
        }
        val joinResult = joinSession(session, uid, displayName, fromInvite = false)
        if (!joinResult) {
            return SessionResolution.Error("加入组队失败")
        }
        val message = if (existing == null) "已创建组队：$code" else "已加入组队：$code"
        return SessionResolution.Success(session, message)
    }

    private fun sessionDoc(code: String) =
        firestore.collection(COLLECTION_SESSIONS).document(code)

    private fun membersCollection(code: String) =
        sessionDoc(code).collection(COLLECTION_MEMBERS)

    private fun externalSharesCollection(code: String) =
        sessionDoc(code).collection(COLLECTION_EXTERNAL_SHARES)

    private fun invitesCollection() =
        firestore.collection(COLLECTION_INVITES)

    private suspend fun fetchSession(code: String): GroupSyncSession? {
        return try {
            val snapshot = Tasks.await(sessionDoc(code).get())
            parseSession(snapshot)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchPublicSessions(): List<GroupSyncSession> {
        return try {
            val snapshot = Tasks.await(
                firestore.collection(COLLECTION_SESSIONS)
                    .whereEqualTo("visibility", VISIBILITY_PUBLIC)
                    .whereEqualTo("status", STATUS_ACTIVE)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(30)
                    .get()
            )
            snapshot.documents.mapNotNull { parseSession(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun createSession(
        code: String,
        semesterId: Long,
        totalWeeks: Int,
        periodCount: Int,
        visibility: String
    ): GroupSyncSession? {
        val uid = auth.currentUser?.uid ?: return null
        val displayName = getSavedDisplayName().orEmpty()
        val now = System.currentTimeMillis()
        val session = GroupSyncSession(
            code = code,
            ownerUid = uid,
            ownerName = displayName,
            semesterId = semesterId,
            totalWeeks = totalWeeks,
            periodCount = periodCount,
            visibility = visibility,
            status = STATUS_ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        return try {
            Tasks.await(sessionDoc(code).set(session.toMap()))
            val member = GroupSyncMember(
                uid = uid,
                displayName = displayName.ifBlank { "我" },
                role = ROLE_OWNER,
                joinedAt = now,
                freeSlots = null
            )
            Tasks.await(membersCollection(code).document(uid).set(member.toMap()))
            session
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun joinSession(
        session: GroupSyncSession,
        uid: String,
        displayName: String,
        fromInvite: Boolean
    ): Boolean {
        val role = if (session.ownerUid == uid) ROLE_OWNER else ROLE_MEMBER
        val member = GroupSyncMember(
            uid = uid,
            displayName = displayName,
            role = role,
            joinedAt = System.currentTimeMillis(),
            freeSlots = null
        )
        return try {
            Tasks.await(
                membersCollection(session.code).document(uid).set(member.toMap(), SetOptions.merge())
            )
            if (fromInvite) {
                Tasks.await(
                    sessionDoc(session.code).set(
                        mapOf("updatedAt" to System.currentTimeMillis()),
                        SetOptions.merge()
                    )
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun upsertMemberAvailability(
        code: String,
        uid: String,
        displayName: String,
        freeSlots: String
    ) {
        val role = if (activeSession?.ownerUid == uid) ROLE_OWNER else ROLE_MEMBER
        val payload = mapOf(
            "uid" to uid,
            "displayName" to displayName,
            "role" to role,
            "joinedAt" to System.currentTimeMillis(),
            "freeSlots" to freeSlots
        )
        Tasks.await(membersCollection(code).document(uid).set(payload, SetOptions.merge()))
    }

    private suspend fun addExternalShare(code: String, memberName: String, freeSlots: String) {
        val payload = mapOf(
            "memberName" to memberName,
            "freeSlots" to freeSlots,
            "createdAt" to System.currentTimeMillis()
        )
        Tasks.await(externalSharesCollection(code).document().set(payload))
    }

    private fun parseSession(snapshot: com.google.firebase.firestore.DocumentSnapshot): GroupSyncSession? {
        if (!snapshot.exists()) return null
        val code = snapshot.getString("code") ?: snapshot.id
        val ownerUid = snapshot.getString("ownerUid") ?: return null
        val ownerName = snapshot.getString("ownerName").orEmpty()
        val semesterId = snapshot.getLong("semesterId") ?: 0L
        val totalWeeks = (snapshot.getLong("totalWeeks") ?: 0L).toInt()
        val periodCount = (snapshot.getLong("periodCount") ?: 0L).toInt()
        val visibility = snapshot.getString("visibility") ?: VISIBILITY_INVITE
        val status = snapshot.getString("status") ?: STATUS_ACTIVE
        val createdAt = snapshot.getLong("createdAt") ?: 0L
        val updatedAt = snapshot.getLong("updatedAt") ?: createdAt
        if (totalWeeks <= 0 || periodCount <= 0) return null
        return GroupSyncSession(
            code = code,
            ownerUid = ownerUid,
            ownerName = ownerName,
            semesterId = semesterId,
            totalWeeks = totalWeeks,
            periodCount = periodCount,
            visibility = visibility,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseMember(snapshot: com.google.firebase.firestore.DocumentSnapshot): GroupSyncMember? {
        if (!snapshot.exists()) return null
        val uid = snapshot.getString("uid") ?: snapshot.id
        val displayName = snapshot.getString("displayName") ?: "成员"
        val role = snapshot.getString("role") ?: ROLE_MEMBER
        val joinedAt = snapshot.getLong("joinedAt") ?: 0L
        val freeSlots = snapshot.getString("freeSlots")
        return GroupSyncMember(
            uid = uid,
            displayName = displayName,
            role = role,
            joinedAt = joinedAt,
            freeSlots = freeSlots
        )
    }

    private fun parseExternalShare(snapshot: com.google.firebase.firestore.DocumentSnapshot): GroupSyncExternalShare? {
        if (!snapshot.exists()) return null
        val memberName = snapshot.getString("memberName") ?: return null
        val freeSlots = snapshot.getString("freeSlots") ?: return null
        val createdAt = snapshot.getLong("createdAt") ?: 0L
        return GroupSyncExternalShare(memberName, freeSlots, createdAt)
    }

    private fun collectFreeSlotsSources(): List<FreeSlotsSource> {
        val sources = mutableListOf<FreeSlotsSource>()
        activeMembers.forEach { member ->
            val freeSlots = member.freeSlots
            if (!freeSlots.isNullOrBlank()) {
                sources.add(FreeSlotsSource(member.displayName, freeSlots))
            }
        }
        externalShares.forEach { share ->
            sources.add(FreeSlotsSource(share.memberName, share.freeSlots))
        }
        return sources
    }

    private fun showExportDialog(session: GroupSyncSession) {
        val input = EditText(this).apply {
            hint = "成员名称"
            setText(getSavedDisplayName())
        }
        AlertDialog.Builder(this)
            .setTitle("导出我的空闲")
            .setView(input)
            .setPositiveButton("导出") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "我" }
                saveDisplayName(name)
                exportMyAvailability(session, name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportMyAvailability(session: GroupSyncSession, memberName: String) {
        ensureAuthenticated {
            val uid = auth.currentUser?.uid ?: return@ensureAuthenticated
            lifecycleScope.launch {
                val freeSlots = withContext(Dispatchers.IO) {
                    buildFreeSlots(session)
                }
                withContext(Dispatchers.IO) {
                    upsertMemberAvailability(session.code, uid, memberName, freeSlots)
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
        val normalizedCode = payload.code.trim().uppercase()
        val session = withContext(Dispatchers.IO) {
            fetchSession(normalizedCode) ?: createSession(
                code = normalizedCode,
                semesterId = currentSemesterId,
                totalWeeks = payload.totalWeeks,
                periodCount = payload.periodCount,
                visibility = VISIBILITY_INVITE
            )
        }
        if (session == null) {
            Toast.makeText(this, "导入失败：无法创建组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (session.totalWeeks != payload.totalWeeks ||
            session.periodCount != payload.periodCount
        ) {
            Toast.makeText(this, "导入失败：成员课表配置不一致", Toast.LENGTH_SHORT).show()
            return
        }
        val expectedLength = session.totalWeeks * 7 * session.periodCount
        if (payload.freeSlots.length != expectedLength) {
            Toast.makeText(this, "导入失败：空闲数据长度不匹配", Toast.LENGTH_SHORT).show()
            return
        }
        withContext(Dispatchers.IO) {
            addExternalShare(session.code, payload.memberName, payload.freeSlots)
        }
        if (activeSession == null || activeSession?.code != session.code) {
            setActiveSession(session)
        }
        Toast.makeText(this, "已导入成员空闲", Toast.LENGTH_SHORT).show()
    }

    private fun computeIntersection() {
        val session = activeSession
        if (session == null) {
            Toast.makeText(this, "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        val shareSources = collectFreeSlotsSources()
        if (shareSources.isEmpty()) {
            Toast.makeText(this, "请先上传或导入成员空闲", Toast.LENGTH_SHORT).show()
            return
        }
        val length = shareSources.first().freeSlots.length
        if (shareSources.any { it.freeSlots.length != length }) {
            Toast.makeText(this, "成员数据长度不一致", Toast.LENGTH_SHORT).show()
            return
        }
        val result = CharArray(length) { '1' }
        shareSources.forEach { share ->
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

    private suspend fun buildFreeSlots(session: GroupSyncSession): String {
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

    private fun isSessionCode(code: String): Boolean {
        return code.matches(Regex("^[A-Z0-9]{6}$"))
    }

    private fun isInviteCode(code: String): Boolean {
        return code.matches(Regex("^I[A-Z0-9]{7}$"))
    }

    private fun isValidSyncCode(code: String): Boolean {
        return isSessionCode(code) || isInviteCode(code)
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = Random()
        return buildString {
            append('I')
            repeat(7) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    private fun formatMemberList(names: Set<String>): String {
        if (names.isEmpty()) return "成员：暂无"
        return "成员：" + names.sorted().joinToString("、")
    }

    private fun notifyNewMembers(sessionId: Long, members: List<String>) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return
            }
        }
        ensureGroupSyncChannel(manager)
        members.forEach { member ->
            val notification = NotificationCompat.Builder(this, GROUP_SYNC_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_schedule)
                .setContentTitle("新的组队成员加入")
                .setContentText("成员 $member 已加入当前组队")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify((sessionId.toInt() shl 8) + member.hashCode().coerceIn(1, 255), notification)
        }
    }

    private fun ensureGroupSyncChannel(manager: NotificationManager) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            GROUP_SYNC_CHANNEL_ID,
            "组队通知",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private sealed class SessionResolution {
        data class Success(val session: GroupSyncSession, val message: String) : SessionResolution()
        data class Error(val message: String) : SessionResolution()
    }

    private data class GroupSyncSession(
        val code: String,
        val ownerUid: String,
        val ownerName: String,
        val semesterId: Long,
        val totalWeeks: Int,
        val periodCount: Int,
        val visibility: String,
        val status: String,
        val createdAt: Long,
        val updatedAt: Long
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "code" to code,
            "ownerUid" to ownerUid,
            "ownerName" to ownerName,
            "semesterId" to semesterId,
            "totalWeeks" to totalWeeks,
            "periodCount" to periodCount,
            "visibility" to visibility,
            "status" to status,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }

    private data class GroupSyncMember(
        val uid: String,
        val displayName: String,
        val role: String,
        val joinedAt: Long,
        val freeSlots: String?
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "uid" to uid,
            "displayName" to displayName,
            "role" to role,
            "joinedAt" to joinedAt,
            "freeSlots" to freeSlots
        )
    }

    private data class GroupSyncExternalShare(
        val memberName: String,
        val freeSlots: String,
        val createdAt: Long
    )

    private data class FreeSlotsSource(
        val name: String,
        val freeSlots: String
    )

    companion object {
        private const val DEFAULT_TOTAL_WEEKS = 20
        private const val DEFAULT_PERIOD_COUNT = 8
        private const val GROUP_SYNC_CHANNEL_ID = "group_sync_notifications"
        private const val VISIBILITY_PUBLIC = "PUBLIC"
        private const val VISIBILITY_PRIVATE = "PRIVATE"
        private const val VISIBILITY_INVITE = "INVITE"
        private const val STATUS_ACTIVE = "ACTIVE"
        private const val STATUS_DISBANDED = "DISBANDED"
        private const val ROLE_OWNER = "OWNER"
        private const val ROLE_MEMBER = "MEMBER"
        private const val COLLECTION_SESSIONS = "group_sync_sessions"
        private const val COLLECTION_MEMBERS = "members"
        private const val COLLECTION_EXTERNAL_SHARES = "external_shares"
        private const val COLLECTION_INVITES = "group_sync_invites"
        private const val INVITE_EXPIRES_MS = 24 * 60 * 60 * 1000L
        private const val PREFS_NAME = "group_sync_prefs"
        private const val KEY_MEMBER_NAME = "member_name"
    }
}
