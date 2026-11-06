package com.example.remoteweatherstation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.remoteweatherstation.features.connection.ConnectionRoute
import com.example.remoteweatherstation.features.dashboard.DashboardRoute
import com.example.remoteweatherstation.features.forecast.ForecastRoute

@Composable
fun WeatherApp() {
    val navigationState = rememberWeatherNavigationState()
    val dashboardGridState = rememberLazyGridState()
    var settingsExitRequested by rememberSaveable { mutableStateOf(false) }
    var settingsExitTarget by rememberSaveable { mutableStateOf<WeatherTab?>(null) }

    Box(Modifier.fillMaxSize().imePadding()) {
        val currentStack = navigationState.backStacks.getValue(navigationState.topLevelRoute)
        val current = currentStack.lastOrNull()
        val selectedTab = when {
            current == ConnectionKey -> WeatherTab.SETTINGS
            navigationState.topLevelRoute == ForecastKey -> WeatherTab.FORECAST
            else -> WeatherTab.DASHBOARD
        }

        fun openSettings() {
            val stack = navigationState.backStacks.getValue(navigationState.topLevelRoute)
            if (stack.lastOrNull() != ConnectionKey) stack.add(ConnectionKey)
        }

        fun selectTab(tab: WeatherTab) {
            if (tab == selectedTab) return
            if (current == ConnectionKey) {
                settingsExitTarget = tab
                settingsExitRequested = true
            } else when (tab) {
                WeatherTab.DASHBOARD -> navigationState.topLevelRoute = DashboardKey
                WeatherTab.FORECAST -> navigationState.topLevelRoute = ForecastKey
                WeatherTab.SETTINGS -> openSettings()
            }
        }

        fun exitSettings() {
            val stack = navigationState.backStacks.getValue(navigationState.topLevelRoute)
            if (stack.lastOrNull() == ConnectionKey) stack.removeLastOrNull()
            when (settingsExitTarget) {
                WeatherTab.DASHBOARD -> navigationState.topLevelRoute = DashboardKey
                WeatherTab.FORECAST -> navigationState.topLevelRoute = ForecastKey
                else -> Unit
            }
            settingsExitTarget = null
            settingsExitRequested = false
        }

        val entryProvider = entryProvider {
            entry<DashboardKey> {
                DashboardRoute(
                    onSetupClick = ::openSettings,
                    gridState = dashboardGridState,
                )
            }
            entry<ForecastKey> { ForecastRoute(onOpenSettings = ::openSettings) }
            entry<ConnectionKey> {
                ConnectionRoute(
                    onExit = ::exitSettings,
                    exitRequest = settingsExitRequested,
                    onCancelExitRequest = {
                        settingsExitTarget = null
                        settingsExitRequested = false
                    },
                )
            }
        }

        Scaffold(bottomBar = { WeatherNavigationBar(selectedTab) { selectTab(it) } }) { innerPadding ->
            NavDisplay(
                entries = navigationState.toDecoratedEntries(entryProvider),
                onBack = navigationState::goBack,
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            )
        }
    }
}

private enum class WeatherTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    FORECAST("Forecast", Icons.Default.Cloud),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
private fun WeatherNavigationBar(selectedTab: WeatherTab, onSelectTab: (WeatherTab) -> Unit) {
    NavigationBar {
        WeatherTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

