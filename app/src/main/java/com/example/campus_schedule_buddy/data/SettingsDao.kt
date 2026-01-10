package com.example.campus_schedule_buddy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM semester_settings LIMIT 1")
    fun observeSemesterSettings(): Flow<SemesterSettingsEntity?>

    @Query("SELECT * FROM semester_settings LIMIT 1")
    suspend fun getSemesterSettings(): SemesterSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSemesterSettings(settings: SemesterSettingsEntity)

    @Query("SELECT * FROM period_times ORDER BY period")
    fun observePeriodTimes(): Flow<List<PeriodTimeEntity>>

    @Query("SELECT * FROM period_times ORDER BY period")
    suspend fun getPeriodTimes(): List<PeriodTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeriodTimes(times: List<PeriodTimeEntity>)

    @Query("SELECT * FROM reminder_settings LIMIT 1")
    fun observeReminderSettings(): Flow<ReminderSettingsEntity?>

    @Query("SELECT * FROM reminder_settings LIMIT 1")
    suspend fun getReminderSettings(): ReminderSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderSettings(settings: ReminderSettingsEntity)

    @Query("SELECT * FROM course_type_reminders")
    fun observeCourseTypeReminders(): Flow<List<CourseTypeReminderEntity>>

    @Query("SELECT * FROM course_type_reminders")
    suspend fun getCourseTypeReminders(): List<CourseTypeReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourseTypeReminders(items: List<CourseTypeReminderEntity>)
}
