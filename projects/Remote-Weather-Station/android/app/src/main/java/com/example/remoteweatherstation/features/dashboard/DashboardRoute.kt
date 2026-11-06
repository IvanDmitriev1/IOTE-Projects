package com.example.remoteweatherstation.features.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DashboardRoute(
    onSetupClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    gridState: LazyGridState? = null,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onSetupClick = onSetupClick,
        onRetryClick = viewModel::retryConnection,
        modifier = modifier,
        showTopBar = showTopBar,
        gridState = gridState ?: androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    )
}
