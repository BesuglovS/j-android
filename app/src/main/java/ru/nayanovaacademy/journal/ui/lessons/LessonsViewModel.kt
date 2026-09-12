package ru.nayanovaacademy.journal.ui.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ru.nayanovaacademy.journal.data.model.Lesson
import ru.nayanovaacademy.journal.data.model.Subject
import ru.nayanovaacademy.journal.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LessonsViewModel @Inject constructor(
    private val repository: JournalRepository
) : ViewModel() {

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _createSuccess = MutableStateFlow<Int?>(null)
    val createSuccess = _createSuccess.asStateFlow()

    /** Заголовок объединённого предмета (когда открыт «Информатика и Труд»). */
    private val _title = MutableStateFlow<String?>(null)
    val title = _title.asStateFlow()

    /** Варианты предмета для создания урока в объединённом журнале (пусто — обычный предмет). */
    private val _subjectOptions = MutableStateFlow<List<Subject>>(emptyList())
    val subjectOptions = _subjectOptions.asStateFlow()

    fun loadLessons(classId: Int, subjectId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _title.value = repository.subjectGroupLabel(classId, subjectId)
            _subjectOptions.value = resolveSubjectOptions(classId, subjectId)
            when (val result = repository.mergedLessons(classId, subjectId)) {
                is JournalRepository.Result.Success -> {
                    _lessons.value = result.data
                }
                is JournalRepository.Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    /** Для объединённого предмета — оба предмета (для выбора при создании урока). */
    private suspend fun resolveSubjectOptions(classId: Int, subjectId: Int): List<Subject> {
        val group = repository.subjectGroup(classId) ?: return emptyList()
        if (subjectId !in ru.nayanovaacademy.journal.util.SubjectMerge.subjectIds(group)) return emptyList()
        val subjects = when (val r = repository.classSubjects(classId)) {
            is JournalRepository.Result.Success -> r.data
            is JournalRepository.Result.Error -> return emptyList()
        }
        return subjects.filter { it.id == group.informatics.id || it.id == group.trud.id }
            .sortedBy { it.name }
    }

    fun createLesson(classId: Int, subjectId: Int, date: String, startTime: String, topic: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.createLesson(classId, subjectId, date, startTime, topic)) {
                is JournalRepository.Result.Success -> {
                    _createSuccess.value = result.data
                }
                is JournalRepository.Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    fun clearCreateSuccess() {
        _createSuccess.value = null
    }
}
