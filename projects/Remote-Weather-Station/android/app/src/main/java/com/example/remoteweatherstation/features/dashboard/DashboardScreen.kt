package com.example.remoteweatherstation.features.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.remoteweatherstation.core.data.MetricReading
import com.example.remoteweatherstation.core.data.MqttConnectionStatus
import com.example.remoteweatherstation.core.data.ReadingStatus
import com.example.remoteweatherstation.core.model.HistorySource
import com.example.remoteweatherstation.core.model.Metric
import com.example.remoteweatherstation.core.model.WeatherSource
import com.example.remoteweatherstation.ui.theme.WeatherTheme
import java.text.NumberFormat
import java.time.Instant

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DashboardScreen(
    state: DashboardUiState,
    onSetupClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    Scaffold(
        modifier = modifier,
        topBar = if (showTopBar) ({ TopAppBar(title = { Text("Remote Weather Station") }) }) else ({ }),
    ) { insets ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 230.dp),
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ConnectionStatusPanel(
                    state = state,
                    onSetupClick = onSetupClick,
                    onRetryClick = onRetryClick,
                )
            }
            items(Metric.entries) { metric ->
                MetricCard(metric, state.readings[metric])
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            "Latest readings stay available while the app process runs.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusPanel(
    state: DashboardUiState,
    onSetupClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Connection status", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            StatusRow(
                icon = connectionIcon(state),
                label = connectionLabel(state),
                isError = state.mqttStatus == MqttConnectionStatus.ERROR || state.mqttStatus == MqttConnectionStatus.OFFLINE || state.cloudError != null,
            )
            if (state.cloudError != null) {
                Text(state.cloudError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.diagnostic?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.activeSource == null) {
                Text("Choose a weather source to display readings.")
                Button(onClick = onSetupClick, modifier = Modifier.padding(top = 2.dp)) { Text("Select weather source") }
            } else if (!state.configured) {
                Text("Set up the selected weather source in connection settings.")
                Button(onClick = onSetupClick, modifier = Modifier.padding(top = 2.dp)) { Text("Open connection settings") }
            } else if (state.mqttStatus == MqttConnectionStatus.ERROR || state.mqttStatus == MqttConnectionStatus.OFFLINE || state.cloudError != null) {
                TextButton(onClick = onRetryClick) { Text("Retry connection") }
            }
        }
    }
}

