package com.fjnu.schedule.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupSyncDao {
    @Insert
    suspend fun insertSession(entity: GroupSyncSessionEntity): Long

    @Query("SELECT * FROM group_sync_sessions WHERE code = :code LIMIT 1")
    suspend fun getSessionByCode(code: String): GroupSyncSessionEntity?

    @Query("SELECT * FROM group_sync_sessions ORDER BY createdAt DESC")
    suspend fun getSessions(): List<GroupSyncSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShare(entity: GroupSyncShareEntity): Long

    @Query("SELECT * FROM group_sync_shares WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeShares(sessionId: Long): Flow<List<GroupSyncShareEntity>>

    @Query("SELECT * FROM group_sync_shares WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getShares(sessionId: Long): List<GroupSyncShareEntity>
}
