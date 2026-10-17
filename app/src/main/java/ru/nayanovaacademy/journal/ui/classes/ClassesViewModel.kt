package ru.nayanovaacademy.journal.ui.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ru.nayanovaacademy.journal.data.model.SchoolClass
import ru.nayanovaacademy.journal.data.model.Subject
import ru.nayanovaacademy.journal.data.repository.JournalRepository
import ru.nayanovaacademy.journal.util.CookieStore
import ru.nayanovaacademy.journal.util.SubjectDisplay
import ru.nayanovaacademy.journal.util.SubjectMerge
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

    private val _subjectsLoading = MutableStateFlow<Set<Int>>(emptySet())
    val subjectsLoading = _subjectsLoading.asStateFlow()

    private val _subjectsError = MutableStateFlow<Map<Int, String>>(emptyMap())
    val subjectsError = _subjectsError.asStateFlow()

    fun loadClasses() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.classes()) {
                is JournalRepository.Result.Success -> {
                    _classes.value = result.data
                    _error.value = null
                }
                is JournalRepository.Result.Error -> {
                    // Если список уже загружен, не заменяем его полноэкранной ошибкой.
                    if (_classes.value.isEmpty()) {
                        _error.value = result.message
                    }
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Загружает предметы класса лениво (при выборе класса).
     * Уже загруженные предметы кэшируются; повторная загрузка — только по force.
     */
    fun loadSubjects(classId: Int, force: Boolean = false) {
        if (!force && _subjects.value.containsKey(classId)) return
        viewModelScope.launch {
            _subjectsLoading.value = _subjectsLoading.value + classId
            _subjectsError.value = _subjectsError.value - classId
            when (val result = repository.classSubjects(classId)) {
                is JournalRepository.Result.Success -> {
                    _subjects.value = _subjects.value + (classId to result.data)
                }
                is JournalRepository.Result.Error -> {
                    _subjectsError.value = _subjectsError.value + (classId to result.message)
                }
            }
            _subjectsLoading.value = _subjectsLoading.value - classId
        }
    }

    /**
     * Загружает предметы класса и его подгрупп (классов вида «7 А1»/«8 А1»):
     * в 7 и 8 параллелях информатика может лежать в подгруппах, и для
     * объединённого предмета нужны предметы всех классов параллели.
     */
    fun loadClassSubjects(classId: Int, force: Boolean = false) {
        val cls = _classes.value.firstOrNull { it.id == classId }
        if (cls == null) {
            loadSubjects(classId, force)
            return
        }
        val mainName = SubjectMerge.mainClassName(cls.name)
        _classes.value
            .filter { SubjectMerge.mainClassName(it.name) == mainName }
            .forEach { loadSubjects(it.id, force) }
    }

    fun logout() {
        viewModelScope.launch {
            cookieStore.clearSession()
        }
    }

    /**
     * Предметы класса для экрана выбора: объединённые предметы показываются
     * одной карточкой (в т.ч. когда информатика лежит в классах-подгруппах,
     * как «7 А1»/«8 А1»).
     */
    fun displayChoices(classId: Int): List<SubjectDisplay.Choice> {
        val cls = _classes.value.firstOrNull { it.id == classId } ?: return emptyList()
        val mainName = SubjectMerge.mainClassName(cls.name)
        val siblings = _classes.value.filter { SubjectMerge.mainClassName(it.name) == mainName }
        val subjectsByClassId = siblings.associate { it.id to (_subjects.value[it.id] ?: emptyList()) }
        return SubjectDisplay.choices(siblings, subjectsByClassId).filter { it.classId == classId }
    }

    /** Классы без подгрупп — для сетки классов (подгруппы прячутся). */
    fun visibleClasses(): List<SchoolClass> =
        _classes.value.filter { !SubjectMerge.isSubgroup(it.name) }

    /** id класса и его подгрупп — для индикатора загрузки предметов. */
    fun siblingClassIds(classId: Int): List<Int> {
        val cls = _classes.value.firstOrNull { it.id == classId } ?: return listOf(classId)
        val mainName = SubjectMerge.mainClassName(cls.name)
        return _classes.value
            .filter { SubjectMerge.mainClassName(it.name) == mainName }
            .map { it.id }
    }
}
