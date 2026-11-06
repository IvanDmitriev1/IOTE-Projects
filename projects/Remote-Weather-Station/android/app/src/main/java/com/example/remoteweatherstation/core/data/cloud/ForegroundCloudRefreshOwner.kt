package com.example.remoteweatherstation.core.data.cloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.example.remoteweatherstation.core.model.StationConfig

/** Reads only while the app is foregrounded; no background service or phone daemon is started. */
@Singleton
class ForegroundCloudRefreshOwner @Inject constructor(
    private val coordinator: CloudRefreshCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    fun start(config: StationConfig) {
        coordinator.activate(config)
        refreshJob = scope.launch {
            while (true) {
                coordinator.refreshLatest()
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    suspend fun stop() {
        val job = refreshJob
        refreshJob = null
        job?.cancel()
        coordinator.deactivateAndCancel()
        job?.join()
    }

    private companion object { const val REFRESH_INTERVAL_MILLIS = 60_000L }
}
