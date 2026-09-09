package com.nayanova.journal.ui.classjournal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayanova.journal.data.model.ClassJournalData
import com.nayanova.journal.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClassJournalViewModel @Inject constructor(
    private val repository: JournalRepository
) : ViewModel() {

    private val _data = MutableStateFlow<ClassJournalData?>(null)
    val data = _data.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** Заголовок объединённого предмета («Информатика и Труд»), если применимо. */
    private val _title = MutableStateFlow<String?>(null)
    val title = _title.asStateFlow()

    private var currentClassId: Int = 0
    private var currentSubjectId: Int = 0

    fun load(classId: Int, subjectId: Int) {
        currentClassId = classId
        currentSubjectId = subjectId
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _title.value = repository.subjectGroupLabel(classId, subjectId)
            when (val result = repository.mergedClassJournal(classId, subjectId)) {
                is JournalRepository.Result.Success -> {
                    _data.value = result.data
                }
                is JournalRepository.Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Добавить оценку ученику за конкретный урок.
     * Существующие оценки ученика за этот урок сохраняются, новая добавляется к ним.
     */
    fun addMark(lessonId: Int, studentId: Int, value: Int, workType: String, comment: String = "") {
        if (value < 2 || value > 5) return
        val data = _data.value ?: return
        val lessonKey = lessonId.toString()
        val studentKey = studentId.toString()
        val existing = data.marks[lessonKey]?.get(studentKey) ?: emptyList()
        val allMarks = existing.map { Triple(it.value, it.workType, it.comment ?: "") } +
            Triple(value, workType.ifBlank { "Урок" }, comment)
        saveMarks(lessonId, studentId, allMarks)
    }

    /**
     * Удалить оценку ученика за конкретный урок по индексу.
     */
    fun removeMark(lessonId: Int, studentId: Int, index: Int) {
        val data = _data.value ?: return
        val lessonKey = lessonId.toString()
        val studentKey = studentId.toString()
        val existing = data.marks[lessonKey]?.get(studentKey) ?: return
        if (index !in existing.indices) return
        val allMarks = existing.map { Triple(it.value, it.workType, it.comment ?: "") }.toMutableList()
        allMarks.removeAt(index)
        saveMarks(lessonId, studentId, allMarks)
    }

    private fun saveMarks(lessonId: Int, studentId: Int, marks: List<Triple<Int, String, String>>) {
        viewModelScope.launch {
            when (val result = repository.saveMarks(lessonId, mapOf(studentId to marks))) {
                is JournalRepository.Result.Success -> load(currentClassId, currentSubjectId)
                is JournalRepository.Result.Error -> _error.value = result.message
            }
        }
    }
}
