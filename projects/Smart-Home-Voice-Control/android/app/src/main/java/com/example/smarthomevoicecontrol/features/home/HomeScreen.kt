package com.example.smarthomevoicecontrol.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: VoiceControlViewModel, onListen: () -> Unit, onOpenSettings: () -> Unit, onSetup: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var examplesExpanded by remember { mutableStateOf(false) }
    var permissionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.permissionDenied) { if (state.permissionDenied) permissionDialog = true }
    if (permissionDialog) AlertDialog(
        onDismissRequest = { permissionDialog = false },
        title = { Text("Local network access needed") },
        text = { Text("Allow Voice Control to contact your Raspberry Pi on the trusted lab network. You can change this permission in app settings.") },
        confirmButton = { TextButton(onClick = { permissionDialog = false; onOpenSettings() }) { Text("Open app settings") } },
        dismissButton = { TextButton(onClick = { permissionDialog = false }) { Text("Later") } }
    )

    Scaffold(topBar = {
        TopAppBar(title = { Text("Voice Control") }, actions = { TextButton(onClick = onSetup) { Text("Settings") } })
    }) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusCard(configured = state.config.validationError() == null, onSetup = onSetup)
                FilledTonalButton(
                    onClick = onListen,
                    enabled = !state.busy,
                    modifier = Modifier.size(88.dp).semantics { contentDescription = "Tap to speak" },
                    shape = CircleShape
                ) {
                    Text("🎤", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.clearAndSetSemantics { })
                }
                Text(if (state.busy) "Listening or running your command…" else "Tap to speak", style = MaterialTheme.typography.bodyLarge)
                if (state.busy) CircularProgressIndicator(Modifier.size(28.dp))

                state.transcript?.let { transcript ->
                    SectionCard("Heard") { Text("“$transcript”", style = MaterialTheme.typography.bodyLarge) }
                }
                if (state.results.isNotEmpty()) {
                    SectionCard("Result", Modifier.semantics { if (!state.busy) liveRegion = LiveRegionMode.Polite }) {
                        state.results.forEach { result ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(result.label, style = MaterialTheme.typography.titleSmall)
                                    Text(result.value, style = MaterialTheme.typography.bodyLarge)
                                }
                                Text(if (result.succeeded) "✓ Done" else "Failed", color = if (result.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                OutlinedButton(onClick = { examplesExpanded = !examplesExpanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (examplesExpanded) "Hide examples" else "Try saying")
                }
                if (examplesExpanded) {
                    val examples = listOf(
                        "Tell me the temperature", "What's the pressure?", "Tell me the sensor data",
                        "Turn on the lights red", "Turn the lights off"
                    )
                    SectionCard("Command examples") { examples.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) } }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(configured: Boolean, onSetup: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (configured) "Pi configured" else "Pi not configured", style = MaterialTheme.typography.titleMedium)
                Text(if (configured) "Ready to send a command" else "Set a private network address to get started", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onSetup) { Text(if (configured) "Edit" else "Set up") }
        }
    }
}

@Composable
private fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
