package ru.nayanovaacademy.journal.ui.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ru.nayanovaacademy.journal.data.repository.JournalRepository
import ru.nayanovaacademy.journal.util.SubjectDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Все предметы всех классов одним списком карточек «класс + предмет»,
 * отсортированным по номеру класса, затем по букве класса, затем по названию
 * предмета. Объединённые предметы (9/8/7 классы, в т.ч. с подгруппами)
 * показываются одной карточкой у основного класса.
 */
@HiltViewModel
class AllSubjectsViewModel @Inject constructor(
    private val repository: JournalRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<SubjectDisplay.Choice>>(emptyList())
    val items = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val classesResult = repository.classes()) {
                is JournalRepository.Result.Error -> {
                    _error.value = classesResult.message
                }
                is JournalRepository.Result.Success -> {
                    val classes = classesResult.data
                    // Предметы всех классов (в т.ч. подгрупп) загружаются
                    // параллельно; репозиторий отдаёт кэш, если сети нет.
                    val subjectsByClassId = coroutineScope {
                        classes.map { cls ->
                            async { cls.id to repository.classSubjects(cls.id) }
                        }.awaitAll()
                    }.mapNotNull { (classId, result) ->
                        (result as? JournalRepository.Result.Success)?.let { classId to it.data }
                    }.toMap()

                    _items.value = SubjectDisplay
                        .choices(classes, subjectsByClassId)
                        .sortedWith(CLASS_SUBJECT_ORDER)

                    // Классы есть, но ни для одного не удалось получить предметы.
                    if (_items.value.isEmpty() && classes.isNotEmpty() && subjectsByClassId.isEmpty()) {
                        _error.value = "Не удалось загрузить предметы"
                    }
                }
            }
            _isLoading.value = false
        }
    }

    private companion object {
        /** Номер класса из названия («10 А» → 10; иначе — в конец). */
        fun gradeOf(className: String): Int =
            className.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE

        /** Буква класса из названия («7 А» → «А»). */
        fun letterOf(className: String): String =
            className.filter { !it.isDigit() }.trim().uppercase()

        val CLASS_SUBJECT_ORDER: Comparator<SubjectDisplay.Choice> = compareBy(
            { gradeOf(it.className) },
            { letterOf(it.className) },
            { it.subjectName.lowercase() },
            { it.classId },
            { it.subjectId }
        )
    }
}
