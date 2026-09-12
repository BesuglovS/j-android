package ru.nayanovaacademy.journal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val grade: Int?,
    val schoolYear: String?,
    val studentCount: Int
)

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: Int,
    val classId: Int,
    val name: String,
    val shortName: String?
)

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: Int,
    val subjectId: Int,
    val classId: Int,
    val date: String,
    val startTime: String?,
    val topic: String?,
    val lessonType: String?,
    val note: String?,
    val className: String?,
    val subjectName: String?
)

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: Int,
    val classId: Int,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val isActive: Int
)

/** Связь «класс — ученик» (аналог student_classes на сервере): ученик может состоять в нескольких классах. */
@Entity(tableName = "class_students", primaryKeys = ["classId", "studentId"])
data class ClassStudentRefEntity(
    val classId: Int,
    val studentId: Int
)

@Entity(tableName = "quarters")
data class QuarterEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val startDate: String?,
    val endDate: String?
)

@Entity(tableName = "lesson_details")
data class LessonDetailEntity(
    @PrimaryKey val lessonId: Int,
    val json: String,
    val updatedAt: Long
)

@Entity(tableName = "class_journals", primaryKeys = ["classId", "subjectId"])
data class ClassJournalEntity(
    val classId: Int,
    val subjectId: Int,
    val json: String,
    val updatedAt: Long
)

@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val operation: String,
    val payload: String,
    val lessonId: Int,
    val createdAt: Long,
    val attempts: Int
)

object PendingOperation {
    const val CREATE_LESSON = "CREATE_LESSON"
    const val SAVE_MARKS = "SAVE_MARKS"
    const val SAVE_ATTENDANCE = "SAVE_ATTENDANCE"
    const val SAVE_REMARKS = "SAVE_REMARKS"
    const val SAVE_HOMEWORK = "SAVE_HOMEWORK"
    const val DELETE_HOMEWORK = "DELETE_HOMEWORK"
}