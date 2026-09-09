package com.nayanova.journal

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.nayanova.journal.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class JournalApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Автоинициализация через androidx.startup отключена (см. AndroidManifest),
        // поэтому настраиваем WorkManager явно, чтобы использовался HiltWorkerFactory.
        // Если кто-то уже успел создать WorkManager, повторная инициализация бросит
        // исключение — проглатываем, т.к. конфликтующая конфигурация вряд ли возможна.
        runCatching { WorkManager.initialize(this, workManagerConfiguration) }
        // Обеспечиваем создание планировщика синхронизации при старте приложения.
        syncScheduler
    }
}