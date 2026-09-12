package ru.nayanovaacademy.journal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingChangeDao {

    @Insert
    suspend fun insert(change: PendingChangeEntity): Long

    @Query("SELECT * FROM pending_changes ORDER BY id")
    suspend fun getAll(): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE id = :id")
    suspend fun getById(id: Long): PendingChangeEntity?

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_changes SET attempts = :attempts WHERE id = :id")
    suspend fun updateAttempts(id: Long, attempts: Int)

    @Query("SELECT COUNT(*) FROM pending_changes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_changes")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM pending_changes")
    suspend fun clearAll()
}