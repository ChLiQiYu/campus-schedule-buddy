package com.example.campus_schedule_buddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.campus_schedule_buddy.data.KnowledgePointEntity
import com.example.campus_schedule_buddy.data.KnowledgeRepository
import com.example.campus_schedule_buddy.model.KnowledgeFilter
import com.example.campus_schedule_buddy.model.KnowledgeProgress
import com.example.campus_schedule_buddy.model.KnowledgeStatus
import com.example.campus_schedule_buddy.util.KnowledgeProgressCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KnowledgeViewModel(
    private val courseId: Long,
    private val repository: KnowledgeRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(KnowledgeFilter.ALL)

    private val allPoints: Flow<List<KnowledgePointEntity>> = repository.observeByCourse(courseId)

    val progress = allPoints.map { KnowledgeProgressCalculator.calculate(it) }.asLiveData()

    val points = combine(allPoints, searchQuery, filter) { items, query, filterMode ->
        val filtered = items.filter { point ->
            val matchesQuery = query.isBlank() || point.title.contains(query, ignoreCase = true)
            val matchesFilter = when (filterMode) {
                KnowledgeFilter.ALL -> true
                KnowledgeFilter.NOT_STARTED -> KnowledgeStatus.fromLevel(point.masteryLevel) == KnowledgeStatus.NOT_STARTED
                KnowledgeFilter.LEARNING -> KnowledgeStatus.fromLevel(point.masteryLevel) == KnowledgeStatus.LEARNING
                KnowledgeFilter.MASTERED -> KnowledgeStatus.fromLevel(point.masteryLevel) == KnowledgeStatus.MASTERED
                KnowledgeFilter.STUCK -> KnowledgeStatus.fromLevel(point.masteryLevel) == KnowledgeStatus.STUCK
                KnowledgeFilter.KEYPOINT -> point.isKeyPoint
            }
            matchesQuery && matchesFilter
        }
        filtered
    }.asLiveData()

    fun updateSearch(query: String) {
        searchQuery.value = query.trim()
    }

    fun updateFilter(filter: KnowledgeFilter) {
        this.filter.value = filter
    }

    fun addPoints(titles: List<String>) {
        val cleaned = titles.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            repository.addPoints(courseId, cleaned)
        }
    }

    fun addPoint(title: String, isKeyPoint: Boolean) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.addPoint(
                KnowledgePointEntity(
                    courseId = courseId,
                    title = trimmed,
                    masteryLevel = KnowledgeStatus.NOT_STARTED.level,
                    isKeyPoint = isKeyPoint,
                    lastReviewedAt = null
                )
            )
        }
    }

    fun updatePoint(
        point: KnowledgePointEntity,
        title: String,
        isKeyPoint: Boolean,
        status: KnowledgeStatus = KnowledgeStatus.fromLevel(point.masteryLevel)
    ) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.updatePoint(
                point.copy(
                    title = trimmed,
                    isKeyPoint = isKeyPoint,
                    masteryLevel = status.level,
                    lastReviewedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateStatus(point: KnowledgePointEntity, status: KnowledgeStatus) {
        viewModelScope.launch {
            repository.updatePoint(
                point.copy(
                    masteryLevel = status.level,
                    lastReviewedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleStatus(point: KnowledgePointEntity) {
        val current = KnowledgeStatus.fromLevel(point.masteryLevel)
        val next = KnowledgeStatus.next(current)
        updateStatus(point, next)
    }

    fun deletePoint(point: KnowledgePointEntity) {
        viewModelScope.launch {
            repository.deletePoint(point)
        }
    }
}

class KnowledgeViewModelFactory(
    private val courseId: Long,
    private val repository: KnowledgeRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KnowledgeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return KnowledgeViewModel(courseId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
