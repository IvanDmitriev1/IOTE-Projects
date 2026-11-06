package com.example.remoteweatherstation.features.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.remoteweatherstation.core.data.cloud.CloudReadFailure
import com.example.remoteweatherstation.core.data.cloud.CloudRefreshCoordinator
import com.example.remoteweatherstation.core.data.ForegroundWeatherSourceOwner
import com.example.remoteweatherstation.core.data.config.SaveResult
import com.example.remoteweatherstation.core.data.config.StationConfigStore
import com.example.remoteweatherstation.core.data.mqtt.MqttTransportFactory
import com.example.remoteweatherstation.core.data.mqtt.toMqttConfig
import com.example.remoteweatherstation.core.model.PressureUnit
import com.example.remoteweatherstation.core.model.StationConfig
import com.example.remoteweatherstation.core.model.StationSecrets
import com.example.remoteweatherstation.core.model.WeatherSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val configStore: StationConfigStore,
    private val sourceOwner: ForegroundWeatherSourceOwner,
    private val mqttFactory: MqttTransportFactory,
    private val cloudRefresh: CloudRefreshCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    private var savedConfig = StationConfig()
    private var savedSecrets = StationSecrets()
    private var loaded = false

    init {
        viewModelScope.launch {
            sourceOwner.activeRevision.collect { revision ->
                _uiState.value = _uiState.value.copy(
                    savedSourceReady = loaded && savedConfig.let { revision == com.example.remoteweatherstation.core.model.ConfigRevision(it.partitionId, it.generation) },
                )
            }
        }
        viewModelScope.launch {
            val config = configStore.config.first()
            savedConfig = config ?: StationConfig()
            savedSecrets = configStore.loadSecrets()
            _uiState.value = restoreDraft(savedConfig.toUiState(savedSecrets))
            loaded = true
        }
    }

    fun update(field: String, value: String) = change { state ->
        when (field) {
            "brokerHost" -> state.copy(brokerHost = value)
            "brokerPort" -> state.copy(brokerPort = value)
            "topic" -> state.copy(topic = value)
            "cloudChannelId" -> state.copy(cloudChannelId = value)
            "forecastChannelId" -> state.copy(forecastChannelId = value)
            "mqttUsernameDraft" -> state.copy(mqttUsernameDraft = value, clearSavedUsername = false)
            "mqttPasswordDraft" -> state.copy(mqttPasswordDraft = value, clearSavedPassword = false)
            "cloudReadKeyDraft" -> state.copy(cloudReadKeyDraft = value, clearSavedCloudKey = false)
            "forecastReadKeyDraft" -> state.copy(forecastReadKeyDraft = value, clearSavedForecastKey = false)
            else -> state
        }
    }

    fun setTls(value: Boolean) = change { it.copy(useTls = value) }
    fun setActiveSource(value: WeatherSource) {
        if (_uiState.value.activeSource == value || _uiState.value.mqttTesting || _uiState.value.cloudTesting || _uiState.value.saving) return
        change { it.copy(activeSource = value, validationErrors = emptyMap()) }
    }
    fun setPressureUnit(value: PressureUnit) = change { it.copy(pressureUnit = value, validationErrors = it.validationErrors - "pressureUnit") }
    fun setCloudPressureUnit(value: PressureUnit) = change { it.copy(cloudPressureUnit = value, validationErrors = it.validationErrors - "cloudPressureUnit") }
    fun setVisibility(key: String, value: Boolean) {
        _uiState.value = when (key) {
            "username" -> _uiState.value.copy(showUsername = value)
            "password" -> _uiState.value.copy(showPassword = value)
            "forecast" -> _uiState.value.copy(showForecastKey = value)
            else -> _uiState.value.copy(showCloudKey = value)
        }
    }
    fun clearSecret(key: String) = change { state -> when (key) {
        "username" -> state.copy(clearSavedUsername = !state.clearSavedUsername, mqttUsernameDraft = "")
        "password" -> state.copy(clearSavedPassword = !state.clearSavedPassword, mqttPasswordDraft = "")
        "forecast" -> state.copy(clearSavedForecastKey = !state.clearSavedForecastKey, forecastReadKeyDraft = "")
        else -> state.copy(clearSavedCloudKey = !state.clearSavedCloudKey, cloudReadKeyDraft = "")
    } }

    fun testMqtt() {
        val snapshot = _uiState.value
        if (snapshot.activeSource != WeatherSource.MQTT || savedConfig.activeSource != WeatherSource.MQTT || !sourceOwner.isActive(savedConfig) || snapshot.saving || snapshot.cloudTesting || snapshot.mqttTesting) return
        val errors = ConnectionValidator.validateMqtt(snapshot.toInput())
        if (errors.keys.any { it in setOf("brokerHost", "brokerPort", "topic", "pressureUnit") }) {
            _uiState.value = snapshot.copy(validationErrors = errors, mqttTestMessage = null)
            return
        }
        _uiState.value = snapshot.copy(mqttTesting = true, mqttTestMessage = null, validationErrors = emptyMap())
        viewModelScope.launch {
            val config = snapshot.toConfig()
            val secrets = resolveSecrets(snapshot)
            try {
                sourceOwner.testConnection(savedConfig) {
                    val transport = mqttFactory.create()
                    try {
                        withContext(Dispatchers.IO) { transport.connect(config.toMqttConfig(), secrets) }
                    } finally {
                        withContext(NonCancellable + Dispatchers.IO) { transport.disconnect() }
                    }
                } ?: return@launch
                _uiState.value = _uiState.value.copy(mqttTestMessage = "MQTT connection and topic subscription succeeded.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(mqttTestMessage = "Could not connect to this MQTT broker or subscribe to the topic.")
            } finally {
                _uiState.value = _uiState.value.copy(mqttTesting = false)
            }
        }
    }

    fun testCloud() {
        val snapshot = _uiState.value
        if (snapshot.activeSource != WeatherSource.THINGSPEAK || savedConfig.activeSource != WeatherSource.THINGSPEAK || !sourceOwner.isActive(savedConfig) || snapshot.saving || snapshot.mqttTesting || snapshot.cloudTesting) return
        val errors = ConnectionValidator.validateCloud(snapshot.toInput(), required = true)
        if (errors.keys.any { it in setOf("cloudChannelId", "cloudPressureUnit") }) {
            _uiState.value = snapshot.copy(validationErrors = errors, cloudTestMessage = null)
            return
        }
        _uiState.value = snapshot.copy(cloudTesting = true, cloudTestMessage = null, validationErrors = emptyMap())
        viewModelScope.launch {
            try {
                val result = sourceOwner.testConnection(savedConfig) {
                    cloudRefresh.test(snapshot.toConfig(), resolveSecrets(snapshot).cloudReadKey)
                } ?: return@launch
                _uiState.value = _uiState.value.copy(cloudTestMessage = when (result.failure) {
                    null -> "ThingSpeak read succeeded; ${result.entryCount} existing entries returned."
                    CloudReadFailure.NOT_CONFIGURED -> "Enter a channel ID to test ThingSpeak history."
                    CloudReadFailure.NETWORK -> "Could not reach ThingSpeak."
                    CloudReadFailure.HTTP -> "ThingSpeak returned an error. Check the channel ID and read key."
                    CloudReadFailure.INVALID_RESPONSE -> "ThingSpeak returned unreadable data."
                    CloudReadFailure.RESPONSE_TOO_LARGE -> "The ThingSpeak response exceeded the safe size limit."
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(cloudTestMessage = "Could not read ThingSpeak history.")
            } finally {
                _uiState.value = _uiState.value.copy(cloudTesting = false)
            }
        }
    }

    fun save() {
        val snapshot = _uiState.value
        if (snapshot.saving || snapshot.mqttTesting || snapshot.cloudTesting) return
        val errors = ConnectionValidator.validateForSave(snapshot.toInput())
        if (errors.isNotEmpty()) {
            _uiState.value = snapshot.copy(validationErrors = errors, generalError = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, generalError = null)
            val secrets = resolveSecrets(snapshot)
            when (val result = configStore.save(snapshot.toConfig(), secrets)) {
                is SaveResult.Saved -> {
                    savedConfig = result.config
                    savedSecrets = secrets
                    clearDraft()
                    _uiState.value = result.config.toUiState(savedSecrets).copy(saveCompleted = true)
                }
                SaveResult.Failed -> _uiState.value = _uiState.value.copy(saving = false, generalError = "Could not save settings securely. Try again.")
            }
        }
    }

    fun requestBack() {
        if (_uiState.value.mqttTesting || _uiState.value.cloudTesting || _uiState.value.saving) return
        _uiState.value = if (_uiState.value.dirty) _uiState.value.copy(showDiscardDialog = true) else _uiState.value.copy(saveCompleted = true)
    }

    fun keepEditing() { _uiState.value = _uiState.value.copy(showDiscardDialog = false) }

    fun discardAndExit() {
        clearDraft()
        _uiState.value = savedConfig.toUiState(savedSecrets).copy(saveCompleted = true)
    }

    private fun change(transform: (ConnectionUiState) -> ConnectionUiState) {
        if (!loaded) return
        val updated = transform(_uiState.value).copy(dirty = true, saveCompleted = false, generalError = null)
        _uiState.value = updated
        persistDraft(updated)
    }

    private fun persistDraft(state: ConnectionUiState) {
        savedState[DRAFT_EXISTS] = true
        savedState[DRAFT_SOURCE] = state.activeSource?.name
        savedState[DRAFT_HOST] = state.brokerHost
        savedState[DRAFT_PORT] = state.brokerPort
        savedState[DRAFT_TLS] = state.useTls
        savedState[DRAFT_TOPIC] = state.topic
        savedState[DRAFT_PRESSURE] = state.pressureUnit?.name
        savedState[DRAFT_CHANNEL] = state.cloudChannelId
        savedState[DRAFT_CLOUD_PRESSURE] = state.cloudPressureUnit?.name
        savedState[DRAFT_FORECAST_CHANNEL] = state.forecastChannelId
        savedState[DRAFT_CLEAR_USERNAME] = state.clearSavedUsername
        savedState[DRAFT_CLEAR_PASSWORD] = state.clearSavedPassword
        savedState[DRAFT_CLEAR_CLOUD_KEY] = state.clearSavedCloudKey
        savedState[DRAFT_CLEAR_FORECAST_KEY] = state.clearSavedForecastKey
    }

    private fun restoreDraft(base: ConnectionUiState): ConnectionUiState {
        if (savedState.get<Boolean>(DRAFT_EXISTS) != true) return base
        return base.copy(
            activeSource = savedState.get<String>(DRAFT_SOURCE)?.let { runCatching { WeatherSource.valueOf(it) }.getOrNull() } ?: base.activeSource,
            brokerHost = savedState[DRAFT_HOST] ?: base.brokerHost,
            brokerPort = savedState[DRAFT_PORT] ?: base.brokerPort,
            useTls = savedState[DRAFT_TLS] ?: base.useTls,
            topic = savedState[DRAFT_TOPIC] ?: base.topic,
            pressureUnit = savedState.get<String>(DRAFT_PRESSURE)?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() } ?: base.pressureUnit,
            cloudChannelId = savedState[DRAFT_CHANNEL] ?: base.cloudChannelId,
            cloudPressureUnit = savedState.get<String>(DRAFT_CLOUD_PRESSURE)?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() } ?: base.cloudPressureUnit,
            forecastChannelId = savedState[DRAFT_FORECAST_CHANNEL] ?: base.forecastChannelId,
            clearSavedUsername = savedState[DRAFT_CLEAR_USERNAME] ?: false,
            clearSavedPassword = savedState[DRAFT_CLEAR_PASSWORD] ?: false,
            clearSavedCloudKey = savedState[DRAFT_CLEAR_CLOUD_KEY] ?: false,
            clearSavedForecastKey = savedState[DRAFT_CLEAR_FORECAST_KEY] ?: false,
            dirty = true,
        )
    }

    private fun clearDraft() {
        listOf(
            DRAFT_EXISTS, DRAFT_SOURCE, DRAFT_HOST, DRAFT_PORT, DRAFT_TLS, DRAFT_TOPIC, DRAFT_PRESSURE,
            DRAFT_CHANNEL, DRAFT_CLOUD_PRESSURE, DRAFT_FORECAST_CHANNEL, DRAFT_CLEAR_USERNAME, DRAFT_CLEAR_PASSWORD,
            DRAFT_CLEAR_CLOUD_KEY, DRAFT_CLEAR_FORECAST_KEY,
        ).forEach { key -> savedState.remove<Any>(key) }
    }

    private fun ConnectionUiState.toInput() = ConnectionInput(
        brokerHost, brokerPort, topic, pressureUnit != null, cloudChannelId, cloudPressureUnit != null, forecastChannelId, activeSource,
    )

    private fun resolveSecrets(state: ConnectionUiState) = when (state.activeSource) {
        WeatherSource.MQTT -> savedSecrets.copy(
            mqttUsername = resolveSecret(savedSecrets.mqttUsername, state.mqttUsernameDraft, state.clearSavedUsername),
            mqttPassword = resolveSecret(savedSecrets.mqttPassword, state.mqttPasswordDraft, state.clearSavedPassword),
            forecastReadKey = resolveSecret(savedSecrets.forecastReadKey, state.forecastReadKeyDraft, state.clearSavedForecastKey),
        )
        WeatherSource.THINGSPEAK -> savedSecrets.copy(
            cloudReadKey = resolveSecret(savedSecrets.cloudReadKey, state.cloudReadKeyDraft, state.clearSavedCloudKey),
            forecastReadKey = resolveSecret(savedSecrets.forecastReadKey, state.forecastReadKeyDraft, state.clearSavedForecastKey),
        )
        null -> savedSecrets.copy(
            forecastReadKey = resolveSecret(savedSecrets.forecastReadKey, state.forecastReadKeyDraft, state.clearSavedForecastKey),
        )
    }

    private fun resolveSecret(saved: String, draft: String, clear: Boolean) =
        if (clear) "" else draft.ifEmpty { saved }

    private fun ConnectionUiState.toConfig() = when (activeSource) {
        WeatherSource.MQTT -> savedConfig.copy(
            activeSource = WeatherSource.MQTT,
            brokerHost = brokerHost.trim(),
            brokerPort = brokerPort.toIntOrNull() ?: savedConfig.brokerPort,
            useTls = useTls,
            topic = topic.trim(),
            pressureUnit = pressureUnit ?: savedConfig.pressureUnit,
            forecastChannelId = forecastChannelId.trim(),
        )
        WeatherSource.THINGSPEAK -> savedConfig.copy(
            activeSource = WeatherSource.THINGSPEAK,
            cloudChannelId = cloudChannelId.trim(),
            cloudPressureUnit = cloudPressureUnit ?: savedConfig.cloudPressureUnit,
            forecastChannelId = forecastChannelId.trim(),
        )
        null -> savedConfig.copy(forecastChannelId = forecastChannelId.trim())
    }

    private fun StationConfig.toUiState(secrets: StationSecrets) = ConnectionUiState(
        loading = false,
        activeSource = activeSource,
        savedActiveSource = activeSource,
        savedSourceReady = sourceOwner.isActive(this),
        brokerHost = brokerHost,
        brokerPort = brokerPort.toString(),
        useTls = useTls,
        topic = topic,
        pressureUnit = if (brokerHost.isNotBlank()) pressureUnit else null,
        cloudChannelId = cloudChannelId,
        cloudPressureUnit = if (cloudChannelId.isNotBlank()) cloudPressureUnit else null,
        forecastChannelId = forecastChannelId,
        hasSavedUsername = secrets.mqttUsername.isNotBlank(),
        hasSavedPassword = secrets.mqttPassword.isNotBlank(),
        hasSavedCloudKey = secrets.cloudReadKey.isNotBlank(),
        hasSavedForecastKey = secrets.forecastReadKey.isNotBlank(),
    )

    private companion object {
        const val DRAFT_EXISTS = "connection_draft_exists"
        const val DRAFT_SOURCE = "connection_draft_source"
        const val DRAFT_HOST = "connection_draft_host"
        const val DRAFT_PORT = "connection_draft_port"
        const val DRAFT_TLS = "connection_draft_tls"
        const val DRAFT_TOPIC = "connection_draft_topic"
        const val DRAFT_PRESSURE = "connection_draft_pressure_unit"
        const val DRAFT_CHANNEL = "connection_draft_channel"
        const val DRAFT_CLOUD_PRESSURE = "connection_draft_cloud_pressure_unit"
        const val DRAFT_FORECAST_CHANNEL = "connection_draft_forecast_channel"
        const val DRAFT_CLEAR_USERNAME = "connection_draft_clear_username"
        const val DRAFT_CLEAR_PASSWORD = "connection_draft_clear_password"
        const val DRAFT_CLEAR_CLOUD_KEY = "connection_draft_clear_cloud_key"
        const val DRAFT_CLEAR_FORECAST_KEY = "connection_draft_clear_forecast_key"
    }
}
