package com.example.campus_schedule_buddy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {
    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    fun observeSemesters(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters WHERE id = :semesterId LIMIT 1")
    fun observeSemester(semesterId: Long): Flow<SemesterEntity?>

    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    suspend fun getSemesters(): List<SemesterEntity>

    @Query("SELECT * FROM semesters WHERE id = :semesterId LIMIT 1")
    suspend fun getSemester(semesterId: Long): SemesterEntity?

    @Insert
    suspend fun insertSemester(semester: SemesterEntity): Long

    @Update
    suspend fun updateSemester(semester: SemesterEntity)
}
