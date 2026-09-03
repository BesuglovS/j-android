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
        private val LOGIN_KEY = stringPreferencesKey("saved_login")
        private val PASSWORD_KEY = stringPreferencesKey("saved_password")
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

    suspend fun saveCredentials(login: String, password: String) {
        context.dataStore.edit {
            it[LOGIN_KEY] = login
            it[PASSWORD_KEY] = password
        }
    }

    suspend fun getSavedLogin(): String {
        return context.dataStore.data.map { it[LOGIN_KEY] ?: "" }.first()
    }

    suspend fun getSavedPassword(): String {
        return context.dataStore.data.map { it[PASSWORD_KEY] ?: "" }.first()
    }

    suspend fun hasSavedCredentials(): Boolean {
        val login = getSavedLogin()
        val password = getSavedPassword()
        return login.isNotEmpty() && password.isNotEmpty()
    }

    suspend fun clearCredentials() {
        context.dataStore.edit {
            it.remove(LOGIN_KEY)
            it.remove(PASSWORD_KEY)
        }
    }

    /**
     * Очищает только сессию (куку). Сохранённые логин/пароль остаются,
     * чтобы автоматический вход сработал при следующем запуске.
     */
    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(COOKIE_KEY)
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return _cachedCookie.value.isNotEmpty()
    }
}
