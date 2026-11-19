package com.example.homeinvasion.data

import android.graphics.Bitmap

data class HomeSession(val uid: String, val homeId: String)

sealed interface SessionState {
    data object Loading : SessionState
    data object Unconfigured : SessionState
    data object NeedsSignIn : SessionState
    data class NeedsApproval(val uid: String) : SessionState
    data class SignedIn(val session: HomeSession) : SessionState
    data class Error(val message: String) : SessionState
}

data class MotionEvent(
    val id: String,
    val detectedAt: Long,
    val snapshotPath: String,
)

data class EventFeed(
    val events: List<MotionEvent> = emptyList(),
    val online: Boolean = false,
    val error: String? = null,
)

sealed interface SnapshotState {
    data object Hidden : SnapshotState
    data object Loading : SnapshotState
    data class Ready(val bitmap: Bitmap) : SnapshotState
    data class Error(val message: String) : SnapshotState
}

data class HomeUiState(
    val session: SessionState = SessionState.Loading,
    val isLoading: Boolean = false,
    val events: List<MotionEvent> = emptyList(),
    val online: Boolean = false,
    val feedError: String? = null,
    val authError: String? = null,
    val authBusy: Boolean = false,
    val selectedEventId: String? = null,
    val selectedEvent: MotionEvent? = null,
    val detailError: String? = null,
    val snapshot: SnapshotState = SnapshotState.Hidden,
)

data class EventRecord(var detectedAt: Long = 0L, var snapshotPath: String = "")
