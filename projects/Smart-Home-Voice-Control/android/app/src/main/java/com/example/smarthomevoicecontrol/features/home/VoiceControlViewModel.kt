package com.example.smarthomevoicecontrol.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthomevoicecontrol.core.CommandParser
import com.example.smarthomevoicecontrol.core.ParseResult
import com.example.smarthomevoicecontrol.core.PiConfig
import com.example.smarthomevoicecontrol.core.VoiceCommand
import com.example.smarthomevoicecontrol.data.PiClient
import com.example.smarthomevoicecontrol.data.PiConfigStore
import com.example.smarthomevoicecontrol.data.TextToColorModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommandResult(val label: String, val value: String, val succeeded: Boolean, val spokenValue: String = value)

data class VoiceControlUiState(
    val config: PiConfig = PiConfig(),
    val transcript: String? = null,
    val results: List<CommandResult> = emptyList(),
    val busy: Boolean = false,
    val permissionDenied: Boolean = false
)

class VoiceControlViewModel(
    private val configStore: PiConfigStore,
    private val piClient: PiClient,
    private val textToColorModel: TextToColorModel? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(VoiceControlUiState())
    val uiState = _uiState.asStateFlow()
    private val _speak = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val speak = _speak.asSharedFlow()
    private var pendingTranscript: String? = null

    init { viewModelScope.launch { configStore.config.collect { config -> _uiState.update { it.copy(config = config) } } } }

    fun beginListening(): Boolean {
        if (_uiState.value.busy)
            return false

        _uiState.update { it.copy(busy = true, transcript = null, results = emptyList(), permissionDenied = false) }
        return true
    }

    fun onRecognitionCancelled(message: String? = null) {
        _uiState.update { it.copy(busy = false, results = message?.let { listOf(CommandResult("Voice input", it, false)) }.orEmpty()) }
    }

    fun onLocalNetworkPermissionDenied() {
        pendingTranscript = null
        _uiState.update {
            it.copy(busy = false, permissionDenied = true,
                results = listOf(CommandResult("Local network", "Permission is required to contact your configured Raspberry Pi.", false)))
        }
    }

    fun queueTranscript(transcript: String) {
        pendingTranscript = transcript
        _uiState.update { it.copy(transcript = transcript) }
    }

    fun executePendingTranscript() {
        val transcript = pendingTranscript ?: return
        pendingTranscript = null
        executeTranscript(transcript)
    }

    fun executeTranscript(transcript: String) {
        if (!_uiState.value.busy) _uiState.update { it.copy(busy = true) }

        _uiState.update { it.copy(transcript = transcript, results = emptyList(), permissionDenied = false) }
        viewModelScope.launch {
            val config = configStore.current()
            val parsed = try {
                textToColorModel?.parse(transcript) ?: CommandParser.parse(transcript)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                finish(listOf(CommandResult("Lights", error.message ?: "Couldn't load color descriptions.", false)))
                return@launch
            } catch (error: LinkageError) {
                finish(listOf(CommandResult("Lights", error.message ?: "The local color runtime couldn't be loaded.", false)))
                return@launch
            }
            when (parsed) {
                is ParseResult.Invalid ->
                    finish(listOf(CommandResult("Command", parsed.reason, false)))
                is ParseResult.Valid -> execute(parsed.commands, config)
            }
        }
    }

    private suspend fun execute(commands: List<VoiceCommand>, config: PiConfig) {
        val invalid = config.validationError()
        if (invalid != null) {
            finish(listOf(CommandResult("Pi setup", "Configure a valid Raspberry Pi address and port first.", false)))
            return
        }
        val results = mutableListOf<CommandResult>()
        var permissionWasRevoked = false
        for (command in commands) {
            when (command) {
                is VoiceCommand.Read -> {
                    val result = piClient.read(config, command.kind)
                    results += result.fold(
                        { CommandResult(it.kind.label, it.displayValue, true, it.spokenValue) },
                        {
                            permissionWasRevoked = permissionWasRevoked || it is SecurityException
                            if (it is SecurityException) CommandResult("Local network", "Permission is required to contact your Raspberry Pi.", false)
                            else CommandResult(command.kind.label, it.message ?: "Pi request failed.", false)
                        }
                    )
                }
                is VoiceCommand.SetLights -> {
                    val result = piClient.setLights(config, command.color)
                    results += result.fold(
                        { CommandResult("Lights", "Pi accepted: ${command.color.label}", true) },
                        {
                            permissionWasRevoked = permissionWasRevoked || it is SecurityException
                            CommandResult("Lights", "Couldn't confirm the light command", false)
                        }
                    )
                }
                is VoiceCommand.SetLightDescription -> {
                    try {
                        val hex = checkNotNull(textToColorModel) { "Color model is unavailable." }.color(command.description)
                        val result = piClient.setLights(config, hex)
                        results += result.fold(
                            { CommandResult("Lights", "Pi accepted: $hex", true) },
                            { permissionWasRevoked = permissionWasRevoked || it is SecurityException; CommandResult("Lights", it.message ?: "Couldn't confirm the light command", false) }
                        )
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { results += CommandResult("Lights", error.message ?: "Couldn't resolve that color description.", false) }
                }
            }
            _uiState.update { it.copy(results = results.toList()) }
        }
        finish(results, permissionWasRevoked)
    }

    private fun finish(results: List<CommandResult>, permissionDenied: Boolean = false) {
        _uiState.update { it.copy(results = results, busy = false, permissionDenied = permissionDenied) }
        if (results.isNotEmpty()) _speak.tryEmit(results.joinToString(". ") { "${it.label}: ${it.spokenValue}" })
    }
}
