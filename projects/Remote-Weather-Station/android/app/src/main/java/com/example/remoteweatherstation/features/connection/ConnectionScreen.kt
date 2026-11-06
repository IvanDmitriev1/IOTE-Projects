package com.example.remoteweatherstation.features.connection

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.remoteweatherstation.core.model.PressureUnit
import com.example.remoteweatherstation.core.model.WeatherSource
import com.example.remoteweatherstation.ui.theme.WeatherTheme

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConnectionScreen(
    state: ConnectionUiState,
    onBack: () -> Unit,
    onUpdate: (String, String) -> Unit,
    onTls: (Boolean) -> Unit,
    onSource: (WeatherSource) -> Unit,
    onPressureUnit: (PressureUnit) -> Unit,
    onCloudPressureUnit: (PressureUnit) -> Unit,
    onVisibility: (String, Boolean) -> Unit,
    onClearSecret: (String) -> Unit,
    onTestMqtt: () -> Unit,
    onTestCloud: () -> Unit,
    onSave: () -> Unit,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    val focusers = remember {
        mapOf(
            "brokerHost" to FocusRequester(),
            "brokerPort" to FocusRequester(),
            "topic" to FocusRequester(),
            "cloudChannelId" to FocusRequester(),
            "forecastChannelId" to FocusRequester(),
            "pressureUnit" to FocusRequester(),
            "cloudPressureUnit" to FocusRequester(),
        )
    }
    LaunchedEffect(state.validationErrors) {
        state.validationErrors.keys.firstOrNull()?.let { focusers[it]?.requestFocus() }
    }
    BackHandler(enabled = !state.mqttTesting && !state.cloudTesting && !state.saving, onBack = onBack)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Connection settings") }) },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onSave, enabled = !state.loading && !state.saving && !state.mqttTesting && !state.cloudTesting) {
                        Text(if (state.saving) "Saving…" else "Save settings")
                    }
                }
            }
        },
    ) { insets ->
        if (state.loading) {
            Text("Loading settings…", modifier = Modifier.padding(insets).padding(24.dp))
        } else {
            Column(
                Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.generalError?.let { ErrorMessageCard(it) }
                SettingsSection("Weather source") {
                    Text("Choose which source supplies dashboard readings.", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.activeSource == WeatherSource.MQTT,
                            onClick = { onSource(WeatherSource.MQTT) },
                            enabled = !state.mqttTesting && !state.cloudTesting && !state.saving,
                            label = { Text("Local MQTT") },
                        )
                        FilterChip(
                            selected = state.activeSource == WeatherSource.THINGSPEAK,
                            onClick = { onSource(WeatherSource.THINGSPEAK) },
                            enabled = !state.mqttTesting && !state.cloudTesting && !state.saving,
                            label = { Text("ThingSpeak") },
                        )
                    }
                    state.validationErrors["activeSource"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                if (state.activeSource == null) {
                    Text("Select a weather source to set up the dashboard.", style = MaterialTheme.typography.bodyMedium)
                }
                if (state.activeSource == WeatherSource.MQTT) {
                SettingsSection("MQTT station") {
                    OutlinedTextField(
                        value = state.brokerHost, onValueChange = { onUpdate("brokerHost", it) },
                        label = { Text("Broker host") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusers.getValue("brokerHost")),
                        isError = state.validationErrors.containsKey("brokerHost"),
                        supportingText = state.validationErrors["brokerHost"]?.let { text -> { Text(text) } },
                    )
                    OutlinedTextField(
                        value = state.brokerPort, onValueChange = { onUpdate("brokerPort", it) },
                        label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusers.getValue("brokerPort")),
                        isError = state.validationErrors.containsKey("brokerPort"),
                        supportingText = state.validationErrors["brokerPort"]?.let { text -> { Text(text) } },
                    )
                    Row(
                        Modifier.fillMaxWidth().toggleable(state.useTls, role = Role.Switch, onValueChange = onTls).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (state.useTls) "TLS enabled · recommended" else "Plaintext connection", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (state.useTls) "The secure default uses port 8883." else "Use plaintext only for a trusted local network. Credentials are exposed on that network.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = state.useTls, onCheckedChange = null)
                    }
                    OutlinedTextField(
                        value = state.topic, onValueChange = { onUpdate("topic", it) },
                        label = { Text("Topic") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusers.getValue("topic")),
                        isError = state.validationErrors.containsKey("topic"),
                        supportingText = state.validationErrors["topic"]?.let { text -> { Text(text) } },
                    )
                    Text("Select the pressure unit sent by this MQTT source. The app converts pressure to hPa for display.", style = MaterialTheme.typography.bodySmall)
                    PressureUnitChoices("MQTT source pressure unit", state.pressureUnit, onPressureUnit, state.validationErrors["pressureUnit"], focusers.getValue("pressureUnit"))
                    SecretField(
                        label = "MQTT username", value = state.mqttUsernameDraft, saved = state.hasSavedUsername,
                        visible = state.showUsername, onChange = { onUpdate("mqttUsernameDraft", it) },
                        onToggle = { onVisibility("username", !state.showUsername) }, clearRequested = state.clearSavedUsername,
                        onClearSaved = { onClearSecret("username") }, password = false,
                    )
                    SecretField(
                        label = "MQTT password", value = state.mqttPasswordDraft, saved = state.hasSavedPassword,
                        visible = state.showPassword, onChange = { onUpdate("mqttPasswordDraft", it) },
                        onToggle = { onVisibility("password", !state.showPassword) }, clearRequested = state.clearSavedPassword,
                        onClearSaved = { onClearSecret("password") }, password = true,
                    )
                    OutlinedButton(onClick = onTestMqtt, enabled = state.activeSource == WeatherSource.MQTT && state.savedActiveSource == WeatherSource.MQTT && state.savedSourceReady && !state.mqttTesting && !state.cloudTesting && !state.saving) {
                        Text(if (state.mqttTesting) "Testing MQTT…" else "Test MQTT")
                    }
                    state.mqttTestMessage?.let { TestResult(it) }
                }
                }
                if (state.activeSource == WeatherSource.THINGSPEAK) {
                SettingsSection("ThingSpeak history") {
                    Text("This app reads existing ThingSpeak history. It does not upload readings or configure a channel.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = state.cloudChannelId, onValueChange = { onUpdate("cloudChannelId", it) },
                        label = { Text("Channel ID") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusers.getValue("cloudChannelId")),
                        isError = state.validationErrors.containsKey("cloudChannelId"),
                        supportingText = state.validationErrors["cloudChannelId"]?.let { text -> { Text(text) } },
                    )
                    Text("Select how the existing cloud pressure field is stored; it is converted to hPa for display.", style = MaterialTheme.typography.bodySmall)
                    PressureUnitChoices("ThingSpeak pressure unit", state.cloudPressureUnit, onCloudPressureUnit, state.validationErrors["cloudPressureUnit"], focusers.getValue("cloudPressureUnit"))
                    SecretField(
                        label = "Private channel read key (optional)", value = state.cloudReadKeyDraft, saved = state.hasSavedCloudKey,
                        visible = state.showCloudKey, onChange = { onUpdate("cloudReadKeyDraft", it) },
                        onToggle = { onVisibility("cloud", !state.showCloudKey) }, clearRequested = state.clearSavedCloudKey,
                        onClearSaved = { onClearSecret("cloud") }, password = true,
                    )
                    Text("Only the channel read API is used. There is no write-key field.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onTestCloud, enabled = state.activeSource == WeatherSource.THINGSPEAK && state.savedActiveSource == WeatherSource.THINGSPEAK && state.savedSourceReady && !state.cloudTesting && !state.mqttTesting && !state.saving) {
                        Text(if (state.cloudTesting) "Testing ThingSpeak…" else "Test cloud history")
                    }
                    state.cloudTestMessage?.let { TestResult(it) }
                }
                }
                SettingsSection("Forecast") {
                    Text("Read a separate ThingSpeak channel for forecasts. This channel is independent of the dashboard source.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = state.forecastChannelId, onValueChange = { onUpdate("forecastChannelId", it) },
                        label = { Text("Forecast channel ID (optional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusers.getValue("forecastChannelId")),
                        isError = state.validationErrors.containsKey("forecastChannelId"),
                        supportingText = state.validationErrors["forecastChannelId"]?.let { text -> { Text(text) } },
                    )
                    SecretField(
                        label = "Forecast channel read key (optional)", value = state.forecastReadKeyDraft, saved = state.hasSavedForecastKey,
                        visible = state.showForecastKey, onChange = { onUpdate("forecastReadKeyDraft", it) },
                        onToggle = { onVisibility("forecast", !state.showForecastKey) }, clearRequested = state.clearSavedForecastKey,
                        onClearSaved = { onClearSecret("forecast") }, password = true,
                    )
                    Text("Leave the channel blank to disable forecasts. A blank key keeps the saved key.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (state.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text("Discard connection changes?") },
            text = { Text("Your unsaved settings will be discarded.") },
            confirmButton = { TextButton(onClick = onDiscard) { Text("Discard") } },
            dismissButton = { TextButton(onClick = onKeepEditing) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun ErrorMessageCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun TestResult(text: String) {
    Text(
        text,
        color = if (text.startsWith("Could")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun PressureUnitChoices(
    label: String,
    selected: PressureUnit?,
    onSelect: (PressureUnit) -> Unit,
    error: String?,
    focusRequester: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            Modifier.fillMaxWidth().focusRequester(focusRequester).focusable().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PressureUnit.entries.forEach { unit ->
                FilterChip(selected = selected == unit, onClick = { onSelect(unit) }, label = { Text(unit.label()) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    saved: Boolean,
    visible: Boolean,
    onChange: (String) -> Unit,
    onToggle: () -> Unit,
    clearRequested: Boolean,
    onClearSaved: () -> Unit,
    password: Boolean,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "Hide $label" else "Show $label",
                    )
                }
            },
            supportingText = if (saved && value.isBlank() && !clearRequested) ({ Text("Saved securely; leave blank to keep the saved value.") }) else null,
        )
        if (saved || clearRequested) TextButton(onClick = onClearSaved) {
            Text(if (clearRequested) "Keep saved value" else "Clear saved value")
        }
    }
}

private fun PressureUnit.label(): String = when (this) {
    PressureUnit.PA -> "Pa"
    PressureUnit.HPA -> "hPa"
    PressureUnit.MMHG -> "mmHg"
}

private fun connectionPreviewState() = ConnectionUiState(
    loading = false,
    brokerHost = "",
    brokerPort = "1883",
    useTls = false,
    topic = "",
    cloudChannelId = "abc",
    validationErrors = mapOf(
        "brokerHost" to "Enter the broker host.",
        "brokerPort" to "Enter a valid port from 1 to 65535.",
        "topic" to "Enter an MQTT topic.",
        "pressureUnit" to "Choose the source pressure unit.",
        "cloudChannelId" to "Enter a numeric channel ID.",
        "cloudPressureUnit" to "Choose the ThingSpeak pressure unit.",
    ),
    generalError = "Fix the highlighted fields before saving.",
)

@Preview(name = "Settings · validation errors · light · compact", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun SettingsValidationCompactPreview() {
    WeatherTheme(darkTheme = false, dynamicColor = false) {
        ConnectionScreen(connectionPreviewState(), {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Settings · validation errors · dark · expanded", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 840, heightDp = 900)
@Composable
private fun SettingsValidationExpandedPreview() {
    WeatherTheme(darkTheme = true, dynamicColor = false) {
        ConnectionScreen(connectionPreviewState(), {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Settings · 200% font", showBackground = true, fontScale = 2f, widthDp = 360, heightDp = 900)
@Composable
private fun SettingsLargeFontPreview() {
    WeatherTheme(darkTheme = false, dynamicColor = false) {
        ConnectionScreen(connectionPreviewState(), {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
    }
}
