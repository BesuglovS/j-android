package ru.nayanovaacademy.journal.data.model

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
    @SerializedName("created_at") val createdAt: String?,
    /** Попытка переписывания (привязана к уроку, из которого поставлена). */
    @SerializedName("is_retake") val isRetake: Int = 0,
    /** Итоговая (последняя) попытка группы — участвует в среднем. */
    @SerializedName("is_current") val isCurrent: Int = 1,
    /** Машиночитаемая дата попытки (у переписывания — дата пересдачи). */
    @SerializedName("attempt_date") val attemptDate: String? = null
)

/** Текущая (итоговая) попытка ученика по предмету — для UI переписывания. */
data class CurrentMark(
    val id: Int,
    @SerializedName("student_id") val studentId: Int,
    val value: Int,
    @SerializedName("work_type") val workType: String,
    val comment: String?,
    @SerializedName("lesson_id") val lessonId: Int,
    @SerializedName("lesson_date") val lessonDate: String?,
    @SerializedName("attempt_date") val attemptDate: String? = null,
    @SerializedName("is_retake") val isRetake: Int = 0
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

/** Квиз python-курса, который класс должен пройти на 100%. */
data class HomeworkQuiz(
    @SerializedName("lesson_number") val lessonNumber: Int,
    val title: String? = null
)

/** Задача contest-web, которую нужно решить в рамках ДЗ. */
data class HomeworkContestTask(
    @SerializedName("contest_id") val contestId: Int,
    @SerializedName("task_id") val taskId: Int,
    @SerializedName("contest_title") val contestTitle: String? = null,
    @SerializedName("task_title") val taskTitle: String? = null
)

data class Homework(
    val id: Int,
    @SerializedName("lesson_id") val lessonId: Int,
    val title: String?,
    val description: String?,
    @SerializedName("due_date") val dueDate: String?,
    val quizzes: List<HomeworkQuiz> = emptyList(),
    @SerializedName("contest_tasks") val contestTasks: List<HomeworkContestTask> = emptyList()
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
    // Текущие (итоговые) попытки по предмету урока — для UI переписывания
    @SerializedName("current_marks") val currentMarks: Map<String, List<CurrentMark>>? = null,
    val remarks: Map<String, List<Remark>>,
    val attendance: Map<String, AttendanceRecord>,
    val homeworks: List<Homework>,
    // ДЗ, заданное на текущем уроке (на следующий урок)
    val homework: Homework? = null,
    // ДЗ с предыдущего урока (тот же класс и предмет), если задано
    @SerializedName("previous_homework") val previousHomework: Homework? = null,
    @SerializedName("previous_lesson_date") val previousLessonDate: String? = null,
    @SerializedName("previous_lesson_id") val previousLessonId: Int? = null,
    // Посещаемость предыдущего урока (studentId -> запись)
    @SerializedName("previous_attendance") val previousAttendance: Map<String, AttendanceRecord> = emptyMap()
)

/** Краткая запись посещаемости для предварительной оценки ДЗ. */
data class AttendanceSummary(
    val status: String? = null,
    @SerializedName("late_minutes") val lateMinutes: Int? = null
)

/** Элемент каталога: квиз python-курса. */
data class QuizCatalogItem(
    @SerializedName("lesson_number") val lessonNumber: Int,
    val title: String = ""
)

/** Элемент каталога: задача контеста. */
data class ContestTaskCatalogItem(
    val id: Int,
    val title: String = ""
)

/** Элемент каталога: контест с задачами. */
data class ContestCatalogItem(
    val id: Int,
    val title: String = "",
    val tasks: List<ContestTaskCatalogItem> = emptyList()
)

/** Каталог требований ДЗ. */
data class HomeworkCatalog(
    val quizzes: List<QuizCatalogItem> = emptyList(),
    val contests: List<ContestCatalogItem> = emptyList(),
    val errors: List<String> = emptyList()
)

/** Результат квиза в предварительной оценке. */
data class HomeworkQuizResult(
    @SerializedName("lesson_number") val lessonNumber: Int,
    val title: String? = null,
    val score: Int? = null,
    val done: Boolean = false
)

/** Результат задачи в предварительной оценке. */
data class HomeworkTaskResult(
    @SerializedName("contest_id") val contestId: Int,
    @SerializedName("task_id") val taskId: Int,
    @SerializedName("contest_title") val contestTitle: String? = null,
    val title: String? = null,
    val done: Boolean = false
)

/** Предварительная оценка одного ученика за ДЗ. */
data class HomeworkStudentGrade(
    @SerializedName("student_id") val studentId: Int,
    @SerializedName("student_name") val studentName: String = "",
    @SerializedName("external_id") val externalId: Int? = null,
    val points: Int = 0,
    @SerializedName("max_points") val maxPoints: Int = 0,
    val grade: Int? = null,
    @SerializedName("has_data") val hasData: Boolean = false,
    @SerializedName("quiz_results") val quizResults: List<HomeworkQuizResult> = emptyList(),
    @SerializedName("task_results") val taskResults: List<HomeworkTaskResult> = emptyList(),
    @SerializedName("attendance_current") val attendanceCurrent: AttendanceSummary? = null,
    @SerializedName("attendance_previous") val attendancePrevious: AttendanceSummary? = null,
    @SerializedName("existing_mark") val existingMark: Int? = null
)

/** Предварительный расчёт оценок за ДЗ. */
data class HomeworkGradePreview(
    val homework: Homework? = null,
    @SerializedName("max_points") val maxPoints: Int = 0,
    @SerializedName("auto_available") val autoAvailable: Boolean = false,
    val errors: List<String> = emptyList(),
    @SerializedName("current_lesson_id") val currentLessonId: Int = 0,
    @SerializedName("previous_lesson_id") val previousLessonId: Int? = null,
    val students: List<HomeworkStudentGrade> = emptyList()
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

/** Запись оценки при сохранении (журнал/среднее). */
data class MarkEntry(
    val value: Int,
    val workType: String,
    val comment: String = "",
    /** Серверный id существующей строки (повторная отсылка попытки). */
    val id: Int = 0,
    /** Попытка переписывания: привязывается к исходной оценке. */
    val retake: Boolean = false,
    /** Дата пересдачи (Y-m-d) — только для попыток переписывания. */
    val attemptDate: String = ""
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
