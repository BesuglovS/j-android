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

    fun loadClasses() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.classes()) {
                is JournalRepository.Result.Success -> {
                    _classes.value = result.data
                    result.data.forEach { cls ->
                        loadSubjects(cls.id)
                    }
                }
                is JournalRepository.Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    private fun loadSubjects(classId: Int) {
        viewModelScope.launch {
            when (val result = repository.classSubjects(classId)) {
                is JournalRepository.Result.Success -> {
                    _subjects.value = _subjects.value + (classId to result.data)
                }
                is JournalRepository.Result.Error -> { /* ignore */ }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            cookieStore.clear()
        }
    }
}
