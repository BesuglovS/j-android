package ru.nayanovaacademy.journal.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ru.nayanovaacademy.journal.data.model.Lesson
import ru.nayanovaacademy.journal.data.model.ScheduleItem
import ru.nayanovaacademy.journal.data.model.Subject
import ru.nayanovaacademy.journal.data.repository.JournalRepository
import ru.nayanovaacademy.journal.data.repository.ScheduleRepository
import ru.nayanovaacademy.journal.util.CookieStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Действие сверки занятия с расписанием. */
enum class ScheduleSyncAction { ADD, UPDATE, DELETE, UNCHANGED, UNMATCHED }

/**
 * Строка плана сверки дня с расписанием.
 * actionable=true — строку можно применить (ADD/UPDATE/DELETE).
 */
data class ScheduleSyncItem(
    val action: ScheduleSyncAction,
    /** Занятие журнала (для UPDATE/DELETE/UNCHANGED). */
    val lessonId: Int? = null,
    val classId: Int? = null,
    val subjectId: Int? = null,
    val journalClassName: String? = null,
    /** Заголовок строки: «08:00 9 Б — Информатика». */
    val title: String,
    /** Пояснение: diff времени/кабинета или причина. */
    val detail: String? = null,
    /** Целевое время начала (ADD/UPDATE). */
    val startTime: String = "",
    /** Целевое примечание — кабинет (ADD/UPDATE). */
    val note: String = "",
    /** Менять ли примечание при обновлении. */
    val updateNote: Boolean = false,
    val selected: Boolean = false
) {
    val actionable: Boolean
        get() = action == ScheduleSyncAction.ADD ||
            action == ScheduleSyncAction.UPDATE ||
            action == ScheduleSyncAction.DELETE
}

