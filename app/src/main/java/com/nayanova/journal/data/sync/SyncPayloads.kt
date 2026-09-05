package com.nayanova.journal.data.sync

import com.google.gson.annotations.SerializedName

/** Payload'ы очереди синхронизации — типизированные, чтобы при воспроизведении
 *  типы (Int вместо Double и т.п.) не искажались при перепрохождении через Gson. */

data class CreateLessonPayload(
    val classId: Int,
    val subjectId: Int,
    val date: String,
    val startTime: String,
    val topic: String,
    /** Локальный отрицательный id урока, созданного офлайн. */
    val localLessonId: Int
)

data class MarkPayload(
    val value: Int,
    @SerializedName("work_type") val workType: String,
    val comment: String
)

data class SaveMarksPayload(
    val lessonId: Int,
    val marks: Map<Int, List<MarkPayload>>
)

data class AttendanceItemPayload(
    val status: String,
    @SerializedName("late_minutes") val lateMinutes: Int
)

data class SaveAttendancePayload(
    val lessonId: Int,
    val attendance: Map<Int, AttendanceItemPayload>
)

data class SaveRemarksPayload(
    val lessonId: Int,
    val remarks: Map<Int, List<String>>,
    @SerializedName("remove_ids") val removeIds: List<Int>
)

data class SaveHomeworkPayload(
    val lessonId: Int,
    val title: String,
    val description: String,
    @SerializedName("due_date") val dueDate: String
)

data class DeleteHomeworkPayload(
    val homeworkId: Int,
    /** Урок, из которого удаляется ДЗ — для обновления кеша после синхронизации. */
    @SerializedName("lesson_id") val lessonId: Int? = null
)