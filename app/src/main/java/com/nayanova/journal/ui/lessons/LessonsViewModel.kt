package com.nayanova.journal.ui.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayanova.journal.data.model.Lesson
import com.nayanova.journal.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LessonsViewModel @Inject constructor(
    private val repository: JournalRepository
) : ViewModel() {

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _createSuccess = MutableStateFlow<Int?>(null)
    val createSuccess = _createSuccess.asStateFlow()

    fun loadLessons(classId: Int, subjectId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.lessons(classId, subjectId)) {
                is JournalRepository.Result.Success -> {
                    _lessons.value = result.data
                }
                is JournalRepository.Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    fun createLesson(classId: Int, subjectId: Int, date: String, startTime: String, topic: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.createLesson(classId, subjectId, date, startTime, topic)) {
                is JournalRepository.Result.Success -> {
                    _createSuccess.value = result.data
                }
                is JournalRepository.Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    fun clearCreateSuccess() {
        _createSuccess.value = null
    }
}
