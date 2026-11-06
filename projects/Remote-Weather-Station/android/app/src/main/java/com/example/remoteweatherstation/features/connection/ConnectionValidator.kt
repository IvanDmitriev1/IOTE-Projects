package com.example.remoteweatherstation.features.connection

import com.example.remoteweatherstation.core.model.WeatherSource

data class ConnectionInput(
    val brokerHost: String,
    val brokerPort: String,
    val topic: String,
    val pressureUnitSelected: Boolean,
    val cloudChannelId: String,
    val cloudPressureUnitSelected: Boolean,
    val forecastChannelId: String,
    val activeSource: WeatherSource?,
)

object ConnectionValidator {
    fun validateMqtt(input: ConnectionInput): Map<String, String> = buildMap {
        if (input.brokerHost.isBlank()) put("brokerHost", "Enter the MQTT broker host")
        val port = input.brokerPort.toIntOrNull()
        if (port == null || port !in 1..65535) put("brokerPort", "Enter a port from 1 to 65535")
        if (input.topic.isBlank() || input.topic.any(Char::isISOControl)) put("topic", "Enter a valid MQTT topic")
        if (!input.pressureUnitSelected) put("pressureUnit", "Choose the MQTT source pressure unit")
    }

    fun validateCloud(input: ConnectionInput, required: Boolean): Map<String, String> = buildMap {
        if (input.cloudChannelId.isBlank()) {
            if (required) put("cloudChannelId", "Enter a ThingSpeak channel ID")
        } else {
            if (!input.cloudChannelId.all(Char::isDigit)) put("cloudChannelId", "Channel ID must contain digits only")
            if (!input.cloudPressureUnitSelected) put("cloudPressureUnit", "Choose the ThingSpeak pressure unit")
        }
    }

    fun validateForSave(input: ConnectionInput): Map<String, String> {
        return buildMap {
            putAll(when (input.activeSource) {
            WeatherSource.MQTT -> validateMqtt(input)
            WeatherSource.THINGSPEAK -> validateCloud(input, required = true)
            null -> emptyMap()
            })
            if (input.forecastChannelId.isNotBlank() && !input.forecastChannelId.all(Char::isDigit)) {
                put("forecastChannelId", "Channel ID must contain digits only")
            }
        }
    }
}
