package com.fjnu.schedule

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
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

class GroupSyncFragment : Fragment(R.layout.fragment_group_sync) {

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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                output.write(GroupSyncSerializer.toJson(payload).toByteArray(Charsets.UTF_8))
                output.flush()
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "导出完成", Toast.LENGTH_SHORT).show()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar(view)
        initViews(view)
        setupRepositories()
        setupListeners(view)
        updateSessionUi(null)
        ensureAuthenticated()
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar_group_sync)
        toolbar.inflateMenu(R.menu.group_sync_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_browse_public -> {
                    ensureAuthenticated { showPublicSessionsDialog() }
                    true
                }
                R.id.action_invite_member -> {
                    ensureAuthenticated { showInviteDialog() }
                    true
                }
                R.id.action_leave_session -> {
                    ensureAuthenticated { leaveSession() }
                    true
                }
                R.id.action_disband_session -> {
                    ensureAuthenticated { disbandSession() }
                    true
                }
                else -> false
            }
        }
    }

    private fun initViews(view: View) {
        codeInput = view.findViewById(R.id.et_sync_code)
        sessionLabel = view.findViewById(R.id.tv_session_code)
        memberCountLabel = view.findViewById(R.id.tv_member_count)
        memberListLabel = view.findViewById(R.id.tv_member_list)
        exportButton = view.findViewById(R.id.btn_export_share)
        importButton = view.findViewById(R.id.btn_import_share)
        computeButton = view.findViewById(R.id.btn_compute_intersection)
        weekSpinner = view.findViewById(R.id.spinner_week)
        resultsContainer = view.findViewById(R.id.container_results)
    }

    private fun setupRepositories() {
        val database = AppDatabase.getInstance(requireContext())
        courseDao = database.courseDao()
        settingsRepository = SettingsRepository(database.settingsDao(), database.semesterDao())

        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.currentSemesterId.collectLatest { semesterId ->
                currentSemesterId = semesterId
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.periodTimes.collectLatest { times ->
                periodTimes = times
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.scheduleSettings.collectLatest { settings ->
                totalWeeks = settings?.totalWeeks ?: DEFAULT_TOTAL_WEEKS
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.observeSemesterStartDate().collectLatest { date ->
                semesterStartDate = date ?: LocalDate.now()
                currentWeek = getCurrentWeek()
                if (activeSession != null) {
                    updateWeekOptions(activeSession!!.totalWeeks)
                }
            }
        }
    }

    private fun setupListeners(view: View) {
        view.findViewById<MaterialButton>(R.id.btn_join_session).setOnClickListener {
            val code = codeInput.text?.toString()?.trim()?.uppercase().orEmpty()
            if (code.isBlank()) {
                Toast.makeText(requireContext(), "请输入Sync Code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isValidSyncCode(code)) {
                Toast.makeText(requireContext(), "Sync Code格式不正确", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureAuthenticated {
                joinOrCreateSession(code)
            }
        }

        exportButton.setOnClickListener {
            val session = activeSession
            if (session == null) {
                Toast.makeText(requireContext(), "请先创建或加入组队", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureAuthenticated { showExportDialog(session) }
        }

        importButton.setOnClickListener {
            if (currentSemesterId <= 0L) {
                Toast.makeText(requireContext(), "学期信息未就绪", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureAuthenticated { importLauncher.launch(arrayOf("application/json")) }
        }

        computeButton.setOnClickListener {
            computeIntersection()
        }

        weekSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
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

    override fun onDestroyView() {
        sessionListener?.remove()
        membersListener?.remove()
        externalSharesListener?.remove()
        super.onDestroyView()
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
                Toast.makeText(requireContext(), "同步失败：${error.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            val session = snapshot?.let { parseSession(it) } ?: return@addSnapshotListener
            val previous = activeSession
            activeSession = session
            updateSessionUi(session)
            if (previous?.status == STATUS_ACTIVE && session.status != STATUS_ACTIVE) {
                Toast.makeText(requireContext(), "组队已解散", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "成员同步失败：${error.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "外部成员同步失败：${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                externalShares = snapshot?.documents?.mapNotNull { parseExternalShare(it) } ?: emptyList()
                currentIntersection = null
                renderIntersection()
            }
    }

    private fun updateSessionUi(session: GroupSyncSession?) {
        if (!isAdded) return
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
        if (!isAdded) return
        val weeks = (1..totalWeeks).map { "第${it}周" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, weeks)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        weekSpinner.adapter = adapter
        val defaultWeek = currentWeek.coerceIn(1, totalWeeks)
        selectedWeek = defaultWeek
        weekSpinner.setSelection(defaultWeek - 1, false)
    }

    private fun joinOrCreateSession(code: String) {
        if (currentSemesterId <= 0L) {
            Toast.makeText(requireContext(), "学期信息未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        ensureDisplayName { displayName ->
            viewLifecycleOwner.lifecycleScope.launch {
                val resolution = withContext(Dispatchers.IO) {
                    resolveSessionForCode(code, displayName)
                }
                when (resolution) {
                    is SessionResolution.Success -> {
                        setActiveSession(resolution.session)
                        Toast.makeText(requireContext(), resolution.message, Toast.LENGTH_SHORT).show()
                    }
                    is SessionResolution.Error -> {
                        Toast.makeText(requireContext(), resolution.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showPublicSessionsDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val sessions = withContext(Dispatchers.IO) { fetchPublicSessions() }
            if (sessions.isEmpty()) {
                Toast.makeText(requireContext(), "暂无公开组队", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = sessions.map { it.code }
            AlertDialog.Builder(requireContext())
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
            Toast.makeText(requireContext(), "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (session.status != STATUS_ACTIVE) {
            Toast.makeText(requireContext(), "当前组队已解散", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
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
            AlertDialog.Builder(requireContext())
                .setTitle("邀请成员")
                .setMessage("邀请码：$inviteCode\n有效期24小时，可分享给队友加入。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun leaveSession() {
        val session = activeSession
        val uid = auth.currentUser?.uid
        if (session == null || uid == null) {
            Toast.makeText(requireContext(), "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (session.ownerUid == uid) {
            Toast.makeText(requireContext(), "队长不能直接退出，请先解散组队", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("退出组队")
            .setMessage("确定要退出当前组队吗？")
            .setPositiveButton("退出") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    Tasks.await(membersCollection(session.code).document(uid).delete())
                    withContext(Dispatchers.Main) {
                        clearActiveSession()
                        Toast.makeText(requireContext(), "已退出组队", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (session.ownerUid != uid) {
            Toast.makeText(requireContext(), "只有队长可以解散组队", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("解散组队")
            .setMessage("解散后成员将无法继续访问该组队，确定解散？")
            .setPositiveButton("解散") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
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
                        Toast.makeText(requireContext(), "已解散组队", Toast.LENGTH_SHORT).show()
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
                    if (isAdded) {
                        Toast.makeText(requireContext(), "已使用匿名身份连接", Toast.LENGTH_SHORT).show()
                    }
                    onReady?.invoke()
                } else {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "匿名登录失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun ensureDisplayName(onReady: (String) -> Unit) {
        val saved = getSavedDisplayName()
        if (!saved.isNullOrBlank()) {
            onReady(saved)
            return
        }
        val input = EditText(requireContext()).apply {
            hint = "匿名昵称"
            setText("我")
        }
        AlertDialog.Builder(requireContext())
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
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MEMBER_NAME, null)
    }

    private fun saveDisplayName(name: String) {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_MEMBER_NAME, name) }
    }

    private fun resolveSessionForCode(code: String, displayName: String): SessionResolution {
        val uid = auth.currentUser?.uid ?: return SessionResolution.Error("未登录")
        return if (isInviteCode(code)) {
            resolveInvite(code, uid, displayName)
        } else {
            resolveSessionCode(code, uid, displayName)
        }
    }

    private fun resolveInvite(inviteCode: String, uid: String, displayName: String): SessionResolution {
        val inviteSnapshot = Tasks.await(invitesCollection().document(inviteCode).get())
        if (!inviteSnapshot.exists()) return SessionResolution.Error("邀请码无效或已过期")
        val sessionCode = inviteSnapshot.getString("sessionCode").orEmpty()
        val usedBy = inviteSnapshot.getString("usedBy")
        val expiresAt = inviteSnapshot.getLong("expiresAt") ?: 0L
        if (sessionCode.isBlank()) return SessionResolution.Error("邀请码数据异常")
        if (!usedBy.isNullOrBlank()) return SessionResolution.Error("邀请码已被使用")
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) return SessionResolution.Error("邀请码已过期")
        val session = fetchSession(sessionCode) ?: return SessionResolution.Error("组队不存在")
        if (session.status != STATUS_ACTIVE) return SessionResolution.Error("该组队已解散")
        ensurePublicVisibility(session.code)
        val joinResult = joinSession(session, uid, displayName, fromInvite = true)
        if (!joinResult) return SessionResolution.Error("加入组队失败")
        Tasks.await(invitesCollection().document(inviteCode).set(mapOf("usedBy" to uid, "usedAt" to System.currentTimeMillis()), SetOptions.merge()))
        return SessionResolution.Success(session, "已通过邀请加入组队：$sessionCode")
    }

    private fun resolveSessionCode(code: String, uid: String, displayName: String): SessionResolution {
        val existing = fetchSession(code)
        val periodCount = derivePeriodCount()
        val session = if (existing == null) {
            if (!isSessionCode(code)) return SessionResolution.Error("Sync Code格式不正确")
            createSession(code, currentSemesterId, totalWeeks, periodCount, VISIBILITY_PUBLIC)
        } else {
            ensurePublicVisibility(code)
            existing
        } ?: return SessionResolution.Error("创建组队失败")
        if (session.status != STATUS_ACTIVE) return SessionResolution.Error("该组队已解散")
        val joinResult = joinSession(session, uid, displayName, fromInvite = false)
        if (!joinResult) return SessionResolution.Error("加入组队失败")
        val message = if (existing == null) "已创建组队：$code" else "已加入组队：$code"
        return SessionResolution.Success(session, message)
    }

    private fun sessionDoc(code: String) = firestore.collection(COLLECTION_SESSIONS).document(code)
    private fun membersCollection(code: String) = sessionDoc(code).collection(COLLECTION_MEMBERS)
    private fun externalSharesCollection(code: String) = sessionDoc(code).collection(COLLECTION_EXTERNAL_SHARES)
    private fun invitesCollection() = firestore.collection(COLLECTION_INVITES)

    private fun fetchSession(code: String): GroupSyncSession? {
        return try {
            val snapshot = Tasks.await(sessionDoc(code).get())
            parseSession(snapshot)
        } catch (_: Exception) { null }
    }

    private fun fetchPublicSessions(): List<GroupSyncSession> {
        return try {
            val snapshot = Tasks.await(firestore.collection(COLLECTION_SESSIONS).whereEqualTo("status", STATUS_ACTIVE).orderBy("createdAt", Query.Direction.DESCENDING).limit(30).get())
            snapshot.documents.mapNotNull { parseSession(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun createSession(code: String, semesterId: Long, totalWeeks: Int, periodCount: Int, visibility: String): GroupSyncSession? {
        val uid = auth.currentUser?.uid ?: return null
        val displayName = getSavedDisplayName().orEmpty()
        val now = System.currentTimeMillis()
        val session = GroupSyncSession(code, uid, displayName, semesterId, totalWeeks, periodCount, VISIBILITY_PUBLIC, STATUS_ACTIVE, now, now)
        return try {
            Tasks.await(sessionDoc(code).set(session.toMap()))
            val member = GroupSyncMember(uid, displayName.ifBlank { "我" }, ROLE_OWNER, now, null)
            Tasks.await(membersCollection(code).document(uid).set(member.toMap()))
            session
        } catch (_: Exception) { null }
    }

    private fun joinSession(session: GroupSyncSession, uid: String, displayName: String, fromInvite: Boolean): Boolean {
        val role = if (session.ownerUid == uid) ROLE_OWNER else ROLE_MEMBER
        val member = GroupSyncMember(uid, displayName, role, System.currentTimeMillis(), null)
        return try {
            Tasks.await(membersCollection(session.code).document(uid).set(member.toMap(), SetOptions.merge()))
            if (fromInvite) {
                Tasks.await(sessionDoc(session.code).set(mapOf("updatedAt" to System.currentTimeMillis()), SetOptions.merge()))
            }
            true
        } catch (_: Exception) { false }
    }

    private fun upsertMemberAvailability(code: String, uid: String, displayName: String, freeSlots: String) {
        val role = if (activeSession?.ownerUid == uid) ROLE_OWNER else ROLE_MEMBER
        val payload = mapOf("uid" to uid, "displayName" to displayName, "role" to role, "joinedAt" to System.currentTimeMillis(), "freeSlots" to freeSlots)
        Tasks.await(membersCollection(code).document(uid).set(payload, SetOptions.merge()))
    }

    private fun addExternalShare(code: String, memberName: String, freeSlots: String) {
        val payload = mapOf("memberName" to memberName, "freeSlots" to freeSlots, "createdAt" to System.currentTimeMillis())
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
        val status = snapshot.getString("status") ?: STATUS_ACTIVE
        val createdAt = snapshot.getLong("createdAt") ?: 0L
        val updatedAt = snapshot.getLong("updatedAt") ?: createdAt
        if (totalWeeks <= 0 || periodCount <= 0) return null
        return GroupSyncSession(code, ownerUid, ownerName, semesterId, totalWeeks, periodCount, VISIBILITY_PUBLIC, status, createdAt, updatedAt)
    }

    private fun ensurePublicVisibility(code: String) {
        try {
            Tasks.await(sessionDoc(code).set(mapOf("visibility" to VISIBILITY_PUBLIC, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()))
        } catch (_: Exception) {}
    }

    private fun parseMember(snapshot: com.google.firebase.firestore.DocumentSnapshot): GroupSyncMember? {
        if (!snapshot.exists()) return null
        val uid = snapshot.getString("uid") ?: snapshot.id
        val displayName = snapshot.getString("displayName") ?: "成员"
        val role = snapshot.getString("role") ?: ROLE_MEMBER
        val joinedAt = snapshot.getLong("joinedAt") ?: 0L
        val freeSlots = snapshot.getString("freeSlots")
        return GroupSyncMember(uid, displayName, role, joinedAt, freeSlots)
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
            if (!freeSlots.isNullOrBlank()) sources.add(FreeSlotsSource(member.displayName, freeSlots))
        }
        externalShares.forEach { share ->
            sources.add(FreeSlotsSource(share.memberName, share.freeSlots))
        }
        return sources
    }

    private fun showExportDialog(session: GroupSyncSession) {
        val input = EditText(requireContext()).apply {
            hint = "成员名称"
            setText(getSavedDisplayName())
        }
        AlertDialog.Builder(requireContext())
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
            viewLifecycleOwner.lifecycleScope.launch {
                val freeSlots = withContext(Dispatchers.IO) { buildFreeSlots(session) }
                withContext(Dispatchers.IO) { upsertMemberAvailability(session.code, uid, memberName, freeSlots) }
                val payload = GroupSyncPayload(session.code, memberName, session.totalWeeks, session.periodCount, freeSlots, System.currentTimeMillis())
                pendingExportPayload = payload
                val fileName = "sync_${session.code}_${memberName}.json"
                exportLauncher.launch(fileName)
            }
        }
    }

    private fun importShareFromUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }
            val payload = GroupSyncSerializer.fromJson(json)
            if (payload == null) {
                Toast.makeText(requireContext(), "导入失败：文件格式不正确", Toast.LENGTH_SHORT).show()
                return@launch
            }
            handleImportedPayload(payload)
        }
    }

    private suspend fun handleImportedPayload(payload: GroupSyncPayload) {
        if (currentSemesterId <= 0L) {
            Toast.makeText(requireContext(), "学期信息未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        val normalizedCode = payload.code.trim().uppercase()
        val session = withContext(Dispatchers.IO) {
            fetchSession(normalizedCode) ?: createSession(normalizedCode, currentSemesterId, payload.totalWeeks, payload.periodCount, VISIBILITY_PUBLIC)
        }
        if (session == null) {
            Toast.makeText(requireContext(), "导入失败：无法创建组队", Toast.LENGTH_SHORT).show()
            return
        }
        if (session.totalWeeks != payload.totalWeeks || session.periodCount != payload.periodCount) {
            Toast.makeText(requireContext(), "导入失败：成员课表配置不一致", Toast.LENGTH_SHORT).show()
            return
        }
        val expectedLength = session.totalWeeks * 7 * session.periodCount
        if (payload.freeSlots.length != expectedLength) {
            Toast.makeText(requireContext(), "导入失败：空闲数据长度不匹配", Toast.LENGTH_SHORT).show()
            return
        }
        withContext(Dispatchers.IO) { addExternalShare(session.code, payload.memberName, payload.freeSlots) }
        if (activeSession == null || activeSession?.code != session.code) setActiveSession(session)
        Toast.makeText(requireContext(), "已导入成员空闲", Toast.LENGTH_SHORT).show()
    }

    private fun computeIntersection() {
        val session = activeSession
        if (session == null) {
            Toast.makeText(requireContext(), "请先创建或加入组队", Toast.LENGTH_SHORT).show()
            return
        }
        val shareSources = collectFreeSlotsSources()
        if (shareSources.isEmpty()) {
            Toast.makeText(requireContext(), "请先上传或导入成员空闲", Toast.LENGTH_SHORT).show()
            return
        }
        val length = shareSources.first().freeSlots.length
        if (shareSources.any { it.freeSlots.length != length }) {
            Toast.makeText(requireContext(), "成员数据长度不一致", Toast.LENGTH_SHORT).show()
            return
        }
        val result = CharArray(length) { '1' }
        shareSources.forEach { share ->
            val slots = share.freeSlots
            for (i in 0 until length) { if (slots[i] != '1') result[i] = '0' }
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
        items.take(maxItems).forEach { text -> resultsContainer.addView(buildResultItem(text)) }
        if (items.size > maxItems) {
            resultsContainer.addView(buildHintText("还有 ${items.size - maxItems} 个时段未展示"))
        }
    }

    private fun buildSlotSummary(intersection: String, periodCount: Int, weekFilter: Int): List<String> {
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
                val time = periodMap[period] ?: fallbackTimes[period]
                val start = if (time is Pair<*,*>) time.first as String else (time as? PeriodTimeEntity)?.startTime
                val end = if (time is Pair<*,*>) time.second as String else (time as? PeriodTimeEntity)?.endTime
                if (!start.isNullOrBlank() && !end.isNullOrBlank()) { append(" ($start-$end)") }
            }
            results.add(label)
        }
        return results
    }

    private fun buildResultItem(text: String): TextView = TextView(requireContext()).apply { this.text = text; textSize = 14f; setTextColor(requireContext().getColor(R.color.text_primary)); setPadding(0, dpToPx(6), 0, dpToPx(6)) }
    private fun buildHintText(text: String): TextView = TextView(requireContext()).apply { this.text = text; textSize = 13f; setTextColor(requireContext().getColor(R.color.text_secondary)); setPadding(0, dpToPx(6), 0, dpToPx(6)) }

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
                    val index = ((week - 1) * 7 + (day - 1)) * periodCount + (period - 1)
                    if (index in 0 until size) busy[index] = true
                }
            }
        }
        return busy.map { if (it) '0' else '1' }.joinToString("")
    }

    private fun derivePeriodCount(): Int = if (periodTimes.isEmpty()) DEFAULT_PERIOD_COUNT else periodTimes.maxOf { it.period }.coerceAtLeast(1)

    private fun defaultPeriodTimes(): Map<Int, Pair<String, String>> = mapOf(1 to ("08:00" to "08:45"), 2 to ("08:55" to "09:40"), 3 to ("10:00" to "10:45"), 4 to ("10:55" to "11:40"), 5 to ("14:00" to "14:45"), 6 to ("14:55" to "15:40"), 7 to ("16:00" to "16:45"), 8 to ("16:55" to "17:40"))

    private fun formatWeekday(dayIndex: Int): String = when (dayIndex) { 1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; else -> "周日" }

    private fun getCurrentWeek(): Int {
        val today = LocalDate.now()
        val daysDiff = ChronoUnit.DAYS.between(semesterStartDate, today)
        return ((daysDiff / 7).toInt() + 1).coerceAtLeast(1)
    }

    private fun isSessionCode(code: String): Boolean = code.matches(Regex("^[A-Z0-9]{6}$"))
    private fun isInviteCode(code: String): Boolean = code.matches(Regex("^I[A-Z0-9]{7}$"))
    private fun isValidSyncCode(code: String): Boolean = isSessionCode(code) || isInviteCode(code)

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = Random()
        return buildString { append('I'); repeat(7) { append(chars[random.nextInt(chars.length)]) } }
    }

    private fun formatMemberList(names: Set<String>): String = if (names.isEmpty()) "成员：暂无" else "成员：" + names.sorted().joinToString("、")

    private fun notifyNewMembers(sessionId: Long, members: List<String>) {
        val manager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        }
        ensureGroupSyncChannel(manager)
        members.forEach { member ->
            val notification = NotificationCompat.Builder(requireContext(), GROUP_SYNC_CHANNEL_ID)
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
        val channel = NotificationChannel(GROUP_SYNC_CHANNEL_ID, "组队通知", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()

    private sealed class SessionResolution {
        data class Success(val session: GroupSyncSession, val message: String) : SessionResolution()
        data class Error(val message: String) : SessionResolution()
    }

    private data class GroupSyncSession(val code: String, val ownerUid: String, val ownerName: String, val semesterId: Long, val totalWeeks: Int, val periodCount: Int, val visibility: String, val status: String, val createdAt: Long, val updatedAt: Long) {
        fun toMap(): Map<String, Any> = mapOf("code" to code, "ownerUid" to ownerUid, "ownerName" to ownerName, "semesterId" to semesterId, "totalWeeks" to totalWeeks, "periodCount" to periodCount, "visibility" to visibility, "status" to status, "createdAt" to createdAt, "updatedAt" to updatedAt)
    }

    private data class GroupSyncMember(val uid: String, val displayName: String, val role: String, val joinedAt: Long, val freeSlots: String?) {
        fun toMap(): Map<String, Any?> = mapOf("uid" to uid, "displayName" to displayName, "role" to role, "joinedAt" to joinedAt, "freeSlots" to freeSlots)
    }

    private data class GroupSyncExternalShare(val memberName: String, val freeSlots: String, val createdAt: Long)
    private data class FreeSlotsSource(val name: String, val freeSlots: String)

    companion object {
        private const val DEFAULT_TOTAL_WEEKS = 20
        private const val DEFAULT_PERIOD_COUNT = 8
        private const val GROUP_SYNC_CHANNEL_ID = "group_sync_notifications"
        private const val VISIBILITY_PUBLIC = "PUBLIC"
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
