package com.nayanova.journal.ui.classes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassesScreen(
    onClassSelected: (Int, Int, String, String) -> Unit,
    onLogout: () -> Unit,
    viewModel: ClassesViewModel = hiltViewModel()
) {
    val classes by viewModel.classes.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var selectedClassId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadClasses()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выбор класса и предмета") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, "Выйти", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = error ?: "",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                // Сетка классов
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(classes) { cls ->
                        val isSelected = selectedClassId == cls.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedClassId = if (isSelected) null else cls.id
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = cls.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${cls.studentCount} учеников",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Предметы выбранного класса
                if (selectedClassId != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Предметы класса ${classes.find { it.id == selectedClassId }?.name ?: ""}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val classSubjects = subjects[selectedClassId] ?: emptyList()
                    if (classSubjects.isEmpty()) {
                        Text(
                            "Нет предметов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 400.dp)
                        ) {
                            items(classSubjects) { subject ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val cls = classes.find { it.id == selectedClassId }
                                            onClassSelected(
                                                selectedClassId!!,
                                                subject.id,
                                                cls?.name ?: "",
                                                subject.name
                                            )
                                        },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = subject.name,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
