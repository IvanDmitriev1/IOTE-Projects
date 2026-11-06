package com.example.remoteweatherstation.core.data.cloud

import com.example.remoteweatherstation.core.data.TelemetryRepository
import com.example.remoteweatherstation.core.data.config.StationConfigStore
import com.example.remoteweatherstation.core.model.ConfigRevision
import com.example.remoteweatherstation.core.model.StationConfig
import com.example.remoteweatherstation.core.model.WeatherSource
import com.example.remoteweatherstation.core.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class RefreshResult(val entryCount: Int, val failure: CloudReadFailure? = null) {
    val succeeded: Boolean get() = failure == null
}

@Singleton
class CloudRefreshCoordinator @Inject constructor(
    private val configStore: StationConfigStore,
    private val reader: ThingSpeakReader,
    private val repository: TelemetryRepository,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inFlight: Deferred<RefreshResult>? = null
    private val gate = Mutex()
    @Volatile private var activeRevision: ConfigRevision? = null

    fun activate(config: StationConfig) {
        activeRevision = if (config.activeSource == WeatherSource.THINGSPEAK && config.isCloudConfigured) {
            ConfigRevision(config.partitionId, config.generation)
        } else null
    }

    suspend fun deactivateAndCancel() {
        val request = gate.withLock {
            activeRevision = null
            inFlight.also { inFlight = null }
        }
        request?.cancelAndJoin()
    }

    suspend fun refreshLatest(): RefreshResult = refresh { config, key ->
        reader.readTail(config.toThingSpeakConfig(), key)
    }

    suspend fun test(config: com.example.remoteweatherstation.core.model.StationConfig, readKey: String): RefreshResult {
        val saved = configStore.config.first()
        if (saved?.activeSource != WeatherSource.THINGSPEAK || activeRevision != ConfigRevision(saved.partitionId, saved.generation)) {
            return RefreshResult(0, CloudReadFailure.NOT_CONFIGURED)
        }
        val result = reader.readTail(config.toThingSpeakConfig(), readKey)
        return RefreshResult(result.entries.size, result.failure)
    }

    private suspend fun refresh(
        fetch: suspend (com.example.remoteweatherstation.core.model.StationConfig, String) -> CloudFetchResult,
    ): RefreshResult {
        val config = configStore.config.first()?.takeIf {
            it.activeSource == WeatherSource.THINGSPEAK && it.isCloudConfigured &&
                activeRevision == ConfigRevision(it.partitionId, it.generation)
        }
            ?: return RefreshResult(0, CloudReadFailure.NOT_CONFIGURED)
        val revision = ConfigRevision(config.partitionId, config.generation)
        val deferred = gate.withLock {
            if (activeRevision != revision) return RefreshResult(0, CloudReadFailure.NOT_CONFIGURED)
            inFlight?.takeUnless { it.isCompleted } ?: scope.async {
                try {
                    val result = fetch(config, configStore.loadSecrets().cloudReadKey)
                    if (result.entries.isNotEmpty()) repository.upsertCloud(revision, result.entries)
                    if (result.failure == null) repository.markCloudRefresh(revision, clock.now())
                    else repository.updateCloudError(revision, result.failure.toUiMessage())
                    RefreshResult(result.entries.size, result.failure)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    repository.updateCloudError(revision, CloudReadFailure.NETWORK.toUiMessage())
                    RefreshResult(0, CloudReadFailure.NETWORK)
                }
            }.also { inFlight = it }
        }
        return deferred.await()
    }

    private fun com.example.remoteweatherstation.core.model.StationConfig.toThingSpeakConfig() =
        ThingSpeakConfig(cloudChannelId, cloudPressureUnit)

    private fun CloudReadFailure.toUiMessage(): String = when (this) {
        CloudReadFailure.NOT_CONFIGURED -> "ThingSpeak is not configured"
        CloudReadFailure.NETWORK -> "Could not reach ThingSpeak"
        CloudReadFailure.HTTP -> "ThingSpeak returned an error"
        CloudReadFailure.INVALID_RESPONSE -> "ThingSpeak returned unreadable data"
        CloudReadFailure.RESPONSE_TOO_LARGE -> "ThingSpeak response exceeded the safe size limit"
    }
}
