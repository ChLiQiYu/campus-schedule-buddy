package com.example.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.schedule.data.RhythmRepository
import com.example.schedule.data.SettingsRepository
import com.example.schedule.model.WorkloadDay
import com.example.schedule.util.WorkloadCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class RhythmViewModel(
    private val rhythmRepository: RhythmRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val autoFocusEnabled = rhythmRepository.observeAutoFocusEnabled().asLiveData()

    val workloadDays = buildWorkloadFlow().asLiveData()

    fun ensureDefaults() {
        viewModelScope.launch {
            rhythmRepository.ensureDefaults()
        }
    }

    fun setAutoFocusEnabled(enabled: Boolean) {
        viewModelScope.launch {
            rhythmRepository.setAutoFocusEnabled(enabled)
        }
    }

    private fun buildWorkloadFlow(): Flow<List<WorkloadDay>> {
        val semesterIdFlow = settingsRepository.currentSemesterId
        val coursesFlow = semesterIdFlow.flatMapLatest { semesterId ->
            if (semesterId <= 0L) {
                flowOf(emptyList())
            } else {
                rhythmRepository.observeCourses(semesterId)
            }
        }
        val tasksFlow = semesterIdFlow.flatMapLatest { semesterId ->
            if (semesterId <= 0L) {
                flowOf(emptyList())
            } else {
                rhythmRepository.observeTaskAttachments(semesterId)
            }
        }
        return combine(
            coursesFlow,
            tasksFlow,
            settingsRepository.periodTimes,
            settingsRepository.observeSemesterStartDate()
        ) { courses, tasks, periodTimes, startDate ->
            val semesterStart = startDate ?: LocalDate.now()
            val weekIndex = getCurrentWeek(semesterStart)
            val periodCount = periodTimes.maxOfOrNull { it.period }?.coerceAtLeast(1) ?: 8
            WorkloadCalculator.calculateWeeklyWorkload(
                courses = courses,
                tasks = tasks,
                semesterStartDate = semesterStart,
                weekIndex = weekIndex,
                totalWeeks = DEFAULT_TOTAL_WEEKS,
                periodCount = periodCount
            )
        }
    }

    private fun getCurrentWeek(semesterStart: LocalDate): Int {
        val daysDiff = ChronoUnit.DAYS.between(semesterStart, LocalDate.now())
        return ((daysDiff / 7).toInt() + 1).coerceAtLeast(1)
    }

    companion object {
        private const val DEFAULT_TOTAL_WEEKS = 20
    }
}

class RhythmViewModelFactory(
    private val rhythmRepository: RhythmRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RhythmViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RhythmViewModel(rhythmRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
