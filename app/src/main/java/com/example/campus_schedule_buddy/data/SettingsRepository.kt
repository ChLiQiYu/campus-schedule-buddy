package com.example.campus_schedule_buddy.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SettingsRepository(private val settingsDao: SettingsDao) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val semesterSettings: Flow<SemesterSettingsEntity?> = settingsDao.observeSemesterSettings()
    val periodTimes: Flow<List<PeriodTimeEntity>> = settingsDao.observePeriodTimes()
    val reminderSettings: Flow<ReminderSettingsEntity?> = settingsDao.observeReminderSettings()
    val courseTypeReminders: Flow<List<CourseTypeReminderEntity>> = settingsDao.observeCourseTypeReminders()

    suspend fun ensureDefaults() {
        if (settingsDao.getPeriodTimes().isEmpty()) {
            settingsDao.upsertPeriodTimes(defaultPeriodTimes())
        }
        if (settingsDao.getCourseTypeReminders().isEmpty()) {
            settingsDao.upsertCourseTypeReminders(defaultCourseTypeReminders())
        }
        if (settingsDao.getReminderSettings() == null) {
            val defaultReminder = ReminderSettingsEntity(
                leadMinutes = 10,
                enableNotification = true,
                enableVibrate = true
            )
            settingsDao.upsertReminderSettings(defaultReminder)
        }
        if (settingsDao.getSemesterSettings() == null) {
            val defaultSemester = SemesterSettingsEntity(startDate = LocalDate.now().format(dateFormatter))
            settingsDao.upsertSemesterSettings(defaultSemester)
        }
    }

    suspend fun updateSemesterStart(date: LocalDate) {
        settingsDao.upsertSemesterSettings(
            SemesterSettingsEntity(startDate = date.format(dateFormatter))
        )
    }

    suspend fun updatePeriodTimes(times: List<PeriodTimeEntity>) {
        settingsDao.upsertPeriodTimes(times)
    }

    suspend fun updateReminderSettings(settings: ReminderSettingsEntity) {
        settingsDao.upsertReminderSettings(settings)
    }

    suspend fun updateCourseTypeReminders(items: List<CourseTypeReminderEntity>) {
        settingsDao.upsertCourseTypeReminders(items)
    }

    fun observeSemesterStartDate(): Flow<LocalDate?> {
        return semesterSettings.map { entity ->
            entity?.startDate?.let { LocalDate.parse(it, dateFormatter) }
        }
    }

    private fun defaultPeriodTimes(): List<PeriodTimeEntity> {
        return listOf(
            PeriodTimeEntity(1, "08:00", "08:45"),
            PeriodTimeEntity(2, "08:55", "09:40"),
            PeriodTimeEntity(3, "10:00", "10:45"),
            PeriodTimeEntity(4, "10:55", "11:40"),
            PeriodTimeEntity(5, "14:00", "14:45"),
            PeriodTimeEntity(6, "14:55", "15:40"),
            PeriodTimeEntity(7, "16:00", "16:45"),
            PeriodTimeEntity(8, "16:55", "17:40")
        )
    }

    private fun defaultCourseTypeReminders(): List<CourseTypeReminderEntity> {
        return listOf(
            CourseTypeReminderEntity("major_required", 10, true),
            CourseTypeReminderEntity("major_elective", 10, true),
            CourseTypeReminderEntity("public_required", 10, true),
            CourseTypeReminderEntity("public_elective", 10, true),
            CourseTypeReminderEntity("experiment", 15, true),
            CourseTypeReminderEntity("pe", 5, true)
        )
    }
}
