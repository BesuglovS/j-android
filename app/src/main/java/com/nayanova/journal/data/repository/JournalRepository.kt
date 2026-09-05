package com.nayanova.journal.data.repository

import com.nayanova.journal.data.api.JournalApi
import com.nayanova.journal.data.local.CacheDao
import com.nayanova.journal.data.local.EntityMappers.toEntity
import com.nayanova.journal.data.local.EntityMappers.toModel
import com.nayanova.journal.data.local.PendingChangeDao
import com.nayanova.journal.data.model.*
import com.nayanova.journal.data.sync.CreateLessonPayload
import com.nayanova.journal.data.sync.DeleteHomeworkPayload
import com.nayanova.journal.data.sync.MarkPayload
import com.nayanova.journal.data.sync.NetworkMonitor
import com.nayanova.journal.data.sync.SaveAttendancePayload
import com.nayanova.journal.data.sync.SaveHomeworkPayload
import com.nayanova.journal.data.sync.SaveMarksPayload
import com.nayanova.journal.data.sync.SaveRemarksPayload
import com.nayanova.journal.data.sync.SyncManager
import com.nayanova.journal.util.CookieStore
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val api: JournalApi,
    private val cookieStore: CookieStore,
    private val syncManager: SyncManager,
    private val networkMonitor: NetworkMonitor,
    private val cacheDao: CacheDao,
    private val pendingChangeDao: PendingChangeDao,
    private val scope: CoroutineScope
) {
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String, val code: Int = 0) : Result<Nothing>()
    }

    private fun isOnline(): Boolean = networkMonitor.isOnline.value

    /** Возвращает Success из кеша, если данные есть; иначе — переданную ошибку. */
    private fun <T> fromCache(cached: T?, err: Result.Error): Result<T> {
        return if (cached != null) Result.Success(cached) else err
    }

    /**
     * Прямой вход по логину/паролю через единый портал (без WebView):
     * POST на /api/login.php, из ответа извлекаем куку auth_session.
     */
    suspend fun login(login: String, password: String): Result<Boolean> {
        return try {
            val response = api.authLogin(
                "https://auth.nayanovaacademy.ru/api/login.php",
                mapOf("login" to login, "password" to password)
            )
            if (response.isSuccessful) {
                val cookie = extractSessionCookie(response.headers().values("Set-Cookie"))
                if (cookie != null) {
                    cookieStore.setCookie(cookie)
                    syncManager.fetchAndCacheAll()
                    Result.Success(true)
                } else {
                    Result.Error("Не удалось получить сессию")
                }
            } else {
                val error = parseError(response.errorBody()?.string())
                Result.Error(error ?: "Ошибка входа", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка сети")
        }
    }

    /** Достать значение auth_session из заголовков Set-Cookie ответа портала. */
    private fun extractSessionCookie(setCookieHeaders: List<String>): String? {
        for (header in setCookieHeaders) {
            val parts = header.split(";")
            val session = parts.firstOrNull { it.trim().startsWith("auth_session=") }
                ?.substringAfter("auth_session=")
                ?.trim()
            if (!session.isNullOrEmpty()) return session
        }
        return null
    }

    private fun parseError(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            JsonParser.parseString(errorBody).asJsonObject["error"]?.asString ?: errorBody
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Проверка сессии. Без сети (если сессия и данные уже есть локально)
     * считаем авторизацию валидной — приложение работает офлайн.
     */
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
            if (cookieStore.getCookie().isNotEmpty()) Result.Success(true)
            else Result.Error(e.message ?: "Ошибка сети")
        }
    }

    suspend fun classes(): Result<List<SchoolClass>> {
        return try {
            if (isOnline()) {
                val response = api.classes()
                if (response.isSuccessful) {
                    val list = response.body()?.get("classes") ?: emptyList()
                    syncManager.cacheClasses(list)
                    Result.Success(list)
                } else {
                    Result.Error("Ошибка загрузки классов", response.code())
                }
            } else {
                fromCache(
                    cacheDao.getClasses().map { it.toModel() }.takeIf { it.isNotEmpty() },
                    Result.Error("Нет сети — список классов недоступен")
                )
            }
        } catch (e: Exception) {
            fromCache(
                cacheDao.getClasses().map { it.toModel() }.takeIf { it.isNotEmpty() },
                Result.Error(e.message ?: "Ошибка сети")
            )
        }
    }

    suspend fun classSubjects(classId: Int): Result<List<Subject>> {
        return try {
            if (isOnline()) {
                val response = api.classSubjects(classId)
                if (response.isSuccessful) {
                    val list = response.body()?.get("subjects") ?: emptyList()
                    syncManager.cacheSubjects(classId, list)
                    Result.Success(list)
                } else {
                    Result.Error("Ошибка загрузки предметов", response.code())
                }
            } else {
                fromCache(
                    cacheDao.getSubjects(classId).map { it.toModel() }.takeIf { it.isNotEmpty() },
                    Result.Error("Нет сети — предметы недоступны")
                )
            }
        } catch (e: Exception) {
            fromCache(
                cacheDao.getSubjects(classId).map { it.toModel() }.takeIf { it.isNotEmpty() },
                Result.Error(e.message ?: "Ошибка сети")
            )
        }
    }

    suspend fun lessons(classId: Int = 0, subjectId: Int = 0): Result<List<Lesson>> {
        return try {
            if (isOnline()) {
                val response = api.lessons(classId, subjectId)
                if (response.isSuccessful) {
                    val list = response.body()?.get("lessons") ?: emptyList()
                    syncManager.cacheLessons(list)
                    Result.Success(list)
                } else {
                    Result.Error("Ошибка загрузки уроков", response.code())
                }
            } else {
                fromCache(
                    cacheDao.getLessons(classId, subjectId).map { it.toModel() }.takeIf { it.isNotEmpty() },
                    Result.Error("Нет сети — уроки недоступны")
                )
            }
        } catch (e: Exception) {
            fromCache(
                cacheDao.getLessons(classId, subjectId).map { it.toModel() }.takeIf { it.isNotEmpty() },
                Result.Error(e.message ?: "Ошибка сети")
            )
        }
    }

    /** Занятия на конкретную дату (формат ГГГГ-ММ-ДД). */
    suspend fun lessonsByDate(date: String): Result<List<Lesson>> {
        return try {
            if (isOnline()) {
                val response = api.lessons(date = date)
                if (response.isSuccessful) {
                    val list = response.body()?.get("lessons") ?: emptyList()
                    syncManager.cacheLessons(list)
                    Result.Success(list)
                } else {
                    Result.Error("Ошибка загрузки занятий", response.code())
                }
            } else {
                fromCache(
                    cacheDao.getLessonsByDate(date).map { it.toModel() }.takeIf { it.isNotEmpty() },
                    Result.Error("Нет сети — расписание недоступно")
                )
            }
        } catch (e: Exception) {
            fromCache(
                cacheDao.getLessonsByDate(date).map { it.toModel() }.takeIf { it.isNotEmpty() },
                Result.Error(e.message ?: "Ошибка сети")
            )
        }
    }

    suspend fun lessonDetail(lessonId: Int): Result<LessonDetail> {
        return try {
            if (isOnline()) {
                val response = api.lessonDetail(lessonId)
                if (response.isSuccessful) {
                    val detail = response.body()
                    if (detail != null) {
                        syncManager.cacheLessonDetail(lessonId, detail)
                        syncManager.cacheStudents(detail.lesson.classId, detail.students)
                        Result.Success(detail)
                    } else {
                        Result.Error("Пустой ответ")
                    }
                } else {
                    Result.Error("Ошибка загрузки урока", response.code())
                }
            } else {
                fromCache(
                    syncManager.getCachedLessonDetail(lessonId),
                    Result.Error("Нет сети — урок недоступен")
                )
            }
        } catch (e: Exception) {
            fromCache(
                syncManager.getCachedLessonDetail(lessonId),
                Result.Error(e.message ?: "Ошибка сети")
            )
        }
    }

    suspend fun createLesson(classId: Int, subjectId: Int, date: String, startTime: String = "", topic: String = ""): Result<Int> {
        val offlineCreate: suspend (Int, Boolean) -> Result<Int> = { localId, enqueue ->
            val payload = CreateLessonPayload(classId, subjectId, date, startTime, topic, localId)
            syncManager.applyCreateLesson(payload)
            if (enqueue) syncManager.enqueueCreateLesson(payload)
            Result.Success(localId)
        }
        return try {
            if (isOnline()) {
                val body = HashMap<String, Any>().apply {
                    put("class_id", classId)
                    put("subject_id", subjectId)
                    put("date", date)
                }
                if (startTime.isNotEmpty()) body["start_time"] = startTime
                if (topic.isNotEmpty()) body["topic"] = topic
                val response = api.lessonCreate(body)
                if (response.isSuccessful) {
                    val id = response.body()?.get("id") ?: 0
                    if (id != 0) {
                        syncManager.cacheLesson(
                            Lesson(
                                id = id, subjectId = subjectId, classId = classId, date = date,
                                startTime = startTime.ifBlank { null }, topic = topic.ifBlank { null },
                                lessonType = null, note = null
                            )
                        )
                        Result.Success(id)
                    } else {
                        Result.Error("Ошибка создания урока")
                    }
                } else {
                    Result.Error("Ошибка создания урока", response.code())
                }
            } else {
                offlineCreate(syncManager.nextNegativeId(), true)
            }
        } catch (e: Exception) {
            offlineCreate(syncManager.nextNegativeId(), true)
        }
    }

    /**
     * Несколько оценок за урок с указанием работы и комментария:
     * marks[studentId] = список троек (значение 2..5, за что, комментарий).
     * Пустой список удаляет все оценки.
     */
    suspend fun saveMarks(lessonId: Int, marks: Map<Int, List<Triple<Int, String, String>>>): Result<Boolean> {
        val payload = SaveMarksPayload(
            lessonId = lessonId,
            marks = marks.mapValues { (_, entries) ->
                entries.filter { (value, _, _) -> value in 2..5 }
                    .map { (value, workType, comment) -> MarkPayload(value, workType, comment) }
            }
        )
        val applyLocally: suspend () -> Result<Boolean> = {
            syncManager.applyMarks(lessonId, payload)
            syncManager.enqueueSaveMarks(payload)
            Result.Success(true)
        }
        return try {
            if (isOnline()) {
                val body = HashMap<String, Any>()
                body["marks"] = payload.marks.mapValues { (_, list) ->
                    list.map { mapOf("value" to it.value, "work_type" to it.workType, "comment" to it.comment) }
                }
                val response = api.marksSave(lessonId, body)
                if (response.isSuccessful) {
                    syncManager.applyMarks(lessonId, payload)
                    Result.Success(true)
                } else {
                    Result.Error("Ошибка сохранения оценок", response.code())
                }
            } else {
                applyLocally()
            }
        } catch (e: Exception) {
            applyLocally()
        }
    }

    /** Домашнее задание урока (на следующий урок). */
    suspend fun saveHomework(lessonId: Int, title: String, description: String, dueDate: String): Result<Boolean> {
        val payload = SaveHomeworkPayload(lessonId, title, description, dueDate)
        val applyLocally: suspend () -> Result<Boolean> = {
            syncManager.applyHomework(lessonId, payload)
            syncManager.enqueueSaveHomework(payload)
            Result.Success(true)
        }
        return try {
            if (isOnline()) {
                val body = HashMap<String, Any>().apply {
                    put("title", title)
                    put("description", description)
                    put("due_date", dueDate)
                }
                val response = api.homeworkSave(lessonId, body)
                if (response.isSuccessful) {
                    syncManager.applyHomework(lessonId, payload)
                    Result.Success(true)
                } else {
                    Result.Error("Ошибка сохранения домашнего задания", response.code())
                }
            } else {
                applyLocally()
            }
        } catch (e: Exception) {
            applyLocally()
        }
    }

    /** Удалить домашнее задание и связанные оценки (work_type = 'ДЗ'). */
    suspend fun deleteHomework(homeworkId: Int, lessonId: Int = 0): Result<Boolean> {
        if (homeworkId <= 0) {
            // Локальное (несинхронизированное) ДЗ — просто убираем из кеша.
            syncManager.applyDeleteHomework(lessonId, homeworkId)
            return Result.Success(true)
        }
        val applyLocally: suspend () -> Result<Boolean> = {
            syncManager.applyDeleteHomework(lessonId, homeworkId)
            syncManager.enqueueDeleteHomework(DeleteHomeworkPayload(homeworkId, lessonId))
            Result.Success(true)
        }
        return try {
            if (isOnline()) {
                val response = api.homeworkDelete(homeworkId)
                if (response.isSuccessful) {
                    syncManager.applyDeleteHomework(lessonId, homeworkId)
                    Result.Success(true)
                } else {
                    Result.Error("Ошибка удаления домашнего задания", response.code())
                }
            } else {
                applyLocally()
            }
        } catch (e: Exception) {
            applyLocally()
        }
    }

    suspend fun saveRemarks(lessonId: Int, remarks: Map<Int, List<String>>, removeIds: List<Int> = emptyList()): Result<Boolean> {
        val payload = SaveRemarksPayload(lessonId, remarks, removeIds)
        val applyLocally: suspend () -> Result<Boolean> = {
            syncManager.applyRemarks(lessonId, payload)
            syncManager.enqueueSaveRemarks(payload)
            Result.Success(true)
        }
        return try {
            if (isOnline()) {
                val body = HashMap<String, Any>()
                body["remarks"] = remarks
                if (removeIds.isNotEmpty()) body["remove_ids"] = removeIds
                val response = api.remarksSave(lessonId, body)
                if (response.isSuccessful) {
                    syncManager.applyRemarks(lessonId, payload)
                    Result.Success(true)
                } else {
                    Result.Error("Ошибка сохранения замечаний", response.code())
                }
            } else {
                applyLocally()
            }
        } catch (e: Exception) {
            applyLocally()
        }
    }

    suspend fun saveAttendance(lessonId: Int, attendance: Map<Int, AttendanceEntry>): Result<Boolean> {
        val payload = SaveAttendancePayload(
            lessonId = lessonId,
            attendance = attendance.mapValues { (_, entry) ->
                com.nayanova.journal.data.sync.AttendanceItemPayload(
                    status = entry.status,
                    lateMinutes = entry.lateMinutes
                )
            }
        )
        val applyLocally: suspend () -> Result<Boolean> = {
            syncManager.applyAttendance(lessonId, payload)
            syncManager.enqueueSaveAttendance(payload)
            Result.Success(true)
        }
        return try {
            if (isOnline()) {
                val body = HashMap<String, Any>()
                body["attendance"] = payload.attendance.mapValues { (_, a) ->
                    buildMap<String, Any> {
                        put("status", a.status)
                        if (a.status == "late" && a.lateMinutes > 0) {
                            put("late_minutes", a.lateMinutes)
                        }
                    }
                }
                val response = api.attendanceSave(lessonId, body)
                if (response.isSuccessful) {
                    syncManager.applyAttendance(lessonId, payload)
                    Result.Success(true)
                } else {
                    Result.Error("Ошибка сохранения посещаемости", response.code())
                }
            } else {
                applyLocally()
            }
        } catch (e: Exception) {
            applyLocally()
        }
    }

    suspend fun students(classId: Int = 0): Result<List<Student>> {
        return try {
            if (isOnline()) {
                val response = api.students(classId)
                if (response.isSuccessful) {
                    val list = response.body()?.get("students") ?: emptyList()
                    syncManager.cacheStudents(classId, list)
                    Result.Success(list)
                } else {
                    Result.Error("Ошибка загрузки учеников", response.code())
                }
            } else {
                fromCache(
                    cacheDao.getStudents(classId).map { it.toModel() }.takeIf { it.isNotEmpty() },
                    Result.Error("Нет сети — ученики недоступны")
                )
            }
        } catch (e: Exception) {
            fromCache(
                cacheDao.getStudents(classId).map { it.toModel() }.takeIf { it.isNotEmpty() },
                Result.Error(e.message ?: "Ошибка сети")
            )
        }
    }

    suspend fun quarters(): Result<List<Quarter>> {
        return try {
            if (isOnline()) {
                val response = api.quarters()
                if (response.isSuccessful) {
                    val list = response.body()?.get("quarters") ?: emptyList()
                    syncManager.cacheQuarters(list)
                    Result.Success(list)
                } else {
                    Result.Error("Ошибка загрузки четвертей", response.code())
                }
            } else {
                fromCache(
                    cacheDao.getQuarters().map { it.toModel() }.takeIf { it.isNotEmpty() },
                    Result.Error("Нет сети — четверти недоступны")
                )
            }
        } catch (e: Exception) {
            fromCache(
                cacheDao.getQuarters().map { it.toModel() }.takeIf { it.isNotEmpty() },
                Result.Error(e.message ?: "Ошибка сети")
            )
        }
    }

    suspend fun classJournal(classId: Int, subjectId: Int): Result<ClassJournalData> {
        return try {
            if (isOnline()) {
                val response = api.classJournal(classId, subjectId)
                if (response.isSuccessful) {
                    val data = response.body()
                    if (data != null) {
                        syncManager.cacheClassJournal(classId, subjectId, data)
                        syncManager.cacheStudents(classId, data.students)
                        Result.Success(data)
                    } else {
                        Result.Error("Пустой ответ")
                    }
                } else {
                    Result.Error("Ошибка загрузки журнала", response.code())
                }
            } else {
                fromCache(
                    syncManager.getCachedClassJournalData(classId, subjectId),
                    Result.Error("Нет сети — журнал недоступен")
                )
            }
        } catch (e: Exception) {
            fromCache(
                syncManager.getCachedClassJournalData(classId, subjectId),
                Result.Error(e.message ?: "Ошибка сети")
            )
        }
    }

    /** Триггер синхронизации из UI (полустовый запрос на синхронизацию сейчас). */
    suspend fun syncNow(): com.nayanova.journal.data.sync.SyncResult = syncManager.syncPendingChanges()

    /** Сеть доступна сейчас. */
    val isOnline = networkMonitor.isOnline

    /** Количество ожидающих синхронизации изменений (офлайн-очередь). */
    val pendingChanges: StateFlow<Int> = pendingChangeDao.observeCount()
        .stateIn(scope, SharingStarted.Eagerly, 0)
}