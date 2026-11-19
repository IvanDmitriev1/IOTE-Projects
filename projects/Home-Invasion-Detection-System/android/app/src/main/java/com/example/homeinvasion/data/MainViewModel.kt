package com.example.homeinvasion.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val motionRepository: MotionRepository,
    private val pushCoordinator: PushCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state = mutableState.asStateFlow()
    private val retryFeed = MutableStateFlow(0)
    private var selectedEventJob: Job? = null
    private var followLatest = true

    init {
        viewModelScope.launch {
            authRepository.session.collectLatest { session ->
                selectedEventJob?.cancel()
                followLatest = true
                mutableState.value = HomeUiState(session = session, isLoading = session is SessionState.SignedIn)
                if (session is SessionState.SignedIn) {
                    combine(retryFeed) { session.session }
                        .flatMapLatest { motionRepository.observeRecent(session.session.homeId) }
                        .collect { feed ->
                            mutableState.value = mutableState.value.copy(
                                isLoading = false,
                                events = feed.events,
                                online = feed.online,
                                feedError = feed.error,
                            )
                            val latest = feed.events.firstOrNull()
                            if (followLatest && latest != null && latest.id != mutableState.value.selectedEventId) {
                                loadEvent(latest.id)
                            }
                        }
                }
            }
        }
    }

    fun retryAuthorization() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(authBusy = true, authError = null)
            try { mutableState.value = mutableState.value.copy(session = authRepository.retryAuthorization()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.value = mutableState.value.copy(authError = failure.message ?: "Couldn't refresh authorization.") }
            finally { mutableState.value = mutableState.value.copy(authBusy = false) }
        }
    }

    fun retry() {
        mutableState.value = mutableState.value.copy(isLoading = true, feedError = null)
        retryFeed.value++
    }

    fun onForeground() {
        val session = (authRepository.session.value as? SessionState.SignedIn)?.session ?: return
        viewModelScope.launch {
            try { pushCoordinator.reconcile(session) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* PushCoordinator exposes the actionable error in shared state. */ }
        }
    }

    fun openEvent(eventId: String) {
        followLatest = false
        loadEvent(eventId)
    }

    private fun loadEvent(eventId: String) {
        val session = (authRepository.session.value as? SessionState.SignedIn)?.session ?: return
        val existing = mutableState.value.events.firstOrNull { it.id == eventId }
        selectedEventJob?.cancel()
        mutableState.value = mutableState.value.copy(
            selectedEventId = eventId,
            selectedEvent = existing,
            detailError = null,
            snapshot = SnapshotState.Loading,
        )
        selectedEventJob = viewModelScope.launch {
            try {
                val event = existing ?: motionRepository.getEvent(session.homeId, eventId)
                    ?: error("This motion event is no longer available.")
                if (authRepository.session.value != SessionState.SignedIn(session) || mutableState.value.selectedEventId != eventId) return@launch
                mutableState.value = mutableState.value.copy(selectedEvent = event)
                val bitmap = motionRepository.loadSnapshot(session.homeId, event)
                if (authRepository.session.value != SessionState.SignedIn(session) || mutableState.value.selectedEventId != eventId) return@launch
                mutableState.value = mutableState.value.copy(snapshot = SnapshotState.Ready(bitmap))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (authRepository.session.value == SessionState.SignedIn(session) && mutableState.value.selectedEventId == eventId) {
                    mutableState.value = mutableState.value.copy(snapshot = SnapshotState.Error(failure.message ?: "Couldn't load the snapshot."))
                }
            }
        }
    }

    fun closeEvent() {
        followLatest = false
        selectedEventJob?.cancel()
        mutableState.value = mutableState.value.copy(selectedEventId = null, selectedEvent = null, detailError = null, snapshot = SnapshotState.Hidden)
    }

}
