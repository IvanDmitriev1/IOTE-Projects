package com.example.remoteweatherstation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object DashboardKey : NavKey

@Serializable
data object ForecastKey : NavKey

@Serializable
data object ConnectionKey : NavKey
