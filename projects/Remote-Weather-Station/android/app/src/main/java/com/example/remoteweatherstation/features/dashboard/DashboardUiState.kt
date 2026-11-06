package com.example.remoteweatherstation.features.dashboard

import com.example.remoteweatherstation.core.data.DashboardReadings
import com.example.remoteweatherstation.core.data.MetricReading
import com.example.remoteweatherstation.core.data.MqttConnectionStatus
import com.example.remoteweatherstation.core.model.Metric
import com.example.remoteweatherstation.core.model.WeatherSource

data class DashboardUiState(
    val loading: Boolean = true,
    val activeSource: WeatherSource? = null,
    val readings: Map<Metric, MetricReading> = emptyMap(),
    val mqttStatus: MqttConnectionStatus = MqttConnectionStatus.NOT_CONFIGURED,
    val mqttLastReceiptMillis: Long? = null,
    val cloudLastEntryMillis: Long? = null,
    val cloudError: String? = null,
    val diagnostic: String? = null,
    val configured: Boolean = false,
    val cloudConfigured: Boolean = false,
) {
    companion object {
        fun from(readings: DashboardReadings) = DashboardUiState(
            loading = false,
            activeSource = readings.activeSource,
            readings = readings.readings,
            mqttStatus = readings.localConnection,
            mqttLastReceiptMillis = readings.localLastReceipt?.toEpochMilli(),
            cloudLastEntryMillis = readings.cloudLastEntry?.toEpochMilli(),
            cloudError = readings.cloudError,
            diagnostic = readings.diagnostic,
            configured = readings.configured,
            cloudConfigured = readings.cloudConfigured,
        )
    }
}
