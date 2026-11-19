package com.example.homeinvasion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.homeinvasion.data.MainViewModel
import com.example.homeinvasion.data.SessionState
import kotlinx.serialization.Serializable

@Serializable private object MainRoute
@Serializable private object SettingsRoute

@Composable
fun HomeWatchApp(eventId: String?, onEventConsumed: (String) -> Unit) {
    val viewModel: MainViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(eventId, state.session) {
        if (!eventId.isNullOrBlank() && state.session is SessionState.SignedIn) {
            navController.navigate(MainRoute) {
                popUpTo<MainRoute> { inclusive = false }
                launchSingleTop = true
            }
            viewModel.openEvent(eventId)
            onEventConsumed(eventId)
        }
    }
    LaunchedEffect(state.session) {
        // Keep the current route while credentials and custom claims initialize.
    }

    HomeWatchTheme {
        NavHost(navController = navController, startDestination = MainRoute) {
            composable<MainRoute> {
                HomeScreen(
                    state = state,
                    onRetryAuthorization = viewModel::retryAuthorization,
                    onRetry = viewModel::retry,
                    onOpenEvent = viewModel::openEvent,
                    onCloseEvent = viewModel::closeEvent,
                    onSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = { navController.popBackStack() }, onCheckAccess = viewModel::retryAuthorization)
            }
        }
    }
}
