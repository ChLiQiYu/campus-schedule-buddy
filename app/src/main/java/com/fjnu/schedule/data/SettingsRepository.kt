package com.example.schedule.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SettingsRepository(
    private val settingsDao: SettingsDao,
    private val semesterDao: SemesterDao
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val semesters: Flow<List<SemesterEntity>> = semesterDao.observeSemesters()
    val appSettings: Flow<AppSettingsEntity?> = settingsDao.observeAppSettings()
    val rhythmSettings: Flow<RhythmSettingsEntity?> = settingsDao.observeRhythmSettings()
    val autoFocusEnabled: Flow<Boolean> = rhythmSettings.map { it?.autoFocusEnabled ?: false }

    val currentSemesterId: Flow<Long> = combine(semesters, appSettings) { items, settings ->
        val fallbackId = items.firstOrNull()?.id ?: 0L
        settings?.currentSemesterId ?: fallbackId
    }

    val currentSemester: Flow<SemesterEntity?> = currentSemesterId.flatMapLatest { id ->
        if (id <= 0L) {
            semesters.map { list -> list.firstOrNull() }
        } else {
            semesterDao.observeSemester(id)
        }
    }

    val periodTimes: Flow<List<PeriodTimeEntity>> = currentSemesterId.flatMapLatest { semesterId ->
        if (semesterId <= 0L) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            settingsDao.observePeriodTimes(semesterId)
        }
    }
    val reminderSettings: Flow<ReminderSettingsEntity?> = currentSemesterId.flatMapLatest { semesterId ->
        if (semesterId <= 0L) {
            kotlinx.coroutines.flow.flowOf(null)
        } else {
            settingsDao.observeReminderSettings(semesterId)
        }
    }
    val courseTypeReminders: Flow<List<CourseTypeReminderEntity>> = currentSemesterId.flatMapLatest { semesterId ->
        if (semesterId <= 0L) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            settingsDao.observeCourseTypeReminders(semesterId)
        }
    }

    suspend fun ensureDefaults() {
        val semesters = semesterDao.getSemesters()
        val currentSemesterId = if (semesters.isEmpty()) {
            val defaultSemester = SemesterEntity(
                name = "默认学期",
                startDate = LocalDate.now().format(dateFormatter)
            )
            val id = semesterDao.insertSemester(defaultSemester)
            seedDefaultsForSemester(id)
            id
        } else {
            semesters.first().id
        }

        val appSettings = settingsDao.getAppSettings()
        if (appSettings == null) {
            settingsDao.upsertAppSettings(AppSettingsEntity(currentSemesterId = currentSemesterId))
        }
        if (settingsDao.getRhythmSettings() == null) {
            settingsDao.upsertRhythmSettings(RhythmSettingsEntity(autoFocusEnabled = false))
        }
        ensureSemesterDefaults(resolveCurrentSemesterId())
    }

    suspend fun updateSemesterStart(date: LocalDate) {
        val semesterId = resolveCurrentSemesterId()
        val current = semesterDao.getSemester(semesterId) ?: return
        semesterDao.updateSemester(current.copy(startDate = date.format(dateFormatter)))
    }

    suspend fun updatePeriodTimes(times: List<PeriodTimeEntity>) {
        val semesterId = resolveCurrentSemesterId()
        settingsDao.upsertPeriodTimes(times.map { it.copy(semesterId = semesterId) })
    }

    suspend fun updateReminderSettings(settings: ReminderSettingsEntity) {
        val semesterId = resolveCurrentSemesterId()
        settingsDao.upsertReminderSettings(settings.copy(semesterId = semesterId))
    }

    suspend fun updateCourseTypeReminders(items: List<CourseTypeReminderEntity>) {
        val semesterId = resolveCurrentSemesterId()
        settingsDao.upsertCourseTypeReminders(items.map { it.copy(semesterId = semesterId) })
    }

    suspend fun setAutoFocusEnabled(enabled: Boolean) {
        settingsDao.upsertRhythmSettings(RhythmSettingsEntity(autoFocusEnabled = enabled))
    }

    fun observeSemesterStartDate(): Flow<LocalDate?> {
        return currentSemester.map { entity ->
            entity?.startDate?.let { LocalDate.parse(it, dateFormatter) }
        }
    }

    suspend fun setCurrentSemester(semesterId: Long) {
        settingsDao.upsertAppSettings(AppSettingsEntity(currentSemesterId = semesterId))
        ensureSemesterDefaults(semesterId)
    }

    suspend fun createSemester(name: String, startDate: LocalDate): Long {
        val id = semesterDao.insertSemester(
            SemesterEntity(name = name, startDate = startDate.format(dateFormatter))
        )
        seedDefaultsForSemester(id)
        setCurrentSemester(id)
        return id
    }

    private suspend fun resolveCurrentSemesterId(): Long {
        val settings = settingsDao.getAppSettings()
        val currentId = settings?.currentSemesterId
        if (currentId != null && currentId > 0) {
            return currentId
        }
        val semesters = semesterDao.getSemesters()
        if (semesters.isNotEmpty()) {
            return semesters.first().id
        }
        val id = semesterDao.insertSemester(
            SemesterEntity(name = "默认学期", startDate = LocalDate.now().format(dateFormatter))
        )
        seedDefaultsForSemester(id)
        settingsDao.upsertAppSettings(AppSettingsEntity(currentSemesterId = id))
        return id
    }

    private suspend fun ensureSemesterDefaults(semesterId: Long) {
        if (settingsDao.getPeriodTimes(semesterId).isEmpty()) {
            settingsDao.upsertPeriodTimes(defaultPeriodTimes(semesterId))
        }
        if (settingsDao.getCourseTypeReminders(semesterId).isEmpty()) {
            settingsDao.upsertCourseTypeReminders(defaultCourseTypeReminders(semesterId))
        }
        if (settingsDao.getReminderSettings(semesterId) == null) {
            val defaultReminder = ReminderSettingsEntity(
                semesterId = semesterId,
                leadMinutes = 10,
                enableNotification = true,
                enableVibrate = true
            )
            settingsDao.upsertReminderSettings(defaultReminder)
        }
    }

    private suspend fun seedDefaultsForSemester(semesterId: Long) {
        settingsDao.upsertPeriodTimes(defaultPeriodTimes(semesterId))
        settingsDao.upsertCourseTypeReminders(defaultCourseTypeReminders(semesterId))
        settingsDao.upsertReminderSettings(
            ReminderSettingsEntity(
                semesterId = semesterId,
                leadMinutes = 10,
                enableNotification = true,
                enableVibrate = true
            )
        )
    }

    private fun defaultPeriodTimes(semesterId: Long): List<PeriodTimeEntity> {
        return listOf(
            PeriodTimeEntity(semesterId, 1, "08:00", "08:45"),
            PeriodTimeEntity(semesterId, 2, "08:55", "09:40"),
            PeriodTimeEntity(semesterId, 3, "10:00", "10:45"),
            PeriodTimeEntity(semesterId, 4, "10:55", "11:40"),
            PeriodTimeEntity(semesterId, 5, "14:00", "14:45"),
            PeriodTimeEntity(semesterId, 6, "14:55", "15:40"),
            PeriodTimeEntity(semesterId, 7, "16:00", "16:45"),
            PeriodTimeEntity(semesterId, 8, "16:55", "17:40")
        )
    }

    private fun defaultCourseTypeReminders(semesterId: Long): List<CourseTypeReminderEntity> {
        return listOf(
            CourseTypeReminderEntity(semesterId, "major_required", 10, true),
            CourseTypeReminderEntity(semesterId, "major_elective", 10, true),
            CourseTypeReminderEntity(semesterId, "public_required", 10, true),
            CourseTypeReminderEntity(semesterId, "public_elective", 10, true),
            CourseTypeReminderEntity(semesterId, "experiment", 15, true),
            CourseTypeReminderEntity(semesterId, "pe", 5, true)
        )
    }
}
