package com.nayanova.journal.data.sync

import com.google.gson.Gson
import com.nayanova.journal.data.api.JournalApi
import com.nayanova.journal.data.local.CacheDao
import com.nayanova.journal.data.local.ClassEntity
import com.nayanova.journal.data.local.ClassJournalEntity
import com.nayanova.journal.data.local.EntityMappers.toEntity
import com.nayanova.journal.data.local.LessonDetailEntity
import com.nayanova.journal.data.local.LessonEntity
import com.nayanova.journal.data.local.PendingChangeDao
import com.nayanova.journal.data.local.PendingChangeEntity
import com.nayanova.journal.data.local.PendingOperation
import com.nayanova.journal.data.model.AttendanceRecord
import com.nayanova.journal.data.model.AttendanceEntry
import com.nayanova.journal.data.model.ClassJournalData
import com.nayanova.journal.data.model.Homework
import com.nayanova.journal.data.model.Lesson
import com.nayanova.journal.data.model.LessonDetail
import com.nayanova.journal.data.model.Mark
import com.nayanova.journal.data.model.Remark
import com.nayanova.journal.data.model.SchoolClass
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(val synced: Int, val remaining: Int)

@Singleton
class SyncManager @Inject constructor(
    private val api: JournalApi,
    private val cacheDao: CacheDao,
    private val pendingChangeDao: PendingChangeDao,
    private val gson: Gson
) {
    private val negativeId = AtomicInteger(-1)
    fun nextNegativeId(): Int = negativeId.getAndDecrement()

    val pendingChanges: Flow<Int> = pendingChangeDao.observeCount()

    // ------------------------------------------------------------------
    // Полная первичная загрузка при входе: кешируем всё в локальную БД.
    // ------------------------------------------------------------------
    suspend fun fetchAndCacheAll(): Boolean {
        return try {
            val classesResp = api.classes()
            if (!classesResp.isSuccessful) return false
            val classes = classesResp.body()?.get("classes") ?: emptyList<SchoolClass>()
            cacheDao.upsertClasses(classes.map { it.toEntity() })

            for (c in classes) {
                val subsResp = api.classSubjects(c.id)
                if (subsResp.isSuccessful) {
                    cacheDao.upsertSubjects((subsResp.body()?.get("subjects") ?: emptyList()).map { it.toEntity() })
                }
                val studsResp = api.students(c.id)
                if (studsResp.isSuccessful) {
                    cacheDao.upsertStudents((studsResp.body()?.get("students") ?: emptyList()).map { it.toEntity() })
                }
                val lessonsResp = api.lessons(c.id, 0)
                if (lessonsResp.isSuccessful) {
                    cacheDao.upsertLessons(
                        (lessonsResp.body()?.get("lessons") ?: emptyList())
                            .filter { it.id > 0 }
                            .map { it.toEntity() }
                    )
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
        data: com.nayanova.journal.data.model.ClassJournalData
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
    ): com.nayanova.journal.data.model.ClassJournalData? {
        val json = cacheDao.getClassJournalJson(classId, subjectId) ?: return null
        return try {
            gson.fromJson(json, com.nayanova.journal.data.model.ClassJournalData::class.java)
        } catch (e: Exception) {
            cacheDao.deleteClassJournal(classId, subjectId)
            null
        }
    }

    suspend fun cacheStudents(classId: Int, students: List<com.nayanova.journal.data.model.Student>) {
        cacheDao.upsertStudents(students.map { it.toEntity() })
    }

    suspend fun cacheQuarters(quarters: List<com.nayanova.journal.data.model.Quarter>) {
        cacheDao.upsertQuarters(quarters.map { it.toEntity() })
    }

    suspend fun cacheSubjects(classId: Int, subjects: List<com.nayanova.journal.data.model.Subject>) {
        cacheDao.upsertSubjects(subjects.map { it.toEntity() })
    }

    // ------------------------------------------------------------------
    // Применение локальных правок к кешу LessonDetail (актуально офлайн).
    // ------------------------------------------------------------------
    suspend fun applyMarks(lessonId: Int, payload: SaveMarksPayload) {
        // Сервер заменяет оценки только для указанных учеников — мержим по-ученически.
        updateLessonDetail(lessonId) { detail ->
            val fromPayload = payload.marks.map { (sid, list) ->
                sid.toString() to list.map { m ->
                    Mark(nextNegativeId(), sid, lessonId, m.value, m.workType, m.comment, null)
                }
            }.toMap()
            detail.copy(marks = detail.marks + fromPayload)
        }
        updateClassJournalForLesson(lessonId) { data ->
            val fromPayload = payload.marks.map { (sid, list) ->
                sid.toString() to list.map { m ->
                    Mark(nextNegativeId(), sid, lessonId, m.value, m.workType, m.comment, null)
                }
            }.toMap()
            val lessonKey = lessonId.toString()
            val prev = data.marks[lessonKey] ?: emptyMap()
            data.copy(marks = data.marks + (lessonKey to (prev + fromPayload)))
        }
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
            note = null,
            className = null,
            subjectName = null
        )
        val entity = lesson.toEntity()
        cacheDao.upsertLesson(entity)
        val students = cacheDao.getStudents(payload.classId).map {
            com.nayanova.journal.data.local.EntityMappers.run { it.toModel() }
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
            val ok = replay(change, lessonIdRemap)
            if (ok) {
                pendingChangeDao.deleteById(change.id)
                synced++
            } else {
                pendingChangeDao.updateAttempts(change.id, change.attempts + 1)
                break
            }
        }
        return SyncResult(synced, changes.size - synced)
    }

    private suspend fun replay(change: PendingChangeEntity, remap: MutableMap<Int, Int>): Boolean {
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
                    if (!resp.isSuccessful) return false
                    p.lessonId?.takeIf { it != 0 }?.let { refreshLessonDetail(it) }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun replayCreateLesson(p: CreateLessonPayload, remap: MutableMap<Int, Int>): Boolean {
        val body = HashMap<String, Any>().apply {
            put("class_id", p.classId)
            put("subject_id", p.subjectId)
            put("date", p.date)
            if (p.startTime.isNotEmpty()) put("start_time", p.startTime)
            if (p.topic.isNotEmpty()) put("topic", p.topic)
        }
        val resp = api.lessonCreate(body)
        if (!resp.isSuccessful) return false
        val newId = resp.body()?.get("id") ?: 0
        if (newId == 0) return false
        remap[p.localLessonId] = newId
        migrateLocalLesson(p.localLessonId, newId)
        refreshLessonDetail(newId)
        return true
    }

    private suspend fun replaySaveMarks(p: SaveMarksPayload, remap: MutableMap<Int, Int>): Boolean {
        val lessonId = remap[p.lessonId] ?: p.lessonId
        val body = HashMap<String, Any>()
        body["marks"] = p.marks.mapValues { (_, list) ->
            list.map { mapOf("value" to it.value, "work_type" to it.workType, "comment" to it.comment) }
        }
        val resp = api.marksSave(lessonId, body)
        if (!resp.isSuccessful) return false
        refreshLessonDetail(lessonId)
        refreshClassJournalForLesson(lessonId)
        return true
    }

    private suspend fun replaySaveAttendance(p: SaveAttendancePayload, remap: MutableMap<Int, Int>): Boolean {
        val lessonId = remap[p.lessonId] ?: p.lessonId
        val body = HashMap<String, Any>()
        body["attendance"] = p.attendance.mapValues { (_, a) ->
            buildMap<String, Any> {
                put("status", a.status)
                if (a.status == "late" && a.lateMinutes > 0) put("late_minutes", a.lateMinutes)
            }
        }
        val resp = api.attendanceSave(lessonId, body)
        if (!resp.isSuccessful) return false
        refreshLessonDetail(lessonId)
        refreshClassJournalForLesson(lessonId)
        return true
    }

    private suspend fun replaySaveRemarks(p: SaveRemarksPayload, remap: MutableMap<Int, Int>): Boolean {
        val lessonId = remap[p.lessonId] ?: p.lessonId
        val body = HashMap<String, Any>().apply {
            put("remarks", p.remarks)
            if (p.removeIds.isNotEmpty()) put("remove_ids", p.removeIds)
        }
        val resp = api.remarksSave(lessonId, body)
        if (!resp.isSuccessful) return false
        refreshLessonDetail(lessonId)
        refreshClassJournalForLesson(lessonId)
        return true
    }

    private suspend fun replaySaveHomework(p: SaveHomeworkPayload, remap: MutableMap<Int, Int>): Boolean {
        val lessonId = remap[p.lessonId] ?: p.lessonId
        val body = HashMap<String, Any>().apply {
            put("title", p.title)
            put("description", p.description)
            put("due_date", p.dueDate)
        }
        val resp = api.homeworkSave(lessonId, body)
        if (!resp.isSuccessful) return false
        refreshLessonDetail(lessonId)
        return true
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
}