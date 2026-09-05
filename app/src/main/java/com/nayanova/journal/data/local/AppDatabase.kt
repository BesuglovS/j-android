package com.nayanova.journal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ClassEntity::class,
        SubjectEntity::class,
        LessonEntity::class,
        StudentEntity::class,
        QuarterEntity::class,
        LessonDetailEntity::class,
        ClassJournalEntity::class,
        PendingChangeEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun pendingChangeDao(): PendingChangeDao
}