package ru.nayanovaacademy.journal.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ru.nayanovaacademy.journal.data.model.*
import ru.nayanovaacademy.journal.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: JournalRepository
) : ViewModel() {

    /** Оценка в локальном состоянии: значение + за что (work_type) + комментарий.
     *  id — серверная строка; isRetake — попытка переписывания (attemptDate —
     *  дата пересдачи); isCurrent — итоговая попытка (в средних участвуют только она). */
    data class LocalMark(
        val value: Int,
        val workType: String,
        val comment: String = "",
        val id: Int = 0,
        val isRetake: Boolean = false,
        val isCurrent: Boolean = true,
        val attemptDate: String = ""
    )

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

    /** Оценки-переписывания, ожидающие удаления на сервере: studentId → [id]. */
    private val _removeMarkIds = MutableStateFlow<Map<Int, List<Int>>>(emptyMap())

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
            marks[sid] = list.map {
                LocalMark(
                    it.value, it.workType.ifBlank { "Урок" }, it.comment ?: "",
                    it.id, it.isRetake == 1, it.isCurrent == 1, it.attemptDate ?: ""
                )
            }
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

    /** Среди попыток одной работы сделать итоговой последнюю по дате пересдачи
     *  (при пустой дате — по дате урока), затем по признаку переписывания и id. */
    private fun markLatestCurrent(list: List<LocalMark>, workType: String): List<LocalMark> {
        val fallbackDate = _detail.value?.lesson?.date ?: ""
        val latestIndex = list.indices
            .filter { list[it].workType == workType }
            .maxWithOrNull(
                compareBy<Int>(
                    { list[it].attemptDate.ifBlank { fallbackDate } },
                    { list[it].isRetake },
                    { list[it].id }
                )
            ) ?: return list
        return list.mapIndexed { i, m ->
            if (m.workType == workType) m.copy(isCurrent = i == latestIndex) else m
        }
    }

    /** Обновить локальный current_marks урока под только что изменённые оценки,
     *  сохранив итоговые оценки других уроков предмета. */
    private fun updateCurrentMarksLocally() {
        val detail = _detail.value ?: return
        val rebuilt = (detail.currentMarks ?: emptyMap())
            .mapValues { (_, list) -> list.filter { it.lessonId != currentLessonId } }
            .toMutableMap()
        for ((sid, list) in _localMarks.value) {
            val key = sid.toString()
            val keep = rebuilt[key].orEmpty().toMutableList()
            val byType = LinkedHashMap<String, MutableList<LocalMark>>()
            list.forEach { byType.getOrPut(it.workType) { mutableListOf() }.add(it) }
            for ((workType, attempts) in byType) {
                val current = attempts.lastOrNull { it.isCurrent } ?: attempts.lastOrNull() ?: continue
                if (current.value !in 2..5) continue
                keep.add(
                    CurrentMark(
                        id = current.id,
                        studentId = sid,
                        value = current.value,
                        workType = workType,
                        comment = current.comment.ifBlank { null },
                        lessonId = currentLessonId,
                        lessonDate = detail.lesson.date,
                        attemptDate = current.attemptDate.ifBlank { null },
                        isRetake = if (current.isRetake) 1 else 0
                    )
                )
            }
            rebuilt[key] = keep
        }
        _detail.value = detail.copy(currentMarks = rebuilt)
    }

    /** Добавить оценку (только 2–5) с указанием, за что она, и комментарием.
     *  retake=true — попытка переписывания: attemptDate (Y-m-d) — дата пересдачи;
     *  сервер привязывает её к уроку исходной оценки, после сохранения урок
     *  перезагружается (оценка «переезжает» в клетку исходной даты). */
    fun addMark(studentId: Int, value: Int, workType: String, comment: String = "", retake: Boolean = false, attemptDate: String = "") {
        if (value < 2 || value > 5) return
        val type = workType.ifBlank { "Урок" }
        val current = _localMarks.value.toMutableMap()
        val existing = current[studentId] ?: emptyList()
        current[studentId] = if (retake) {
            // Пересдача становится итоговой, прежняя попытка работы — в историю.
            existing.map { if (it.workType == type) it.copy(isCurrent = false) else it } +
                LocalMark(value, type, comment, isRetake = true, attemptDate = attemptDate)
        } else {
            existing + LocalMark(value, type, comment)
        }
        _localMarks.value = current
        updateCurrentMarksLocally()
        autoSaveMarks(retake)
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
        updateCurrentMarksLocally()
        _saveMessage.value = "Оценки добавлены: $added"
        autoSaveMarks()
    }

    /** Удалить оценку по индексу. */
    fun removeMark(studentId: Int, index: Int) {
        val current = _localMarks.value.toMutableMap()
        val list = current[studentId] ?: return
        if (index >= list.size) return
        val removed = list[index]
        // Попытка переписывания удаляется на сервере явным действием (по id)
        if (removed.isRetake && removed.id > 0) {
            val map = _removeMarkIds.value.toMutableMap()
            map[studentId] = (map[studentId] ?: emptyList()) + removed.id
            _removeMarkIds.value = map
        }
        val remaining = list.filterIndexed { i, _ -> i != index }
        // После удаления пересдачи исходная оценка снова становится итоговой.
        current[studentId] = if (removed.isRetake) {
            markLatestCurrent(remaining, removed.workType)
        } else {
            remaining
        }
        _localMarks.value = current
        updateCurrentMarksLocally()
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

    private fun autoSaveMarks(withRetake: Boolean = false) {
        val lessonId = currentLessonId
        if (lessonId == 0) return
        viewModelScope.launch {
            val marksForApi = _localMarks.value.mapValues { (_, list) ->
                list.map { MarkEntry(it.value, it.workType, it.comment, it.id, it.isRetake, it.attemptDate) }
            }
            repository.saveMarks(lessonId, marksForApi, _removeMarkIds.value)
            afterMarksSaved(withRetake, lessonId)
        }
    }

    /** После успешного сохранения переписывания — перезагрузка урока (попытка
     *  привязана к уроку исходной оценки) и очистка очереди удалений. */
    private suspend fun afterMarksSaved(withRetake: Boolean, lessonId: Int) {
        if (!withRetake && _removeMarkIds.value.isEmpty()) return
        _removeMarkIds.value = emptyMap()
        if (withRetake) {
            _isLoading.value = true
            when (val result = repository.lessonDetail(lessonId)) {
                is JournalRepository.Result.Success -> {
                    _detail.value = result.data
                    initLocalState(result.data)
                }
                is JournalRepository.Result.Error -> {}
            }
            _isLoading.value = false
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
                list.map { MarkEntry(it.value, it.workType, it.comment, it.id, it.isRetake, it.attemptDate) }
            }
            when (val result = repository.saveMarks(lessonId, marksForApi, _removeMarkIds.value)) {
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
            when (val result = repository.deleteHomework(homeworkId, lessonId)) {
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
