package com.nayanova.journal.ui.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayanova.journal.data.model.SchoolClass
import com.nayanova.journal.data.model.Subject
import com.nayanova.journal.data.repository.JournalRepository
import com.nayanova.journal.util.CookieStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClassesViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val cookieStore: CookieStore
) : ViewModel() {

    private val _classes = MutableStateFlow<List<SchoolClass>>(emptyList())
    val classes = _classes.asStateFlow()

    private val _subjects = MutableStateFlow<Map<Int, List<Subject>>>(emptyMap())
    val subjects = _subjects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _subjectsLoading = MutableStateFlow<Set<Int>>(emptySet())
    val subjectsLoading = _subjectsLoading.asStateFlow()

    private val _subjectsError = MutableStateFlow<Map<Int, String>>(emptyMap())
    val subjectsError = _subjectsError.asStateFlow()

    fun loadClasses() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.classes()) {
                is JournalRepository.Result.Success -> {
                    _classes.value = result.data
                    _error.value = null
                }
                is JournalRepository.Result.Error -> {
                    // Если список уже загружен, не заменяем его полноэкранной ошибкой.
                    if (_classes.value.isEmpty()) {
                        _error.value = result.message
                    }
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Загружает предметы класса лениво (при выборе класса).
     * Уже загруженные предметы кэшируются; повторная загрузка — только по force.
     */
    fun loadSubjects(classId: Int, force: Boolean = false) {
        if (!force && _subjects.value.containsKey(classId)) return
        viewModelScope.launch {
            _subjectsLoading.value = _subjectsLoading.value + classId
            _subjectsError.value = _subjectsError.value - classId
            when (val result = repository.classSubjects(classId)) {
                is JournalRepository.Result.Success -> {
                    _subjects.value = _subjects.value + (classId to result.data)
                }
                is JournalRepository.Result.Error -> {
                    _subjectsError.value = _subjectsError.value + (classId to result.message)
                }
            }
            _subjectsLoading.value = _subjectsLoading.value - classId
        }
    }

    fun logout() {
        viewModelScope.launch {
            cookieStore.clearSession()
        }
    }
}
