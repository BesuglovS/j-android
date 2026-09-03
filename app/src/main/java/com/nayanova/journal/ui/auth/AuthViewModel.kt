package com.nayanova.journal.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayanova.journal.data.repository.JournalRepository
import com.nayanova.journal.util.CookieStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val cookieStore: CookieStore
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _hasSavedCredentials = MutableStateFlow(false)
    val hasSavedCredentials = _hasSavedCredentials.asStateFlow()

    private val _credentialsLoaded = MutableStateFlow(false)
    val credentialsLoaded = _credentialsLoaded.asStateFlow()

    private val _savedLogin = MutableStateFlow("")
    val savedLogin = _savedLogin.asStateFlow()

    private val _savedPassword = MutableStateFlow("")
    val savedPassword = _savedPassword.asStateFlow()

    init {
        loadSavedCredentials()
    }

    private fun loadSavedCredentials() {
        viewModelScope.launch {
            _savedLogin.value = cookieStore.getSavedLogin()
            _savedPassword.value = cookieStore.getSavedPassword()
            _hasSavedCredentials.value = cookieStore.hasSavedCredentials()
            _credentialsLoaded.value = true
        }
    }

    fun checkAuth() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.checkAuth()) {
                is JournalRepository.Result.Success -> {
                    _isLoggedIn.value = result.data
                    if (!result.data) {
                        _error.value = "Необходима авторизация"
                    }
                }
                is JournalRepository.Result.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Вызывается из потока WebView (JavascriptInterface) при ручном входе.
     * Сохраняет учётные данные, если включена опция сохранения.
     */
    fun onManualCredentials(login: String, password: String, remember: Boolean) {
        if (!remember) return
        viewModelScope.launch {
            cookieStore.saveCredentials(login, password)
            _hasSavedCredentials.value = true
            _savedLogin.value = login
            _savedPassword.value = password
        }
    }

    fun clearSavedCredentials() {
        viewModelScope.launch {
            cookieStore.clearCredentials()
            _hasSavedCredentials.value = false
            _savedLogin.value = ""
            _savedPassword.value = ""
        }
    }

    fun logout() {
        viewModelScope.launch {
            cookieStore.clearSession()
            _isLoggedIn.value = false
            loadSavedCredentials()
        }
    }
}
