package com.example.remoteweatherstation.features.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ConnectionRoute(
    onExit: () -> Unit,
    exitRequest: Boolean,
    onCancelExitRequest: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(exitRequest) { if (exitRequest) viewModel.requestBack() }
    LaunchedEffect(state.saveCompleted) { if (state.saveCompleted) onExit() }
    ConnectionScreen(
        state = state,
        onBack = viewModel::requestBack,
        onUpdate = viewModel::update,
        onTls = viewModel::setTls,
        onSource = viewModel::setActiveSource,
        onPressureUnit = viewModel::setPressureUnit,
        onCloudPressureUnit = viewModel::setCloudPressureUnit,
        onVisibility = viewModel::setVisibility,
        onClearSecret = viewModel::clearSecret,
        onTestMqtt = viewModel::testMqtt,
        onTestCloud = viewModel::testCloud,
        onSave = viewModel::save,
        onKeepEditing = {
            viewModel.keepEditing()
            onCancelExitRequest()
        },
        onDiscard = { viewModel.discardAndExit() },
    )
}
