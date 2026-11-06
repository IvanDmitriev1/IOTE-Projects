package com.example.remoteweatherstation.core.data.config

import com.example.remoteweatherstation.core.model.StationConfig
import com.example.remoteweatherstation.core.model.StationSecrets
import kotlinx.coroutines.flow.Flow

sealed interface SaveResult {
    data class Saved(val config: StationConfig) : SaveResult
    data object Failed : SaveResult
}

interface StationConfigStore {
    val config: Flow<StationConfig?>
    suspend fun save(config: StationConfig, secrets: StationSecrets): SaveResult
    suspend fun loadSecrets(): StationSecrets
}
