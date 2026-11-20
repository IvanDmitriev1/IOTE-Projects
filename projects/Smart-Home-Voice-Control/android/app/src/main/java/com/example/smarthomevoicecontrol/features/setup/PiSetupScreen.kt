package com.example.smarthomevoicecontrol.features.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiSetupScreen(
    viewModel: PiSetupViewModel,
    onTestConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val canEdit = !state.saving && !state.saved
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text("Raspberry Pi setup") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
    }) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Use the Pi's private IPv4 address on the same trusted lab network. Its HTTP service is unencrypted and has no authentication.")
                OutlinedTextField(
                    value = state.address,
                    onValueChange = viewModel::updateAddress,
                    enabled = canEdit,
                    label = { Text("Private IPv4 address") },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.port,
                    onValueChange = viewModel::updatePort,
                    enabled = canEdit,
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.configOrNull() == null) {
                    Text(state.validationError(), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
                Text("Test connection reads temperature using these draft settings. Your device's speech provider may process audio online.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        enabled = canEdit && !state.checking && state.configOrNull() != null,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (state.checking) "Testing…" else "Test connection") }
                    Button(
                        onClick = viewModel::save,
                        enabled = canEdit && state.configOrNull() != null,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (state.saving) "Saving…" else "Save") }
                }
                state.saveError?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                state.testResult?.let { result ->
                    Card(Modifier.fillMaxWidth()) { Text(result, Modifier.padding(16.dp)) }
                    if (result.startsWith("Local network permission")) TextButton(onClick = onOpenSettings) { Text("Open app settings") }
                }
            }
        }
    }
}
