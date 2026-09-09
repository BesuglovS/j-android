package com.nayanova.journal.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.nayanova.journal.data.api.AuthInterceptor
import com.nayanova.journal.data.api.JournalApi
import com.nayanova.journal.data.local.AppDatabase
import com.nayanova.journal.data.local.CacheDao
import com.nayanova.journal.data.local.PendingChangeDao
import com.nayanova.journal.data.api.ScheduleApi
import com.nayanova.journal.util.CookieStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCookieStore(@ApplicationContext context: Context): CookieStore {
        return CookieStore(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://j.nayanovaacademy.ru/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideJournalApi(retrofit: Retrofit): JournalApi {
        return retrofit.create(JournalApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "journal.db")
            .fallbackToDestructiveMigration()
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideCacheDao(db: AppDatabase): CacheDao = db.cacheDao()

    @Provides
    @Singleton
    fun providePendingChangeDao(db: AppDatabase): PendingChangeDao = db.pendingChangeDao()

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * API сайта расписания (r-web): отдельный клиент без cookie-интерцептора
     * журнала, чтобы не отправлять сессию журнала на чужой домен.
     */
    @Provides
    @Singleton
    fun provideScheduleApi(): ScheduleApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://r.nayanovaacademy.ru/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(ScheduleApi::class.java)
    }
}