@Composable
private fun StatusRow(icon: ImageVector, label: String, isError: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetricCard(metric: Metric, reading: MetricReading?) {
    val number = reading?.let {
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(it.value)
    } ?: "Unavailable"
    val source = when (reading?.source) {
        HistorySource.LOCAL -> "On this phone"
        HistorySource.CLOUD -> "ThingSpeak"
        null -> "No reading"
    }
    val freshness = when (reading?.status) {
        ReadingStatus.FRESH -> if (reading.source == HistorySource.LOCAL) "Received ${ageLabel(reading.observedAt)} ago" else "Cloud · ${ageLabel(reading.observedAt)} old"
        ReadingStatus.STALE -> "Stale · last known ${ageLabel(reading.observedAt)} ago"
        ReadingStatus.UNKNOWN -> "Time uncertain · ${reading.observedAt}"
        null -> "Unavailable"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(metric.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(metric.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                buildAnnotatedString {
                    append(number)
                    if (reading != null) {
                        append(" ")
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.labelLarge.fontSize)) { append(metric.unit) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(source, style = MaterialTheme.typography.bodyMedium)
            Text(freshness, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Metric.icon(): ImageVector = when (this) {
    Metric.TEMPERATURE -> Icons.Outlined.DeviceThermostat
    Metric.HUMIDITY -> Icons.Outlined.WaterDrop
    Metric.PRESSURE -> Icons.Outlined.Speed
    Metric.LIGHT -> Icons.Outlined.Lightbulb
}

private fun connectionIcon(state: DashboardUiState): ImageVector = when (state.activeSource) {
    null -> Icons.Outlined.WifiOff
    WeatherSource.MQTT -> mqttStatusIcon(state)
    WeatherSource.THINGSPEAK -> if (state.cloudError != null || !state.cloudConfigured) Icons.Outlined.CloudOff else Icons.Outlined.Cloud
}

private fun connectionLabel(state: DashboardUiState): String = when (state.activeSource) {
    null -> "No weather source selected"
    WeatherSource.MQTT -> mqttStatusLabel(state)
    WeatherSource.THINGSPEAK -> cloudStatusLabel(state)
}

private fun mqttStatusIcon(state: DashboardUiState): ImageVector = when (state.mqttStatus) {
    MqttConnectionStatus.CONNECTED -> Icons.Outlined.Wifi
    MqttConnectionStatus.CONNECTING -> Icons.Outlined.Sync
    MqttConnectionStatus.OFFLINE, MqttConnectionStatus.ERROR, MqttConnectionStatus.NOT_CONFIGURED -> Icons.Outlined.WifiOff
}

private fun mqttStatusLabel(state: DashboardUiState): String = when (state.mqttStatus) {
    MqttConnectionStatus.NOT_CONFIGURED -> "MQTT · not configured"
    MqttConnectionStatus.CONNECTING -> "MQTT · connecting"
    MqttConnectionStatus.CONNECTED -> "MQTT · connected${state.mqttLastReceiptMillis?.let { " · received ${ageLabel(Instant.ofEpochMilli(it))} ago" } ?: " · waiting for the first reading"}"
    MqttConnectionStatus.OFFLINE -> "MQTT · offline${state.mqttLastReceiptMillis?.let { " · last received ${ageLabel(Instant.ofEpochMilli(it))} ago" } ?: ""}"
    MqttConnectionStatus.ERROR -> "MQTT · connection error"
}

private fun cloudStatusLabel(state: DashboardUiState): String = when {
    state.cloudError != null -> "ThingSpeak · read failed; latest readings remain for this session"
    !state.cloudConfigured -> "ThingSpeak · not configured"
    state.cloudLastEntryMillis != null -> "ThingSpeak · latest entry ${ageLabel(Instant.ofEpochMilli(state.cloudLastEntryMillis))} ago"
    else -> "ThingSpeak · waiting for a reading"
}

private fun ageLabel(instant: Instant): String {
    val seconds = ((System.currentTimeMillis() - instant.toEpochMilli()) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        else -> "${seconds / 86_400}d"
    }
}

private fun dashboardPreviewState(configured: Boolean): DashboardUiState {
    val now = Instant.now()
    val readings = if (configured) mapOf(
        Metric.TEMPERATURE to MetricReading(22.4, HistorySource.LOCAL, now.minusSeconds(8), ReadingStatus.FRESH),
        Metric.HUMIDITY to MetricReading(43.0, HistorySource.LOCAL, now.minusSeconds(8), ReadingStatus.FRESH),
        Metric.PRESSURE to MetricReading(1013.2, HistorySource.CLOUD, now.minusSeconds(48), ReadingStatus.STALE),
        Metric.LIGHT to MetricReading(267.0, HistorySource.LOCAL, now.minusSeconds(8), ReadingStatus.FRESH),
    ) else emptyMap()
    return DashboardUiState(
        loading = false,
        readings = readings,
        mqttStatus = if (configured) MqttConnectionStatus.CONNECTED else MqttConnectionStatus.NOT_CONFIGURED,
        mqttLastReceiptMillis = if (configured) now.minusSeconds(8).toEpochMilli() else null,
        cloudLastEntryMillis = if (configured) now.minusSeconds(48).toEpochMilli() else null,
        configured = configured,
        cloudConfigured = configured,
    )
}

@Preview(name = "Dashboard · configured · light · compact", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ConfiguredDashboardCompactPreview() {
    WeatherTheme(darkTheme = false, dynamicColor = false) {
        DashboardScreen(dashboardPreviewState(true), {}, {})
    }
}

@Preview(name = "Dashboard · unconfigured · light", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun UnconfiguredDashboardPreview() {
    WeatherTheme(darkTheme = false, dynamicColor = false) {
        DashboardScreen(dashboardPreviewState(false), {}, {})
    }
}

@Preview(name = "Dashboard · dark · expanded", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 840, heightDp = 800)
@Composable
private fun ExpandedDashboardPreview() {
    WeatherTheme(darkTheme = true, dynamicColor = false) {
        DashboardScreen(dashboardPreviewState(true), {}, {})
    }
}

@Preview(name = "Dashboard · 200% font", showBackground = true, fontScale = 2f, widthDp = 360, heightDp = 800)
@Composable
private fun LargeFontDashboardPreview() {
    WeatherTheme(darkTheme = false, dynamicColor = false) {
        DashboardScreen(dashboardPreviewState(true), {}, {})
    }
}
