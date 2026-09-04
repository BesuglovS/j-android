package com.nayanova.journal.ui.journal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.nayanova.journal.data.model.Student

// Оценка не может быть 1: только 2, 3, 4, 5
val VALID_MARKS = listOf(2, 3, 4, 5)

// За что выставлена оценка (work_type). Короткие подписи — для клетки таблицы.
val WORK_TYPES = listOf("Урок", "ДЗ", "Сам/р", "Тест", "К/р", "Диктант")

// Оценка за домашнее задание
const val HOMEWORK_TYPE = "ДЗ"

fun markColor(value: Int): Color = when (value) {
    2 -> Color(0xFFFF9800)
    3 -> Color(0xFFFFC107)
    4 -> Color(0xFF4CAF50)
    5 -> Color(0xFF1565C0)
    else -> Color.Gray
}

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
    var showHomeworkDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { showHomeworkDialog = true }) {
                        Icon(Icons.Default.HomeWork, "Домашнее задание", tint = MaterialTheme.colorScheme.onPrimary)
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
                    hasPreviousHomework = detail?.previousHomework != null,
                    previousHomework = detail?.previousHomework,
                    onAddMark = { sid, v, wt, c -> viewModel.addMark(sid, v, wt, c) },
                    onAddMarkBulk = { marks, wt, c -> viewModel.addMarkBulk(marks, wt, c) },
                    onRemoveMark = { sid, i -> viewModel.removeMark(sid, i) },
                    onAttendanceChange = { sid, s -> viewModel.updateAttendance(sid, s) },
                    onLateMinutesChange = { sid, m -> viewModel.updateLateMinutes(sid, m) },
                    onAddRemark = { sid, t -> viewModel.addRemark(sid, t) },
                    onRemoveRemark = { sid, i -> viewModel.removeRemark(sid, i) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }

    if (showHomeworkDialog) {
        HomeworkDialog(
            existing = detail?.homework,
            onSave = { title, description, dueDate ->
                showHomeworkDialog = false
                viewModel.saveHomework(lessonId, title, description, dueDate)
            },
            onDelete = detail?.homework?.let { hw ->
                { viewModel.deleteHomework(hw.id, lessonId) }
            },
            onDismiss = { showHomeworkDialog = false }
        )
    }
}

