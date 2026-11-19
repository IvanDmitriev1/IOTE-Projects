package com.example.homeinvasion.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.homeinvasion.data.HomeUiState
import com.example.homeinvasion.data.MotionEvent
import com.example.homeinvasion.data.SessionState
import com.example.homeinvasion.data.SnapshotState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetryAuthorization: () -> Unit,
    onRetry: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onCloseEvent: () -> Unit,
    onSettings: () -> Unit,
) {
    val signedIn = state.session is SessionState.SignedIn
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectedEventId) {
        if (state.selectedEventId != null) listState.animateScrollToItem(1)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home Watch") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        if (!signedIn) {
            SetupPanel(state, onRetryAuthorization, Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ConnectionCard(state, onRetry)
                }
                state.selectedEventId?.let { eventId ->
                    val event = state.selectedEvent ?: MotionEvent(eventId, 0L, "")
                    item(key = "selected-$eventId") {
                        SnapshotCard(event, state.snapshot, onRetrySnapshot = { onOpenEvent(event.id) }, onClose = onCloseEvent)
                    }
                }
                if (state.isLoading && state.events.isEmpty()) {
                    item { LoadingRow("Loading recent motion events…") }
                } else if (state.feedError != null) {
                    item { InlineError(state.feedError, onRetry) }
                } else if (state.events.isEmpty()) {
                    item { EmptyState(state.online) }
                } else {
                    item { Text("Recent motion", style = MaterialTheme.typography.titleLarge) }
                    items(state.events, key = MotionEvent::id) { event ->
                        EventRow(event = event, onClick = { onOpenEvent(event.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupPanel(state: HomeUiState, onRetryAuthorization: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Home motion alerts", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (state.session is SessionState.Loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Connecting securely…", style = MaterialTheme.typography.bodyMedium)
        } else if (state.session is SessionState.NeedsApproval) {
            Text("This Google account is waiting for home access. Check access after the administrator links your account to the home.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetryAuthorization, enabled = !state.authBusy) { Text("Check access") }
        }
        val error = when (state.session) {
            SessionState.Unconfigured -> "Firebase is not configured. Add app/google-services.json and rebuild."
            is SessionState.Error -> state.session.message
            else -> state.authError
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConnectionCard(state: HomeUiState, onRetry: () -> Unit) {
    val message = when {
        state.online -> "Connected"
        state.events.isNotEmpty() -> "Offline · saved events may be out of date"
        else -> "Offline · waiting for a connection"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.titleMedium)
                if (state.online) Text("Showing up to 20 recent events", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRetry, enabled = !state.isLoading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh events")
            }
        }
    }
}

@Composable
private fun EventRow(event: MotionEvent, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Motion detected", style = MaterialTheme.typography.titleMedium)
                Text(formatDetectedAt(event.detectedAt), style = MaterialTheme.typography.bodyMedium)
            }
            Text("View snapshot", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SnapshotCard(event: MotionEvent, snapshot: SnapshotState, onRetrySnapshot: () -> Unit, onClose: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Motion snapshot", style = MaterialTheme.typography.titleLarge)
                    Text(formatDetectedAt(event.detectedAt), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = onClose) { Text("Close") }
            }
            when (snapshot) {
                SnapshotState.Hidden -> Unit
                SnapshotState.Loading -> LoadingRow("Loading private snapshot…")
                is SnapshotState.Error -> InlineError(snapshot.message, onRetrySnapshot)
                is SnapshotState.Ready -> Image(
                    bitmap = snapshot.bitmap.asImageBitmap(),
                    contentDescription = "Room snapshot for motion detected at ${formatDetectedAt(event.detectedAt)}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(online: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (online) "No motion events yet" else "Events aren't available offline", style = MaterialTheme.typography.titleMedium)
        Text("New sensor events will appear here.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadingRow(label: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
        }
    }
}

private fun formatDetectedAt(timestamp: Long): String = runCatching {
    DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm:ss", Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
}.getOrDefault("Time unavailable")
