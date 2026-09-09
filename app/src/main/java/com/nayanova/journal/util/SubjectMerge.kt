package com.nayanova.journal.util

import com.nayanova.journal.data.model.Subject

/**
 * Объединение «Информатика» и «Труд (технология)» в один предмет для классов
 * преподавателя Безуглова С.В. Уроки, оценки, посещаемость и средняя считаются
 * общими, ДЗ показывается на ближайшем из двух видов уроков.
 *
 * Определение применяется только для 9 классов, у которых в списке предметов
 * присутствуют оба предмета пары.
 */
object SubjectMerge {

    /** Пара предметов одного объединённого предмета в конкретном классе. */
    data class SubjectGroup(
        val classId: Int,
        val informatics: Subject,
        val trud: Subject
    ) {
        val label: String get() = displayName(informatics, trud)
    }

    private const val INFORMATICS_NAME = "ИНФОРМАТИКА"
    private const val TRUD_PREFIX = "ТРУД"

    fun normalize(s: String): String =
        s.uppercase().replace('Ё', 'Е').filter { !it.isWhitespace() }

    /** 9 класс: по полю grade или по названию («9А», «9 А»). */
    fun isNinthGrade(grade: Int?, className: String?): Boolean {
        if (grade == 9) return true
        if (grade != null) return false
        return normalize(className ?: "").startsWith("9")
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

    /** Возвращает пару (Информатика, Труд), если в классе есть оба предмета. */
    fun findPair(subjects: List<Subject>): Pair<Subject, Subject>? {
        val informatics = subjects.firstOrNull { isInformatics(it) } ?: return null
        val trud = subjects.firstOrNull { isTrudTechnology(it) } ?: return null
        return informatics to trud
    }

    /** Объединённое название предмета: «Информатика и Труд (технология)». */
    fun displayName(informatics: Subject, trud: Subject): String =
        "${informatics.name} и ${trud.name}"

    fun subjectIds(group: SubjectGroup): Set<Int> = setOf(group.informatics.id, group.trud.id)
}