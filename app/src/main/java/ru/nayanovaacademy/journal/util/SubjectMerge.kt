package ru.nayanovaacademy.journal.util

import ru.nayanovaacademy.journal.data.model.SchoolClass
import ru.nayanovaacademy.journal.data.model.Subject

/**
 * Объединение журналов нескольких предметов в один. Поддерживаются три
 * параллели:
 * 1) 9 классы — «Информатика» + «Труд (технология)» (преподаватель
 *    Безуглова С.В.);
 * 2) 8 классы — «Информатика» + «Моделирование и пилотирование
 *    беспилотных авиационных систем»;
 * 3) 7 классы — «Информатика» + «Инженерия беспилотных авиационных
 *    систем».
 *
 * В 8 и 7 параллелях информатика может преподаваться по подгруппам:
 * отдельными классами «8 А1», «8 А2» (соответственно «7 А1», «7 А2»),
 * а не в основном классе. Тогда в объединение входят предметы подгрупп,
 * а сами подгруппы в списках прячутся за основным классом.
 *
 * Уроки, оценки, посещаемость и средняя считаются общими, ДЗ показывается
 * на ближайшем из объединённых уроков. Объединение применяется только если
 * в параллели присутствуют оба нужных предмета.
 */
object SubjectMerge {

    /** Предмет объединения: конкретный предмет конкретного класса. */
    data class Member(
        val classId: Int,
        val className: String,
        val subject: Subject
    )

    /**
     * Объединённый предмет параллели: все входящие в него предметы
     * (информатика — первыми, затем парный предмет).
     */
    data class SubjectGroup(val members: List<Member>) {
        /** Названия предметов без повторов: «Информатика и Труд (технология)». */
        val label: String
            get() = members.map { it.subject.name }.distinct().joinToString(" и ")

        val subjectIds: Set<Int> get() = members.map { it.subject.id }.toSet()
        val classIds: Set<Int> get() = members.map { it.classId }.toSet()

        fun contains(classId: Int, subjectId: Int): Boolean =
            members.any { it.classId == classId && it.subject.id == subjectId }

        fun containsSubject(subjectId: Int): Boolean = subjectId in subjectIds
    }

    private const val INFORMATICS_NAME = "ИНФОРМАТИКА"
    private const val TRUD_PREFIX = "ТРУД"
    private const val UAV_SYSTEMS_PREFIX = "МОДЕЛИРОВАНИЕИПИЛОТИРОВАНИЕ"
    private const val ENGINEERING_PREFIX = "ИНЖЕНЕРИЯ"
    private const val BPLA = "БПЛА"
    private const val UNMANNED = "БЕСПИЛОТН"

    fun normalize(s: String): String =
        s.uppercase().replace('Ё', 'Е').filter { !it.isWhitespace() }

    /**
     * Имя основного класса параллели: у класса-подгруппы отбрасывается
     * хвост из цифр («7 А1» → «7 А», «8 А2» → «8 А»). У обычного класса
     * имя не меняется («10 А» → «10 А»).
     */
    fun mainClassName(className: String): String = className.trim().trimEnd { it.isDigit() }

    /** Имя класса-подгруппы: «7 А1» — подгруппа «7 А». */
    fun isSubgroup(className: String): Boolean =
        mainClassName(className) != className.trim()

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

    /** 7 класс: по полю grade или по названию («7А», «7 А»). */
    fun isSeventhGrade(grade: Int?, className: String?): Boolean {
        if (grade == 7) return true
        if (grade != null) return false
        return normalize(className ?: "").startsWith("7")
    }

    fun isInformatics(subject: Subject): Boolean {
        val key = normalize(subject.name)
        val short = normalize(subject.shortName ?: "")
        // «Информатика» и возможные варианты («Информатика (по подгруппам)»),
        // но не «Информатика и моделирование» (это отдельный предмет).
        return key == INFORMATICS_NAME || key.startsWith("ИНФОРМАТИКА(") ||
            short == INFORMATICS_NAME || short.startsWith("ИНФОРМАТИКА(")
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

    /** «Инженерия беспилотных авиационных систем» / «Инженерия БПЛА». */
    fun isBplaEngineering(subject: Subject): Boolean {
        val key = normalize(subject.name)
        val short = normalize(subject.shortName ?: "")
        return (key.contains(ENGINEERING_PREFIX) && (key.contains(BPLA) || key.contains(UNMANNED))) ||
            (short.contains(ENGINEERING_PREFIX) && (short.contains(BPLA) || short.contains(UNMANNED)))
    }

    /**
     * Объединённый предмет параллели [mainName] (основной класс + подгруппы).
     * Возвращает null, если класс не 7–9 или в параллели нет обоих предметов.
     *
     * @param siblings классы параллели (основной и подгруппы)
     * @param subjectsByClassId предметы каждого класса параллели
     */
    fun group(
        grade: Int?,
        mainName: String,
        siblings: List<SchoolClass>,
        subjectsByClassId: Map<Int, List<Subject>>
    ): SubjectGroup? {
        val gradeNum = grade ?: mainName.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        val other: (Subject) -> Boolean = when (gradeNum) {
            9 -> ::isTrudTechnology
            8 -> ::isUavSystems
            7 -> ::isBplaEngineering
            else -> return null
        }
        // Основной класс — первым, затем подгруппы по имени.
        val ordered = siblings.sortedWith(
            compareBy({ it.name.trim() != mainName.trim() }, { it.name })
        )
        val main = ordered.firstOrNull { it.name.trim() == mainName.trim() }

        // Информатика: либо в основном классе целиком, либо в подгруппах.
        val informatics = mutableListOf<Member>()
        val mainInformatics = main?.let { c ->
            subjectsByClassId[c.id].orEmpty().firstOrNull { isInformatics(it) }
        }
        if (main != null && mainInformatics != null) {
            informatics += Member(main.id, main.name, mainInformatics)
        } else {
            ordered.forEach { c ->
                subjectsByClassId[c.id].orEmpty()
                    .filter { isInformatics(it) }
                    .forEach { informatics += Member(c.id, c.name, it) }
            }
        }

        val others = ordered.flatMap { c ->
            subjectsByClassId[c.id].orEmpty()
                .filter(other)
                .map { Member(c.id, c.name, it) }
        }

        if (informatics.isEmpty() || others.isEmpty()) return null
        return SubjectGroup(informatics + others)
    }
}
