package com.fjnu.schedule.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgePointDao {
    @Query("SELECT * FROM knowledge_points WHERE courseId = :courseId ORDER BY id DESC")
    fun observeByCourse(courseId: Long): Flow<List<KnowledgePointEntity>>

    @Query("SELECT * FROM knowledge_points WHERE courseId = :courseId ORDER BY id DESC")
    suspend fun getByCourse(courseId: Long): List<KnowledgePointEntity>

    @Insert
    suspend fun insert(entity: KnowledgePointEntity): Long

    @Insert
    suspend fun insertAll(entities: List<KnowledgePointEntity>)

    @Update
    suspend fun update(entity: KnowledgePointEntity)

    @Delete
    suspend fun delete(entity: KnowledgePointEntity)

    @Query("DELETE FROM knowledge_points WHERE courseId = :courseId")
    suspend fun deleteByCourse(courseId: Long)
}
