package ru.nayanovaacademy.journal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.nayanovaacademy.journal.ui.auth.AuthViewModel

/** Состояние попытки автовхода: на уровне процесса приложения.
 *  Автовход по сохранённым данным возможен только один раз за запуск
 *  (при начальном запуске); после явного «Выйти» в той же сессии
 *  повторный автовход не выполняется.
 *  */
object AutoLoginState {
    var attempted = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasSavedCredentials by viewModel.hasSavedCredentials.collectAsState()
    val savedLogin by viewModel.savedLogin.collectAsState()
    val savedPassword by viewModel.savedPassword.collectAsState()
    val credentialsLoaded by viewModel.credentialsLoaded.collectAsState()

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var saveCredentials by remember { mutableStateOf(false) }
    var checkboxInitialized by remember { mutableStateOf(false) }
    var fieldsPrefilled by remember { mutableStateOf(false) }
    // Инициализация из процессного флага: после выхода автовход не повторяется
    var autoLoginAttempted by remember { mutableStateOf(AutoLoginState.attempted) }

    LaunchedEffect(Unit) {
        viewModel.checkAuth()
    }

    // Предзаполняем поля сохранёнными данными после их загрузки.
    LaunchedEffect(credentialsLoaded, savedLogin, savedPassword) {
        if (credentialsLoaded && !fieldsPrefilled) {
            login = savedLogin
            password = savedPassword
            fieldsPrefilled = true
        }
    }

    LaunchedEffect(credentialsLoaded) {
        if (credentialsLoaded && !checkboxInitialized) {
            saveCredentials = hasSavedCredentials
            checkboxInitialized = true
        }
    }

    // Автовход: если сессия недействительна и есть сохранённые данные,
    // выполняем вход один раз без нажатия кнопки.
    LaunchedEffect(credentialsLoaded, hasSavedCredentials, isLoggedIn, isLoading) {
        if (credentialsLoaded && hasSavedCredentials && !isLoggedIn && !isLoading &&
            !autoLoginAttempted
        ) {
            autoLoginAttempted = true
            AutoLoginState.attempted = true
            viewModel.login(savedLogin, savedPassword, remember = true)
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
                title = { Text("Журнал — Настройки") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!credentialsLoaded) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (autoLoginAttempted) "Автоматический вход…" else "Вход…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                "Вход в электронный журнал",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Логин и пароль от единой системы. Доступны только учителям журнала.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = login,
                onValueChange = { login = it },
                label = { Text("Логин") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = saveCredentials,
                    onCheckedChange = { saveCredentials = it }
                )
                Text(
                    "Сохранить логин и пароль",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = {
                    autoLoginAttempted = true
                    AutoLoginState.attempted = true
                    viewModel.login(login, password, saveCredentials)
                },
                enabled = login.isNotBlank() && !password.isBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Войти")
            }

            if (hasSavedCredentials) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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