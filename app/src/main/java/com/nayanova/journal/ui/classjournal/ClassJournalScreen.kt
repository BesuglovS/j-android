package com.nayanova.journal.ui.classjournal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import com.nayanova.journal.data.model.Lesson
import com.nayanova.journal.data.model.Student
import com.nayanova.journal.ui.journal.JournalViewModel
import com.nayanova.journal.ui.journal.MarkDialog
import com.nayanova.journal.ui.journal.markColor
import com.nayanova.journal.ui.status.OfflineStatusBanner
import kotlin.math.roundToInt

private val COLOR_PRESENT = Color(0xFF4CAF50)
private val COLOR_ABSENT = Color(0xFFE53935)
private val COLOR_LATE = Color(0xFFFFC107)
private val COLOR_BORDER_LIGHT = Color(0xFFBDBDBD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassJournalScreen(
    classId: Int,
    subjectId: Int,
    className: String,
    subjectName: String,
    onLessonSelected: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: ClassJournalViewModel = hiltViewModel()
) {
    val data by viewModel.data.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val title by viewModel.title.collectAsState()

    LaunchedEffect(classId, subjectId) {
        viewModel.load(classId, subjectId)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Журнал: $className — ${title ?: subjectName}",
                                style = MaterialTheme.typography.titleMedium
                            )
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
                        IconButton(onClick = { viewModel.load(classId, subjectId) }) {
                            Icon(Icons.Default.Refresh, "Обновить", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                )
                OfflineStatusBanner()
            }
        }
    ) { padding ->
        when {
            isLoading && data == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                text = error ?: "",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.load(classId, subjectId) }) {
                            Text("Повторить")
                        }
                    }
                }
            }
            data == null || data!!.lessons.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Нет данных за текущую четверть", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                val journalData = data!!
                ClassJournalTable(
                    lessons = journalData.lessons,
                    students = journalData.students,
                    marks = journalData.marks,
                    attendance = journalData.attendance,
                    onLessonSelected = onLessonSelected,
                    onAddMark = { lessonId, sid, value, workType, comment ->
                        viewModel.addMark(lessonId, sid, value, workType, comment)
                    },
                    onRemoveMark = { lessonId, sid, index ->
                        viewModel.removeMark(lessonId, sid, index)
                    },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClassJournalTable(
    lessons: List<Lesson>,
    students: List<Student>,
    marks: Map<String, Map<String, List<com.nayanova.journal.data.model.Mark>>>,
    attendance: Map<String, Map<String, com.nayanova.journal.data.model.AttendanceEntry>>,
    onLessonSelected: (Int) -> Unit,
    onAddMark: (Int, Int, Int, String, String) -> Unit,
    onRemoveMark: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val studentColWidth = 150.dp
    val avgColWidth = 56.dp
    val dateColWidth = 80.dp
    val cellHeight = 48.dp
    val headerHeight = 56.dp

    var markDialogStudent by remember { mutableStateOf<Student?>(null) }
    var markDialogLesson by remember { mutableStateOf<Lesson?>(null) }

    Column(modifier = modifier) {
        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem(color = COLOR_PRESENT, text = "Был")
            LegendItem(color = COLOR_ABSENT, text = "Н (не был)")
            LegendItem(color = COLOR_LATE, text = "Опоздал")
        }

        val verticalScroll = rememberScrollState()
        val horizontalScroll = rememberScrollState()

        // Внешний Row: вертикальный скролл охватывает всю таблицу,
        // левая колонка имён фиксирована, правая скроллится по горизонтали.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
        ) {
            // ---- Фиксированная левая колонка: имена учеников ----
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Header "Ученик"
                Box(
                    modifier = Modifier
                        .width(studentColWidth)
                        .height(headerHeight)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(0.5.dp, COLOR_BORDER_LIGHT)
                        .padding(4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "Ученик",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Student name cells
                students.forEachIndexed { index, student ->
                    val rowBg = if (index % 2 == 0) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .width(studentColWidth)
                            .height(cellHeight)
                            .background(rowBg)
                            .border(0.5.dp, COLOR_BORDER_LIGHT)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "${student.lastName} ${student.firstName}",
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ---- Правая прокручиваемая часть: даты занятий + средняя ----
            Column(
                modifier = Modifier.horizontalScroll(horizontalScroll)
            ) {
                // Header row: date headers + "Средн."
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(0.5.dp, COLOR_BORDER_LIGHT)
                ) {
                    lessons.forEach { lesson ->
                        val dateShort = formatLessonDate(lesson.date)
                        Box(
                            modifier = Modifier
                                .width(dateColWidth)
                                .height(headerHeight)
                                .padding(2.dp)
                                .clickable { onLessonSelected(lesson.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dateShort,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!lesson.startTime.isNullOrBlank()) {
                                    Text(
                                        text = lesson.startTime,
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // "Средняя" header (самый правый столбец)
                    Box(
                        modifier = Modifier
                            .width(avgColWidth)
                            .height(headerHeight)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Средн.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Student rows (cells for each lesson + average)
                students.forEachIndexed { index, student ->
                    val rowBg = if (index % 2 == 0) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .border(0.5.dp, COLOR_BORDER_LIGHT)
                    ) {
                        // Cells for each lesson
                        lessons.forEach { lesson ->
                            val lessonKey = lesson.id.toString()
                            val studentKey = student.id.toString()
                            val studentMarks = marks[lessonKey]?.get(studentKey) ?: emptyList()
                            val studentAttendance = attendance[lessonKey]?.get(studentKey)
                            val status = studentAttendance?.status ?: "present"

                            // Border color based on attendance
                            val borderColor = when (status) {
                                "absent" -> COLOR_ABSENT
                                "late" -> COLOR_LATE
                                else -> COLOR_PRESENT
                            }
                            val borderWidth = if (status != "present") 2.dp else 1.dp
                            val cellBg = when (status) {
                                "absent" -> COLOR_ABSENT.copy(alpha = 0.08f)
                                "late" -> COLOR_LATE.copy(alpha = 0.10f)
                                else -> Color.Transparent
                            }

                            Box(
                                modifier = Modifier
                                    .width(dateColWidth)
                                    .height(cellHeight)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(cellBg)
                                    .border(borderWidth, borderColor, RoundedCornerShape(3.dp))
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            markDialogStudent = student
                                            markDialogLesson = lesson
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (status == "absent") {
                                    Text(
                                        "Н",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = COLOR_ABSENT
                                    )
                                } else {
                                    // Show marks
                                    if (studentMarks.isNotEmpty()) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            studentMarks.take(2).forEach { mark ->
                                                Text(
                                                    text = mark.value.toString(),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = markColor(mark.value),
                                                    lineHeight = 16.sp
                                                )
                                            }
                                            if (studentMarks.size > 2) {
                                                Text(
                                                    "+${studentMarks.size - 2}",
                                                    fontSize = 8.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else if (status == "late") {
                                        val mins = studentAttendance?.lateMinutes ?: 0
                                        if (mins > 0) {
                                            Text(
                                                "${mins}м",
                                                fontSize = 10.sp,
                                                color = COLOR_LATE
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Average grade cell (самый правый столбец)
                        val studentAvg = averageMark(marks, student.id)
                        Box(
                            modifier = Modifier
                                .width(avgColWidth)
                                .height(cellHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            if (studentAvg != null) {
                                Text(
                                    text = formatAverage(studentAvg),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = markColor(studentAvg.roundToInt())
                                )
                            } else {
                                Text(
                                    "—",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог оценок (как на экране занятия): список оценок с удалением + добавление 2–5.
    // Открывается долгим нажатием на ячейку ученик × занятие.
    val dialogStudent = markDialogStudent
    val dialogLesson = markDialogLesson
    if (dialogStudent != null && dialogLesson != null) {
        val lessonKey = dialogLesson.id.toString()
        val studentKey = dialogStudent.id.toString()
        val lessonMarks = marks[lessonKey]?.get(studentKey) ?: emptyList()
        MarkDialog(
            studentName = "${dialogStudent.lastName} ${dialogStudent.firstName}",
            marks = lessonMarks.map {
                JournalViewModel.LocalMark(it.value, it.workType, it.comment ?: "")
            },
            fixedWorkType = null,
            onAdd = { value, workType, comment ->
                onAddMark(dialogLesson.id, dialogStudent.id, value, workType, comment)
            },
            onRemove = { index ->
                onRemoveMark(dialogLesson.id, dialogStudent.id, index)
            },
            onDismiss = {
                markDialogStudent = null
                markDialogLesson = null
            }
        )
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .border(2.dp, color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 11.sp)
    }
}

private fun formatLessonDate(dateStr: String): String {
    // "2026-09-01" -> "01.09"
    return try {
        val parts = dateStr.split("-")
        if (parts.size == 3) "${parts[2]}.${parts[1]}" else dateStr
    } catch (_: Exception) {
        dateStr
    }
}

/** Средняя оценка ученика за четверть (по всем оценкам во всех уроках). */
private fun averageMark(
    marks: Map<String, Map<String, List<com.nayanova.journal.data.model.Mark>>>,
    studentId: Int
): Double? {
    val studentKey = studentId.toString()
    val values = mutableListOf<Int>()
    for ((_, studentMarks) in marks) {
        studentMarks[studentKey]?.forEach { mark ->
            if (mark.value in 2..5) values.add(mark.value)
        }
    }
    if (values.isEmpty()) return null
    return values.average()
}

private fun formatAverage(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == Math.floor(rounded)) rounded.toInt().toString() else rounded.toString()
}
