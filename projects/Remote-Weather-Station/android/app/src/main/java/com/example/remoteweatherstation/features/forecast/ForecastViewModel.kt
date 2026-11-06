package com.example.remoteweatherstation.features.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remoteweatherstation.core.data.cloud.CloudReadFailure
import com.example.remoteweatherstation.core.data.cloud.ForecastFetchResult
import com.example.remoteweatherstation.core.data.cloud.ThingSpeakReader
import com.example.remoteweatherstation.core.data.config.StationConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val configStore: StationConfigStore,
    private val reader: ThingSpeakReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForecastUiState())
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()
    private val settings = MutableStateFlow<ForecastSettings?>(null)
    private val refreshLock = Mutex()
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            configStore.config.collect { config ->
                val next = ForecastSettings(config?.forecastChannelId.orEmpty().trim(), configStore.loadSecrets().forecastReadKey)
                if (settings.value != next) {
                    settings.value = next
                    _uiState.value = ForecastUiState(
                        configurationLoaded = true,
                        configured = next.channelId.isNotBlank() && next.channelId.all(Char::isDigit),
                    )
                }
            }
        }
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            settings.filterNotNull().first()
            while (isActive) {
                refresh(waitForLock = true)
                delay(RETRY_INTERVAL_MILLIS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh(waitForLock: Boolean = false) {
        if (waitForLock) refreshLock.lock() else if (!refreshLock.tryLock()) return
        var request: ForecastSettings? = null
        var ownsRefreshing = false
        try {
            val currentRequest = settings.value ?: return
            request = currentRequest
            if (currentRequest.channelId.isBlank() || !currentRequest.channelId.all(Char::isDigit)) {
                _uiState.value = ForecastUiState(configurationLoaded = true)
                return
            }
            _uiState.value = _uiState.value.copy(configured = true, refreshing = true, error = null)
            ownsRefreshing = true
            val result = try {
                reader.readForecast(currentRequest.channelId, currentRequest.readKey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ForecastFetchResult(failure = CloudReadFailure.NETWORK)
            }
            if (settings.value != currentRequest) return
            _uiState.value = when {
                result.failure != null -> _uiState.value.copy(refreshing = false, error = result.failure.message())
                result.reading != null -> ForecastUiState(configurationLoaded = true, configured = true, reading = result.reading)
                else -> ForecastUiState(configurationLoaded = true, configured = true, empty = true)
            }
        } finally {
            if (ownsRefreshing && settings.value == request && _uiState.value.refreshing) {
                _uiState.value = _uiState.value.copy(refreshing = false)
            }
            refreshLock.unlock()
        }
    }

    private fun CloudReadFailure.message(): String = when (this) {
        CloudReadFailure.NOT_CONFIGURED -> "Enter a valid forecast channel ID in Settings."
        CloudReadFailure.NETWORK -> "Could not reach ThingSpeak. Check your connection and retry."
        CloudReadFailure.HTTP -> "ThingSpeak returned an error. Check the forecast channel ID and read key."
        CloudReadFailure.INVALID_RESPONSE -> "ThingSpeak returned unreadable forecast data."
        CloudReadFailure.RESPONSE_TOO_LARGE -> "ThingSpeak returned a response that was too large."
    }

    private data class ForecastSettings(val channelId: String, val readKey: String)

    private companion object { const val RETRY_INTERVAL_MILLIS = 60_000L }
}
