package com.example.remoteweatherstation.core.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.remoteweatherstation.core.model.PressureUnit
import com.example.remoteweatherstation.core.model.StationConfig
import com.example.remoteweatherstation.core.model.StationSecrets
import com.example.remoteweatherstation.core.model.WeatherSource
import com.example.remoteweatherstation.core.model.inferLegacyWeatherSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.stationDataStore: DataStore<Preferences> by preferencesDataStore(name = "station_preferences")

@Singleton
class DataStoreStationConfigStore @Inject constructor(
    @ApplicationContext context: Context,
    private val encryptedSecrets: EncryptedSecretStore,
) : StationConfigStore {
    private val store = context.stationDataStore

    override val config: Flow<StationConfig?> = store.data.map { preferences ->
        if (preferences[PRESENT] != true) null else {
            val brokerHost = preferences[BROKER_HOST].orEmpty()
            val topic = preferences[TOPIC] ?: "channel1"
            val cloudChannelId = preferences[CLOUD_CHANNEL].orEmpty()
            val forecastChannelId = preferences[FORECAST_CHANNEL].orEmpty()
            val activeSource = preferences[ACTIVE_SOURCE]?.let { runCatching { WeatherSource.valueOf(it) }.getOrNull() }
                ?: if (preferences[ACTIVE_SOURCE] == null) {
                    val mqttConfigured = brokerHost.isNotBlank() && topic.isNotBlank()
                    val cloudConfigured = cloudChannelId.isNotBlank() && cloudChannelId.all(Char::isDigit)
                    inferLegacyWeatherSource(mqttConfigured, cloudConfigured)
                } else null
            StationConfig(
            activeSource = activeSource,
            brokerHost = brokerHost,
            brokerPort = preferences[BROKER_PORT] ?: 8883,
            useTls = preferences[USE_TLS] ?: true,
            topic = topic,
            pressureUnit = preferences[PRESSURE_UNIT]?.let(PressureUnit::valueOf) ?: PressureUnit.HPA,
            cloudChannelId = cloudChannelId,
            cloudPressureUnit = preferences[CLOUD_PRESSURE_UNIT]?.let(PressureUnit::valueOf) ?: PressureUnit.HPA,
            forecastChannelId = forecastChannelId,
            hasMqttUsername = preferences[HAS_MQTT_USERNAME] ?: false,
            hasMqttPassword = preferences[HAS_MQTT_PASSWORD] ?: false,
            hasCloudReadKey = preferences[HAS_CLOUD_READ_KEY] ?: false,
            generation = preferences[GENERATION] ?: 0,
            )
        }
    }

    override suspend fun save(config: StationConfig, secrets: StationSecrets): SaveResult {
        val previousSecrets = try {
            encryptedSecrets.read()
        } catch (_: Exception) {
            return SaveResult.Failed
        }
        val current = this.config.first()
        val next = config.copy(
            hasMqttUsername = secrets.mqttUsername.isNotBlank(),
            hasMqttPassword = secrets.mqttPassword.isNotBlank(),
            hasCloudReadKey = secrets.cloudReadKey.isNotBlank(),
            generation = (current?.generation ?: 0L) + 1L,
        )
        return try {
            encryptedSecrets.write(secrets)
            store.edit { preferences ->
                preferences[PRESENT] = true
                preferences[ACTIVE_SOURCE] = next.activeSource?.name.orEmpty()
                preferences[BROKER_HOST] = next.brokerHost.trim()
                preferences[BROKER_PORT] = next.brokerPort
                preferences[USE_TLS] = next.useTls
                preferences[TOPIC] = next.topic.trim()
                preferences[PRESSURE_UNIT] = next.pressureUnit.name
                preferences[CLOUD_CHANNEL] = next.cloudChannelId.trim()
                preferences[CLOUD_PRESSURE_UNIT] = next.cloudPressureUnit.name
                preferences[FORECAST_CHANNEL] = next.forecastChannelId.trim()
                preferences[HAS_MQTT_USERNAME] = next.hasMqttUsername
                preferences[HAS_MQTT_PASSWORD] = next.hasMqttPassword
                preferences[HAS_CLOUD_READ_KEY] = next.hasCloudReadKey
                preferences[GENERATION] = next.generation
            }
            SaveResult.Saved(next)
        } catch (_: Exception) {
            runCatching { encryptedSecrets.write(previousSecrets) }
            SaveResult.Failed
        }
    }

    override suspend fun loadSecrets(): StationSecrets = runCatching { encryptedSecrets.read() }.getOrDefault(StationSecrets())

    private companion object {
        val PRESENT = booleanPreferencesKey("configured")
        val ACTIVE_SOURCE = stringPreferencesKey("active_source")
        val BROKER_HOST = stringPreferencesKey("broker_host")
        val BROKER_PORT = intPreferencesKey("broker_port")
        val USE_TLS = booleanPreferencesKey("use_tls")
        val TOPIC = stringPreferencesKey("topic")
        val PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
        val CLOUD_CHANNEL = stringPreferencesKey("cloud_channel")
        val CLOUD_PRESSURE_UNIT = stringPreferencesKey("cloud_pressure_unit")
        val FORECAST_CHANNEL = stringPreferencesKey("forecast_channel")
        val HAS_MQTT_USERNAME = booleanPreferencesKey("has_mqtt_username")
        val HAS_MQTT_PASSWORD = booleanPreferencesKey("has_mqtt_password")
        val HAS_CLOUD_READ_KEY = booleanPreferencesKey("has_cloud_read_key")
        val GENERATION = longPreferencesKey("generation")
    }
}
