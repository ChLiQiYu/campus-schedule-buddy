package com.fjnu.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.fjnu.schedule.data.CourseTypeReminderEntity
import com.fjnu.schedule.data.PeriodTimeEntity
import com.fjnu.schedule.data.ReminderSettingsEntity
import com.fjnu.schedule.data.ScheduleSettingsEntity
import com.fjnu.schedule.data.SettingsRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val semesters = repository.semesters.asLiveData()
    val currentSemesterId = repository.currentSemesterId.asLiveData()
    val currentSemester = repository.currentSemester.asLiveData()
    val semesterStartDate = repository.observeSemesterStartDate().asLiveData()
    val periodTimes = repository.periodTimes.asLiveData()
    val scheduleSettings = repository.scheduleSettings.asLiveData()
    val reminderSettings = repository.reminderSettings.asLiveData()
    val typeReminders = repository.courseTypeReminders.asLiveData()

    fun ensureDefaults() {
        viewModelScope.launch {
            repository.ensureDefaults()
        }
    }

    fun updateSemesterStart(date: LocalDate) {
        viewModelScope.launch {
            repository.updateSemesterStart(date)
        }
    }

    fun setCurrentSemester(semesterId: Long) {
        viewModelScope.launch {
            repository.setCurrentSemester(semesterId)
        }
    }

    fun createSemester(name: String, startDate: LocalDate) {
        viewModelScope.launch {
            repository.createSemester(name, startDate)
        }
    }

    fun updatePeriodTimes(times: List<PeriodTimeEntity>) {
        viewModelScope.launch {
            repository.updatePeriodTimes(times)
        }
    }

    fun updateScheduleSettings(settings: ScheduleSettingsEntity) {
        viewModelScope.launch {
            repository.updateScheduleSettings(settings)
        }
    }

    fun updateReminderSettings(settings: ReminderSettingsEntity) {
        viewModelScope.launch {
            repository.updateReminderSettings(settings)
        }
    }

    fun updateTypeReminders(items: List<CourseTypeReminderEntity>) {
        viewModelScope.launch {
            repository.updateCourseTypeReminders(items)
        }
    }
}

class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
