package com.nayanova.journal.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nayanova.journal.data.model.Student

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    lessonId: Int,
    onBack: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val localMarks by viewModel.localMarks.collectAsState()
    val localAttendance by viewModel.localAttendance.collectAsState()
    val localRemarks by viewModel.localRemarks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()

    LaunchedEffect(lessonId) {
        viewModel.loadLesson(lessonId)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            detail?.let { "${it.lesson.className} — ${it.lesson.subjectName}" } ?: "Журнал",
                            style = MaterialTheme.typography.titleMedium
                        )
                        detail?.lesson?.let { lesson ->
                            Text(
                                text = buildString {
                                    append(lesson.date)
                                    if (!lesson.startTime.isNullOrBlank()) append(" ${lesson.startTime}")
                                    if (!lesson.topic.isNullOrBlank()) append(" · ${lesson.topic}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { viewModel.saveMarks(lessonId) }) {
                        Icon(Icons.Default.Grade, "Сохранить оценки", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { viewModel.saveAttendance(lessonId) }) {
                        Icon(Icons.Default.CheckCircle, "Сохранить посещаемость", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { viewModel.saveRemarks(lessonId) }) {
                        Icon(Icons.Default.Comment, "Сохранить замечания", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val students = detail?.students ?: emptyList()
            if (students.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет учеников", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                JournalGrid(
                    students = students,
                    localMarks = localMarks,
                    localAttendance = localAttendance,
                    localRemarks = localRemarks,
                    onMarkChange = { sid, wt, v -> viewModel.updateMark(sid, wt, v) },
                    onMarkCommentChange = { sid, wt, c -> viewModel.updateMarkComment(sid, wt, c) },
                    onAttendanceChange = { sid, s -> viewModel.updateAttendance(sid, s) },
                    onAddRemark = { sid, t -> viewModel.addRemark(sid, t) },
                    onRemoveRemark = { sid, i -> viewModel.removeRemark(sid, i) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

@Composable
fun JournalGrid(
    students: List<Student>,
    localMarks: Map<Int, Map<String, com.nayanova.journal.data.model.MarkEntry>>,
    localAttendance: Map<Int, com.nayanova.journal.data.model.AttendanceEntry>,
    localRemarks: Map<Int, MutableList<String>>,
    onMarkChange: (Int, String, Int) -> Unit,
    onMarkCommentChange: (Int, String, String) -> Unit,
    onAttendanceChange: (Int, String) -> Unit,
    onAddRemark: (Int, String) -> Unit,
    onRemoveRemark: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStudentId by remember { mutableStateOf<Int?>(null) }
    var showRemarkDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Заголовок: кнопки сохранения
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { /* handled by top bar */ },
                label = { Text("Оценки") },
                leadingIcon = { Icon(Icons.Default.Grade, null, Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = { /* handled by top bar */ },
                label = { Text("Посещаемость") },
                leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = { /* handled by top bar */ },
                label = { Text("Замечания") },
                leadingIcon = { Icon(Icons.Default.Comment, null, Modifier.size(16.dp)) }
            )
        }

        // Таблица: горизонтальный + вертикальный скролл
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Шапка таблицы
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    // Столбец ученика
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(40.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Ученик", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    // Столбец: Оценка
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(40.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Оцен.", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    // Столбец: Комментарий к оценке
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(40.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Комм.", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    // Столбец: Посещаемость
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(40.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Посещ.", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    // Столбец: Замечания
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(40.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Замечания", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }

                // Строки учеников
                students.forEachIndexed { index, student ->
                    val bgColor = if (index % 2 == 0) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    val attendance = localAttendance[student.id]?.status ?: "present"
                    val attendanceColor = when (attendance) {
                        "late" -> Color(0xFFFFF3E0)
                        "absent" -> Color(0xFFFFEBEE)
                        else -> bgColor
                    }

                    Row(
                        modifier = Modifier
                            .background(attendanceColor)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        // Имя ученика
                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .height(48.dp)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "${student.lastName} ${student.firstName.firstOrNull() ?: ""}.",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Оценка (1-5)
                        val markValue = localMarks[student.id]?.get("lesson")?.value ?: 0
                        MarkSelector(
                            value = markValue,
                            onValueChange = { onMarkChange(student.id, "lesson", it) },
                            modifier = Modifier
                                .width(60.dp)
                                .height(48.dp)
                        )

                        // Комментарий к оценке
                        val markComment = localMarks[student.id]?.get("lesson")?.comment ?: ""
                        SmallTextField(
                            value = markComment,
                            onValueChange = { onMarkCommentChange(student.id, "lesson", it) },
                            modifier = Modifier
                                .width(80.dp)
                                .height(48.dp)
                        )

                        // Посещаемость
                        AttendanceSelector(
                            status = attendance,
                            onStatusChange = { onAttendanceChange(student.id, it) },
                            modifier = Modifier
                                .width(90.dp)
                                .height(48.dp)
                        )

                        // Замечания
                        val remarks = localRemarks[student.id] ?: mutableListOf()
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(48.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .clickable { selectedStudentId = student.id; showRemarkDialog = true },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (remarks.isEmpty()) {
                                Text(
                                    "  +",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = remarks.joinToString("; ") { it.take(15) },
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRemarkDialog && selectedStudentId != null) {
        RemarkDialog(
            studentName = students.find { it.id == selectedStudentId }?.let {
                "${it.lastName} ${it.firstName}"
            } ?: "",
            remarks = (localRemarks[selectedStudentId] ?: mutableListOf()).toList(),
            onAdd = { text ->
                onAddRemark(selectedStudentId!!, text)
            },
            onRemove = { index ->
                onRemoveRemark(selectedStudentId!!, index)
            },
            onDismiss = { showRemarkDialog = false; selectedStudentId = null }
        )
    }
}

@Composable
fun MarkSelector(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = mapOf(
        1 to Color(0xFFE53935),
        2 to Color(0xFFFF9800),
        3 to Color(0xFFFFC107),
        4 to Color(0xFF4CAF50),
        5 to Color(0xFF1565C0)
    )
    val currentColor = colors[value] ?: MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(currentColor.copy(alpha = 0.15f))
            .border(1.dp, currentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable {
                val next = if (value >= 5) 0 else value + 1
                onValueChange(next)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (value > 0) value.toString() else "—",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) currentColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AttendanceSelector(
    status: String,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color, label) = when (status) {
        "present" -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "+")
        "late" -> Triple(Icons.Default.Schedule, Color(0xFFFF9800), "Оп.")
        "absent" -> Triple(Icons.Default.Cancel, Color(0xFFE53935), "Н/")
        else -> Triple(Icons.Default.HelpOutline, Color.Gray, "?")
    }

    Row(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .clickable {
                val next = when (status) {
                    "present" -> "late"
                    "late" -> "absent"
                    else -> "present"
                }
                onStatusChange(next)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SmallTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(2.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
            singleLine = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemarkDialog(
    studentName: String,
    remarks: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var newText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Замечания: $studentName") },
        text = {
            Column {
                if (remarks.isNotEmpty()) {
                    remarks.forEachIndexed { index, text ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp
                            )
                            IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Удалить", modifier = Modifier.size(14.dp))
                            }
                        }
                        if (index < remarks.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    label = { Text("Новое замечание") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (newText.isNotBlank()) {
                    onAdd(newText.trim())
                    newText = ""
                }
            }) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
