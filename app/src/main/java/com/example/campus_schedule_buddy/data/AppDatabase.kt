package com.example.campus_schedule_buddy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.campus_schedule_buddy.model.Course

@Database(
    entities = [
        Course::class,
        SemesterSettingsEntity::class,
        PeriodTimeEntity::class,
        ReminderSettingsEntity::class,
        CourseTypeReminderEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "campus_schedule.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}

private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS semester_settings (
                id INTEGER NOT NULL PRIMARY KEY,
                startDate TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS period_times (
                period INTEGER NOT NULL PRIMARY KEY,
                startTime TEXT NOT NULL,
                endTime TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reminder_settings (
                id INTEGER NOT NULL PRIMARY KEY,
                leadMinutes INTEGER NOT NULL,
                enableNotification INTEGER NOT NULL,
                enableVibrate INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS course_type_reminders (
                type TEXT NOT NULL PRIMARY KEY,
                leadMinutes INTEGER NOT NULL,
                enabled INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
