package com.example.remoteweatherstation.core.data.cloud

import com.example.remoteweatherstation.core.data.CloudEntry
import com.example.remoteweatherstation.core.model.PressureUnit

data class ThingSpeakConfig(val channelId: String, val pressureUnit: PressureUnit)

enum class CloudReadFailure { NOT_CONFIGURED, NETWORK, HTTP, INVALID_RESPONSE, RESPONSE_TOO_LARGE }

data class CloudFetchResult(
    val entries: List<CloudEntry> = emptyList(),
    val failure: CloudReadFailure? = null,
) {
    val succeeded: Boolean get() = failure == null
}

interface ThingSpeakReader {
    suspend fun readTail(config: ThingSpeakConfig, readKey: String): CloudFetchResult
    suspend fun readForecast(channelId: String, readKey: String): ForecastFetchResult
}
