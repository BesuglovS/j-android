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
    var showUrlInput by remember { mutableStateOf(true) }
    var serverUrl by remember { mutableStateOf("https://j.nayanovaacademy.ru") }
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    // Проверяем сохранённую сессию через /api/v1/me: если кука недействительна,
    // она будет очищена, и пользователь останется на экране входа.
    LaunchedEffect(Unit) {
        viewModel.checkAuth()
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
            if (showUrlInput) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Адрес сервера журнала",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("https://j.nayanovaacademy.ru") }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                showUrlInput = false
                                scope.launch {
                                    cookieStore.setServerUrl(serverUrl)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Открыть страницу входа")
                        }
                    }
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
                            Text("Загрузка страницы входа…")
                        }
                    }
                }

                SsoWebView(
                    serverUrl = serverUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isLoading) Modifier else Modifier),
                    onCookieReceived = { cookie ->
                        scope.launch {
                            cookieStore.setCookie(cookie)
                            viewModel.checkAuth()
                        }
                    },
                    onPageLoaded = { isLoading = false }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SsoWebView(
    serverUrl: String,
    modifier: Modifier = Modifier,
    onCookieReceived: (String) -> Unit,
    onPageLoaded: () -> Unit
) {
    var cookieFound by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT

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

                val authUrl = "https://auth.nayanovaacademy.ru/index.php?page=login&redirect=${java.net.URLEncoder.encode("$serverUrl/", "UTF-8")}"
                loadUrl(authUrl)
            }
        },
        update = { /* no-op */ }
    )
}
