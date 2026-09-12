package ru.nayanovaacademy.journal.data.sync

import com.google.gson.Gson
import ru.nayanovaacademy.journal.data.api.JournalApi
import ru.nayanovaacademy.journal.data.local.CacheDao
import ru.nayanovaacademy.journal.data.local.ClassEntity
import ru.nayanovaacademy.journal.data.local.ClassJournalEntity
import ru.nayanovaacademy.journal.data.local.ClassStudentRefEntity
import ru.nayanovaacademy.journal.data.local.EntityMappers.toEntity
import ru.nayanovaacademy.journal.data.local.LessonDetailEntity
import ru.nayanovaacademy.journal.data.local.LessonEntity
import ru.nayanovaacademy.journal.data.local.PendingChangeDao
import ru.nayanovaacademy.journal.data.local.PendingChangeEntity
import ru.nayanovaacademy.journal.data.local.PendingOperation
import ru.nayanovaacademy.journal.data.model.AttendanceRecord
import ru.nayanovaacademy.journal.data.model.AttendanceEntry
import ru.nayanovaacademy.journal.data.model.ClassJournalData
import ru.nayanovaacademy.journal.data.model.Homework
import ru.nayanovaacademy.journal.data.model.Lesson
import ru.nayanovaacademy.journal.data.model.LessonDetail
import ru.nayanovaacademy.journal.data.model.Mark
import ru.nayanovaacademy.journal.data.model.Remark
import ru.nayanovaacademy.journal.data.model.SchoolClass
import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(val synced: Int, val remaining: Int)

/** Итог воспроизведения одного изменения очереди. */
private enum class ReplayResult {
    SUCCESS,
    FAILURE,
    /** HTTP 401: сессия протухла — данные нельзя отбрасывать, ждём перелогина. */
    AUTH_FAILURE
}

