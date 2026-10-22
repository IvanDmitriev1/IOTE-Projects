package com.example.rgbstripcontroller.ui.control

sealed interface ApplyStatus {
    data object Idle : ApplyStatus
    data object Sending : ApplyStatus
    data object Succeeded : ApplyStatus
    data object Failed : ApplyStatus
}
