package com.nayanova.journal.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayanova.journal.data.model.*
import com.nayanova.journal.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: JournalRepository
) : ViewModel() {

    private val _detail = MutableStateFlow<LessonDetail?>(null)
    val detail = _detail.asStateFlow()

    private val _localMarks = MutableStateFlow<Map<Int, Map<String, MarkEntry>>>(emptyMap())
    val localMarks = _localMarks.asStateFlow()

    private val _localAttendance = MutableStateFlow<Map<Int, AttendanceEntry>>(emptyMap())
    val localAttendance = _localAttendance.asStateFlow()

    private val _localRemarks = MutableStateFlow<Map<Int, MutableList<String>>>(emptyMap())
    val localRemarks = _localRemarks.asStateFlow()

    private val _removeRemarkIds = MutableStateFlow<List<Int>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage = _saveMessage.asStateFlow()

    fun loadLesson(lessonId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.lessonDetail(lessonId)) {
                is JournalRepository.Result.Success -> {
                    _detail.value = result.data
                    initLocalState(result.data)
                }
                is JournalRepository.Result.Error -> {
                    _saveMessage.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    private fun initLocalState(detail: LessonDetail) {
        val marks = mutableMapOf<Int, MutableMap<String, MarkEntry>>()
        for ((studentId, byType) in detail.marks) {
            val sid = studentId.toIntOrNull() ?: continue
            marks[sid] = mutableMapOf()
            for ((workType, mark) in byType) {
                marks[sid]!![workType] = MarkEntry(mark.value, mark.comment ?: "")
            }
        }
        _localMarks.value = marks

        val att = mutableMapOf<Int, AttendanceEntry>()
        for ((studentId, record) in detail.attendance) {
            val sid = studentId.toIntOrNull() ?: continue
            att[sid] = AttendanceEntry(record.status, record.comment ?: "")
        }
        _localAttendance.value = att

        val remarks = mutableMapOf<Int, MutableList<String>>()
        for ((studentId, remarkList) in detail.remarks) {
            val sid = studentId.toIntOrNull() ?: continue
            remarks[sid] = remarkList.map { it.text }.toMutableList()
        }
        _localRemarks.value = remarks
    }

    fun updateMark(studentId: Int, workType: String, value: Int) {
        val current = _localMarks.value.toMutableMap()
        val byType = (current[studentId] ?: mutableMapOf()).toMutableMap()
        byType[workType] = MarkEntry(value, byType[workType]?.comment ?: "")
        current[studentId] = byType
        _localMarks.value = current
    }

    fun updateMarkComment(studentId: Int, workType: String, comment: String) {
        val current = _localMarks.value.toMutableMap()
        val byType = (current[studentId] ?: mutableMapOf()).toMutableMap()
        byType[workType] = MarkEntry(byType[workType]?.value ?: 0, comment)
        current[studentId] = byType
        _localMarks.value = current
    }

    fun updateAttendance(studentId: Int, status: String) {
        val current = _localAttendance.value.toMutableMap()
        current[studentId] = AttendanceEntry(status, current[studentId]?.comment ?: "")
        _localAttendance.value = current
    }

    fun addRemark(studentId: Int, text: String) {
        val current = _localRemarks.value.toMutableMap()
        val list = (current[studentId] ?: mutableListOf())
        list.add(text)
        current[studentId] = list
        _localRemarks.value = current
    }

    fun removeRemark(studentId: Int, index: Int) {
        val current = _localRemarks.value.toMutableMap()
        val list = (current[studentId] ?: mutableListOf())
        if (index < list.size) {
            list.removeAt(index)
        }
        current[studentId] = list
        _localRemarks.value = current
    }

    fun saveMarks(lessonId: Int) {
        viewModelScope.launch {
            val marksForApi = mutableMapOf<Int, MutableMap<String, MarkEntry>>()
            for ((sid, byType) in _localMarks.value) {
                marksForApi[sid] = mutableMapOf()
                for ((wt, entry) in byType) {
                    if (entry.value > 0) {
                        marksForApi[sid]!![wt] = entry
                    }
                }
            }
            when (val result = repository.saveMarks(lessonId, marksForApi)) {
                is JournalRepository.Result.Success -> _saveMessage.value = "Оценки сохранены"
                is JournalRepository.Result.Error -> _saveMessage.value = result.message
            }
        }
    }

    fun saveAttendance(lessonId: Int) {
        viewModelScope.launch {
            when (val result = repository.saveAttendance(lessonId, _localAttendance.value)) {
                is JournalRepository.Result.Success -> _saveMessage.value = "Посещаемость сохранена"
                is JournalRepository.Result.Error -> _saveMessage.value = result.message
            }
        }
    }

    fun saveRemarks(lessonId: Int) {
        viewModelScope.launch {
            val remarksForApi = mutableMapOf<Int, List<String>>()
            for ((sid, texts) in _localRemarks.value) {
                remarksForApi[sid] = texts.filter { it.isNotBlank() }
            }
            when (val result = repository.saveRemarks(lessonId, remarksForApi, _removeRemarkIds.value)) {
                is JournalRepository.Result.Success -> _saveMessage.value = "Замечания сохранены"
                is JournalRepository.Result.Error -> _saveMessage.value = result.message
            }
        }
    }

    fun clearMessage() {
        _saveMessage.value = null
    }
}
