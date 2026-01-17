package com.fjnu.schedule.jw

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fjnu.schedule.JwImportActivity
import com.fjnu.schedule.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText

class JwSchoolSelectActivity : AppCompatActivity() {

    private lateinit var adapter: JwSchoolAdapter
    private val allSchools = JwSchoolCatalog.list()
    private var semesterId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jw_school_select)

        semesterId = intent.getLongExtra(JwImportActivity.EXTRA_SEMESTER_ID, 0L)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_jw_school)
        toolbar.setNavigationOnClickListener { finish() }

        val searchInput = findViewById<TextInputEditText>(R.id.et_school_search)
        val recyclerView = findViewById<RecyclerView>(R.id.rv_school_list)

        adapter = JwSchoolAdapter(allSchools.toMutableList()) { school ->
            val intent = Intent(this, JwImportActivity::class.java).apply {
                putExtra(JwImportActivity.EXTRA_SEMESTER_ID, semesterId)
                putExtra(JwImportActivity.EXTRA_SCHOOL_ID, school.id)
            }
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchInput.doAfterTextChanged { editable ->
            val keyword = editable?.toString()?.trim().orEmpty()
            val filtered = if (keyword.isBlank()) {
                allSchools
            } else {
                allSchools.filter { it.name.contains(keyword, ignoreCase = true) }
            }
            adapter.submitList(filtered)
        }
    }

    private class JwSchoolAdapter(
        private val schools: MutableList<JwSchool>,
        private val onClick: (JwSchool) -> Unit
    ) : RecyclerView.Adapter<JwSchoolAdapter.SchoolViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SchoolViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_jw_school, parent, false)
            return SchoolViewHolder(view, onClick)
        }

        override fun onBindViewHolder(holder: SchoolViewHolder, position: Int) {
            holder.bind(schools[position])
        }

        override fun getItemCount(): Int = schools.size

        fun submitList(items: List<JwSchool>) {
            schools.clear()
            schools.addAll(items)
            notifyDataSetChanged()
        }

        class SchoolViewHolder(
            itemView: View,
            private val onClick: (JwSchool) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            private val nameView: TextView = itemView.findViewById(R.id.tv_school_name)
            private val metaView: TextView = itemView.findViewById(R.id.tv_school_meta)

            fun bind(school: JwSchool) {
                nameView.text = school.name
                metaView.text = school.loginUrl.ifBlank { "支持网页登录导入" }
                itemView.setOnClickListener { onClick(school) }
            }
        }
    }
}
