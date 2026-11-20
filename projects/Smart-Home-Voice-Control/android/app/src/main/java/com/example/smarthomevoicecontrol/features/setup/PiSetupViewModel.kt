package com.example.smarthomevoicecontrol.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthomevoicecontrol.core.PiConfig
import com.example.smarthomevoicecontrol.core.ReadingKind
import com.example.smarthomevoicecontrol.data.PiClient
import com.example.smarthomevoicecontrol.data.PiConfigStore
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PiSetupUiState(
    val address: String = "",
    val port: String = "8000",
    val checking: Boolean = false,
    val testResult: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null
) {
    fun configOrNull(): PiConfig? {
        val parsedPort = port.toIntOrNull() ?: return null
        val value = PiConfig(address.trim(), parsedPort)
        return value.takeIf { it.validationError() == null }
    }

    fun validationError(): String = port.toIntOrNull()?.let {
        PiConfig(address.trim(), it).validationError()
    } ?: "Port must be between 1 and 65535."
}

class PiSetupViewModel(private val configStore: PiConfigStore, private val piClient: PiClient) : ViewModel() {
    private val _uiState = MutableStateFlow(PiSetupUiState())
    val uiState = _uiState.asStateFlow()
    private var resetJob: Job? = null
    private var testJob: Job? = null
    private var saveJob: Job? = null
    private var draftVersion = 0L
    private var pendingPermissionVersion: Long? = null

    init { discard() }

    fun updateAddress(value: String) = updateDraft { it.copy(address = value) }
    fun updatePort(value: String) = updateDraft { it.copy(port = value) }

    private fun updateDraft(update: (PiSetupUiState) -> PiSetupUiState) {
        if (_uiState.value.saving || _uiState.value.saved) return
        invalidateDraft()
        _uiState.update { update(it).copy(checking = false, testResult = null, saveError = null) }
    }

    private fun invalidateDraft() {
        draftVersion++
        testJob?.cancel()
        resetJob?.cancel()
    }

    fun discard() {
        invalidateDraft()
        _uiState.update { PiSetupUiState(saving = it.saving) }
        resetJob = viewModelScope.launch {
            saveJob?.join()
            val config = configStore.current()
            _uiState.value = PiSetupUiState(config.address, config.port.toString())
        }
    }

    fun requestTestPermission() {
        pendingPermissionVersion = draftVersion
    }

    fun onTestPermissionResult(granted: Boolean) {
        val version = pendingPermissionVersion
        pendingPermissionVersion = null
        if (version != draftVersion) return
        if (granted) testConnection()
        else _uiState.update { it.copy(testResult = "Local network permission is required to test the Raspberry Pi connection.") }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.checking || state.saving || state.saved) return
        val config = state.configOrNull() ?: return
        val version = draftVersion
        _uiState.update { it.copy(checking = true, testResult = null) }
        testJob = viewModelScope.launch {
            val result = piClient.read(config, ReadingKind.TEMPERATURE)
            if (version != draftVersion) return@launch
            _uiState.update {
                it.copy(checking = false, testResult = result.fold(
                    { "Connection succeeded: ${it.displayValue}" },
                    { if (it is SecurityException) "Local network permission is required to test the Raspberry Pi connection."
                        else "Connection failed: ${it.message ?: "Invalid Pi response."}" }
                ))
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.saved) return
        val config = state.configOrNull() ?: return
        invalidateDraft()
        val version = draftVersion
        _uiState.update { it.copy(saving = true, checking = false, testResult = null, saveError = null) }
        saveJob = viewModelScope.launch {
            try {
                configStore.save(config)
                if (version == draftVersion) _uiState.update { it.copy(saved = true) }
            } catch (_: IOException) {
                if (version == draftVersion) _uiState.update { it.copy(saveError = "Couldn't save the Pi settings. Try again.") }
            } finally {
                _uiState.update { it.copy(saving = false) }
            }
        }
    }
}
