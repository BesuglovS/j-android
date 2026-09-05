package com.nayanova.journal.data.local

import com.nayanova.journal.data.model.Lesson
import com.nayanova.journal.data.model.Quarter
import com.nayanova.journal.data.model.SchoolClass
import com.nayanova.journal.data.model.Student
import com.nayanova.journal.data.model.Subject

object EntityMappers {

    fun SchoolClass.toEntity() = ClassEntity(
        id = id,
        name = name,
        grade = grade,
        schoolYear = schoolYear,
        studentCount = studentCount
    )

    fun ClassEntity.toModel() = SchoolClass(
        id = id,
        name = name,
        grade = grade,
        schoolYear = schoolYear,
        studentCount = studentCount
    )

    fun Subject.toEntity() = SubjectEntity(
        id = id,
        classId = classId,
        name = name,
        shortName = shortName
    )

    fun SubjectEntity.toModel() = Subject(
        id = id,
        classId = classId,
        name = name,
        shortName = shortName
    )

    fun Lesson.toEntity() = LessonEntity(
        id = id,
        subjectId = subjectId,
        classId = classId,
        date = date,
        startTime = startTime,
        topic = topic,
        lessonType = lessonType,
        note = note,
        className = className,
        subjectName = subjectName
    )

    fun LessonEntity.toModel() = Lesson(
        id = id,
        subjectId = subjectId,
        classId = classId,
        date = date,
        startTime = startTime,
        topic = topic,
        lessonType = lessonType,
        note = note,
        className = className,
        subjectName = subjectName
    )

    fun Student.toEntity() = StudentEntity(
        id = id,
        classId = classId,
        lastName = lastName,
        firstName = firstName,
        middleName = middleName,
        isActive = isActive
    )

    fun StudentEntity.toModel() = Student(
        id = id,
        classId = classId,
        lastName = lastName,
        firstName = firstName,
        middleName = middleName,
        isActive = isActive
    )

    fun Quarter.toEntity() = QuarterEntity(
        id = id,
        name = name,
        startDate = startDate,
        endDate = endDate
    )

    fun QuarterEntity.toModel() = Quarter(
        id = id,
        name = name,
        startDate = startDate,
        endDate = endDate
    )
}