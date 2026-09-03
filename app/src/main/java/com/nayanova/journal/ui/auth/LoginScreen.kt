package com.nayanova.journal.ui.auth

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.nayanova.journal.util.CookieStore
import kotlinx.coroutines.launch

/** Экранирование значения для вставки в JS-строку в одинарных кавычках. */
private fun jsEscape(value: String): String =
    value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")

/** Перехват отправки формы логина: передаёт введённые логин/пароль в приложение. */
private const val HOOK_JS = """
    (function() {
        var form = document.querySelector('form');
        if (form && !form.dataset.credHook) {
            form.dataset.credHook = '1';
            form.addEventListener('submit', function() {
                var l = document.getElementById('login');
                var p = document.getElementById('password');
                if (l && p && window.CredentialsBridge) {
                    window.CredentialsBridge.onCredentials(l.value, p.value);
                }
            });
        }
    })()
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cookieStore = remember { CookieStore(context) }
    var isLoading by remember { mutableStateOf(true) }
    var serverUrl by remember { mutableStateOf("https://j.nayanovaacademy.ru") }
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val hasSavedCredentials by viewModel.hasSavedCredentials.collectAsState()
    val savedLogin by viewModel.savedLogin.collectAsState()
    val savedPassword by viewModel.savedPassword.collectAsState()
    val credentialsLoaded by viewModel.credentialsLoaded.collectAsState()
    var saveCredentials by remember { mutableStateOf(false) }
    var checkboxInitialized by remember { mutableStateOf(false) }
    var autoLoginFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        serverUrl = cookieStore.getServerUrl()
        // Проверяем сохранённую сессию через /api/v1/me: если кука недействительна,
        // она будет очищена, и пользователь останется на экране входа.
        viewModel.checkAuth()
    }

    // Чекбокс отражает наличие сохранённых данных после их загрузки из настроек.
    LaunchedEffect(credentialsLoaded) {
        if (credentialsLoaded && !checkboxInitialized) {
            saveCredentials = hasSavedCredentials
            checkboxInitialized = true
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            onLoginSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал — Вход") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Ждём загрузки настроек, чтобы WebView получил актуальные
            // сохранённые логин/пароль (иначе автологин пропустит их).
            if (!credentialsLoaded) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (hasSavedCredentials)
                                    "Автоматический вход…"
                                else
                                    "Загрузка страницы входа…"
                            )
                        }
                    }
                }

                // Чекбокс виден только на странице входа при ручном вводе:
                // когда сохранённых данных нет или автологин не удался.
                if (!hasSavedCredentials || autoLoginFailed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = saveCredentials,
                            onCheckedChange = { saveCredentials = it }
                        )
                        Text(
                            "Сохранить логин и пароль",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                SsoWebView(
                    serverUrl = serverUrl,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    autoLogin = hasSavedCredentials,
                    savedLogin = savedLogin,
                    savedPassword = savedPassword,
                    onCookieReceived = { cookie ->
                        scope.launch {
                            cookieStore.setCookie(cookie)
                            viewModel.checkAuth()
                        }
                    },
                    onCredentialsDetected = { login, password ->
                        // Вызывается из потока WebView (JavascriptInterface).
                        if (login.isNotEmpty() && password.isNotEmpty()) {
                            viewModel.onManualCredentials(login, password, saveCredentials)
                        }
                    },
                    onAutoLoginFailed = { autoLoginFailed = true },
                    onPageLoaded = { isLoading = false }
                )
            }

            if (credentialsLoaded && hasSavedCredentials) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Сохранённый вход",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Логин: $savedLogin",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.clearSavedCredentials()
                            }
                        ) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SsoWebView(
    serverUrl: String,
    modifier: Modifier = Modifier,
    autoLogin: Boolean = false,
    savedLogin: String = "",
    savedPassword: String = "",
    onCookieReceived: (String) -> Unit,
    onCredentialsDetected: (String, String) -> Unit,
    onAutoLoginFailed: () -> Unit = {},
    onPageLoaded: () -> Unit
) {
    var cookieFound by remember { mutableStateOf(false) }
    // Автологин выполняется один раз: при неверном пароле не зацикливаемся.
    var autoLoginDone by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val bridge = object {
                @JavascriptInterface
                fun onCredentials(login: String, password: String) {
                    onCredentialsDetected(login, password)
                }
            }

            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT

                addJavascriptInterface(bridge, "CredentialsBridge")

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onPageLoaded()
                        val cookies = cookieManager.getCookie(url) ?: ""
                        if (cookies.contains("auth_session=")) {
                            val sessionCookie = cookies
                                .split(";")
                                .map { it.trim() }
                                .firstOrNull { it.startsWith("auth_session=") }
                                ?.substringAfter("auth_session=")
                                ?: ""
                            if (sessionCookie.isNotEmpty() && !cookieFound) {
                                cookieFound = true
                                onCookieReceived(sessionCookie)
                            }
                        }

                        if (!cookieFound) {
                            // Ставим перехватчик отправки формы для запоминания данных.
                            evaluateJavascript(HOOK_JS, null)
                        }

                        if (autoLogin && !cookieFound && !autoLoginDone) {
                            autoLoginDone = true
                            val js = """
                                (function() {
                                    var loginField = document.getElementById('login');
                                    var passwordField = document.getElementById('password');
                                    if (loginField && passwordField) {
                                        loginField.value = '${jsEscape(savedLogin)}';
                                        passwordField.value = '${jsEscape(savedPassword)}';
                                        loginField.dispatchEvent(new Event('input', { bubbles: true }));
                                        passwordField.dispatchEvent(new Event('input', { bubbles: true }));
                                        var form = loginField.closest('form');
                                        if (form) {
                                            form.submit();
                                            return 'submitted';
                                        }
                                    }
                                    return 'no_form';
                                })()
                            """.trimIndent()
                            evaluateJavascript(js, null)
                        }

                        // Автологин не удался: после попытки снова открыта
                        // страница входа портала без куки сессии.
                        if (autoLoginDone && !cookieFound &&
                            url != null && url.startsWith("https://auth.nayanovaacademy.ru")
                        ) {
                            onAutoLoginFailed()
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed()
                    }
                }

                webChromeClient = WebChromeClient()

                if (autoLogin) {
                    // Устаревшая (недействительная) кука в WebView помешала бы
                    // автологину — страница считала бы пользователя «вошедшим».
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()
                }

                val authUrl = "https://auth.nayanovaacademy.ru/index.php?page=login&redirect=${java.net.URLEncoder.encode("$serverUrl/", "UTF-8")}"
                loadUrl(authUrl)
            }
        },
        update = { /* no-op */ }
    )
}
