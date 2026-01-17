package com.fjnu.schedule.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings LIMIT 1")
    fun observeAppSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings LIMIT 1")
    suspend fun getAppSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppSettings(settings: AppSettingsEntity)

    @Query("SELECT * FROM period_times WHERE semesterId = :semesterId ORDER BY period")
    fun observePeriodTimes(semesterId: Long): Flow<List<PeriodTimeEntity>>

    @Query("SELECT * FROM period_times WHERE semesterId = :semesterId ORDER BY period")
    suspend fun getPeriodTimes(semesterId: Long): List<PeriodTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeriodTimes(times: List<PeriodTimeEntity>)

    @Query("DELETE FROM period_times WHERE semesterId = :semesterId AND period > :maxPeriod")
    suspend fun deletePeriodTimesAfter(semesterId: Long, maxPeriod: Int)

    @Query("SELECT * FROM schedule_settings WHERE semesterId = :semesterId LIMIT 1")
    fun observeScheduleSettings(semesterId: Long): Flow<ScheduleSettingsEntity?>

    @Query("SELECT * FROM schedule_settings WHERE semesterId = :semesterId LIMIT 1")
    suspend fun getScheduleSettings(semesterId: Long): ScheduleSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduleSettings(settings: ScheduleSettingsEntity)

    @Query("SELECT * FROM reminder_settings WHERE semesterId = :semesterId LIMIT 1")
    fun observeReminderSettings(semesterId: Long): Flow<ReminderSettingsEntity?>

    @Query("SELECT * FROM reminder_settings WHERE semesterId = :semesterId LIMIT 1")
    suspend fun getReminderSettings(semesterId: Long): ReminderSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderSettings(settings: ReminderSettingsEntity)

    @Query("SELECT * FROM course_type_reminders WHERE semesterId = :semesterId")
    fun observeCourseTypeReminders(semesterId: Long): Flow<List<CourseTypeReminderEntity>>

    @Query("SELECT * FROM course_type_reminders WHERE semesterId = :semesterId")
    suspend fun getCourseTypeReminders(semesterId: Long): List<CourseTypeReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourseTypeReminders(items: List<CourseTypeReminderEntity>)

    @Query("SELECT * FROM rhythm_settings LIMIT 1")
    fun observeRhythmSettings(): Flow<RhythmSettingsEntity?>

    @Query("SELECT * FROM rhythm_settings LIMIT 1")
    suspend fun getRhythmSettings(): RhythmSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRhythmSettings(settings: RhythmSettingsEntity)
}
