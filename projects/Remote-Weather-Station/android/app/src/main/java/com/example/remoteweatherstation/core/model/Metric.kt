package com.example.remoteweatherstation.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Metric(val wireKey: String, val label: String, val unit: String) {
    TEMPERATURE("t", "Temperature", "°C"),
    HUMIDITY("h", "Humidity", "% RH"),
    PRESSURE("p", "Pressure", "hPa"),
    LIGHT("l", "Light level", "sensor units"),
}

@Serializable
enum class PressureUnit {
    PA,
    HPA,
    MMHG;

    fun toHectopascals(value: Double): Double = when (this) {
        PA -> value / 100.0
        HPA -> value
        MMHG -> value * 1.333223684
    }
}

@Serializable
enum class HistorySource { LOCAL, CLOUD }

enum class WeatherSource { MQTT, THINGSPEAK }

fun inferLegacyWeatherSource(mqttConfigured: Boolean, cloudConfigured: Boolean): WeatherSource? = when {
    mqttConfigured && !cloudConfigured -> WeatherSource.MQTT
    cloudConfigured && !mqttConfigured -> WeatherSource.THINGSPEAK
    else -> null
}

data class MetricValues(
    val temperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val pressureHpa: Double? = null,
    val lightSensorUnits: Double? = null,
) {
    operator fun get(metric: Metric): Double? = when (metric) {
        Metric.TEMPERATURE -> temperatureC
        Metric.HUMIDITY -> humidityPercent
        Metric.PRESSURE -> pressureHpa
        Metric.LIGHT -> lightSensorUnits
    }

    fun isEmpty(): Boolean = temperatureC == null && humidityPercent == null &&
        pressureHpa == null && lightSensorUnits == null
}

data class ParsedTelemetry(
    val values: MetricValues,
    val invalidFields: Set<Metric> = emptySet(),
    val diagnostic: String? = null,
)

data class StationConfig(
    val activeSource: WeatherSource? = null,
    val brokerHost: String = "",
    val brokerPort: Int = 8883,
    val useTls: Boolean = true,
    val topic: String = "channel1",
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val cloudChannelId: String = "",
    val cloudPressureUnit: PressureUnit = PressureUnit.HPA,
    val forecastChannelId: String = "",
    val hasMqttUsername: Boolean = false,
    val hasMqttPassword: Boolean = false,
    val hasCloudReadKey: Boolean = false,
    val generation: Long = 0,
) {
    val isMqttConfigured: Boolean get() = brokerHost.isNotBlank() && topic.isNotBlank()
    val isCloudConfigured: Boolean get() = cloudChannelId.isNotBlank() && cloudChannelId.all(Char::isDigit)

    /** Credential changes rotate a client without reinterpreting existing readings. */
    val partitionId: String
        get() = listOf(
            activeSource, brokerHost.trim().lowercase(), brokerPort, useTls, topic.trim(), pressureUnit,
            cloudChannelId.trim(), cloudPressureUnit,
        ).joinToString("|")
}

@Serializable
data class StationSecrets(
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val cloudReadKey: String = "",
    val forecastReadKey: String = "",
)

data class ConfigRevision(val partitionId: String, val generation: Long)
