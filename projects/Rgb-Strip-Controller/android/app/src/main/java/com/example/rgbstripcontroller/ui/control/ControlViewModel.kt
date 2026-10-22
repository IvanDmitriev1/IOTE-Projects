package com.example.rgbstripcontroller.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rgbstripcontroller.data.LightService
import com.example.rgbstripcontroller.data.model.MAX_RGB_CHANNEL_VALUE
import com.example.rgbstripcontroller.data.model.LightState
import com.example.rgbstripcontroller.data.model.RainbowDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ControlViewModel @Inject constructor(private val service: LightService) : ViewModel() {
    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState = _uiState.asStateFlow()

    fun onEndpointChanged(value: String) = edit { copy(endpointUrl = value) }
    fun onPowerChanged(value: Boolean) = edit { copy(light = light.copy(isOn = value)) }
    fun onRedChanged(value: Int) = edit { copy(light = light.copy(red = value.coerceIn(0, MAX_RGB_CHANNEL_VALUE))) }
    fun onGreenChanged(value: Int) = edit { copy(light = light.copy(green = value.coerceIn(0, MAX_RGB_CHANNEL_VALUE))) }
    fun onBlueChanged(value: Int) = edit { copy(light = light.copy(blue = value.coerceIn(0, MAX_RGB_CHANNEL_VALUE))) }

    fun onApply() = send(allowed = { it.canApply }) { endpoint, state ->
        service.applyState(endpoint, state)
    }

    fun onRainbow(direction: RainbowDirection) = send(allowed = { it.canSetRainbow }) { endpoint, _ ->
        service.setRainbow(endpoint, direction)
    }

    private fun send(
        allowed: (ControlUiState) -> Boolean,
        request: suspend (String, LightState) -> Result<Unit>,
    ) {
        val current = _uiState.value
        if (!allowed(current)) return
        val endpoint = current.endpointUrl.trim()
        val snapshot = current.light
        _uiState.value = current.copy(isSending = true, status = ApplyStatus.Sending)
        viewModelScope.launch {
            try {
                request(endpoint, snapshot).getOrThrow()
                _uiState.update { it.copy(status = ApplyStatus.Succeeded) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(status = ApplyStatus.Failed) }
            } finally {
                _uiState.update { state ->
                    if (state.status == ApplyStatus.Sending) {
                        state.copy(isSending = false, status = ApplyStatus.Idle)
                    } else {
                        state.copy(isSending = false)
                    }
                }
            }
        }
    }

    private inline fun edit(transform: ControlUiState.() -> ControlUiState) {
        _uiState.update { state ->
            if (state.isSending) state else state.transform().copy(status = ApplyStatus.Idle)
        }
    }

}
