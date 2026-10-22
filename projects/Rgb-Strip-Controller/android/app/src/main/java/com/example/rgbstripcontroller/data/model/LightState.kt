package com.example.rgbstripcontroller.data.model

internal const val MAX_RGB_CHANNEL_VALUE = 255

data class LightState(
    val isOn: Boolean,
    val red: Int,
    val green: Int,
    val blue: Int,
) {
    init {
        require(red in 0..MAX_RGB_CHANNEL_VALUE) { "Red must be between 0 and $MAX_RGB_CHANNEL_VALUE." }
        require(green in 0..MAX_RGB_CHANNEL_VALUE) { "Green must be between 0 and $MAX_RGB_CHANNEL_VALUE." }
        require(blue in 0..MAX_RGB_CHANNEL_VALUE) { "Blue must be between 0 and $MAX_RGB_CHANNEL_VALUE." }
    }
}