@Singleton
class SyncManager @Inject constructor(
    private val api: JournalApi,
    private val cacheDao: CacheDao,
    private val pendingChangeDao: PendingChangeDao,
    private val gson: Gson
) {
    private val negativeId = AtomicInteger(-1)
    fun nextNegativeId(): Int = negativeId.getAndDecrement()

    /** Максимальное число попыток до того, как изменение будет отброшено (dead-letter). */
    private val maxAttempts = 5

    /** Параллелизм фоновой предзагрузки (запросов в полёте). На сервере 1 CPU
     *  и небольшой php-fpm пул — слишком много параллельных запросов душит
     *  его и задерживает запросы приложения. */
    private val preloadConcurrency = 3

    val pendingChanges: Flow<Int> = pendingChangeDao.observeCount()

    /** Параллельный проход по классам с ограничением одновременных запросов. */
    private suspend fun <T> forEachClassParallel(
        classes: List<T>,
        body: suspend (T) -> Unit
    ) {
        val semaphore = kotlinx.coroutines.sync.Semaphore(preloadConcurrency)
        coroutineScope {
            for (c in classes) {
                launch {
                    semaphore.withPermit { body(c) }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Полная первичная загрузка при входе: кешируем всё в локальную БД.
    // ------------------------------------------------------------------
    suspend fun fetchAndCacheAll(): Boolean {
        return try {
            if (!refreshReferenceData()) return false
            forEachClassParallel(cacheDao.getClasses()) { c ->
                val lessonsResp = api.lessons(c.id, 0)
                if (lessonsResp.isSuccessful) {
                    cacheDao.upsertLessons(
                        (lessonsResp.body()?.get("lessons") ?: emptyList())
                            .filter { it.id > 0 }
                            .map { it.toEntity() }
                    )
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Обновление справочников (классы, предметы, ученики, четверти).
    // Вызывается при входе и фоновой синхронизацией, чтобы офлайн-кеш
    // не отставал от сервера (сайт при этом всегда актуален).
    // ------------------------------------------------------------------
    suspend fun refreshReferenceData(): Boolean {
        return try {
            val classesResp = api.classes()
            if (!classesResp.isSuccessful) return false
            val classes = classesResp.body()?.get("classes") ?: emptyList<SchoolClass>()
            cacheDao.upsertClasses(classes.map { it.toEntity() })

            forEachClassParallel(classes) { c ->
                val subsResp = api.classSubjects(c.id)
                if (subsResp.isSuccessful) {
                    cacheDao.upsertSubjects((subsResp.body()?.get("subjects") ?: emptyList()).map { it.toEntity() })
                }
                val studsResp = api.students(c.id)
                if (studsResp.isSuccessful) {
                    cacheStudents(c.id, studsResp.body()?.get("students") ?: emptyList())
                }
            }

            val quartersResp = api.quarters()
            if (quartersResp.isSuccessful) {
                cacheDao.upsertQuarters((quartersResp.body()?.get("quarters") ?: emptyList()).map { it.toEntity() })
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Кеширование данных с сервера (используется repository при успешных read).
    // ------------------------------------------------------------------
    suspend fun cacheClasses(classes: List<SchoolClass>) =
        cacheDao.upsertClasses(classes.map { it.toEntity() })

    suspend fun cacheLessons(lessons: List<Lesson>) =
        cacheDao.upsertLessons(lessons.filter { it.id > 0 }.map { it.toEntity() })

    suspend fun cacheLesson(lesson: Lesson) =
        cacheDao.upsertLesson(lesson.toEntity())

    suspend fun cacheLessonDetail(lessonId: Int, detail: LessonDetail) {
        cacheDao.upsertLessonDetail(
            LessonDetailEntity(lessonId, gson.toJson(detail), System.currentTimeMillis())
        )
    }

    suspend fun getCachedLessonDetail(lessonId: Int): LessonDetail? {
        val json = cacheDao.getLessonDetailJson(lessonId) ?: return null
        return try {
            gson.fromJson(json, LessonDetail::class.java)
        } catch (e: Exception) {
            cacheDao.deleteLessonDetail(lessonId)
            null
        }
    }

    suspend fun cacheClassJournal(classId: Int, subjectId: Int, json: String) {
        cacheDao.upsertClassJournal(
            ClassJournalEntity(classId, subjectId, json, System.currentTimeMillis())
        )
    }

    suspend fun cacheClassJournal(
        classId: Int,
        subjectId: Int,
        data: ru.nayanovaacademy.journal.data.model.ClassJournalData
    ) {
        cacheDao.upsertClassJournal(
            ClassJournalEntity(classId, subjectId, gson.toJson(data), System.currentTimeMillis())
        )
    }

    suspend fun getCachedClassJournal(classId: Int, subjectId: Int): String? =
        cacheDao.getClassJournalJson(classId, subjectId)

    suspend fun getCachedClassJournalData(
        classId: Int,
        subjectId: Int
    ): ru.nayanovaacademy.journal.data.model.ClassJournalData? {
        val json = cacheDao.getClassJournalJson(classId, subjectId) ?: return null
        return try {
            gson.fromJson(json, ru.nayanovaacademy.journal.data.model.ClassJournalData::class.java)
        } catch (e: Exception) {
            cacheDao.deleteClassJournal(classId, subjectId)
            null
        }
    }

    suspend fun cacheStudents(classId: Int, students: List<ru.nayanovaacademy.journal.data.model.Student>) {
        cacheDao.upsertStudents(students.map { it.toEntity() })
        if (classId > 0) {
            // Связь «класс — ученики» — как на сервере (student_classes), а не по
            // основному классу ученика: ученик может состоять в нескольких классах.
            cacheDao.clearClassStudents(classId)
            cacheDao.insertClassStudents(students.map { ClassStudentRefEntity(classId, it.id) })
        }
    }

    suspend fun cacheQuarters(quarters: List<ru.nayanovaacademy.journal.data.model.Quarter>) {
        cacheDao.upsertQuarters(quarters.map { it.toEntity() })
    }

    suspend fun cacheSubjects(classId: Int, subjects: List<ru.nayanovaacademy.journal.data.model.Subject>) {
        cacheDao.upsertSubjects(subjects.map { it.toEntity() })
    }

    // ------------------------------------------------------------------
    // Применение локальных правок к кешу LessonDetail (актуально офлайн).
    // ------------------------------------------------------------------
    suspend fun applyMarks(lessonId: Int, payload: SaveMarksPayload) {
        // Сервер: обычные записи заменяют базовые (is_retake=0) оценки клетки
        // (ученик, урок, work_type); записи с retake=1 — попытки переписывания,
        // добавляемые/обновляемые по id (привязка к уроку исходной оценки —
        // на сервере); remove_mark_ids — явное удаление попыток.
        updateLessonDetail(lessonId) { detail ->
            detail.copy(marks = updateLessonStudentMarks(detail.marks, lessonId, payload, detail.lesson.date))
        }
        updateClassJournalForLesson(lessonId) { data ->
            val lessonKey = lessonId.toString()
            val lessonDate = data.lessons.firstOrNull { it.id == lessonId }?.date
            val perStudent = updateLessonStudentMarks(data.marks[lessonKey] ?: emptyMap(), lessonId, payload, lessonDate)
            val withLesson = data.copy(marks = data.marks + (lessonKey to perStudent))
            // Локальный пересчёт is_current по всем урокам предмета журнала
            recomputeClassJournalCurrent(withLesson)
        }
    }

    /**
     * Применение payload к оценкам одного урока (map «studentId → список»):
     * обычные записи убирают старые базовые строки клетки work_type и добавляют
     * заново; retake-записи обновляются по id или добавляются; пустой список
     * ученика убирает все его базовые оценки урока; удалённые id — фильтруются.
     */
    private fun updateLessonStudentMarks(
        lessonMarks: Map<String, List<Mark>>,
        lessonId: Int,
        payload: SaveMarksPayload,
        lessonDate: String?
    ): Map<String, List<Mark>> {
        val result = lessonMarks.toMutableMap()
        val removedByStudent = payload.removeMarkIds
        val allRemoved = removedByStudent.values.flatten().toSet()

        // Удаление попыток по id
        if (allRemoved.isNotEmpty()) {
            for (key in result.keys.toList()) {
                val kept = result[key]!!.filter { it.id !in allRemoved }
                if (kept.isEmpty()) result.remove(key) else result[key] = kept
            }
        }

        val fallbackLessonDate = lessonDate ?: ""
        for ((sid, entries) in payload.marks) {
            val key = sid.toString()
            var list = (result[key] ?: emptyList()).toMutableList()
            if (entries.isEmpty()) {
                // Пустой список — убрать базовые оценки этого урока (ретейки остаются)
                list = list.filter { it.isRetake == 1 }.toMutableList()
                if (list.isEmpty()) result.remove(key) else result[key] = list
                continue
            }
            // Базовые строки урока с типами, не указанными в payload, удаляются
            val baseTypes = entries.filter { !it.retake }.map { it.workType }.toSet()
            list.removeAll { it.isRetake == 0 && it.workType !in baseTypes }
            for (entry in entries) {
                if (entry.retake) {
                    val mark = Mark(
                        if (entry.id > 0) entry.id else nextNegativeId(),
                        sid, lessonId, entry.value, entry.workType, entry.comment, null, 1,
                        /* isCurrent */ 1, entry.attemptDate.ifBlank { fallbackLessonDate }
                    )
                    if (entry.id > 0) {
                        val idx = list.indexOfFirst { it.id == entry.id }
                        if (idx >= 0) list[idx] = mark else list.add(mark)
                    } else {
                        list.add(mark)
                    }
                } else {
                    // Базовая оценка обновляется НА МЕСТЕ (id и attempt_date
                    // сохраняются), чтобы правки клетки не меняли порядок попыток.
                    val matchIdx = list.indexOfFirst {
                        it.isRetake == 0 && (entry.id > 0 && it.id == entry.id || entry.id == 0 && it.workType == entry.workType)
                    }
                    if (matchIdx >= 0) {
                        list[matchIdx] = list[matchIdx].copy(value = entry.value, comment = entry.comment)
                    } else {
                        list.add(Mark(nextNegativeId(), sid, lessonId, entry.value, entry.workType, entry.comment, null, 0, 1, fallbackLessonDate))
                    }
                }
            }
            result[key] = list
        }
        return result
    }

    /** Локальный пересчёт is_current журнала: итоговая попытка группы
     *  (ученик, work_type) — с максимальной attempt_date; при равных датах
     *  приоритет у переписывания, затем по id. */
    private fun recomputeClassJournalCurrent(data: ru.nayanovaacademy.journal.data.model.ClassJournalData): ru.nayanovaacademy.journal.data.model.ClassJournalData {
        var marks = data.marks
        for ((lessonKey, perStudent) in marks) {
            if (perStudent.isEmpty()) continue
            val rebuilt = mutableMapOf<String, List<Mark>>()
            for ((sid, list) in perStudent) {
                val byType = LinkedHashMap<String, MutableList<Mark>>()
                list.forEach { byType.getOrPut(it.workType) { mutableListOf() }.add(it) }
                rebuilt[sid] = byType.values.flatMap { attempts ->
                    val latest = attempts.maxWithOrNull(
                        compareBy<Mark>(
                            { it.attemptDate ?: "" },
                            { it.isRetake },
                            { it.id }
                        )
                    )
                    attempts.map { m ->
                        val flag = if (m.id == latest?.id) 1 else 0
                        if (m.isCurrent == flag) m else m.copy(isCurrent = flag)
                    }
                }
            }
            marks = marks + (lessonKey to rebuilt)
        }
        return data.copy(marks = marks)
    }

    suspend fun applyAttendance(lessonId: Int, payload: SaveAttendancePayload) {
        updateLessonDetail(lessonId) { detail ->
            val fromPayload = payload.attendance.map { (sid, a) ->
                sid.toString() to AttendanceRecord(
                    nextNegativeId(), sid, lessonId, a.status, null,
                    a.lateMinutes.coerceAtLeast(0).takeIf { a.status == "late" }, null
                )
            }.toMap()
            detail.copy(attendance = detail.attendance + fromPayload)
        }
        updateClassJournalForLesson(lessonId) { data ->
            val fromPayload = payload.attendance.map { (sid, a) ->
                sid.toString() to AttendanceEntry(a.status, a.lateMinutes)
            }.toMap()
            val lessonKey = lessonId.toString()
            val prev = data.attendance[lessonKey] ?: emptyMap()
            data.copy(attendance = data.attendance + (lessonKey to (prev + fromPayload)))
        }
    }

    suspend fun applyRemarks(lessonId: Int, payload: SaveRemarksPayload) {
        updateLessonDetail(lessonId) { detail ->
            val removeIds = payload.removeIds.toSet()
            val kept = detail.remarks.mapValues { (_, list) ->
                list.filter { it.id !in removeIds }
            }.filterValues { it.isNotEmpty() }
            val fromPayload = payload.remarks.map { (sid, texts) ->
                sid.toString() to texts.map { Remark(nextNegativeId(), lessonId, sid, it, null) }
            }.toMap()
            detail.copy(remarks = kept + fromPayload)
        }
    }

    suspend fun applyHomework(lessonId: Int, payload: SaveHomeworkPayload) {
        updateLessonDetail(lessonId) { detail ->
            val hw = Homework(
                id = nextNegativeId(),
                lessonId = lessonId,
                title = payload.title,
                description = payload.description,
                dueDate = payload.dueDate
            )
            detail.copy(homework = hw)
        }
    }

    suspend fun applyDeleteHomework(lessonId: Int, homeworkId: Int) {
        updateLessonDetail(lessonId) { detail ->
            detail.copy(
                homework = detail.homework?.takeIf { it.id != homeworkId },
                homeworks = detail.homeworks.filter { it.id != homeworkId }
            )
        }
    }

    /** Создание урока офлайн: локальный негативный id + пустой LessonDetail для офлайн-редактирования. */
    suspend fun applyCreateLesson(payload: CreateLessonPayload): LessonEntity {
        val lesson = Lesson(
            id = payload.localLessonId,
            subjectId = payload.subjectId,
            classId = payload.classId,
            date = payload.date,
            startTime = payload.startTime.ifBlank { null },
            topic = payload.topic.ifBlank { null },
            lessonType = null,
            note = payload.note.ifBlank { null },
            className = null,
            subjectName = null
        )
        val entity = lesson.toEntity()
        cacheDao.upsertLesson(entity)
        val students = cacheDao.getStudentsForClass(payload.classId)
            .ifEmpty { cacheDao.getStudents(payload.classId) }
            .map {
                ru.nayanovaacademy.journal.data.local.EntityMappers.run { it.toModel() }
            }
        val detail = LessonDetail(
            lesson = lesson,
            students = students,
            marks = emptyMap(),
            remarks = emptyMap(),
            attendance = emptyMap(),
            homeworks = emptyList()
        )
        cacheDao.upsertLessonDetail(
            LessonDetailEntity(payload.localLessonId, gson.toJson(detail), System.currentTimeMillis())
        )
        return entity
    }

    private suspend fun updateLessonDetail(lessonId: Int, transform: (LessonDetail) -> LessonDetail) {
        val existing = getCachedLessonDetail(lessonId) ?: return
        val updated = transform(existing)
        cacheDao.upsertLessonDetail(
            LessonDetailEntity(lessonId, gson.toJson(updated), System.currentTimeMillis())
        )
    }

    /** Обновляет снапшот class-journal для урока, если он закеширован. */
    private suspend fun updateClassJournalForLesson(
        lessonId: Int,
        transform: (ClassJournalData) -> ClassJournalData
    ) {
        val lesson = try { cacheDao.getLesson(lessonId) } catch (e: Exception) { null } ?: return
        val data = getCachedClassJournalData(lesson.classId, lesson.subjectId) ?: return
        val updated = transform(data)
        cacheClassJournal(lesson.classId, lesson.subjectId, updated)
    }

    // ------------------------------------------------------------------
    // Постановка операций в очередь на синхронизацию.
    // ------------------------------------------------------------------
    private suspend fun enqueue(operation: String, payload: Any, lessonId: Int) {
        pendingChangeDao.insert(
            PendingChangeEntity(
                id = 0,
                operation = operation,
                payload = gson.toJson(payload),
                lessonId = lessonId,
                createdAt = System.currentTimeMillis(),
                attempts = 0
            )
        )
    }

    suspend fun enqueueCreateLesson(payload: CreateLessonPayload) =
        enqueue(PendingOperation.CREATE_LESSON, payload, payload.localLessonId)

    suspend fun enqueueSaveMarks(payload: SaveMarksPayload) =
        enqueue(PendingOperation.SAVE_MARKS, payload, payload.lessonId)

    suspend fun enqueueSaveAttendance(payload: SaveAttendancePayload) =
        enqueue(PendingOperation.SAVE_ATTENDANCE, payload, payload.lessonId)

    suspend fun enqueueSaveRemarks(payload: SaveRemarksPayload) =
        enqueue(PendingOperation.SAVE_REMARKS, payload, payload.lessonId)

    suspend fun enqueueSaveHomework(payload: SaveHomeworkPayload) =
        enqueue(PendingOperation.SAVE_HOMEWORK, payload, payload.lessonId)

    suspend fun enqueueDeleteHomework(payload: DeleteHomeworkPayload) =
        enqueue(PendingOperation.DELETE_HOMEWORK, payload, 0)

    // ------------------------------------------------------------------
    // Синхронизация: воспроизведение очереди изменений на сервере.
    // ------------------------------------------------------------------
    suspend fun syncPendingChanges(): SyncResult {
        val changes = pendingChangeDao.getAll()
        if (changes.isEmpty()) return SyncResult(0, 0)

        var synced = 0
        val lessonIdRemap = mutableMapOf<Int, Int>()
        for (change in changes) {
            when (replay(change, lessonIdRemap)) {
                ReplayResult.SUCCESS -> {
                    pendingChangeDao.deleteById(change.id)
                    synced++
                }
                ReplayResult.AUTH_FAILURE -> {
                    // Сессия протухла: данные НЕ отбрасываем. Дождёмся перелогина
                    // (после успешного входа repository сам запустит синхронизацию),
                    // а пока просто останавливаемся, чтобы не долбить сервер.
                    pendingChangeDao.updateAttempts(change.id, change.attempts + 1)
                    break
                }
                ReplayResult.FAILURE -> {
                    val attempts = change.attempts + 1
                    if (attempts >= maxAttempts) {
                        // Безнадёжное изменение: без лимита мы бы ретраили его вечно,
                        // блокируя всю очередь. Отбрасываем (остаётся в logcat для разбора).
                        Log.w(
                            TAG,
                            "Dropping permanently failing change id=${change.id} " +
                                "op=${change.operation} after $attempts attempts: " +
                                change.payload.take(2000)
                        )
                        pendingChangeDao.deleteById(change.id)
                    } else {
                        pendingChangeDao.updateAttempts(change.id, attempts)
                        break
                    }
                }
            }
        }
        return SyncResult(synced, changes.size - synced)
    }

    private suspend fun replay(change: PendingChangeEntity, remap: MutableMap<Int, Int>): ReplayResult {
        return try {
            when (change.operation) {
                PendingOperation.CREATE_LESSON -> {
                    val p = gson.fromJson(change.payload, CreateLessonPayload::class.java)
                    replayCreateLesson(p, remap)
                }
                PendingOperation.SAVE_MARKS -> {
                    val p = gson.fromJson(change.payload, SaveMarksPayload::class.java)
                    replaySaveMarks(p, remap)
                }
                PendingOperation.SAVE_ATTENDANCE -> {
                    val p = gson.fromJson(change.payload, SaveAttendancePayload::class.java)
                    replaySaveAttendance(p, remap)
                }
                PendingOperation.SAVE_REMARKS -> {
                    val p = gson.fromJson(change.payload, SaveRemarksPayload::class.java)
                    replaySaveRemarks(p, remap)
                }
                PendingOperation.SAVE_HOMEWORK -> {
                    val p = gson.fromJson(change.payload, SaveHomeworkPayload::class.java)
                    replaySaveHomework(p, remap)
                }
                PendingOperation.DELETE_HOMEWORK -> {
                    val p = gson.fromJson(change.payload, DeleteHomeworkPayload::class.java)
                    val resp = api.homeworkDelete(p.homeworkId)
                    if (!resp.isSuccessful) return failure(resp)
                    p.lessonId?.takeIf { it != 0 }?.let { refreshLessonDetail(it) }
                    ReplayResult.SUCCESS
                }
                else -> ReplayResult.FAILURE
            }
        } catch (e: Exception) {
            ReplayResult.FAILURE
        }
    }

    /** 401 означает протухшую сессию — передаём отдельным результатом. */
    private fun failure(resp: retrofit2.Response<*>): ReplayResult =
        if (resp.code() == 401) ReplayResult.AUTH_FAILURE else ReplayResult.FAILURE

    private suspend fun replayCreateLesson(p: CreateLessonPayload, remap: MutableMap<Int, Int>): ReplayResult {
        val body = HashMap<String, Any>().apply {
            put("class_id", p.classId)
            put("subject_id", p.subjectId)
            put("date", p.date)
            if (p.startTime.isNotEmpty()) put("start_time", p.startTime)
            if (p.topic.isNotEmpty()) put("topic", p.topic)
            if (p.note.isNotEmpty()) put("note", p.note)
        }
        val resp = api.lessonCreate(body)
        if (!resp.isSuccessful) return failure(resp)
        val newId = resp.body()?.get("id") ?: 0
        if (newId == 0) return ReplayResult.FAILURE
        remap[p.localLessonId] = newId
        migrateLocalLesson(p.localLessonId, newId)
        refreshLessonDetail(newId)
        return ReplayResult.SUCCESS
    }

    private suspend fun replaySaveMarks(p: SaveMarksPayload, remap: MutableMap<Int, Int>): ReplayResult {
        val lessonId = remap[p.lessonId] ?: p.lessonId
        val body = HashMap<String, Any>()
        body["marks"] = p.marks.mapValues { (_, list) ->
            list.map { m ->
                buildMap<String, Any> {
                    put("value", m.value)
                    put("work_type", m.workType)
                    put("comment", m.comment)
                    if (m.id > 0) put("id", m.id)
                    if (m.retake) put("retake", 1)
                }
            }
        }
        if (p.removeMarkIds.isNotEmpty()) {
            body["remove_mark_ids"] = p.removeMarkIds
        }
        val resp = api.marksSave(lessonId, body)
        if (!resp.isSuccessful) return failure(resp)
        refreshLessonDetail(lessonId)
        refreshClassJournalForLesson(lessonId)
        return ReplayResult.SUCCESS
    }

    private suspend fun replaySaveAttendance(p: SaveAttendancePayload, remap: MutableMap<Int, Int>): ReplayResult {
        val lessonId = remap[p.lessonId] ?: p.lessonId
        val body = HashMap<String, Any>()
        body["attendance"] = p.attendance.mapValues { (_, a) ->
            buildMap<String, Any> {
                put("status", a.status)
                if (a.status == "late" && a.lateMinutes > 0) put("late_minutes", a.lateMinutes)
            }
        }
        val resp = api.attendanceSave(lessonId, body)
        if (!resp.isSuccessful) return failure(resp)
        refreshLessonDetail(lessonId)
        refreshClassJournalForLesson(lessonId)
        return ReplayResult.SUCCESS
    }

    private suspend fun replaySaveRemarks(p: SaveRemarksPayload, remap: MutableMap<Int, Int>): ReplayResult {
        val lessonId = remap[p.lessonId] ?: p.lessonId
        val body = HashMap<String, Any>().apply {
            put("remarks", p.remarks)
            if (p.removeIds.isNotEmpty()) put("remove_ids", p.removeIds)
        }
        val resp = api.remarksSave(lessonId, body)
        if (!resp.isSuccessful) return failure(resp)
        refreshLessonDetail(lessonId)
        refreshClassJournalForLesson(lessonId)
        return ReplayResult.SUCCESS
    }

    private suspend fun replaySaveHomework(p: SaveHomeworkPayload, remap: MutableMap<Int, Int>): ReplayResult {
        val lessonId = remap[p.lessonId] ?: p.lessonId
        val body = HashMap<String, Any>().apply {
            put("title", p.title)
            put("description", p.description)
            put("due_date", p.dueDate)
        }
        val resp = api.homeworkSave(lessonId, body)
        if (!resp.isSuccessful) return failure(resp)
        refreshLessonDetail(lessonId)
        return ReplayResult.SUCCESS
    }

    private suspend fun replayDeleteHomework(p: DeleteHomeworkPayload): Boolean {
        val resp = api.homeworkDelete(p.homeworkId)
        return resp.isSuccessful
    }

    /** Перенос локального (негативного) урока на id с сервера. */
    private suspend fun migrateLocalLesson(localId: Int, newId: Int) {
        val entity = cacheDao.getLesson(localId) ?: return
        cacheDao.upsertLesson(entity.copy(id = newId))
        cacheDao.deleteLesson(localId)

        val detailJson = cacheDao.getLessonDetailJson(localId)
        if (detailJson != null) {
            cacheDao.deleteLessonDetail(localId)
            cacheDao.upsertLessonDetail(
                LessonDetailEntity(newId, detailJson, System.currentTimeMillis())
            )
        }
    }

    public suspend fun refreshLessonDetail(lessonId: Int) {
        try {
            val resp = api.lessonDetail(lessonId)
            if (resp.isSuccessful) {
                resp.body()?.let { cacheLessonDetail(lessonId, it) }
            }
        } catch (e: Exception) {
            // Не критично: синхронизированные данные уже отправлены.
        }
    }

    private suspend fun refreshClassJournalForLesson(lessonId: Int) {
        try {
            val lesson = cacheDao.getLesson(lessonId) ?: return
            val cached = cacheDao.getClassJournalJson(lesson.classId, lesson.subjectId) ?: return
            val resp = api.classJournal(lesson.classId, lesson.subjectId)
            if (resp.isSuccessful) {
                resp.body()?.let { cacheClassJournal(lesson.classId, lesson.subjectId, gson.toJson(it)) }
            }
        } catch (e: Exception) {
            // Не критично.
        }
    }

    private companion object {
        const val TAG = "SyncManager"
    }
}