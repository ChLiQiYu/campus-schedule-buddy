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
        SemesterEntity::class,
        AppSettingsEntity::class,
        PeriodTimeEntity::class,
        ReminderSettingsEntity::class,
        CourseTypeReminderEntity::class,
        CourseAttachmentEntity::class,
        CourseNoteEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun settingsDao(): SettingsDao
    abstract fun semesterDao(): SemesterDao
    abstract fun workspaceDao(): CourseWorkspaceDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "campus_schedule.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
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

private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS semesters (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                startDate TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                id INTEGER NOT NULL PRIMARY KEY,
                currentSemesterId INTEGER NOT NULL
            )
            """.trimIndent()
        )

        val startDate = db.query("SELECT startDate FROM semester_settings LIMIT 1").use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
        val safeStartDate = startDate ?: java.time.LocalDate.now().toString()
        db.execSQL(
            "INSERT INTO semesters (id, name, startDate) VALUES (1, '默认学期', '$safeStartDate')"
        )
        db.execSQL("INSERT INTO app_settings (id, currentSemesterId) VALUES (1, 1)")

        db.execSQL("ALTER TABLE courses ADD COLUMN semesterId INTEGER NOT NULL DEFAULT 1")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS period_times_new (
                semesterId INTEGER NOT NULL,
                period INTEGER NOT NULL,
                startTime TEXT NOT NULL,
                endTime TEXT NOT NULL,
                PRIMARY KEY (semesterId, period)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO period_times_new (semesterId, period, startTime, endTime)
            SELECT 1, period, startTime, endTime FROM period_times
            """.trimIndent()
        )
        db.execSQL("DROP TABLE period_times")
        db.execSQL("ALTER TABLE period_times_new RENAME TO period_times")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reminder_settings_new (
                semesterId INTEGER NOT NULL,
                leadMinutes INTEGER NOT NULL,
                enableNotification INTEGER NOT NULL,
                enableVibrate INTEGER NOT NULL,
                PRIMARY KEY (semesterId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO reminder_settings_new (semesterId, leadMinutes, enableNotification, enableVibrate)
            SELECT 1, leadMinutes, enableNotification, enableVibrate FROM reminder_settings
            """.trimIndent()
        )
        db.execSQL("DROP TABLE reminder_settings")
        db.execSQL("ALTER TABLE reminder_settings_new RENAME TO reminder_settings")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS course_type_reminders_new (
                semesterId INTEGER NOT NULL,
                type TEXT NOT NULL,
                leadMinutes INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                PRIMARY KEY (semesterId, type)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO course_type_reminders_new (semesterId, type, leadMinutes, enabled)
            SELECT 1, type, leadMinutes, enabled FROM course_type_reminders
            """.trimIndent()
        )
        db.execSQL("DROP TABLE course_type_reminders")
        db.execSQL("ALTER TABLE course_type_reminders_new RENAME TO course_type_reminders")

        db.execSQL("DROP TABLE semester_settings")
    }
}

private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS course_attachments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                semesterId INTEGER NOT NULL,
                courseId INTEGER NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                uri TEXT,
                url TEXT,
                dueAt INTEGER,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_course_attachments_semesterId_courseId
            ON course_attachments (semesterId, courseId)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS course_notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                semesterId INTEGER NOT NULL,
                courseId INTEGER NOT NULL,
                content TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_course_notes_semesterId_courseId
            ON course_notes (semesterId, courseId)
            """.trimIndent()
        )
    }
}
