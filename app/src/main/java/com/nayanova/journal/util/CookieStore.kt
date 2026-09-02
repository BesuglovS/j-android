package com.nayanova.journal.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class CookieStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private val COOKIE_KEY = stringPreferencesKey("auth_session")
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
    }

    private val _cachedCookie = MutableStateFlow("")
    val cookieFlow: Flow<String> = context.dataStore.data.map { it[COOKIE_KEY] ?: "" }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map {
        it[SERVER_URL_KEY] ?: "https://j.nayanovaacademy.ru"
    }

    init {
        scope.launch {
            cookieFlow.collect { _cachedCookie.value = it }
        }
    }

    suspend fun setCookie(value: String) {
        context.dataStore.edit { it[COOKIE_KEY] = value }
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL_KEY] = url }
    }

    fun getCookie(): String = _cachedCookie.value

    suspend fun getServerUrl(): String {
        return context.dataStore.data.map { it[SERVER_URL_KEY] ?: "https://j.nayanovaacademy.ru" }.first()
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun isLoggedIn(): Boolean {
        return _cachedCookie.value.isNotEmpty()
    }
}
