package com.example.homeinvasion.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val alertsEnabled: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false,
    val subscribed: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferences: DevicePreferences,
    private val pushCoordinator: PushCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(preferences.alertsEnabled, pushCoordinator.status) { enabled, push ->
                SettingsUiState(
                    alertsEnabled = enabled,
                    notificationPermissionGranted = pushCoordinator.hasNotificationPermission(),
                    error = push.error,
                    subscribed = push.subscribed,
                )
            }.collect { observed ->
                mutableState.update { current -> observed.copy(busy = current.busy, error = observed.error ?: current.error) }
            }
        }
    }

    fun refreshPermission() {
        mutableState.value = mutableState.value.copy(notificationPermissionGranted = pushCoordinator.hasNotificationPermission())
    }

    fun setAlertsEnabled(enabled: Boolean) {
        val session = (authRepository.session.value as? SessionState.SignedIn)?.session ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            runCatching { pushCoordinator.setEnabled(session, enabled) }
                .onFailure { mutableState.value = mutableState.value.copy(error = it.message ?: "Couldn't update notifications.") }
            mutableState.value = mutableState.value.copy(busy = false, notificationPermissionGranted = pushCoordinator.hasNotificationPermission())
        }
    }

    fun onPermissionDenied() {
        mutableState.value = mutableState.value.copy(
            notificationPermissionGranted = false,
            error = "Android blocked notifications. Allow them in system settings to receive motion alerts.",
        )
    }
}
