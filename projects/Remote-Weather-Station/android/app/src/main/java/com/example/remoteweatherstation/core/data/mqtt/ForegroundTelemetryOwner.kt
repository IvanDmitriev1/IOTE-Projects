package com.example.remoteweatherstation.core.data.mqtt

import com.example.remoteweatherstation.core.data.MqttConnectionStatus
import com.example.remoteweatherstation.core.data.MqttReceipt
import com.example.remoteweatherstation.core.data.TelemetryRepository
import com.example.remoteweatherstation.core.data.config.StationConfigStore
import com.example.remoteweatherstation.core.model.ConfigRevision
import com.example.remoteweatherstation.core.time.ProcessSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ThreadLocalRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundTelemetryOwner @Inject constructor(
    private val configStore: StationConfigStore,
    private val repository: TelemetryRepository,
    private val transportFactory: MqttTransportFactory,
    private val parser: LegacyTelemetryParser,
    private val processSession: ProcessSession,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val control = Mutex()
    @Volatile private var foreground = false
    private var connectionJob: Job? = null
    @Volatile private var activeTransport: MqttTransport? = null
    private var currentRevision: ConfigRevision? = null

    suspend fun start(config: com.example.remoteweatherstation.core.model.StationConfig) = control.withLock {
        if (config.activeSource != com.example.remoteweatherstation.core.model.WeatherSource.MQTT || !config.isMqttConfigured) return@withLock
        startLocked(config)
    }

    private fun startLocked(config: com.example.remoteweatherstation.core.model.StationConfig) {
        foreground = true
        currentRevision = ConfigRevision(config.partitionId, config.generation)
        currentRevision?.let { revision -> scope.launch { repository.updateMqttDiagnostic(revision, null) } }
        connect(config)
    }

    suspend fun stop() = control.withLock { stopLocked() }

    private suspend fun stopLocked() {
        val revision = currentRevision
        foreground = false
        currentRevision = null
        connectionJob?.cancelAndJoin()
        connectionJob = null
        activeTransport?.disconnect()
        activeTransport = null
        revision?.let { repository.updateMqttStatus(it, MqttConnectionStatus.OFFLINE) }
    }

    fun retry() {
        scope.launch {
            control.withLock {
                if (foreground) configStore.config.first()
                    ?.takeIf { it.activeSource == com.example.remoteweatherstation.core.model.WeatherSource.MQTT && it.isMqttConfigured }
                    ?.let { config -> stopLocked(); startLocked(config) }
            }
        }
    }

    private fun connect(config: com.example.remoteweatherstation.core.model.StationConfig) {
        if (!foreground || !config.isMqttConfigured) return
        val revision = ConfigRevision(config.partitionId, config.generation)
        currentRevision = revision
        connectionJob = scope.launch {
            if (configStore.config.first()?.let { it.activeSource == com.example.remoteweatherstation.core.model.WeatherSource.MQTT && ConfigRevision(it.partitionId, it.generation) == revision } != true) return@launch
            var retry = 0
            while (foreground && currentRevision == revision) {
                val transport = transportFactory.create()
                activeTransport = transport
                try {
                    repository.updateMqttStatus(revision, MqttConnectionStatus.CONNECTING)
                    transport.connect(config.toMqttConfig(), configStore.loadSecrets())
                    repository.updateMqttStatus(revision, MqttConnectionStatus.CONNECTED)
                    retry = 0
                    transport.events.collect { event ->
                        if (!foreground || currentRevision != revision) throw CancellationException("Connection revision changed")
                        when (event) {
                            is MqttEvent.Message -> {
                                val parsed = parser.parse(event.payload, config.pressureUnit)
                                repository.updateMqttDiagnostic(revision, parsed.diagnostic)
                                repository.recordMqtt(
                                    revision,
                                    MqttReceipt(event.receivedAt, event.elapsedMillis, processSession.id, event.retained, parsed),
                                )
                            }
                            MqttEvent.Disconnected -> throw IllegalStateException("MQTT disconnected")
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: MqttAuthenticationException) {
                    repository.updateMqttStatus(revision, MqttConnectionStatus.ERROR)
                    break
                } catch (_: Exception) {
                    repository.updateMqttStatus(revision, MqttConnectionStatus.ERROR)
                } finally {
                    withContext(NonCancellable) { transport.disconnect() }
                    if (activeTransport === transport) activeTransport = null
                }
                if (!foreground || currentRevision != revision) break
                val base = (1L shl retry.coerceAtMost(6)).coerceAtMost(MAX_RETRY_SECONDS)
                val jitter = ThreadLocalRandom.current().nextLong(0, (base / 3).coerceAtLeast(1))
                delay((base + jitter).coerceAtMost(MAX_RETRY_SECONDS) * 1000)
                retry++
            }
        }
    }

    companion object {
        private const val MAX_RETRY_SECONDS = 60L
    }
}
