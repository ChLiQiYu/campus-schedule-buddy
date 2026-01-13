package com.example.campus_schedule_buddy

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar

class LearningHubActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_hub)
        setupToolbar()
        setupActions()
        setupBottomNavigation()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_learning_hub)
        setSupportActionBar(toolbar)
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.btn_open_group_sync).setOnClickListener {
            startActivity(Intent(this, GroupSyncActivity::class.java))
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottom_navigation_learning_hub)
        bottomNavigation.selectedItemId = R.id.navigation_partners
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_schedule -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.navigation_partners -> true
                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfileCenterActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
