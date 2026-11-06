package com.example.remoteweatherstation.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remoteweatherstation.core.data.TelemetryRepository
import com.example.remoteweatherstation.core.data.cloud.CloudRefreshCoordinator
import com.example.remoteweatherstation.core.data.mqtt.ForegroundTelemetryOwner
import com.example.remoteweatherstation.core.model.WeatherSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: TelemetryRepository,
    private val telemetryOwner: ForegroundTelemetryOwner,
    private val cloudRefresh: CloudRefreshCoordinator,
) : ViewModel() {
    val uiState = repository.observeDashboard()
        .map(DashboardUiState::from)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun retryConnection() {
        when (uiState.value.activeSource) {
            WeatherSource.MQTT -> telemetryOwner.retry()
            WeatherSource.THINGSPEAK -> viewModelScope.launch { cloudRefresh.refreshLatest() }
            null -> Unit
        }
    }
}
