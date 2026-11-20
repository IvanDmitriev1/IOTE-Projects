package com.example.smarthomevoicecontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.smarthomevoicecontrol.core.PiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name = "pi_config")

class PiConfigStore(private val store: DataStore<Preferences>) {
    val config: Flow<PiConfig> = store.data.map { values ->
        PiConfig(
            address = values[ADDRESS].orEmpty(),
            port = values[PORT] ?: 8000,
        )
    }

    suspend fun current() = config.first()

    suspend fun save(value: PiConfig) {
        require(value.validationError() == null)
        store.edit {
            it[ADDRESS] = value.address
            it[PORT] = value.port
        }
    }

    companion object {
        private val ADDRESS = stringPreferencesKey("address")
        private val PORT = intPreferencesKey("port")
        fun create(context: Context) = PiConfigStore(context.preferences)
    }
}
