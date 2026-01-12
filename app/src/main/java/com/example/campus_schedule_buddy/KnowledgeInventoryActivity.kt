package com.example.campus_schedule_buddy

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campus_schedule_buddy.data.AppDatabase
import com.example.campus_schedule_buddy.data.KnowledgeRepository
import com.example.campus_schedule_buddy.knowledge.KnowledgeAdapter
import com.example.campus_schedule_buddy.model.KnowledgeFilter
import com.example.campus_schedule_buddy.model.KnowledgeProgress
import com.example.campus_schedule_buddy.model.KnowledgeStatus
import com.example.campus_schedule_buddy.viewmodel.KnowledgeViewModel
import com.example.campus_schedule_buddy.viewmodel.KnowledgeViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial

class KnowledgeInventoryActivity : AppCompatActivity() {
    private lateinit var viewModel: KnowledgeViewModel
    private lateinit var adapter: KnowledgeAdapter

    private lateinit var progressRing: CircularProgressIndicator
    private lateinit var progressPercent: TextView
    private lateinit var progressSummary: TextView
    private lateinit var emptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_knowledge_inventory)
        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME).orEmpty()
        if (courseId <= 0L) {
            Toast.makeText(this, "课程信息无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar(courseName)
        setupViewModel(courseId)
        initViews()
        setupRecycler()
        setupFilters()
        setupActions()
        observeData()
    }

    private fun setupToolbar(courseName: String) {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_knowledge)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = if (courseName.isBlank()) "知识清单" else "知识清单 · $courseName"
    }

    private fun setupViewModel(courseId: Long) {
        val database = AppDatabase.getInstance(this)
        val repository = KnowledgeRepository(database.knowledgePointDao())
        val factory = KnowledgeViewModelFactory(courseId, repository)
        viewModel = ViewModelProvider(this, factory)[KnowledgeViewModel::class.java]
    }

    private fun initViews() {
        progressRing = findViewById(R.id.progress_ring)
        progressPercent = findViewById(R.id.tv_progress_percent)
        progressSummary = findViewById(R.id.tv_progress_summary)
        emptyState = findViewById(R.id.tv_empty_state)
    }

    private fun setupRecycler() {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_knowledge)
        adapter = KnowledgeAdapter(
            onStatusClick = { point -> viewModel.toggleStatus(point) },
            onItemClick = { point -> showEditDialog(point) },
            onItemLongClick = { point -> confirmDelete(point) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFilters() {
        val chipAll = findViewById<Chip>(R.id.chip_filter_all)
        val chipNotStarted = findViewById<Chip>(R.id.chip_filter_not_started)
        val chipLearning = findViewById<Chip>(R.id.chip_filter_learning)
        val chipMastered = findViewById<Chip>(R.id.chip_filter_mastered)
        val chipStuck = findViewById<Chip>(R.id.chip_filter_stuck)
        val chipKey = findViewById<Chip>(R.id.chip_filter_key)

        chipAll.isChecked = true
        chipAll.setOnClickListener { viewModel.updateFilter(KnowledgeFilter.ALL) }
        chipNotStarted.setOnClickListener { viewModel.updateFilter(KnowledgeFilter.NOT_STARTED) }
        chipLearning.setOnClickListener { viewModel.updateFilter(KnowledgeFilter.LEARNING) }
        chipMastered.setOnClickListener { viewModel.updateFilter(KnowledgeFilter.MASTERED) }
        chipStuck.setOnClickListener { viewModel.updateFilter(KnowledgeFilter.STUCK) }
        chipKey.setOnClickListener { viewModel.updateFilter(KnowledgeFilter.KEYPOINT) }

        val searchInput = findViewById<EditText>(R.id.et_search)
        searchInput.doAfterTextChanged { text ->
            viewModel.updateSearch(text?.toString().orEmpty())
        }
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.btn_add_point).setOnClickListener {
            showAddDialog()
        }
        findViewById<MaterialButton>(R.id.btn_batch_add).setOnClickListener {
            showBatchAddDialog()
        }
    }

    private fun observeData() {
        viewModel.points.observe(this) { points ->
            adapter.submitList(points)
            emptyState.visibility = if (points.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.progress.observe(this) { progress ->
            renderProgress(progress)
        }
    }

    private fun renderProgress(progress: KnowledgeProgress) {
        progressRing.progress = progress.percent
        progressPercent.text = "${progress.percent}%"
        val summary = "未开始 ${progress.notStartedCount} · 学习中 ${progress.learningCount} · 已掌握 ${progress.masteredCount} · 模糊 ${progress.stuckCount}"
        progressSummary.text = summary
    }

    private fun showAddDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(this).apply {
            hint = "知识点名称"
        }
        val keySwitch = SwitchMaterial(this).apply {
            text = "标记为重点"
        }
        container.addView(titleInput)
        container.addView(keySwitch)

        AlertDialog.Builder(this)
            .setTitle("新增知识点")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val title = titleInput.text.toString()
                if (title.isBlank()) {
                    Toast.makeText(this, "请输入知识点名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.addPoint(title, keySwitch.isChecked)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showBatchAddDialog() {
        val input = EditText(this).apply {
            hint = "每行一个知识点"
            minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
        }
        AlertDialog.Builder(this)
            .setTitle("批量添加")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val lines = input.text.toString().lines()
                viewModel.addPoints(lines)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditDialog(point: com.example.campus_schedule_buddy.data.KnowledgePointEntity) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(this).apply {
            setText(point.title)
        }
        val keySwitch = SwitchMaterial(this).apply {
            text = "标记为重点"
            isChecked = point.isKeyPoint
        }
        val statusButton = MaterialButton(this).apply {
            text = "切换状态：${KnowledgeStatus.fromLevel(point.masteryLevel).label}"
        }
        var selectedStatus = KnowledgeStatus.fromLevel(point.masteryLevel)
        statusButton.setOnClickListener {
            val options = KnowledgeStatus.values().map { it.label }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("选择掌握状态")
                .setItems(options) { _, which ->
                    selectedStatus = KnowledgeStatus.values()[which]
                    statusButton.text = "切换状态：${selectedStatus.label}"
                }
                .show()
        }
        container.addView(titleInput)
        container.addView(keySwitch)
        container.addView(statusButton)

        AlertDialog.Builder(this)
            .setTitle("编辑知识点")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val title = titleInput.text.toString()
                if (title.isBlank()) {
                    Toast.makeText(this, "请输入知识点名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updatePoint(point, title, keySwitch.isChecked, selectedStatus)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(point: com.example.campus_schedule_buddy.data.KnowledgePointEntity) {
        AlertDialog.Builder(this)
            .setTitle("删除知识点")
            .setMessage("确定删除“${point.title}”吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deletePoint(point)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"
        const val EXTRA_COURSE_NAME = "extra_course_name"
    }
}
