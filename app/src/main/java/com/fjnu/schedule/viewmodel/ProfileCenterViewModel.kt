package com.example.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.example.schedule.data.CourseRepository
import com.example.schedule.data.SettingsRepository
import com.example.schedule.model.Course
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class ProfileCenterViewModel(
    private val courseRepository: CourseRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val coursesFlow: Flow<List<Course>> = settingsRepository.currentSemesterId.flatMapLatest { semesterId ->
        if (semesterId <= 0L) {
            flowOf(emptyList())
        } else {
            courseRepository.observeCourses(semesterId)
        }
    }

    val courses = coursesFlow.asLiveData()
}

class ProfileCenterViewModelFactory(
    private val courseRepository: CourseRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileCenterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileCenterViewModel(courseRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
