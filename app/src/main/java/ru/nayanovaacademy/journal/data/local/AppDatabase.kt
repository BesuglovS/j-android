package ru.nayanovaacademy.journal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ClassEntity::class,
        SubjectEntity::class,
        LessonEntity::class,
        StudentEntity::class,
        ClassStudentRefEntity::class,
        QuarterEntity::class,
        LessonDetailEntity::class,
        ClassJournalEntity::class,
        PendingChangeEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun pendingChangeDao(): PendingChangeDao

    companion object {
        /** v2: таблица связей «класс — ученик» (class_students). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `class_students` (" +
                        "`classId` INTEGER NOT NULL, `studentId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`classId`, `studentId`))"
                )
            }
        }
    }
}