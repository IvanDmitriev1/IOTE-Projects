package com.example.remoteweatherstation.core.data

import com.example.remoteweatherstation.core.data.config.StationConfigStore
import com.example.remoteweatherstation.core.model.ConfigRevision
import com.example.remoteweatherstation.core.model.HistorySource
import com.example.remoteweatherstation.core.model.Metric
import com.example.remoteweatherstation.core.model.StationConfig
import com.example.remoteweatherstation.core.model.WeatherSource
import com.example.remoteweatherstation.core.time.ProcessSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryTelemetryRepository @Inject constructor(
    private val configStore: StationConfigStore,
    private val freshness: FreshnessPolicy,
    processSession: ProcessSession,
) : TelemetryRepository {
    private val processSessionId = processSession.id
    private val snapshot = MutableStateFlow(TelemetrySnapshot())
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            configStore.config.distinctUntilChanged().collect {
                mutex.withLock { activateRevision(configStore.config.first()?.revision()) }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeDashboard(): Flow<DashboardReadings> = configStore.config
        .distinctUntilChanged()
        .flatMapLatest { config ->
            if (config == null) return@flatMapLatest flowOf(DashboardReadings())
            val revision = config.revision()
            val clockTicks = flow {
                while (true) {
                    emit(Unit)
                    delay(5_000)
                }
            }
            combine(snapshot, clockTicks) { stored, _ ->
                dashboard(config, stored.takeIf { it.revision == revision } ?: TelemetrySnapshot(revision))
            }.distinctUntilChanged()
        }

    override suspend fun recordMqtt(revision: ConfigRevision, receipt: MqttReceipt) {
        if (receipt.parsed.values.isEmpty()) return
        updateForRevision(revision) { current ->
            val local = current.localReadings.toMutableMap()
            Metric.entries.forEach { metric ->
                receipt.parsed.values[metric]?.let { value ->
                    local[metric] = LocalMetricValue(
                        value = value,
                        observedAt = receipt.receivedAt,
                        elapsedMillis = receipt.elapsedMillis,
                        processSessionId = receipt.processSessionId,
                        retained = receipt.retained,
                    )
                }
            }
            current.copy(
                localReadings = local,
                localLastReceipt = receipt.receivedAt,
                diagnostic = receipt.parsed.diagnostic,
            )
        }
    }

    override suspend fun upsertCloud(revision: ConfigRevision, entries: List<CloudEntry>) {
        if (entries.isEmpty()) return
        if (configStore.config.first()?.isCloudConfigured != true) return
        updateForRevision(revision) { current ->
            val cloud = current.cloudReadings.toMutableMap()
            entries.forEach { entry ->
                Metric.entries.forEach metricLoop@{ metric ->
                    val value = entry.values[metric] ?: return@metricLoop
                    val previous = cloud[metric]
                    if (previous == null || entry.createdAt > previous.observedAt ||
                        (entry.createdAt == previous.observedAt && entry.entryId > previous.entryId)
                    ) {
                        cloud[metric] = CloudMetricValue(value, entry.createdAt, entry.entryId)
                    }
                }
            }
            val latestEntry = entries.maxWithOrNull(compareBy<CloudEntry>({ it.createdAt }, { it.entryId }))?.createdAt
            current.copy(
                cloudReadings = cloud,
                cloudLastEntry = listOfNotNull(current.cloudLastEntry, latestEntry).maxOrNull(),
            )
        }
    }

    override suspend fun updateMqttStatus(revision: ConfigRevision, status: MqttConnectionStatus) {
        updateForRevision(revision) { it.copy(mqttStatus = status) }
    }

    override suspend fun updateMqttDiagnostic(revision: ConfigRevision, diagnostic: String?) {
        updateForRevision(revision) { it.copy(diagnostic = diagnostic) }
    }

    override suspend fun updateCloudError(revision: ConfigRevision, error: String?) {
        updateForRevision(revision) { it.copy(cloudError = error) }
    }

    override suspend fun markCloudRefresh(revision: ConfigRevision, at: Instant) {
        updateForRevision(revision) { it.copy(cloudError = null, cloudRefreshedAt = at) }
    }

    private suspend fun updateForRevision(revision: ConfigRevision, update: (TelemetrySnapshot) -> TelemetrySnapshot) {
        mutex.withLock {
            if (configStore.config.first()?.revision() != revision) return@withLock
            activateRevision(revision)
            snapshot.value = update(snapshot.value)
        }
    }

    private fun activateRevision(revision: ConfigRevision?) {
        if (snapshot.value.revision != revision) snapshot.value = TelemetrySnapshot(revision = revision)
    }

    private fun dashboard(config: StationConfig, stored: TelemetrySnapshot): DashboardReadings {
        val readings = Metric.entries.mapNotNull { metric ->
            val local = stored.localReadings[metric]?.let { value ->
                MetricReading(
                    value.value,
                    HistorySource.LOCAL,
                    value.observedAt,
                    freshness.local(value.retained, value.processSessionId, processSessionId, value.elapsedMillis),
                    value.retained,
                )
            }
            val cloud = stored.cloudReadings[metric]?.let { value ->
                MetricReading(value.value, HistorySource.CLOUD, value.observedAt, freshness.cloud(value.observedAt))
            }
            val picked = when (config.activeSource) {
                WeatherSource.MQTT -> local
                WeatherSource.THINGSPEAK -> cloud
                null -> null
            }
            picked?.let { metric to it }
        }.toMap()
        return DashboardReadings(
            activeSource = config.activeSource,
            readings = readings,
            localLastReceipt = stored.localLastReceipt.takeIf { config.activeSource == WeatherSource.MQTT },
            localConnection = if (config.activeSource == WeatherSource.MQTT && config.isMqttConfigured) stored.mqttStatus else MqttConnectionStatus.NOT_CONFIGURED,
            cloudLastEntry = (stored.cloudLastEntry ?: stored.cloudRefreshedAt).takeIf { config.activeSource == WeatherSource.THINGSPEAK },
            cloudError = stored.cloudError.takeIf { config.activeSource == WeatherSource.THINGSPEAK },
            diagnostic = stored.diagnostic.takeIf { config.activeSource == WeatherSource.MQTT },
            configured = when (config.activeSource) {
                WeatherSource.MQTT -> config.isMqttConfigured
                WeatherSource.THINGSPEAK -> config.isCloudConfigured
                null -> false
            },
            cloudConfigured = config.activeSource == WeatherSource.THINGSPEAK && config.isCloudConfigured,
        )
    }

    private fun StationConfig.revision() = ConfigRevision(partitionId, generation)

    private data class LocalMetricValue(
        val value: Double,
        val observedAt: Instant,
        val elapsedMillis: Long,
        val processSessionId: String,
        val retained: Boolean,
    )

    private data class CloudMetricValue(val value: Double, val observedAt: Instant, val entryId: Long)

    private data class TelemetrySnapshot(
        val revision: ConfigRevision? = null,
        val mqttStatus: MqttConnectionStatus = MqttConnectionStatus.NOT_CONFIGURED,
        val localReadings: Map<Metric, LocalMetricValue> = emptyMap(),
        val cloudReadings: Map<Metric, CloudMetricValue> = emptyMap(),
        val localLastReceipt: Instant? = null,
        val diagnostic: String? = null,
        val cloudLastEntry: Instant? = null,
        val cloudError: String? = null,
        val cloudRefreshedAt: Instant? = null,
    )
}