@Composable
fun JournalGrid(
    students: List<Student>,
    localMarks: Map<Int, List<JournalViewModel.LocalMark>>,
    localAttendance: Map<Int, com.nayanova.journal.data.model.AttendanceEntry>,
    localRemarks: Map<Int, List<String>>,
    hasPreviousHomework: Boolean,
    previousHomework: com.nayanova.journal.data.model.Homework?,
    onAddMark: (Int, Int, String, String) -> Unit,
    onAddMarkBulk: (List<Pair<Int, Int>>, String, String) -> Unit,
    onRemoveMark: (Int, Int) -> Unit,
    onAttendanceChange: (Int, String) -> Unit,
    onLateMinutesChange: (Int, Int) -> Unit,
    onAddRemark: (Int, String) -> Unit,
    onRemoveRemark: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStudentId by remember { mutableStateOf<Int?>(null) }
    var showRemarkDialog by remember { mutableStateOf(false) }
    var showMarkDialog by remember { mutableStateOf(false) }
    var showBulkMarkDialog by remember { mutableStateOf(false) }
    var showHomeworkInfoDialog by remember { mutableStateOf(false) }
    // null — выбор «за что» в диалоге; "ДЗ" — фиксируется колонкой ДЗ
    var markDialogWorkType by remember { mutableStateOf<String?>(null) }
    var lateDialogStudentId by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
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
                    TableCell("Ученик", 180.dp, 40.dp, Alignment.CenterStart, 12.sp)
                    // Колонка «Оценки» с кнопкой массового выставления
                    Row(
                        modifier = Modifier
                            .width(160.dp)
                            .height(40.dp)
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Оценки",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showBulkMarkDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.GroupAdd,
                                contentDescription = "Оценка многим ученикам",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (hasPreviousHomework) {
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(40.dp)
                                .padding(4.dp)
                                .clickable { showHomeworkInfoDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "ДЗ (прошл. урок)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    TableCell("Посещ.", 110.dp, 40.dp, Alignment.Center, 11.sp)
                    TableCell("Замечания", 120.dp, 40.dp, Alignment.Center, 11.sp)
                    TableCell("Телефон", 80.dp, 40.dp, Alignment.Center, 11.sp)
                }

                // Строки учеников
                students.forEachIndexed { index, student ->
                    val bgColor = if (index % 2 == 0) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    val attendance = localAttendance[student.id] ?: com.nayanova.journal.data.model.AttendanceEntry()
                    val attendanceColor = when (attendance.status) {
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

                        // Оценки (несколько за урок, у каждой — за что)
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(48.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .clickable {
                                    selectedStudentId = student.id
                                    markDialogWorkType = null
                                    showMarkDialog = true
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val marks = localMarks[student.id] ?: emptyList()
                            if (marks.isEmpty()) {
                                Text(
                                    "  —",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    marks.forEach { mark ->
                                        MiniMark(value = mark.value, workType = mark.workType, comment = mark.comment)
                                    }
                                }
                            }
                        }

                        // Оценка за ДЗ с прошлого урока
                        if (hasPreviousHomework) {
                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(48.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        selectedStudentId = student.id
                                        markDialogWorkType = HOMEWORK_TYPE
                                        showMarkDialog = true
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val hwMarks = (localMarks[student.id] ?: emptyList())
                                    .filter { it.workType == HOMEWORK_TYPE }
                                if (hwMarks.isEmpty()) {
                                    Text(
                                        "  +",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        hwMarks.forEach { mark ->
                                            MiniMark(value = mark.value, workType = mark.workType, comment = mark.comment)
                                        }
                                    }
                                }
                            }
                        }

                        // Посещаемость: статус по нажатию (Да → Н → Оп.); минуты опоздания — долгим нажатием
                        AttendanceSelector(
                            status = attendance.status,
                            lateMinutes = attendance.lateMinutes,
                            onStatusChange = { newStatus ->
                                onAttendanceChange(student.id, newStatus)
                            },
                            onLongPress = { lateDialogStudentId = student.id },
                            modifier = Modifier
                                .width(110.dp)
                                .height(48.dp)
                        )

                        // Замечания
                        val remarks = localRemarks[student.id] ?: emptyList()
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

                        // Телефон (замечание об использовании телефона)
                        val hasPhoneRemark = (localRemarks[student.id] ?: emptyList()).any {
                            it.contains("телефон", ignoreCase = true)
                        }
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(48.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (hasPhoneRemark) Color(0xFFFFEBEE)
                                    else Color.Transparent
                                )
                                .border(
                                    0.5.dp,
                                    if (hasPhoneRemark) Color(0xFFE53935).copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    if (!hasPhoneRemark) {
                                        onAddRemark(student.id, "Использование телефона на уроке")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Телефон",
                                tint = if (hasPhoneRemark) Color(0xFFE53935)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог оценок
    if (showMarkDialog && selectedStudentId != null) {
        MarkDialog(
            studentName = studentName(students, selectedStudentId),
            marks = localMarks[selectedStudentId] ?: emptyList(),
            fixedWorkType = markDialogWorkType,
            onAdd = { value, workType, comment -> onAddMark(selectedStudentId!!, value, workType, comment) },
            onRemove = { index -> onRemoveMark(selectedStudentId!!, index) },
            onDismiss = { showMarkDialog = false; selectedStudentId = null }
        )
    }

    // Диалог массового выставления оценок
    if (showBulkMarkDialog) {
        BulkMarkDialog(
            students = students,
            onApply = { marks, workType, comment ->
                showBulkMarkDialog = false
                onAddMarkBulk(marks, workType, comment)
            },
            onDismiss = { showBulkMarkDialog = false }
        )
    }

    // Диалог минут опоздания
    if (lateDialogStudentId != null) {
        val entry = localAttendance[lateDialogStudentId] ?: com.nayanova.journal.data.model.AttendanceEntry(status = "late")
        LateMinutesDialog(
            studentName = studentName(students, lateDialogStudentId),
            initialMinutes = entry.lateMinutes,
            onConfirm = { minutes ->
                onLateMinutesChange(lateDialogStudentId!!, minutes)
                lateDialogStudentId = null
            },
            onDismiss = { lateDialogStudentId = null }
        )
    }

    // Диалог замечаний
    if (showRemarkDialog && selectedStudentId != null) {
        RemarkDialog(
            studentName = studentName(students, selectedStudentId),
            remarks = localRemarks[selectedStudentId] ?: emptyList(),
            onAdd = { text ->
                onAddRemark(selectedStudentId!!, text)
            },
            onRemove = { index ->
                onRemoveRemark(selectedStudentId!!, index)
            },
            onDismiss = { showRemarkDialog = false; selectedStudentId = null }
        )
    }

    // Диалог информации о домашнем задании
    if (showHomeworkInfoDialog) {
        HomeworkInfoDialog(
            homework = previousHomework,
            onDismiss = { showHomeworkInfoDialog = false }
        )
    }
}

private fun studentName(students: List<Student>, id: Int?): String {
    return students.find { it.id == id }?.let { "${it.lastName} ${it.firstName}" } ?: ""
}

@Composable
private fun TableCell(text: String, width: Dp, height: Dp, align: Alignment, fontSize: TextUnit) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(4.dp),
        contentAlignment = align
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = fontSize, textAlign = TextAlign.Center)
    }
}

/** Мини-колонка оценки в таблице: значение + за что (подпись снизу).
 *  Если у оценки есть комментарий, подпись подсвечивается основным цветом. */
@Composable
private fun MiniMark(value: Int, workType: String, comment: String = "") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(44.dp)
    ) {
        Text(
            value.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = markColor(value)
        )
        Text(
            workType.take(7),
            fontSize = 7.sp,
            lineHeight = 8.sp,
            color = if (comment.isNotBlank()) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Диалог оценок ученика: список поставленных оценок (с удалением, у каждой —
 * за что) и кнопки добавления 2/3/4/5. Если fixedWorkType != null, тип работы
 * зафиксирован (например, "ДЗ" из колонки ДЗ); иначе выбирается в диалоге.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkDialog(
    studentName: String,
    marks: List<JournalViewModel.LocalMark>,
    fixedWorkType: String?,
    onAdd: (Int, String, String) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(fixedWorkType ?: "Урок") }
    var customMode by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оценки: $studentName") },
        text = {
            Column {
                if (marks.isNotEmpty()) {
                    marks.forEachIndexed { index, mark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(markColor(mark.value).copy(alpha = 0.15f))
                                    .border(1.dp, markColor(mark.value).copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    mark.value.toString(),
                                    fontWeight = FontWeight.Bold,
                                    color = markColor(mark.value)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mark.workType, fontSize = 14.sp)
                                if (mark.comment.isNotBlank()) {
                                    Text(
                                        mark.comment,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, "Удалить", modifier = Modifier.size(16.dp))
                            }
                        }
                        if (index < marks.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (fixedWorkType != null) {
                    Text("За: $fixedWorkType", style = MaterialTheme.typography.titleSmall)
                } else {
                    Text("За что оценка", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WORK_TYPES.forEach { type ->
                            FilterChip(
                                selected = !customMode && selectedType == type,
                                onClick = { customMode = false; selectedType = type },
                                label = { Text(type, fontSize = 12.sp) }
                            )
                        }
                        FilterChip(
                            selected = customMode,
                            onClick = { customMode = true },
                            label = { Text("Другое…", fontSize = 12.sp) }
                        )
                    }
                    if (customMode) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            label = { Text("Свой вариант") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Добавить оценку", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VALID_MARKS.forEach { value ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(markColor(value).copy(alpha = 0.15f))
                                .border(1.dp, markColor(value).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val workType = when {
                                        fixedWorkType != null -> fixedWorkType
                                        customMode -> customText.trim().ifBlank { return@clickable }
                                        else -> selectedType
                                    }
                                    onAdd(value, workType, comment.trim())
                                    comment = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                value.toString(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = markColor(value)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}

/**
 * Массовое выставление оценок: «за что» (например, контрольная по теме) и
 * комментарий вводятся один раз для всех, а значение оценки выбирается
 * отдельно у каждого ученика кнопками 2–5 в его строке. Кому оценка не
 * выбрана — тот пропускается. Повторное нажатие снимает выбор.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkMarkDialog(
    students: List<Student>,
    onApply: (marks: List<Pair<Int, Int>>, workType: String, comment: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf("Урок") }
    var customMode by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    // Выбранная оценка на ученика: studentId -> значение (2..5)
    var selectedMarks by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    val workTypeResolved = if (customMode) customText.trim() else selectedType

    // Окно почти во весь экран: список учеников занимает всё доступное место
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Оценки за работу", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("За что оценка (для всех)", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WORK_TYPES.forEach { type ->
                            FilterChip(
                                selected = !customMode && selectedType == type,
                                onClick = { customMode = false; selectedType = type },
                                label = { Text(type, fontSize = 12.sp) }
                            )
                        }
                        FilterChip(
                            selected = customMode,
                            onClick = { customMode = true },
                            label = { Text("Другое…", fontSize = 12.sp) }
                        )
                    }
                    if (customMode) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            label = { Text("Свой вариант") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Комментарий (необязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Оценки: ${selectedMarks.size} из ${students.size}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedMarks.isNotEmpty()) {
                            TextButton(onClick = { selectedMarks = emptyMap() }) {
                                Text("Сбросить")
                            }
                        }
                    }

                    // Список учеников: всё оставшееся место окна, прокрутка — только если не влезло
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        students.forEach { student ->
                            val selected = selectedMarks[student.id]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${student.lastName} ${student.firstName}",
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    VALID_MARKS.forEach { value ->
                                        val isSel = selected == value
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(markColor(value).copy(alpha = if (isSel) 0.35f else 0.12f))
                                                .border(
                                                    if (isSel) 2.dp else 1.dp,
                                                    markColor(value).copy(alpha = if (isSel) 1f else 0.4f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    selectedMarks = if (isSel)
                                                        selectedMarks - student.id
                                                    else
                                                        selectedMarks + (student.id to value)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                value.toString(),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = markColor(value)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Отмена")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = selectedMarks.isNotEmpty() && workTypeResolved.isNotBlank(),
                            onClick = {
                                onApply(selectedMarks.map { it.key to it.value }, workTypeResolved, comment.trim())
                            }
                        ) {
                            Text("Добавить (${selectedMarks.size})")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Диалог домашнего задания: вносится на текущем уроке, выполняется к
 * следующему. Если ДЗ уже задано — поля предзаполнены.
 */
@Composable
fun HomeworkDialog(
    existing: com.nayanova.journal.data.model.Homework?,
    onSave: (title: String, description: String, dueDate: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var dueDate by remember { mutableStateOf(existing?.dueDate ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val canSave = title.isNotBlank() || description.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Домашнее задание") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Задаётся на этом уроке, выполнить — к следующему занятию.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Задание") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Подробности (необязательно)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Срок, ГГГГ-ММ-ДД (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                if (existing != null && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Удалить")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    enabled = canSave,
                    onClick = { onSave(title.trim(), description.trim(), dueDate.trim()) }
                ) {
                    Text("Сохранить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить задание?") },
            text = { Text("Оценки за это задание также будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDismiss()
                    onDelete?.invoke()
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Диалог для отображения информации о домашнем задании (только чтение).
 */
@Composable
fun HomeworkInfoDialog(
    homework: com.nayanova.journal.data.model.Homework?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Домашнее задание") },
        text = {
            if (homework == null) {
                Text("Домашнее задание не задано", fontSize = 14.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!homework.title.isNullOrBlank()) {
                        Text(homework.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    if (!homework.description.isNullOrBlank()) {
                        Text(homework.description, fontSize = 14.sp)
                    }
                    if (!homework.dueDate.isNullOrBlank()) {
                        Text(
                            "Срок сдачи: ${homework.dueDate}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

/**
 * Диалог ввода периода опоздания в минутах.
 */
@Composable
fun LateMinutesDialog(
    studentName: String,
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(if (initialMinutes > 0) initialMinutes.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Опоздание: $studentName") },
        text = {
            Column {
                Text("На сколько минут ученик опоздал?")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("Минуты") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    suffix = { Text("мин") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.toIntOrNull() ?: 0) }) {
                Text("ОК")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/** Переключатель посещаемости: короткое нажатие — следующий статус
 *  (Да → Н → Оп.), долгое нажатие — ввод минут опоздания. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttendanceSelector(
    status: String,
    lateMinutes: Int,
    onStatusChange: (String) -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (icon, color, label) = when (status) {
        "present" -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "+")
        "late" -> Triple(Icons.Default.Schedule, Color(0xFFFF9800), if (lateMinutes > 0) "Оп.${lateMinutes}м" else "Оп.")
        "absent" -> Triple(Icons.Default.Cancel, Color(0xFFE53935), "Н")
        else -> Triple(Icons.Default.HelpOutline, Color.Gray, "?")
    }

    Row(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = {
                    val next = when (status) {
                        "present" -> "absent"
                        "absent" -> "late"
                        else -> "present"
                    }
                    onStatusChange(next)
                },
                onLongClick = onLongPress
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
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