@HiltViewModel
class DayViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val scheduleRepository: ScheduleRepository,
    private val cookieStore: CookieStore
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** План сверки дня с расписанием (null — диалог закрыт). */
    private val _scheduleSyncItems = MutableStateFlow<List<ScheduleSyncItem>?>(null)
    val scheduleSyncItems = _scheduleSyncItems.asStateFlow()

    private val _scheduleSyncLoading = MutableStateFlow(false)
    val scheduleSyncLoading = _scheduleSyncLoading.asStateFlow()

    private val _isApplyingSchedule = MutableStateFlow(false)
    val isApplyingSchedule = _isApplyingSchedule.asStateFlow()

    private val _scheduleSyncMessage = MutableStateFlow<String?>(null)
    val scheduleSyncMessage = _scheduleSyncMessage.asStateFlow()

    /** Идёт принудительная синхронизация. */
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    /** Результат/ошибка принудительной синхронизации (одноразовое сообщение). */
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

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
        _scheduleSyncItems.value = null
        loadLessons()
    }

    fun previousDay() = setDate(_selectedDate.value.minusDays(1))

    fun nextDay() = setDate(_selectedDate.value.plusDays(1))

    /**
     * Принудительная синхронизация: проталкивание офлайн-очереди, обновление
     * справочников/кеша и перезагрузка сегодняшнего списка занятий.
     */
    fun syncNow() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncMessage.value = null
        viewModelScope.launch {
            val result = repository.syncNow()
            _syncMessage.value = when {
                result.remaining > 0 -> "Синхронизировано ${result.synced}, в очереди ${result.remaining}"
                result.synced > 0 -> "Синхронизировано изменений: ${result.synced}"
                else -> null
            }
            // Обновить список занятий дня после синхронизации
            try {
                when (val r = repository.lessonsByDate(_selectedDate.value.toString())) {
                    is JournalRepository.Result.Success -> {
                        _lessons.value = r.data
                        _error.value = null
                    }
                    is JournalRepository.Result.Error -> {}
                }
            } catch (_: Exception) {}
            _isSyncing.value = false
        }
    }

    /** Сообщение синхронизации показано — сбросить. */
    fun consumeSyncMessage() {
        _syncMessage.value = null
    }

    /** Выход: очистить сессию. Сохранённые логин/пароль остаются для следующего запуска. */
    fun logout() {
        viewModelScope.launch {
            cookieStore.clearSession()
        }
    }

    // ================== Сверка дня с расписанием ==================

    /** Загрузить расписание Безуглова С.В. на выбранный день и открыть предпросмотр сверки. */
    fun openScheduleSyncPreview() {
        if (_scheduleSyncLoading.value || _isApplyingSchedule.value) return
        viewModelScope.launch {
            _scheduleSyncLoading.value = true
            _scheduleSyncItems.value = null
            val date = _selectedDate.value.toString()
            when (val result = scheduleRepository.teacherSchedule(date)) {
                is ScheduleRepository.Result.Error -> {
                    _scheduleSyncLoading.value = false
                    _scheduleSyncMessage.value = result.message
                }
                is ScheduleRepository.Result.Success -> {
                    if (result.data.isEmpty()) {
                        _scheduleSyncLoading.value = false
                        _scheduleSyncMessage.value = "В расписании нет занятий Безуглова С.В. на этот день"
                    } else {
                        _scheduleSyncItems.value = buildSyncPlan(date, result.data)
                        _scheduleSyncLoading.value = false
                    }
                }
            }
        }
    }

    /**
     * Сопоставляет расписание с занятиями дня и строит план:
     * добавить недостающие, обновить время/кабинет, удалить лишние.
     * Уроки с отрицательным id (созданные офлайн) в сверку не берём.
     */
    private suspend fun buildSyncPlan(
        date: String,
        schedule: List<ScheduleItem>
    ): List<ScheduleSyncItem> {
        val classes = when (val r = repository.classes()) {
            is JournalRepository.Result.Success -> r.data
            is JournalRepository.Result.Error -> emptyList()
        }
        val journalLessons = when (val r = repository.lessonsByDate(date)) {
            is JournalRepository.Result.Success -> r.data
            is JournalRepository.Result.Error -> _lessons.value
        }
        val byKey = journalLessons
            .filter { it.id > 0 }
            .groupBy { it.classId to it.subjectId }
            .mapValues { it.value.toMutableList() }

        val adds = mutableListOf<ScheduleSyncItem>()
        val updates = mutableListOf<ScheduleSyncItem>()
        val unchanged = mutableListOf<ScheduleSyncItem>()
        val unmatched = mutableListOf<ScheduleSyncItem>()

        for (item in schedule) {
            // Подгруппа «8А.И2» у класса 8А — это класс журнала «8 А2».
            val groupClass = item.parallelGroup?.let { group ->
                val parts = group.split('.')
                val num = parts.lastOrNull()?.filter { it.isDigit() } ?: ""
                if (parts.size == 2 && num.isNotEmpty()) item.className + num else null
            }
            val cls = classes.firstOrNull {
                normalizeName(it.name) == normalizeName(groupClass ?: item.className)
            }
            val subjectId = cls?.let { c ->
                subjectsFor(c.id).firstOrNull {
                    normalizeName(it.name) == normalizeName(item.subject) ||
                        normalizeName(it.shortName ?: "") == normalizeName(item.subject)
                }?.id
            }
            val time = item.timeStart ?: ""
            val roomNote = item.room?.takeIf { it.isNotBlank() }?.let { "Каб. $it" } ?: ""
            val classLabel = item.className + (item.parallelGroup?.let { " ($it)" } ?: "")
            val title = listOf(time.ifBlank { "—" }, classLabel, item.subject).joinToString(" ")

            if (cls == null || subjectId == null) {
                unmatched += ScheduleSyncItem(
                    action = ScheduleSyncAction.UNMATCHED,
                    title = title,
                    detail = if (cls == null) "Класс не найден в журнале" else "Предмет не найден в журнале",
                    startTime = time,
                    note = roomNote
                )
                continue
            }

            val candidates = byKey[cls.id to subjectId]
            if (candidates.isNullOrEmpty()) {
                adds += ScheduleSyncItem(
                    action = ScheduleSyncAction.ADD,
                    classId = cls.id,
                    subjectId = subjectId,
                    journalClassName = cls.name,
                    title = title,
                    detail = "Новое занятие",
                    startTime = time,
                    note = roomNote,
                    selected = true
                )
                continue
            }

            val lesson = candidates.removeAt(0)
            val timeDiff = time.isNotBlank() && (lesson.startTime ?: "") != time
            // Примечание трогаем только если оно пустое или в формате «Каб. …».
            val noteChangeable = roomNote.isNotBlank() &&
                (lesson.note.isNullOrBlank() || lesson.note.startsWith("Каб."))
            val noteDiff = noteChangeable && lesson.note != roomNote
            val base = ScheduleSyncItem(
                action = ScheduleSyncAction.UNCHANGED,
                lessonId = lesson.id,
                classId = cls.id,
                subjectId = subjectId,
                journalClassName = cls.name,
                title = title,
                startTime = time,
                note = roomNote,
                updateNote = noteDiff
            )
            if (timeDiff || noteDiff) {
                val diff = listOfNotNull(
                    if (timeDiff) "${lesson.startTime ?: "—"} → ${time.ifBlank { "—" }}" else null,
                    if (noteDiff) "${lesson.note ?: "—"} → $roomNote" else null
                ).joinToString(" · ")
                updates += base.copy(action = ScheduleSyncAction.UPDATE, detail = diff, selected = true)
            } else {
                unchanged += base.copy(detail = "Уже соответствует")
            }
        }

        // Оставшиеся занятия журнала — их нет в расписании: удалить.
        val deletes = byKey.values.flatten().map { lesson ->
            ScheduleSyncItem(
                action = ScheduleSyncAction.DELETE,
                lessonId = lesson.id,
                classId = lesson.classId,
                subjectId = lesson.subjectId,
                journalClassName = lesson.className,
                title = listOf(
                    lesson.startTime ?: "—",
                    lesson.className ?: "Класс",
                    lesson.subjectName ?: "Предмет"
                ).joinToString(" "),
                detail = "Нет в расписании",
                selected = true
            )
        }

        return adds + updates + deletes + unchanged + unmatched
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

    fun toggleScheduleSyncItem(index: Int) {
        val current = _scheduleSyncItems.value ?: return
        if (index !in current.indices) return
        val item = current[index]
        if (!item.actionable) return
        _scheduleSyncItems.value = current.toMutableList().apply {
            set(index, item.copy(selected = !item.selected))
        }
    }

    fun dismissScheduleSyncPreview() {
        if (!_isApplyingSchedule.value) _scheduleSyncItems.value = null
    }

    /** Применить выбранные строки плана сверки. */
    fun confirmScheduleSync() {
        val selected = _scheduleSyncItems.value?.filter { it.actionable && it.selected } ?: return
        if (selected.isEmpty() || _isApplyingSchedule.value) return
        viewModelScope.launch {
            _isApplyingSchedule.value = true
            val date = _selectedDate.value.toString()
            var added = 0
            var updated = 0
            var deleted = 0
            var failed = 0
            for (item in selected) {
                when (item.action) {
                    ScheduleSyncAction.ADD -> {
                        val r = repository.createLesson(
                            classId = item.classId!!,
                            subjectId = item.subjectId!!,
                            date = date,
                            startTime = item.startTime,
                            note = item.note
                        )
                        if (r is JournalRepository.Result.Success) added++ else failed++
                    }
                    ScheduleSyncAction.UPDATE -> {
                        val r = repository.updateLesson(
                            lessonId = item.lessonId!!,
                            startTime = item.startTime,
                            note = item.note,
                            updateNote = item.updateNote
                        )
                        if (r is JournalRepository.Result.Success) updated++ else failed++
                    }
                    ScheduleSyncAction.DELETE -> {
                        val r = repository.deleteLesson(item.lessonId!!)
                        if (r is JournalRepository.Result.Success) deleted++ else failed++
                    }
                    else -> {}
                }
            }
            _isApplyingSchedule.value = false
            _scheduleSyncItems.value = null
            loadLessons()
            _scheduleSyncMessage.value = buildString {
                append("Добавлено: $added")
                append(", обновлено: $updated")
                append(", удалено: $deleted")
                if (failed > 0) append(", ошибок: $failed")
            }
        }
    }

    /** Сбросить показанное уведомление сверки (после снекбара). */
    fun consumeScheduleSyncMessage() {
        _scheduleSyncMessage.value = null
    }
}
