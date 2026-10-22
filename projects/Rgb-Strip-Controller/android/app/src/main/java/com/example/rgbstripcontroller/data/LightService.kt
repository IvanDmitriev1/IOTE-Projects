package com.example.rgbstripcontroller.data

import com.example.rgbstripcontroller.data.model.LightState
import com.example.rgbstripcontroller.data.model.RainbowDirection

interface LightService {
    suspend fun applyState(endpointUrl: String, state: LightState): Result<Unit>
    suspend fun setRainbow(endpointUrl: String, direction: RainbowDirection): Result<Unit>
}
