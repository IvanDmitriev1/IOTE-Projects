package com.example.remoteweatherstation.core.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.remoteweatherstation.core.data.cloud.ForegroundCloudRefreshOwner
import com.example.remoteweatherstation.core.data.config.StationConfigStore
import com.example.remoteweatherstation.core.data.mqtt.ForegroundTelemetryOwner
import com.example.remoteweatherstation.core.model.StationConfig
import com.example.remoteweatherstation.core.model.WeatherSource
import com.example.remoteweatherstation.core.model.ConfigRevision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundWeatherSourceOwner @Inject constructor(
    private val configStore: StationConfigStore,
    private val telemetryOwner: ForegroundTelemetryOwner,
    private val cloudOwner: ForegroundCloudRefreshOwner,
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var config: StationConfig? = null
    @Volatile private var foreground = false
    private var started = false
    private val _activeRevision = MutableStateFlow<ConfigRevision?>(null)
    val activeRevision = _activeRevision.asStateFlow()
    private val testGate = Mutex()
    private var connectionTest: Job? = null

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            launch {
                configStore.config.collect {
                    config = it
                    changes.trySend(Unit)
                }
            }
            for (ignored in changes) reconcile()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        changes.trySend(Unit)
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        changes.trySend(Unit)
    }

    private suspend fun reconcile() {
        val test = testGate.withLock {
            _activeRevision.value = null
            connectionTest.also { connectionTest = null }
        }
        test?.cancelAndJoin()
        telemetryOwner.stop()
        cloudOwner.stop()
        val current = config ?: return
        if (!foreground) return
        when (current.activeSource) {
            WeatherSource.MQTT -> if (current.isMqttConfigured) {
                telemetryOwner.start(current)
                _activeRevision.value = ConfigRevision(current.partitionId, current.generation)
            }
            WeatherSource.THINGSPEAK -> if (current.isCloudConfigured) {
                cloudOwner.start(current)
                _activeRevision.value = ConfigRevision(current.partitionId, current.generation)
            }
            null -> Unit
        }
    }

    fun isActive(config: StationConfig): Boolean = foreground &&
        _activeRevision.value == ConfigRevision(config.partitionId, config.generation)

    suspend fun <T : Any> testConnection(config: StationConfig, test: suspend () -> T): T? = supervisorScope {
        val request = testGate.withLock {
            if (!isActive(config) || connectionTest?.isCompleted == false) return@supervisorScope null
            async { test() }.also { connectionTest = it }
        }
        request.await()
    }
}
