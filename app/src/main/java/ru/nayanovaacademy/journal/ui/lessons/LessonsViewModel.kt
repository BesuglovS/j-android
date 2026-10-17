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

    /** Для объединённого предмета — все его предметы (для выбора при создании урока). */
    private suspend fun resolveSubjectOptions(classId: Int, subjectId: Int): List<Subject> {
        val group = repository.subjectGroup(classId) ?: return emptyList()
        if (!group.containsSubject(subjectId)) return emptyList()
        return group.members.map { it.subject }.distinctBy { it.name }.sortedBy { it.name }
    }

    fun createLesson(classId: Int, subjectId: Int, date: String, startTime: String, topic: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // Для объединённого предмета урок создаётся у класса-владельца
            // предмета: информатика 7 А/8 А лежит в подгруппах (7 А1, 8 А1).
            val ownerClassId = repository.subjectGroup(classId)
                ?.members?.firstOrNull { it.subject.id == subjectId }?.classId
                ?: classId
            when (val result = repository.createLesson(ownerClassId, subjectId, date, startTime, topic)) {
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
