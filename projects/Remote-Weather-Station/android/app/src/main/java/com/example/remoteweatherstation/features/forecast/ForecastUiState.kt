package com.example.remoteweatherstation.features.forecast

import com.example.remoteweatherstation.core.data.cloud.ForecastReading

data class ForecastUiState(
    val configurationLoaded: Boolean = false,
    val configured: Boolean = false,
    val refreshing: Boolean = false,
    val empty: Boolean = false,
    val reading: ForecastReading? = null,
    val error: String? = null,
)
