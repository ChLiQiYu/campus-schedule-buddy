package com.example.schedule

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.schedule.data.AppDatabase
import com.example.schedule.data.CourseRepository
import com.example.schedule.data.SettingsRepository
import com.example.schedule.model.Course
import com.example.schedule.viewmodel.ProfileCenterViewModel
import com.example.schedule.viewmodel.ProfileCenterViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ProfileCenterFragment : Fragment(R.layout.fragment_profile_center) {
    private lateinit var knowledgeContainer: LinearLayout
    private lateinit var knowledgeEmpty: TextView
    private lateinit var viewModel: ProfileCenterViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar_profile_center)
        toolbar.title = "个人中心"
        knowledgeContainer = view.findViewById(R.id.container_knowledge_courses)
        knowledgeEmpty = view.findViewById(R.id.tv_knowledge_empty)
        setupViewModel()
        setupActions(view)
        observeCourses()
    }

    private fun setupViewModel() {
        val database = AppDatabase.getInstance(requireContext())
        val courseRepository = CourseRepository(database.courseDao())
        val settingsRepository = SettingsRepository(database.settingsDao(), database.semesterDao())
        val factory = ProfileCenterViewModelFactory(courseRepository, settingsRepository)
        viewModel = ViewModelProvider(this, factory)[ProfileCenterViewModel::class.java]
    }

    private fun setupActions(view: View) {
        view.findViewById<MaterialButton>(R.id.btn_open_settings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.btn_open_rhythm).setOnClickListener {
            startActivity(Intent(requireContext(), RhythmActivity::class.java))
        }
    }

    private fun observeCourses() {
        viewModel.courses.observe(viewLifecycleOwner) { courses ->
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
            val button = MaterialButton(requireContext()).apply {
                text = "查看《${course.name}》知识清单"
                setOnClickListener { openKnowledgeInventory(course) }
            }
            knowledgeContainer.addView(button)
        }
    }

    private fun openKnowledgeInventory(course: Course) {
        val intent = Intent(requireContext(), KnowledgeInventoryActivity::class.java).apply {
            putExtra(KnowledgeInventoryActivity.EXTRA_COURSE_ID, course.id)
            putExtra(KnowledgeInventoryActivity.EXTRA_COURSE_NAME, course.name)
        }
        startActivity(intent)
    }
}
