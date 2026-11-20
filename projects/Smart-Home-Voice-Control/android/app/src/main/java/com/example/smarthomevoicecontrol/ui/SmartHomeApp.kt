package com.example.smarthomevoicecontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smarthomevoicecontrol.features.home.VoiceControlViewModel
import com.example.smarthomevoicecontrol.features.home.HomeScreen
import com.example.smarthomevoicecontrol.features.setup.PiSetupViewModel
import com.example.smarthomevoicecontrol.features.setup.PiSetupScreen

@Composable
fun SmartHomeApp(
    voiceViewModel: VoiceControlViewModel,
    setupViewModel: PiSetupViewModel,
    onListen: () -> Unit,
    onTestConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    onSpeak: (String) -> Unit
) {
    LaunchedEffect(voiceViewModel) { voiceViewModel.speak.collect(onSpeak) }
    val navController = rememberNavController()
    NavHost(navController, startDestination = "home") {
        composable("home") {
            HomeScreen(voiceViewModel, onListen, onOpenSettings) { navController.navigate("setup") }
        }
        composable("setup") { entry ->
            val state by setupViewModel.uiState.collectAsStateWithLifecycle()
            val closeSetup = {
                if (navController.currentBackStackEntry == entry) {
                    setupViewModel.discard()
                    navController.popBackStack()
                }
                Unit
            }
            LaunchedEffect(entry, state.saved) {
                if (state.saved && setupViewModel.uiState.value.saved) closeSetup()
            }
            PiSetupScreen(
                viewModel = setupViewModel,
                onTestConnection = onTestConnection,
                onOpenSettings = onOpenSettings,
                onBack = closeSetup
            )
        }
    }
}
