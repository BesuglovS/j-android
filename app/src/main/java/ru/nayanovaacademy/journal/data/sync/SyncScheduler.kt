package ru.nayanovaacademy.journal.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        schedulePeriodicSync()

        networkMonitor.isOnline
            .onEach { online ->
                if (online) {
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        UNIQUE_SYNC_NOW,
                        ExistingWorkPolicy.REPLACE,
                        syncNowRequest()
                    )
                }
            }
            .launchIn(scope)
    }

    /** Периодическая синхронизация (раз в 3 часа) при наличии сети. */
    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_SYNC_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun syncNowRequest() = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
        .build()

    companion object {
        private const val UNIQUE_SYNC_PERIODIC = "sync_periodic"
        private const val UNIQUE_SYNC_NOW = "sync_now"
    }
}