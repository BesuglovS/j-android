package ru.nayanovaacademy.journal.ui.status

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ru.nayanovaacademy.journal.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SyncStatus(
    val isOnline: Boolean,
    val pendingChanges: Int
)

@HiltViewModel
class AppStatusViewModel @Inject constructor(
    repository: JournalRepository
) : ViewModel() {

    val status: StateFlow<SyncStatus> = combine(
        repository.isOnline,
        repository.pendingChanges
    ) { online, pending ->
        SyncStatus(isOnline = online, pendingChanges = pending)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SyncStatus(true, 0))
}

/**
 * Полоса состояния синхронизации: показывается, когда приложение офлайн
 * или есть неотправленные изменения. Просто вставляется в Scaffold экранов.
 */
@Composable
fun OfflineStatusBanner(modifier: Modifier = Modifier) {
    val viewModel: AppStatusViewModel = hiltViewModel()
    val status by viewModel.status.collectAsState()

    val isOffline = !status.isOnline
    val hasPending = status.pendingChanges > 0
    if (!isOffline && !hasPending) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isOffline) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.secondaryContainer
    ) {
        RowContent(
            isOffline = isOffline,
            pendingChanges = status.pendingChanges
        )
    }
}

@Composable
private fun RowContent(isOffline: Boolean, pendingChanges: Int) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.Sync,
            contentDescription = null,
            tint = if (isOffline) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = when {
                isOffline && pendingChanges > 0 ->
                    "Офлайн: изменения синхронизируются при появлении сети ($pendingChanges в очереди)"
                isOffline -> "Офлайн: показаны сохранённые данные"
                else -> "Изменения будут синхронизированы ($pendingChanges в очереди)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isOffline) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}