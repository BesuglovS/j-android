package com.nayanova.journal.data.repository

import com.nayanova.journal.data.api.JournalApi
import com.nayanova.journal.data.model.*
import com.nayanova.journal.util.CookieStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val api: JournalApi,
    private val cookieStore: CookieStore
) {
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String, val code: Int = 0) : Result<Nothing>()
    }

    suspend fun checkAuth(): Result<Boolean> {
        return try {
            val response = api.me()
            if (response.isSuccessful) {
                Result.Success(true)
            } else if (response.code() == 401) {
                cookieStore.clearSession()
                Result.Success(false)
            } else {
                Result.Error("Ошибка авторизации", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun classes(): Result<List<SchoolClass>> {
        return try {
            val response = api.classes()
            if (response.isSuccessful) {
                Result.Success(response.body()?.get("classes") ?: emptyList())
            } else {
                Result.Error("Ошибка загрузки классов", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun classSubjects(classId: Int): Result<List<Subject>> {
        return try {
            val response = api.classSubjects(classId)
            if (response.isSuccessful) {
                Result.Success(response.body()?.get("subjects") ?: emptyList())
            } else {
                Result.Error("Ошибка загрузки предметов", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun lessons(classId: Int = 0, subjectId: Int = 0): Result<List<Lesson>> {
        return try {
            val response = api.lessons(classId, subjectId)
            if (response.isSuccessful) {
                Result.Success(response.body()?.get("lessons") ?: emptyList())
            } else {
                Result.Error("Ошибка загрузки уроков", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    /** Занятия на конкретную дату (формат ГГГГ-ММ-ДД). */
    suspend fun lessonsByDate(date: String): Result<List<Lesson>> {
        return try {
            val response = api.lessons(date = date)
            if (response.isSuccessful) {
                Result.Success(response.body()?.get("lessons") ?: emptyList())
            } else {
                Result.Error("Ошибка загрузки занятий", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun lessonDetail(lessonId: Int): Result<LessonDetail> {
        return try {
            val response = api.lessonDetail(lessonId)
            if (response.isSuccessful) {
                val detail = response.body()
                if (detail != null) {
                    Result.Success(detail)
                } else {
                    Result.Error("Пустой ответ")
                }
            } else {
                Result.Error("Ошибка загрузки урока", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun createLesson(classId: Int, subjectId: Int, date: String, startTime: String = "", topic: String = ""): Result<Int> {
        return try {
            val body = HashMap<String, Any>().apply {
                put("class_id", classId)
                put("subject_id", subjectId)
                put("date", date)
            }
            if (startTime.isNotEmpty()) body["start_time"] = startTime
            if (topic.isNotEmpty()) body["topic"] = topic
            val response = api.lessonCreate(body)
            if (response.isSuccessful) {
                Result.Success(response.body()?.get("id") ?: 0)
            } else {
                Result.Error("Ошибка создания урока", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    /**
     * Несколько оценок за урок с указанием работы и комментария:
     * marks[studentId] = список троек (значение 2..5, за что, комментарий).
     * Пустой список удаляет все оценки.
     */
    suspend fun saveMarks(lessonId: Int, marks: Map<Int, List<Triple<Int, String, String>>>): Result<Boolean> {
        return try {
            val body = HashMap<String, Any>()
            body["marks"] = marks.mapValues { (_, entries) ->
                entries.filter { (value, _, _) -> value in 2..5 }.map { (value, workType, comment) ->
                    mapOf("value" to value, "work_type" to workType, "comment" to comment)
                }
            }
            val response = api.marksSave(lessonId, body)
            if (response.isSuccessful) Result.Success(true)
            else Result.Error("Ошибка сохранения оценок", response.code())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    /** Домашнее задание урока (на следующий урок). */
    suspend fun saveHomework(lessonId: Int, title: String, description: String, dueDate: String): Result<Boolean> {
        return try {
            val body = HashMap<String, Any>().apply {
                put("title", title)
                put("description", description)
                put("due_date", dueDate)
            }
            val response = api.homeworkSave(lessonId, body)
            if (response.isSuccessful) Result.Success(true)
            else Result.Error("Ошибка сохранения домашнего задания", response.code())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    /** Удалить домашнее задание и связанные оценки (work_type = 'ДЗ'). */
    suspend fun deleteHomework(homeworkId: Int): Result<Boolean> {
        return try {
            val response = api.homeworkDelete(homeworkId)
            if (response.isSuccessful) Result.Success(true)
            else Result.Error("Ошибка удаления домашнего задания", response.code())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun saveRemarks(lessonId: Int, remarks: Map<Int, List<String>>, removeIds: List<Int> = emptyList()): Result<Boolean> {
        return try {
            val body = HashMap<String, Any>()
            body["remarks"] = remarks
            if (removeIds.isNotEmpty()) body["remove_ids"] = removeIds
            val response = api.remarksSave(lessonId, body)
            if (response.isSuccessful) Result.Success(true)
            else Result.Error("Ошибка сохранения замечаний", response.code())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun saveAttendance(lessonId: Int, attendance: Map<Int, AttendanceEntry>): Result<Boolean> {
        return try {
            val body = HashMap<String, Any>()
            body["attendance"] = attendance.mapValues { (_, entry) ->
                buildMap<String, Any> {
                    put("status", entry.status)
                    if (entry.status == "late" && entry.lateMinutes > 0) {
                        put("late_minutes", entry.lateMinutes)
                    }
                }
            }
            val response = api.attendanceSave(lessonId, body)
            if (response.isSuccessful) Result.Success(true)
            else Result.Error("Ошибка сохранения посещаемости", response.code())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun students(classId: Int = 0): Result<List<Student>> {
        return try {
            val response = api.students(classId)
            if (response.isSuccessful) {
                Result.Success(response.body()?.get("students") ?: emptyList())
            } else {
                Result.Error("Ошибка загрузки учеников", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun quarters(): Result<List<Quarter>> {
        return try {
            val response = api.quarters()
            if (response.isSuccessful) {
                Result.Success(response.body()?.get("quarters") ?: emptyList())
            } else {
                Result.Error("Ошибка загрузки четвертей", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun classJournal(classId: Int, subjectId: Int): Result<ClassJournalData> {
        return try {
            val response = api.classJournal(classId, subjectId)
            if (response.isSuccessful) {
                val data = response.body()
                if (data != null) {
                    Result.Success(data)
                } else {
                    Result.Error("Пустой ответ")
                }
            } else {
                Result.Error("Ошибка загрузки журнала", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }
}
