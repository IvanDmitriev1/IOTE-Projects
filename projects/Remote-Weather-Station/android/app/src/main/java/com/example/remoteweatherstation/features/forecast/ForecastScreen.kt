package com.example.remoteweatherstation.features.forecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.remoteweatherstation.core.data.cloud.ForecastReading
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(state: ForecastUiState, onRefresh: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Forecast") }) }) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                !state.configurationLoaded -> MessageCard("Loading forecast settings…", showProgress = true)
                !state.configured -> {
                    MessageCard("Set a forecast channel in Settings to read the latest station forecast.")
                    Button(onClick = onOpenSettings) { Text("Open settings") }
                }
                state.reading != null -> {
                    ForecastCard(state.reading, state.refreshing, onRefresh)
                    state.error?.let { ErrorCard(it) }
                }
                state.refreshing -> MessageCard("Loading the latest forecast…", showProgress = true)
                state.error != null -> {
                    ErrorCard(state.error)
                    Button(onClick = onRefresh) { Text("Retry") }
                }
                state.empty -> {
                    MessageCard("No forecast has been published yet.")
                    Button(onClick = onRefresh) { Text("Refresh") }
                }
            }
        }
    }
}

@Composable
private fun ForecastCard(reading: ForecastReading, refreshing: Boolean, onRefresh: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Station forecast", style = MaterialTheme.typography.titleMedium)
            Text(reading.text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(
                "Station reading · ${reading.createdAt.atZone(ZoneId.systemDefault()).format(timestampFormatter())}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "Refreshing…" else "Refresh")
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, showProgress: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showProgress) CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

private fun timestampFormatter() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
