package com.example.rgbstripcontroller.ui.control

import com.example.rgbstripcontroller.data.isValidEndpoint
import com.example.rgbstripcontroller.data.model.LightState
import com.example.rgbstripcontroller.data.model.MAX_RGB_CHANNEL_VALUE

data class ControlUiState(
    val endpointUrl: String = "",
    val light: LightState = LightState(true, MAX_RGB_CHANNEL_VALUE, 128, 64),
    val isSending: Boolean = false,
    val status: ApplyStatus = ApplyStatus.Idle,
) {
    val canApply: Boolean
        get() = !isSending && isValidEndpoint(endpointUrl)

    val canSetRainbow: Boolean
        get() = canApply && light.isOn

    val showEndpointError: Boolean
        get() = endpointUrl.isNotBlank() && !isValidEndpoint(endpointUrl)
}
