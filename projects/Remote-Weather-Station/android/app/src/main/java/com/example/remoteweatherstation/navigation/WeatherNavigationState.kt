package com.example.remoteweatherstation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

private val weatherTopLevelRoutes = buildSet<NavKey> {
    add(DashboardKey)
    add(ForecastKey)
}

@Composable
internal fun rememberWeatherNavigationState(): WeatherNavigationState {
    val topLevelRoute = rememberSerializable(
        DashboardKey,
        weatherTopLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer()),
    ) { mutableStateOf<NavKey>(DashboardKey) }
    val backStacks = weatherTopLevelRoutes.associateWith { key -> rememberNavBackStack(key) }
    return remember(weatherTopLevelRoutes) {
        WeatherNavigationState(DashboardKey, topLevelRoute, backStacks)
    }
}

internal class WeatherNavigationState(
    private val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    var topLevelRoute by topLevelRoute

    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decoratedEntries = backStacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                    rememberViewModelStoreNavEntryDecorator<NavKey>(),
                ),
                entryProvider = entryProvider,
            )
        }
        val routesInUse = if (topLevelRoute == startRoute) listOf(startRoute) else listOf(startRoute, topLevelRoute)
        return routesInUse.flatMap { decoratedEntries[it].orEmpty() }
    }

    fun goBack() {
        val stack = backStacks.getValue(topLevelRoute)
        val current = stack.lastOrNull() ?: return
        when {
            current == ConnectionKey -> Unit
            current == topLevelRoute && topLevelRoute != startRoute -> topLevelRoute = startRoute
            stack.size > 1 -> stack.removeLastOrNull()
        }
    }
}
