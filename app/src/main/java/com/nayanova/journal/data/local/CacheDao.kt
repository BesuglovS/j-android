package com.nayanova.journal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClasses(classes: List<ClassEntity>)

    @Query("SELECT * FROM classes ORDER BY name")
    suspend fun getClasses(): List<ClassEntity>

    @Query("DELETE FROM classes")
    suspend fun clearClasses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubjects(subjects: List<SubjectEntity>)

    @Query("SELECT * FROM subjects WHERE classId = :classId ORDER BY name")
    suspend fun getSubjects(classId: Int): List<SubjectEntity>

    @Query("DELETE FROM subjects")
    suspend fun clearSubjects()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessons(lessons: List<LessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLesson(lesson: LessonEntity)

    @Query("SELECT * FROM lessons WHERE classId = :classId AND subjectId = :subjectId ORDER BY date DESC")
    suspend fun getLessons(classId: Int, subjectId: Int): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE date = :date ORDER BY startTime")
    suspend fun getLessonsByDate(date: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE classId = :classId ORDER BY date DESC")
    suspend fun getLessonsByClass(classId: Int): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun getLesson(id: Int): LessonEntity?

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteLesson(id: Int)

    @Query("SELECT DISTINCT classId FROM lessons")
    suspend fun getLessonClassIds(): List<Int>

    @Query("DELETE FROM lessons")
    suspend fun clearLessons()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudents(students: List<StudentEntity>)

    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY lastName, firstName")
    suspend fun getStudents(classId: Int): List<StudentEntity>

    @Query(
        "SELECT s.* FROM students s JOIN class_students cs ON cs.studentId = s.id " +
            "WHERE cs.classId = :classId AND s.isActive = 1 ORDER BY s.lastName, s.firstName"
    )
    suspend fun getStudentsForClass(classId: Int): List<StudentEntity>

    @Query("DELETE FROM class_students WHERE classId = :classId")
    suspend fun clearClassStudents(classId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassStudents(refs: List<ClassStudentRefEntity>)

    @Query("DELETE FROM students")
    suspend fun clearStudents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuarters(quarters: List<QuarterEntity>)

    @Query("SELECT * FROM quarters ORDER BY id")
    suspend fun getQuarters(): List<QuarterEntity>

    @Query("DELETE FROM quarters")
    suspend fun clearQuarters()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessonDetail(detail: LessonDetailEntity)

    @Query("SELECT json FROM lesson_details WHERE lessonId = :lessonId")
    suspend fun getLessonDetailJson(lessonId: Int): String?

    @Query("DELETE FROM lesson_details WHERE lessonId = :lessonId")
    suspend fun deleteLessonDetail(lessonId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClassJournal(journal: ClassJournalEntity)

    @Query("SELECT json FROM class_journals WHERE classId = :classId AND subjectId = :subjectId")
    suspend fun getClassJournalJson(classId: Int, subjectId: Int): String?

    @Query("DELETE FROM class_journals WHERE classId = :classId AND subjectId = :subjectId")
    suspend fun deleteClassJournal(classId: Int, subjectId: Int)
}