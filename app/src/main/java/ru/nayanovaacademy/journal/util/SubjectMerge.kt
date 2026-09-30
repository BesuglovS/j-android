package ru.nayanovaacademy.journal.util

import ru.nayanovaacademy.journal.data.model.Subject

/**
 * Объединение предметов в один. Поддерживаются две пары:
 * 1) «Информатика» + «Труд (технология)» в 9 классах (преподаватель
 *    Безуглова С.В.);
 * 2) «Информатика» + «Моделирование и пилотирование беспилотных
 *    авиационных систем» в 8 классах.
 *
 * Уроки, оценки, посещаемость и средняя считаются общими, ДЗ показывается
 * на ближайшем из двух видов уроков. Пара применяется к классу, только если
 * в его списке предметов присутствуют оба предмета пары.
 */
object SubjectMerge {

    /** Пара предметов одного объединённого предмета в конкретном классе. */
    data class SubjectGroup(
        val classId: Int,
        val first: Subject,
        val second: Subject
    ) {
        val label: String get() = displayName(first, second)
    }

    private const val INFORMATICS_NAME = "ИНФОРМАТИКА"
    private const val TRUD_PREFIX = "ТРУД"
    private const val UAV_SYSTEMS_PREFIX = "МОДЕЛИРОВАНИЕИПИЛОТИРОВАНИЕ"

    fun normalize(s: String): String =
        s.uppercase().replace('Ё', 'Е').filter { !it.isWhitespace() }

    /** 9 класс: по полю grade или по названию («9А», «9 А»). */
    fun isNinthGrade(grade: Int?, className: String?): Boolean {
        if (grade == 9) return true
        if (grade != null) return false
        return normalize(className ?: "").startsWith("9")
    }

    /** 8 класс: по полю grade или по названию («8А», «8 А»). */
    fun isEighthGrade(grade: Int?, className: String?): Boolean {
        if (grade == 8) return true
        if (grade != null) return false
        return normalize(className ?: "").startsWith("8")
    }

    fun isInformatics(subject: Subject): Boolean {
        val key = normalize(subject.name)
        val short = normalize(subject.shortName ?: "")
        return key == INFORMATICS_NAME || short == INFORMATICS_NAME
    }

    fun isTrudTechnology(subject: Subject): Boolean {
        val key = normalize(subject.name)
        val short = normalize(subject.shortName ?: "")
        return key.startsWith(TRUD_PREFIX) || short.startsWith(TRUD_PREFIX) || key.contains("ТЕХНОЛОГИЯ") || short.contains("ТЕХНОЛОГИЯ")
    }

    fun isUavSystems(subject: Subject): Boolean {
        val key = normalize(subject.name)
        val short = normalize(subject.shortName ?: "")
        return key.contains(UAV_SYSTEMS_PREFIX) || short.contains(UAV_SYSTEMS_PREFIX)
    }

    /**
     * Пара предметов объединённого предмета для класса: в 9 классах —
     * (Информатика, Труд), в 8 классах — (Информатика, Моделирование
     * и пилотирование беспилотных авиационных систем). Возвращает null,
     * если класс без объединения или в его списке нет обоих предметов пары.
     */
    fun findPair(grade: Int?, className: String?, subjects: List<Subject>): Pair<Subject, Subject>? {
        if (isNinthGrade(grade, className)) {
            val informatics = subjects.firstOrNull { isInformatics(it) } ?: return null
            val trud = subjects.firstOrNull { isTrudTechnology(it) } ?: return null
            return informatics to trud
        }
        if (isEighthGrade(grade, className)) {
            val informatics = subjects.firstOrNull { isInformatics(it) } ?: return null
            val uav = subjects.firstOrNull { isUavSystems(it) } ?: return null
            return informatics to uav
        }
        return null
    }

    /** Объединённое название предмета: «A и B». */
    fun displayName(first: Subject, second: Subject): String =
        "${first.name} и ${second.name}"

    fun subjectIds(group: SubjectGroup): Set<Int> = setOf(group.first.id, group.second.id)
}