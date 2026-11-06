package com.example.remoteweatherstation.core.data

import com.example.remoteweatherstation.core.model.ConfigRevision
import com.example.remoteweatherstation.core.model.Metric
import com.example.remoteweatherstation.core.model.MetricValues
import com.example.remoteweatherstation.core.model.WeatherSource
import com.example.remoteweatherstation.core.model.ParsedTelemetry
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class MqttReceipt(
    val receivedAt: Instant,
    val elapsedMillis: Long,
    val processSessionId: String,
    val retained: Boolean,
    val parsed: ParsedTelemetry,
)

data class CloudEntry(
    val entryId: Long,
    val createdAt: Instant,
    val values: MetricValues,
)

data class DashboardReadings(
    val activeSource: WeatherSource? = null,
    val readings: Map<Metric, MetricReading> = emptyMap(),
    val localLastReceipt: Instant? = null,
    val localConnection: MqttConnectionStatus = MqttConnectionStatus.NOT_CONFIGURED,
    val cloudLastEntry: Instant? = null,
    val cloudError: String? = null,
    val diagnostic: String? = null,
    val configured: Boolean = false,
    val cloudConfigured: Boolean = false,
)

enum class MqttConnectionStatus { NOT_CONFIGURED, CONNECTING, CONNECTED, OFFLINE, ERROR }

interface TelemetryRepository {
    fun observeDashboard(): Flow<DashboardReadings>
    suspend fun recordMqtt(revision: ConfigRevision, receipt: MqttReceipt)
    suspend fun upsertCloud(revision: ConfigRevision, entries: List<CloudEntry>)
    suspend fun updateMqttStatus(revision: ConfigRevision, status: MqttConnectionStatus)
    suspend fun updateMqttDiagnostic(revision: ConfigRevision, diagnostic: String?)
    suspend fun updateCloudError(revision: ConfigRevision, error: String?)
    suspend fun markCloudRefresh(revision: ConfigRevision, at: Instant)
}
