package com.fjnu.schedule.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fjnu.schedule.model.Course
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, startPeriod")
    fun observeCourses(semesterId: Long): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    suspend fun getAllCourses(semesterId: Long): List<Course>

    @Query("SELECT * FROM courses WHERE id = :courseId LIMIT 1")
    suspend fun getCourse(courseId: Long): Course?

    @Insert
    suspend fun insert(course: Course): Long

    @Insert
    suspend fun insertAll(courses: List<Course>)

    @Update
    suspend fun update(course: Course)

    @Delete
    suspend fun delete(course: Course)

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    suspend fun deleteAll(semesterId: Long)

    @Query("SELECT DISTINCT name FROM courses WHERE semesterId = :semesterId AND name != ''")
    suspend fun getCourseNames(semesterId: Long): List<String>

    @Query("SELECT DISTINCT teacher FROM courses WHERE semesterId = :semesterId AND teacher IS NOT NULL AND teacher != ''")
    suspend fun getTeachers(semesterId: Long): List<String>

    @Query("SELECT DISTINCT location FROM courses WHERE semesterId = :semesterId AND location IS NOT NULL AND location != ''")
    suspend fun getLocations(semesterId: Long): List<String>

    @Transaction
    suspend fun replaceAll(semesterId: Long, courses: List<Course>) {
        deleteAll(semesterId)
        if (courses.isNotEmpty()) {
            insertAll(courses)
        }
    }
}
