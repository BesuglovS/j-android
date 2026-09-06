package com.nayanova.journal.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Int,
    val login: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("is_admin") val isAdmin: Boolean
)

data class SchoolClass(
    val id: Int,
    val name: String,
    val grade: Int?,
    @SerializedName("school_year") val schoolYear: String?,
    @SerializedName("student_count") val studentCount: Int = 0
)

data class Subject(
    val id: Int,
    @SerializedName("class_id") val classId: Int,
    val name: String,
    @SerializedName("short_name") val shortName: String?
)

data class Lesson(
    val id: Int,
    @SerializedName("subject_id") val subjectId: Int,
    @SerializedName("class_id") val classId: Int,
    val date: String,
    @SerializedName("start_time") val startTime: String?,
    val topic: String?,
    @SerializedName("lesson_type") val lessonType: String?,
    val note: String?,
    @SerializedName("class_name") val className: String? = null,
    @SerializedName("subject_name") val subjectName: String? = null
)

data class Student(
    val id: Int,
    @SerializedName("class_id") val classId: Int,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("middle_name") val middleName: String?,
    @SerializedName("is_active") val isActive: Int = 1
)

data class Mark(
    val id: Int,
    @SerializedName("student_id") val studentId: Int,
    @SerializedName("lesson_id") val lessonId: Int,
    val value: Int,
    @SerializedName("work_type") val workType: String,
    val comment: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class Remark(
    val id: Int,
    @SerializedName("lesson_id") val lessonId: Int,
    @SerializedName("student_id") val studentId: Int,
    val text: String,
    @SerializedName("created_at") val createdAt: String?
)

data class AttendanceRecord(
    val id: Int,
    @SerializedName("student_id") val studentId: Int,
    @SerializedName("lesson_id") val lessonId: Int,
    val status: String,
    val comment: String?,
    @SerializedName("late_minutes") val lateMinutes: Int? = null,
    @SerializedName("created_at") val createdAt: String?
)

data class Homework(
    val id: Int,
    @SerializedName("lesson_id") val lessonId: Int,
    val title: String?,
    val description: String?,
    @SerializedName("due_date") val dueDate: String?
)

data class Quarter(
    val id: Int,
    val name: String,
    @SerializedName("start_date") val startDate: String?,
    @SerializedName("end_date") val endDate: String?
)

data class LessonDetail(
    val lesson: Lesson,
    val students: List<Student>,
    // Несколько оценок за урок: список оценок на ученика
    val marks: Map<String, List<Mark>>,
    val remarks: Map<String, List<Remark>>,
    val attendance: Map<String, AttendanceRecord>,
    val homeworks: List<Homework>,
    // ДЗ, заданное на текущем уроке (на следующий урок)
    val homework: Homework? = null,
    // ДЗ с предыдущего урока (тот же класс и предмет), если задано
    @SerializedName("previous_homework") val previousHomework: Homework? = null,
    @SerializedName("previous_lesson_date") val previousLessonDate: String? = null
)

data class AttendanceEntry(
    val status: String = "present",
    val lateMinutes: Int = 0
)

data class ClassJournalData(
    val lessons: List<Lesson>,
    val students: List<Student>,
    val marks: Map<String, Map<String, List<Mark>>>,
    val attendance: Map<String, Map<String, AttendanceEntry>>
)

/** Строка расписания из r-web (api.php?action=schedule). */
data class ScheduleItem(
    val date: String,
    @SerializedName("day_of_week") val dayOfWeek: String? = null,
    @SerializedName("class_name") val className: String = "",
    @SerializedName("lesson_num") val lessonNum: Int? = null,
    @SerializedName("time_start") val timeStart: String? = null,
    @SerializedName("time_end") val timeEnd: String? = null,
    val subject: String = "",
    val teacher: String? = null,
    val room: String? = null,
    @SerializedName("parallel_group") val parallelGroup: String? = null
)
