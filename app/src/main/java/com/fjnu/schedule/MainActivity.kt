package com.fjnu.schedule

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.fjnu.schedule.media.MediaObserverService

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView
    private var currentTag: String = TAG_SCHEDULE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        currentTag = savedInstanceState?.getString(KEY_CURRENT_TAG) ?: TAG_SCHEDULE
        setupBottomNavigation()
        if (savedInstanceState == null) {
            switchTo(currentTag)
        } else {
            restoreFragments()
        }
    }

    override fun onStart() {
        super.onStart()
        startService(Intent(this, MediaObserverService::class.java))
    }

    override fun onStop() {
        stopService(Intent(this, MediaObserverService::class.java))
        super.onStop()
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            val targetTag = when (item.itemId) {
                R.id.navigation_schedule -> TAG_SCHEDULE
                R.id.navigation_partners -> TAG_PARTNERS
                R.id.navigation_profile -> TAG_PROFILE
                else -> TAG_SCHEDULE
            }
            if (targetTag == currentTag) {
                return@setOnItemSelectedListener true
            }
            switchTo(targetTag)
            true
        }
        bottomNavigation.selectedItemId = when (currentTag) {
            TAG_PARTNERS -> R.id.navigation_partners
            TAG_PROFILE -> R.id.navigation_profile
            else -> R.id.navigation_schedule
        }
    }

    private fun restoreFragments() {
        val fragments = listOf(TAG_SCHEDULE, TAG_PARTNERS, TAG_PROFILE)
        val transaction = supportFragmentManager.beginTransaction()
        fragments.forEach { tag ->
            val fragment = supportFragmentManager.findFragmentByTag(tag)
            if (fragment != null) {
                if (tag == currentTag) {
                    transaction.show(fragment)
                } else {
                    transaction.hide(fragment)
                }
            }
        }
        transaction.commit()
    }

    private fun switchTo(tag: String) {
        val scheduleFragment = findOrCreateFragment(TAG_SCHEDULE) { ScheduleFragment() }
        val partnersFragment = findOrCreateFragment(TAG_PARTNERS) { LearningHubFragment() }
        val profileFragment = findOrCreateFragment(TAG_PROFILE) { ProfileCenterFragment() }

        val transaction = supportFragmentManager.beginTransaction()
        transaction.hide(scheduleFragment)
        transaction.hide(partnersFragment)
        transaction.hide(profileFragment)

        val target = when (tag) {
            TAG_PARTNERS -> partnersFragment
            TAG_PROFILE -> profileFragment
            else -> scheduleFragment
        }
        transaction.show(target)
        transaction.commit()
        currentTag = tag
    }

    private fun findOrCreateFragment(tag: String, factory: () -> Fragment): Fragment {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) return existing
        val fragment = factory()
        supportFragmentManager.beginTransaction()
            .add(R.id.main_container, fragment, tag)
            .commitNow()
        return fragment
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_CURRENT_TAG, currentTag)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        if (currentTag != TAG_SCHEDULE) {
            bottomNavigation.selectedItemId = R.id.navigation_schedule
            switchTo(TAG_SCHEDULE)
            return
        }
        super.onBackPressed()
    }

    companion object {
        private const val KEY_CURRENT_TAG = "key_current_tag"
        private const val TAG_SCHEDULE = "nav_schedule"
        private const val TAG_PARTNERS = "nav_partners"
        private const val TAG_PROFILE = "nav_profile"
    }
}
