package ru.nayanovaacademy.journal.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.nayanovaacademy.journal.ui.status.OfflineStatusBanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("ru"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    onLessonSelected: (Int) -> Unit,
    onAllSubjects: () -> Unit,
    onCreateLesson: () -> Unit,
    onLogout: () -> Unit,
    viewModel: DayViewModel = hiltViewModel()
) {
    val lessons by viewModel.lessons.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    val syncItems by viewModel.scheduleSyncItems.collectAsState()
    val syncLoading by viewModel.scheduleSyncLoading.collectAsState()
    val isApplyingSchedule by viewModel.isApplyingSchedule.collectAsState()
    val scheduleSyncMessage by viewModel.scheduleSyncMessage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(scheduleSyncMessage) {
        scheduleSyncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeScheduleSyncMessage()
        }
    }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSyncMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Электронный журнал") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    TextButton(onClick = onAllSubjects) {
                        Text("Все предметы", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    // Принудительная синхронизация: офлайн-очередь + кеш + список дня
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        IconButton(onClick = { viewModel.syncNow() }) {
                            Icon(Icons.Default.Sync, "Синхронизировать", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    IconButton(onClick = {
                        // Сначала очистить сессию (иначе экран входа «вернёт»
                        // живую сессию), затем показать экран входа
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, "Выйти", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openScheduleSyncPreview() },
                    icon = { Icon(Icons.Default.Rule, contentDescription = null) },
                    text = { Text("Сверить с расписанием") }
                )
                ExtendedFloatingActionButton(
                    onClick = onCreateLesson,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Новое занятие") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OfflineStatusBanner()

            // Строка выбора даты: стрелки + дата (по нажатию — календарь)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousDay() }) {
                    Icon(Icons.Default.ChevronLeft, "Предыдущий день")
                }
                Text(
                    text = selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDatePicker = true }
                )
                IconButton(onClick = { viewModel.nextDay() }) {
                    Icon(Icons.Default.ChevronRight, "Следующий день")
                }
            }

            when {
                isLoading && lessons.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Text(
                                    text = error ?: "",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.loadLessons() }) {
                                Text("Повторить")
                            }
                        }
                    }
                }
                lessons.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Нет занятий на этот день", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(lessons) { lesson ->
                            LessonCard(lesson = lesson, onClick = { onLessonSelected(lesson.id) })
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.setDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (syncLoading || syncItems != null) {
        ScheduleSyncDialog(
            items = syncItems,
            isLoading = syncLoading,
            isApplying = isApplyingSchedule,
            selectedCount = syncItems?.count { it.actionable && it.selected } ?: 0,
            onToggle = viewModel::toggleScheduleSyncItem,
            onConfirm = viewModel::confirmScheduleSync,
            onDismiss = viewModel::dismissScheduleSyncPreview
        )
    }
}

@Composable
private fun ScheduleSyncDialog(
    items: List<ScheduleSyncItem>?,
    isLoading: Boolean,
    isApplying: Boolean,
    selectedCount: Int,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сверка с расписанием") },
        text = {
            when {
                isLoading || items == null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                items.isEmpty() -> {
                    Text("Расхождений нет — журнал совпадает с расписанием")
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(items) { index, item ->
                            ScheduleSyncRow(item = item, onToggle = { onToggle(index) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isApplying && selectedCount > 0) {
                Text(if (selectedCount > 0) "Применить ($selectedCount)" else "Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isApplying) {
                Text(if (isApplying) "Применяю…" else "Отмена")
            }
        }
    )
}

@Composable
private fun ScheduleSyncRow(item: ScheduleSyncItem, onToggle: () -> Unit) {
    val actionLabel = when (item.action) {
        ScheduleSyncAction.ADD -> "Добавить"
        ScheduleSyncAction.UPDATE -> "Изменить"
        ScheduleSyncAction.DELETE -> "Удалить"
        ScheduleSyncAction.UNCHANGED -> "Без изменений"
        ScheduleSyncAction.UNMATCHED -> "Не сопоставлено"
    }
    val actionColor = when (item.action) {
        ScheduleSyncAction.ADD -> MaterialTheme.colorScheme.primary
        ScheduleSyncAction.UPDATE -> MaterialTheme.colorScheme.tertiary
        ScheduleSyncAction.DELETE -> MaterialTheme.colorScheme.error
        ScheduleSyncAction.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
        ScheduleSyncAction.UNMATCHED -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.actionable) Modifier.clickable(onClick = onToggle) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.selected,
            onCheckedChange = if (item.actionable) ({ onToggle() }) else null
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = actionColor,
                    fontWeight = FontWeight.Bold
                )
                if (!item.detail.isNullOrBlank()) {
                    Text(
                        text = " · " + item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: ru.nayanovaacademy.journal.data.model.Lesson, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Время занятия
            Text(
                text = if (lesson.startTime.isNullOrBlank()) "—" else lesson.startTime!!,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(lesson.className ?: "Класс")
                        append(" — ")
                        append(lesson.subjectName ?: "Предмет")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!lesson.topic.isNullOrBlank()) {
                    Text(
                        text = lesson.topic!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
