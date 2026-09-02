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

    fun logout() {
        viewModelScope.launch {
            cookieStore.clear()
            _isLoggedIn.value = false
        }
    }
}
