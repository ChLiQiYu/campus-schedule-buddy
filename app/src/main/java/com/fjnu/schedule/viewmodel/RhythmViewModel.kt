package com.fjnu.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.fjnu.schedule.data.RhythmRepository
import com.fjnu.schedule.data.SettingsRepository
import com.fjnu.schedule.model.WorkloadDay
import com.fjnu.schedule.util.WorkloadCalculator
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
            settingsRepository.observeSemesterStartDate(),
            settingsRepository.scheduleSettings
        ) { courses, tasks, periodTimes, startDate, scheduleSettings ->
            val semesterStart = startDate ?: LocalDate.now()
            val totalWeeks = scheduleSettings?.totalWeeks ?: DEFAULT_TOTAL_WEEKS
            val weekIndex = getCurrentWeek(semesterStart, totalWeeks)
            val periodCount = periodTimes.maxOfOrNull { it.period }?.coerceAtLeast(1) ?: 8
            WorkloadCalculator.calculateWeeklyWorkload(
                courses = courses,
                tasks = tasks,
                semesterStartDate = semesterStart,
                weekIndex = weekIndex,
                totalWeeks = totalWeeks,
                periodCount = periodCount
            )
        }
    }

    private fun getCurrentWeek(semesterStart: LocalDate, totalWeeks: Int): Int {
        val daysDiff = ChronoUnit.DAYS.between(semesterStart, LocalDate.now())
        return ((daysDiff / 7).toInt() + 1).coerceAtLeast(1).coerceAtMost(totalWeeks)
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
