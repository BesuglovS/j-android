package com.nayanova.journal.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nayanova.journal.data.local.PendingChangeDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    private val pendingChangeDao: PendingChangeDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val pendingResult = if (pendingChangeDao.count() == 0) {
                Result.success()
            } else {
                val result = syncManager.syncPendingChanges()
                if (result.remaining == 0) Result.success()
                else Result.retry()
            }
            // Обновляем справочники (классы/предметы/ученики) при каждой синхронизации —
            // best effort: сбой обновления не влияет на результат выше.
            syncManager.refreshReferenceData()
            pendingResult
        } catch (e: Exception) {
            Result.retry()
        }
    }
}