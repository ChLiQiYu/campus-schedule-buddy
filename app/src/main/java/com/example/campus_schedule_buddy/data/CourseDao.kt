package com.example.campus_schedule_buddy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.campus_schedule_buddy.model.Course
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startPeriod")
    fun observeCourses(): Flow<List<Course>>

    @Query("SELECT * FROM courses")
    suspend fun getAllCourses(): List<Course>

    @Insert
    suspend fun insert(course: Course): Long

    @Insert
    suspend fun insertAll(courses: List<Course>)

    @Update
    suspend fun update(course: Course)

    @Delete
    suspend fun delete(course: Course)

    @Query("DELETE FROM courses")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(courses: List<Course>) {
        deleteAll()
        if (courses.isNotEmpty()) {
            insertAll(courses)
        }
    }
}
