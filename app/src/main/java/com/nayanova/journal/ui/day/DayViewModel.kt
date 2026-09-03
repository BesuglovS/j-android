package com.nayanova.journal.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayanova.journal.data.model.Lesson
import com.nayanova.journal.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DayViewModel @Inject constructor(
    private val repository: JournalRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        loadLessons()
    }

    fun loadLessons() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.lessonsByDate(_selectedDate.value.toString())) {
                is JournalRepository.Result.Success -> {
                    _lessons.value = result.data
                    _error.value = null
                }
                is JournalRepository.Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    fun setDate(date: LocalDate) {
        if (date == _selectedDate.value) return
        _selectedDate.value = date
        loadLessons()
    }

    fun previousDay() = setDate(_selectedDate.value.minusDays(1))

    fun nextDay() = setDate(_selectedDate.value.plusDays(1))
}
