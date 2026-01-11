package com.example.campus_schedule_buddy.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.campus_schedule_buddy.data.AppDatabase
import com.example.campus_schedule_buddy.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val courseDao = database.courseDao()
                val settingsDao = database.settingsDao()
                val semesterDao = database.semesterDao()
                val settingsRepository = SettingsRepository(settingsDao, semesterDao)
                settingsRepository.ensureDefaults()
                val currentSemesterId = settingsDao.getAppSettings()?.currentSemesterId ?: return@launch
                val courses = courseDao.getAllCourses(currentSemesterId)
                val periodTimes = settingsDao.getPeriodTimes(currentSemesterId)
                val reminderSettings = settingsDao.getReminderSettings(currentSemesterId) ?: return@launch
                val typeReminders = settingsDao.getCourseTypeReminders(currentSemesterId)
                val semester = semesterDao.getSemester(currentSemesterId)
                val semesterStart = semester?.startDate?.let {
                    LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
                } ?: LocalDate.now()
                val scheduler = ReminderScheduler(context)
                scheduler.scheduleUpcomingReminders(
                    courses,
                    periodTimes,
                    semesterStart,
                    reminderSettings,
                    typeReminders
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
