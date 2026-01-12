package com.example.campus_schedule_buddy.data

import kotlinx.coroutines.flow.Flow

class GroupSyncRepository(private val groupSyncDao: GroupSyncDao) {
    suspend fun createSession(
        code: String,
        semesterId: Long,
        totalWeeks: Int,
        periodCount: Int
    ): GroupSyncSessionEntity {
        val entity = GroupSyncSessionEntity(
            code = code,
            semesterId = semesterId,
            totalWeeks = totalWeeks,
            periodCount = periodCount,
            createdAt = System.currentTimeMillis()
        )
        val id = groupSyncDao.insertSession(entity)
        return entity.copy(id = id)
    }

    suspend fun getSessionByCode(code: String): GroupSyncSessionEntity? {
        return groupSyncDao.getSessionByCode(code)
    }

    fun observeShares(sessionId: Long): Flow<List<GroupSyncShareEntity>> {
        return groupSyncDao.observeShares(sessionId)
    }

    suspend fun getShares(sessionId: Long): List<GroupSyncShareEntity> {
        return groupSyncDao.getShares(sessionId)
    }

    suspend fun upsertShare(
        sessionId: Long,
        memberName: String,
        freeSlots: String
    ) {
        groupSyncDao.upsertShare(
            GroupSyncShareEntity(
                sessionId = sessionId,
                memberName = memberName,
                freeSlots = freeSlots,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
