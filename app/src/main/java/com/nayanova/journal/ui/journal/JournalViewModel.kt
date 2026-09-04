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

    /** Оценка в локальном состоянии: значение + за что (work_type) + комментарий. */
    data class LocalMark(val value: Int, val workType: String, val comment: String = "")

    /** Замечание в локальном состоянии: id (0 — новое, ещё не отправленное) + текст. */
    data class RemarkRef(val id: Int, val text: String)

    private var currentLessonId: Int = 0

    private val _detail = MutableStateFlow<LessonDetail?>(null)
    val detail = _detail.asStateFlow()

    // Несколько оценок за урок: список (значение + за что) на ученика
    private val _localMarks = MutableStateFlow<Map<Int, List<LocalMark>>>(emptyMap())
    val localMarks = _localMarks.asStateFlow()

    private val _localAttendance = MutableStateFlow<Map<Int, AttendanceEntry>>(emptyMap())
    val localAttendance = _localAttendance.asStateFlow()

    // Важно: неизменяемые списки — иначе MutableStateFlow не увидит изменений
    // при мутации одного и того же объекта (сравнение по содержимому).
    private val _localRemarks = MutableStateFlow<Map<Int, List<RemarkRef>>>(emptyMap())
    val localRemarks = _localRemarks.asStateFlow()

    private val _removeRemarkIds = MutableStateFlow<List<Int>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage = _saveMessage.asStateFlow()

    fun loadLesson(lessonId: Int) {
        currentLessonId = lessonId
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
        val marks = mutableMapOf<Int, List<LocalMark>>()
        for ((studentId, list) in detail.marks) {
            val sid = studentId.toIntOrNull() ?: continue
            marks[sid] = list.map { LocalMark(it.value, it.workType.ifBlank { "Урок" }, it.comment ?: "") }
        }
        _localMarks.value = marks

        val att = mutableMapOf<Int, AttendanceEntry>()
        for ((studentId, record) in detail.attendance) {
            val sid = studentId.toIntOrNull() ?: continue
            att[sid] = AttendanceEntry(record.status, record.lateMinutes ?: 0)
        }
        _localAttendance.value = att

        val remarks = mutableMapOf<Int, List<RemarkRef>>()
        for ((studentId, remarkList) in detail.remarks) {
            val sid = studentId.toIntOrNull() ?: continue
            remarks[sid] = remarkList.map { RemarkRef(it.id, it.text) }
        }
        _localRemarks.value = remarks
    }

    /** Добавить оценку (только 2–5) с указанием, за что она, и комментарием. */
    fun addMark(studentId: Int, value: Int, workType: String, comment: String = "") {
        if (value < 2 || value > 5) return
        val type = workType.ifBlank { "Урок" }
        val current = _localMarks.value.toMutableMap()
        current[studentId] = (current[studentId] ?: emptyList()) + LocalMark(value, type, comment)
        _localMarks.value = current
        autoSaveMarks()
    }

    /**
     * Массовое добавление оценок: «за что» и комментарий вводятся один раз,
     * значение оценки (2–5) выбрано для каждого ученика отдельно.
     */
    fun addMarkBulk(marks: List<Pair<Int, Int>>, workType: String, comment: String) {
        if (marks.isEmpty()) return
        val type = workType.ifBlank { "Урок" }
        val current = _localMarks.value.toMutableMap()
        var added = 0
        for ((sid, value) in marks) {
            if (value < 2 || value > 5) continue
            current[sid] = (current[sid] ?: emptyList()) + LocalMark(value, type, comment)
            added++
        }
        _localMarks.value = current
        _saveMessage.value = "Оценки добавлены: $added"
        autoSaveMarks()
    }

    /** Удалить оценку по индексу. */
    fun removeMark(studentId: Int, index: Int) {
        val current = _localMarks.value.toMutableMap()
        val list = current[studentId] ?: return
        if (index >= list.size) return
        current[studentId] = list.filterIndexed { i, _ -> i != index }
        _localMarks.value = current
        autoSaveMarks()
    }

    fun updateAttendance(studentId: Int, status: String) {
        val current = _localAttendance.value.toMutableMap()
        val prev = current[studentId] ?: AttendanceEntry()
        current[studentId] = AttendanceEntry(status, prev.lateMinutes)
        _localAttendance.value = current
        autoSaveAttendance()
    }

    /** Период опоздания в минутах (статус 'late'). */
    fun updateLateMinutes(studentId: Int, minutes: Int) {
        val current = _localAttendance.value.toMutableMap()
        val prev = current[studentId] ?: AttendanceEntry(status = "late")
        current[studentId] = prev.copy(lateMinutes = minutes.coerceIn(0, 999))
        _localAttendance.value = current
        autoSaveAttendance()
    }

    fun addRemark(studentId: Int, text: String) {
        if (text.isBlank()) return
        val current = _localRemarks.value.toMutableMap()
        current[studentId] = (current[studentId] ?: emptyList()) + RemarkRef(0, text.trim())
        _localRemarks.value = current
        autoSaveRemarks()
    }

    fun removeRemark(studentId: Int, index: Int) {
        val current = _localRemarks.value.toMutableMap()
        val list = current[studentId] ?: return
        if (index >= list.size) return
        val removed = list[index]
        if (removed.id > 0) {
            _removeRemarkIds.value = _removeRemarkIds.value + removed.id
        }
        current[studentId] = list.filterIndexed { i, _ -> i != index }
        _localRemarks.value = current
        autoSaveRemarks()
    }

    private fun autoSaveMarks() {
        val lessonId = currentLessonId
        if (lessonId == 0) return
        viewModelScope.launch {
            val marksForApi = _localMarks.value.mapValues { (_, list) ->
                list.map { Triple(it.value, it.workType, it.comment) }
            }
            repository.saveMarks(lessonId, marksForApi)
        }
    }

    private fun autoSaveAttendance() {
        val lessonId = currentLessonId
        if (lessonId == 0) return
        viewModelScope.launch {
            repository.saveAttendance(lessonId, _localAttendance.value)
        }
    }

    private fun autoSaveRemarks() {
        val lessonId = currentLessonId
        if (lessonId == 0) return
        viewModelScope.launch {
            val remarksForApi = mutableMapOf<Int, List<String>>()
            for ((sid, refs) in _localRemarks.value) {
                remarksForApi[sid] = refs.map { it.text }.filter { it.isNotBlank() }
            }
            repository.saveRemarks(lessonId, remarksForApi, _removeRemarkIds.value)
        }
    }

    fun saveMarks(lessonId: Int) {
        viewModelScope.launch {
            val marksForApi = _localMarks.value.mapValues { (_, list) ->
                list.map { Triple(it.value, it.workType, it.comment) }
            }
            when (val result = repository.saveMarks(lessonId, marksForApi)) {
                is JournalRepository.Result.Success -> _saveMessage.value = "Оценки сохранены"
                is JournalRepository.Result.Error -> _saveMessage.value = result.message
            }
        }
    }

    /** Сохранить домашнее задание урока (на следующий урок) и обновить данные. */
    fun saveHomework(lessonId: Int, title: String, description: String, dueDate: String) {
        viewModelScope.launch {
            when (val result = repository.saveHomework(lessonId, title, description, dueDate)) {
                is JournalRepository.Result.Success -> {
                    _saveMessage.value = "Домашнее задание сохранено"
                    loadLesson(lessonId)
                }
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
            for ((sid, refs) in _localRemarks.value) {
                remarksForApi[sid] = refs.map { it.text }.filter { it.isNotBlank() }
            }
            when (val result = repository.saveRemarks(lessonId, remarksForApi, _removeRemarkIds.value)) {
                is JournalRepository.Result.Success -> _saveMessage.value = "Замечания сохранены"
                is JournalRepository.Result.Error -> _saveMessage.value = result.message
            }
        }
    }

    fun deleteHomework(homeworkId: Int, lessonId: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteHomework(homeworkId)) {
                is JournalRepository.Result.Success -> {
                    _saveMessage.value = "Домашнее задание удалено"
                    loadLesson(lessonId)
                }
                is JournalRepository.Result.Error -> _saveMessage.value = result.message
            }
        }
    }

    fun clearMessage() {
        _saveMessage.value = null
    }
}
