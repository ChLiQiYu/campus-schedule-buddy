package com.fjnu.schedule

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.fjnu.schedule.data.AppDatabase
import com.fjnu.schedule.data.CourseRepository
import com.fjnu.schedule.data.SettingsRepository
import com.fjnu.schedule.model.Course
import com.fjnu.schedule.viewmodel.ProfileCenterViewModel
import com.fjnu.schedule.viewmodel.ProfileCenterViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class ProfileCenterActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var knowledgeContainer: LinearLayout
    private lateinit var knowledgeEmpty: TextView
    private lateinit var viewModel: ProfileCenterViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_center)
        setupToolbar()
        initViews()
        setupViewModel()
        setupActions()
        setupBottomNavigation()
        observeCourses()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_profile_center)
        setSupportActionBar(toolbar)
    }

    private fun initViews() {
        knowledgeContainer = findViewById(R.id.container_knowledge_courses)
        knowledgeEmpty = findViewById(R.id.tv_knowledge_empty)
    }

    private fun setupViewModel() {
        val database = AppDatabase.getInstance(this)
        val courseRepository = CourseRepository(database.courseDao())
        val settingsRepository = SettingsRepository(database.settingsDao(), database.semesterDao())
        val factory = ProfileCenterViewModelFactory(courseRepository, settingsRepository)
        viewModel = ViewModelProvider(this, factory)[ProfileCenterViewModel::class.java]
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.btn_open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btn_open_rhythm).setOnClickListener {
            startActivity(Intent(this, RhythmActivity::class.java))
        }
    }

    private fun observeCourses() {
        viewModel.courses.observe(this) { courses ->
            renderKnowledgeCourses(courses)
        }
    }

    private fun renderKnowledgeCourses(courses: List<Course>) {
        knowledgeContainer.removeAllViews()
        if (courses.isEmpty()) {
            knowledgeEmpty.visibility = View.VISIBLE
            return
        }
        knowledgeEmpty.visibility = View.GONE
        courses.forEach { course ->
            val button = MaterialButton(this).apply {
                text = "《${course.name}》"
                setOnClickListener { openKnowledgeInventory(course) }
            }
            knowledgeContainer.addView(button)
        }
    }

    private fun openKnowledgeInventory(course: Course) {
        val intent = Intent(this, KnowledgeInventoryActivity::class.java).apply {
            putExtra(KnowledgeInventoryActivity.EXTRA_COURSE_ID, course.id)
            putExtra(KnowledgeInventoryActivity.EXTRA_COURSE_NAME, course.name)
        }
        startActivity(intent)
    }

    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottom_navigation_profile)
        bottomNavigation.selectedItemId = R.id.navigation_profile
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_schedule -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.navigation_partners -> {
                    startActivity(Intent(this, GroupSyncActivity::class.java))
                    finish()
                    true
                }
                R.id.navigation_profile -> true
                else -> false
            }
        }
    }
}
