package com.nayanova.journal.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayanova.journal.data.model.Lesson
import com.nayanova.journal.data.model.ScheduleItem
import com.nayanova.journal.data.model.Subject
import com.nayanova.journal.data.repository.JournalRepository
import com.nayanova.journal.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Строка расписания в предпросмотре импорта.
 * enabled=false — класс/предмет не сопоставился или занятие уже есть.
 */
data class ScheduleImportItem(
    val schedule: ScheduleItem,
    val classId: Int? = null,
    val subjectId: Int? = null,
    /** Имя сопоставленного класса журнала (для показа в предпросмотре). */
    val journalClassName: String? = null,
    val exists: Boolean = false,
    val selected: Boolean = false
) {
    val enabled: Boolean get() = classId != null && subjectId != null && !exists
}

@HiltViewModel
class DayViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** Предпросмотр импорта из расписания (null — диалог закрыт). */
    private val _importItems = MutableStateFlow<List<ScheduleImportItem>?>(null)
    val importItems = _importItems.asStateFlow()

    private val _importLoading = MutableStateFlow(false)
    val importLoading = _importLoading.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage = _importMessage.asStateFlow()

    // Кеш предметов по классам
    private val subjectsCache = mutableMapOf<Int, List<Subject>>()

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
        _importItems.value = null
        loadLessons()
    }

    fun previousDay() = setDate(_selectedDate.value.minusDays(1))

    fun nextDay() = setDate(_selectedDate.value.plusDays(1))

    // ================== Импорт занятий из расписания ==================

    /** Загрузить расписание Безуглова С.В. на выбранный день и открыть предпросмотр. */
    fun openImportPreview() {
        if (_importLoading.value || _isImporting.value) return
        viewModelScope.launch {
            _importLoading.value = true
            _importItems.value = null
            val date = _selectedDate.value.toString()
            when (val result = scheduleRepository.teacherSchedule(date)) {
                is ScheduleRepository.Result.Error -> {
                    _importLoading.value = false
                    _importMessage.value = result.message
                }
                is ScheduleRepository.Result.Success -> {
                    if (result.data.isEmpty()) {
                        _importLoading.value = false
                        _importMessage.value = "В расписании нет занятий Безуглова С.В. на этот день"
                    } else {
                        _importItems.value = buildImportItems(date, result.data)
                        _importLoading.value = false
                    }
                }
            }
        }
    }

    /** Сопоставление строк расписания с классами и предметами журнала. */
    private suspend fun buildImportItems(
        date: String,
        items: List<ScheduleItem>
    ): List<ScheduleImportItem> {
        val classes = when (val r = repository.classes()) {
            is JournalRepository.Result.Success -> r.data
            is JournalRepository.Result.Error -> emptyList()
        }
        val existing = _lessons.value
        return items.map { item ->
            // Подгруппа «Информатика (7А.И2)» у класса 7А — это предмет «Информатика»
            // у группы «7 А2» журнала: класс из расписания + номер после точки
            val groupClass = item.parallelGroup?.let { group ->
                val parts = group.split('.')
                val num = parts.lastOrNull()?.filter { it.isDigit() } ?: ""
                if (parts.size == 2 && num.isNotEmpty()) item.className + num else null
            }
            val cls = classes.firstOrNull {
                normalizeName(it.name) == normalizeName(groupClass ?: item.className)
            }
            var classId: Int? = null
            var subjectId: Int? = null
            if (cls != null) {
                classId = cls.id
                val subjects = subjectsFor(cls.id)
                subjectId = subjects.firstOrNull {
                    normalizeName(it.name) == normalizeName(item.subject) ||
                        normalizeName(it.shortName ?: "") == normalizeName(item.subject)
                }?.id
            }
            val exists = classId != null && subjectId != null &&
                existing.any { it.classId == classId && it.subjectId == subjectId && it.date == date }
            ScheduleImportItem(
                schedule = item,
                classId = classId,
                subjectId = subjectId,
                journalClassName = cls?.name,
                exists = exists,
                selected = classId != null && subjectId != null && !exists
            )
        }
    }

    private suspend fun subjectsFor(classId: Int): List<Subject> {
        subjectsCache[classId]?.let { return it }
        val subjects = when (val r = repository.classSubjects(classId)) {
            is JournalRepository.Result.Success -> r.data
            is JournalRepository.Result.Error -> emptyList()
        }
        subjectsCache[classId] = subjects
        return subjects
    }

    // Без пробелов целиком: «7Г» и «7 Г» — один класс (и «Информатика и ИКТ» = «Информатика и ИКТ»)
    private fun normalizeName(s: String): String =
        s.uppercase().replace('Ё', 'Е').filter { !it.isWhitespace() }

    fun toggleImportItem(index: Int) {
        val current = _importItems.value ?: return
        if (index !in current.indices) return
        val item = current[index]
        if (!item.enabled) return
        _importItems.value = current.toMutableList().apply {
            set(index, item.copy(selected = !item.selected))
        }
    }

    fun dismissImportPreview() {
        if (!_isImporting.value) _importItems.value = null
    }

    /** Создать занятия журнала для выбранных строк предпросмотра. */
    fun confirmImport() {
        val selected = _importItems.value?.filter { it.enabled && it.selected } ?: return
        if (selected.isEmpty() || _isImporting.value) return
        viewModelScope.launch {
            _isImporting.value = true
            val date = _selectedDate.value.toString()
            var added = 0
            var failed = 0
            for (item in selected) {
                val room = item.schedule.room?.takeIf { it.isNotBlank() } ?: ""
                val result = repository.createLesson(
                    classId = item.classId!!,
                    subjectId = item.subjectId!!,
                    date = date,
                    startTime = item.schedule.timeStart ?: "",
                    note = if (room.isNotEmpty()) "Каб. $room" else ""
                )
                if (result is JournalRepository.Result.Success) added++ else failed++
            }
            _isImporting.value = false
            _importItems.value = null
            loadLessons()
            _importMessage.value = buildString {
                append("Добавлено занятий: $added")
                if (failed > 0) append(", с ошибками: $failed")
            }
        }
    }

    /** Сбросить показанное уведомление (после снекбара). */
    fun consumeImportMessage() {
        _importMessage.value = null
    }
}
