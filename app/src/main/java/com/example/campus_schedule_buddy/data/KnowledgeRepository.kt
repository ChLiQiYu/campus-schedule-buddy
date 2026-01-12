package com.example.campus_schedule_buddy.data

import kotlinx.coroutines.flow.Flow

class KnowledgeRepository(private val dao: KnowledgePointDao) {
    fun observeByCourse(courseId: Long): Flow<List<KnowledgePointEntity>> {
        return dao.observeByCourse(courseId)
    }

    suspend fun getByCourse(courseId: Long): List<KnowledgePointEntity> {
        return dao.getByCourse(courseId)
    }

    suspend fun addPoints(courseId: Long, titles: List<String>) {
        val entities = titles.map { title ->
            KnowledgePointEntity(
                courseId = courseId,
                title = title,
                masteryLevel = 0,
                isKeyPoint = false,
                lastReviewedAt = null
            )
        }
        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
    }

    suspend fun addPoint(entity: KnowledgePointEntity): Long {
        return dao.insert(entity)
    }

    suspend fun updatePoint(entity: KnowledgePointEntity) {
        dao.update(entity)
    }

    suspend fun deletePoint(entity: KnowledgePointEntity) {
        dao.delete(entity)
    }
}
