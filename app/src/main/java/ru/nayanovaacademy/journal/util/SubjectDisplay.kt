package ru.nayanovaacademy.journal.util

import ru.nayanovaacademy.journal.data.model.SchoolClass
import ru.nayanovaacademy.journal.data.model.Subject

/**
 * Предмет класса для отображения в списках выбора. Объединённые предметы
 * (9 классы — «Информатика и Труд», 8 классы — «Информатика и моделирование»,
 * 7 классы — «Информатика и инженерия БПЛА») показываются одной карточкой,
 * в т.ч. когда информатика лежит в классах-подгруппах (7 А1/7 А2, 8 А1/8 А2).
 */
object SubjectDisplay {

    /** Карточка «класс + предмет»; для объединённого — общее название. */
    data class Choice(
        val classId: Int,
        val className: String,
        val subjectId: Int,
        val subjectName: String,
        val isGroup: Boolean = false
    )

    /**
     * Карточки предметов для набора классов. Объединённые предметы
     * показываются одной карточкой у основного класса параллели;
     * классы-подгруппы, вошедшие в объединение, не показываются.
     */
    fun choices(
        classes: List<SchoolClass>,
        subjectsByClassId: Map<Int, List<Subject>>
    ): List<Choice> {
        val result = mutableListOf<Choice>()
        classes.groupBy { SubjectMerge.mainClassName(it.name) }.forEach { (mainName, siblings) ->
            val main = siblings.firstOrNull { it.name.trim() == mainName.trim() }
            val subjects = siblings.associate { it.id to subjectsByClassId[it.id].orEmpty() }
            val group = SubjectMerge.group(
                main?.grade ?: siblings.firstOrNull()?.grade,
                mainName,
                siblings,
                subjects
            )
            if (group != null && main != null) {
                val member = group.members.firstOrNull { it.classId == main.id } ?: group.members.first()
                result += Choice(main.id, main.name, member.subject.id, group.label, isGroup = true)
            } else {
                siblings.forEach { cls ->
                    subjectsByClassId[cls.id].orEmpty().forEach { subject ->
                        result += Choice(cls.id, cls.name, subject.id, subject.name)
                    }
                }
            }
        }
        return result
    }
}
