package ru.nayanovaacademy.journal.data.repository

import ru.nayanovaacademy.journal.data.api.JournalApi
import ru.nayanovaacademy.journal.data.local.CacheDao
import ru.nayanovaacademy.journal.data.local.EntityMappers.toEntity
import ru.nayanovaacademy.journal.data.local.EntityMappers.toModel
import ru.nayanovaacademy.journal.data.local.PendingChangeDao
import ru.nayanovaacademy.journal.data.model.*
import ru.nayanovaacademy.journal.data.sync.CreateLessonPayload
import ru.nayanovaacademy.journal.data.sync.DeleteHomeworkPayload
import ru.nayanovaacademy.journal.data.sync.DeleteLessonPayload
import ru.nayanovaacademy.journal.data.sync.MarkPayload
import ru.nayanovaacademy.journal.data.sync.NetworkMonitor
import ru.nayanovaacademy.journal.data.sync.SaveAttendancePayload
import ru.nayanovaacademy.journal.data.sync.SaveHomeworkPayload
import ru.nayanovaacademy.journal.data.sync.SaveMarksPayload
import ru.nayanovaacademy.journal.data.sync.SaveRemarksPayload
import ru.nayanovaacademy.journal.data.sync.SyncManager
import ru.nayanovaacademy.journal.data.sync.UpdateLessonPayload
import ru.nayanovaacademy.journal.util.CookieStore
import ru.nayanovaacademy.journal.util.SubjectMerge
import ru.nayanovaacademy.journal.util.SubjectMerge.SubjectGroup
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
                    // Вход не блокируем предзагрузкой: экран журнала открывается
                    // сразу. Сначала проталкиваем офлайн-изменения (важно),
                    // затем фоном наполняем офлайн-кеш (fetchAndCacheAll).
                    scope.launch {
                        try {
                            syncManager.syncPendingChanges()
                            syncManager.fetchAndCacheAll()
                        } catch (e: Exception) {
                            // Фоновая предзагрузка не критична: провалится —
                            // повторит плановая (SyncWorker) или следующий вход
                        }
                    }
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
        return when (val result = fetchLessonDetail(lessonId)) {
            is Result.Success -> {
                val enhanced = enhanceHomeworkForGroup(result.data)
                if (enhanced != result.data) {
                    syncManager.cacheLessonDetail(lessonId, enhanced)
                }
                Result.Success(enhanced)
            }
            is Result.Error -> result
        }
    }

    private suspend fun fetchLessonDetail(lessonId: Int): Result<LessonDetail> {
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

    /**
     * Для объединённого предмета («Информатика + Труд», «Информатика
     * и моделирование + Пилотирование БПЛА») ДЗ предыдущего урока — это ДЗ
     * ближайшего предыдущего занятия из ОБОИХ предметов, а не только
     * того же предмета. Так задание, выданное на одном виде урока, показывается
     * на ближайшем следующем (сдача и оценка «ДЗ»).
     */
    private suspend fun enhanceHomeworkForGroup(detail: LessonDetail): LessonDetail {
        val lesson = detail.lesson
        val group = subjectGroup(lesson.classId) ?: return detail
        if (lesson.subjectId !in group.subjectIds) return detail

        val merged = when (val r = mergedLessons(lesson.classId, lesson.subjectId)) {
            is Result.Success -> r.data
            is Result.Error -> return detail
        }
        // mergedLessons сортирует по убыванию (новые сверху): предыдущий урок — следующий в списке.
        val index = merged.indexOfFirst { it.id == lesson.id }
        if (index < 0 || index >= merged.lastIndex) return detail
        val prev = merged[index + 1]
        // Ближайший предыдущий урок того же предмета — сервер уже вернул его ДЗ.
        if (prev.subjectId == lesson.subjectId) return detail
        val prevDetail = when (val r = fetchLessonDetail(prev.id)) {
            is Result.Success -> r.data
            is Result.Error -> return detail
        }
        val prevHomework = prevDetail.homeworks.firstOrNull()
        return detail.copy(
            previousHomework = prevHomework,
            previousLessonDate = prev.date
        )
    }

    suspend fun createLesson(
        classId: Int,
        subjectId: Int,
        date: String,
        startTime: String = "",
        topic: String = "",
        note: String = ""
    ): Result<Int> {
        val offlineCreate: suspend (Int, Boolean) -> Result<Int> = { localId, enqueue ->
            val payload = CreateLessonPayload(classId, subjectId, date, startTime, topic, note, localId)
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
                if (note.isNotEmpty()) body["note"] = note
                val response = api.lessonCreate(body)
                if (response.isSuccessful) {
                    val id = response.body()?.get("id") ?: 0
                    if (id != 0) {
                        syncManager.cacheLesson(
                            Lesson(
                                id = id, subjectId = subjectId, classId = classId, date = date,
                                startTime = startTime.ifBlank { null }, topic = topic.ifBlank { null },
                                lessonType = null, note = note.ifBlank { null }
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
     * Обновление занятия (сверка с расписанием): время начала и примечание.
     * При [updateNote]=false трогаем только время. Офлайн — локальная правка
     * + очередь.
     */
    suspend fun updateLesson(
        lessonId: Int,
        startTime: String,
        note: String,
        updateNote: Boolean = true
    ): Result<Boolean> {
        val payload = UpdateLessonPayload(lessonId, startTime, note, updateNote)
        val offlineApply: suspend () -> Result<Boolean> = {
            syncManager.applyUpdateLesson(payload)
            syncManager.enqueueUpdateLesson(payload)
            Result.Success(true)
        }
        return try {
            if (isOnline() && lessonId > 0) {
                val body = HashMap<String, Any>()
                body["start_time"] = startTime
                if (updateNote) body["note"] = note
                val response = api.lessonUpdate(lessonId, body)
                if (response.isSuccessful) {
                    syncManager.applyUpdateLesson(payload)
                    Result.Success(true)
                } else {
                    Result.Error("Ошибка обновления занятия", response.code())
                }
            } else {
                offlineApply()
            }
        } catch (e: Exception) {
            offlineApply()
        }
    }

    /**
     * Удаление занятия (сверка с расписанием). Сервер не даёт удалить урок с
     * оценками/ДЗ/посещаемостью/замечаниями — вернётся ошибка с кодом 409.
     */
    suspend fun deleteLesson(lessonId: Int): Result<Boolean> {
        val payload = DeleteLessonPayload(lessonId)
        val offlineApply: suspend () -> Result<Boolean> = {
            syncManager.applyDeleteLesson(lessonId)
            syncManager.enqueueDeleteLesson(payload)
            Result.Success(true)
        }
        return try {
            if (isOnline() && lessonId > 0) {
                val response = api.lessonDelete(lessonId)
                if (response.isSuccessful || response.code() == 404) {
                    syncManager.applyDeleteLesson(lessonId)
                    Result.Success(true)
                } else if (response.code() == 409) {
                    Result.Error("Нельзя удалить: есть оценки/ДЗ/посещаемость", 409)
                } else {
                    Result.Error("Ошибка удаления занятия", response.code())
                }
            } else {
                offlineApply()
            }
        } catch (e: Exception) {
            offlineApply()
        }
    }

    /**
     * Несколько оценок за урок: marks[studentId] = список записей
     * (значение 2..5, за что, комментарий; id — существующая строка;
     * retake = попытка переписывания, дата пересдачи — в attemptDate).
     * Обычные записи заменяют базовые оценки клетки; переписывания
     * удаляются явно через removeMarkIds[studentId] = [id, ...].
     */
    suspend fun saveMarks(
        lessonId: Int,
        marks: Map<Int, List<MarkEntry>>,
        removeMarkIds: Map<Int, List<Int>> = emptyMap()
    ): Result<Boolean> {
        val payload = SaveMarksPayload(
            lessonId = lessonId,
            marks = marks.mapValues { (_, entries) ->
                entries.filter { it.value in 2..5 }
                    .map { MarkPayload(it.value, it.workType, it.comment, it.id, it.retake, it.attemptDate) }
            },
            removeMarkIds = removeMarkIds
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
                    list.map { m ->
                        buildMap<String, Any> {
                            put("value", m.value)
                            put("work_type", m.workType)
                            put("comment", m.comment)
                            if (m.id > 0) put("id", m.id)
                            if (m.retake) {
                                put("retake", 1)
                                if (m.attemptDate.isNotBlank()) put("date", m.attemptDate)
                            }
                        }
                    }
                }
                if (payload.removeMarkIds.isNotEmpty()) {
                    body["remove_mark_ids"] = payload.removeMarkIds
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
                ru.nayanovaacademy.journal.data.sync.AttendanceItemPayload(
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
                    cacheDao.getStudentsForClass(classId)
                        .ifEmpty { cacheDao.getStudents(classId) }
                        .map { it.toModel() }
                        .takeIf { it.isNotEmpty() },
                    Result.Error("Нет сети — ученики недоступны")
                )
            }
        } catch (e: Exception) {
            fromCache(
                cacheDao.getStudentsForClass(classId)
                    .ifEmpty { cacheDao.getStudents(classId) }
                    .map { it.toModel() }
                    .takeIf { it.isNotEmpty() },
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

    // ------------------------------------------------------------------
    // Объединённые предметы: «Информатика + Труд (технология)» (9 классы),
    // «Информатика + Моделирование и пилотирование БПЛА» (8 классы),
    // «Информатика + Инженерия БПЛА» (7 классы). В 8 и 7 параллелях
    // информатика может лежать в классах-подгруппах (8 А1/8 А2, 7 А1/7 А2).
    // ------------------------------------------------------------------

    /**
     * Объединённый предмет параллели для класса [classId]: в него входят
     * предметы основного класса и его подгрупп. Возвращается, только если
     * в параллели есть оба нужных предмета.
     */
    suspend fun subjectGroup(classId: Int): SubjectGroup? {
        val classes = when (val r = classes()) {
            is Result.Success -> r.data
            is Result.Error -> return null
        }
        val cls = classes.firstOrNull { it.id == classId } ?: return null
        val mainName = SubjectMerge.mainClassName(cls.name)
        val siblings = classes.filter { SubjectMerge.mainClassName(it.name) == mainName }
        val subjectsByClassId = siblings.associate { c ->
            c.id to when (val r = classSubjects(c.id)) {
                is Result.Success -> r.data
                is Result.Error -> emptyList()
            }
        }
        return SubjectMerge.group(cls.grade, mainName, siblings, subjectsByClassId)
    }

    /** Заголовок объединённого предмета, если classId+subjectId входят в группу. */
    suspend fun subjectGroupLabel(classId: Int, subjectId: Int): String? {
        val group = subjectGroup(classId) ?: return null
        return if (group.containsSubject(subjectId)) group.label else null
    }

    /**
     * Уроки объединённого предмета: если subjectId входит в группу — уроки
     * всех входящих в неё предметов одним списком (по дате/времени, новые сверху).
     */
    suspend fun mergedLessons(classId: Int, subjectId: Int): Result<List<Lesson>> {
        val group = subjectGroup(classId) ?: return lessons(classId, subjectId)
        if (!group.containsSubject(subjectId)) return lessons(classId, subjectId)

        val results = group.members.map { it to lessons(it.classId, it.subject.id) }
        val lists = results.mapNotNull { (_, r) -> (r as? Result.Success<List<Lesson>>)?.data }
        if (lists.isEmpty()) return results.first().second
        return Result.Success(mergeLessons(lists))
    }

    private fun mergeLessons(lists: List<List<Lesson>>): List<Lesson> =
        lists.flatten()
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Lesson> { it.date }
                    .thenByDescending { it.startTime ?: "" }
                    .thenByDescending { it.id }
            )

    /**
     * Общий журнал объединённого предмета: склеивает уроки, учеников, оценки
     * и посещаемость всех входящих в группу предметов (в т.ч. подгрупп).
     * Средняя оценка считается по всем урокам. Офлайн-кеш остаётся
     * попредметным, поэтому локальные правки видны.
     */
    suspend fun mergedClassJournal(classId: Int, subjectId: Int): Result<ClassJournalData> {
        val group = subjectGroup(classId) ?: return classJournal(classId, subjectId)
        if (!group.containsSubject(subjectId)) return classJournal(classId, subjectId)

        val results = group.members.map { it to classJournal(it.classId, it.subject.id) }
        val journals = results.mapNotNull { (_, r) -> (r as? Result.Success<ClassJournalData>)?.data }
        if (journals.isEmpty()) return results.first().second
        return Result.Success(mergeClassJournals(journals))
    }

    private fun mergeClassJournals(journals: List<ClassJournalData>): ClassJournalData =
        ClassJournalData(
            lessons = journals.flatMap { it.lessons }
                .distinctBy { it.id }
                .sortedWith(
                    compareBy<Lesson> { it.date }
                        .thenBy { it.startTime ?: "" }
                        .thenBy { it.id }
                ),
            students = journals.flatMap { it.students }
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.lastName }, { it.firstName })),
            marks = journals.fold(emptyMap<String, Map<String, List<Mark>>>()) { acc, d -> acc + d.marks },
            attendance = journals.fold(emptyMap<String, Map<String, AttendanceEntry>>()) { acc, d -> acc + d.attendance }
        )

    /** Триггер синхронизации из UI (полустовый запрос на синхронизацию сейчас). */
    suspend fun syncNow(): ru.nayanovaacademy.journal.data.sync.SyncResult = syncManager.syncPendingChanges()

    /** Сеть доступна сейчас. */
    val isOnline = networkMonitor.isOnline

    /** Количество ожидающих синхронизации изменений (офлайн-очередь). */
    val pendingChanges: StateFlow<Int> = pendingChangeDao.observeCount()
        .stateIn(scope, SharingStarted.Eagerly, 0)
}