package com.example.homeinvasion.data

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    val session: StateFlow<SessionState>
    suspend fun retryAuthorization(): SessionState
    suspend fun signIn(activity: Activity)
    suspend fun signOut()

    companion object {
        const val DEMO_HOME_ID = "demo"
        const val FIREBASE_SETUP_MESSAGE = "Firebase is not configured. Add this app's google-services.json file and rebuild."
        const val FIREBASE_DATABASE_SETUP_MESSAGE = "Realtime Database URL is missing. Set the local Gradle property realtimeDatabaseUrl or include the URL in google-services.json."
    }
}

@Singleton
class DemoSessionRepository @Inject constructor() : AuthRepository {
    private val demoSession = SessionState.SignedIn(HomeSession("demo-viewer", AuthRepository.DEMO_HOME_ID))
    override val session: StateFlow<SessionState> = MutableStateFlow(demoSession)
    override suspend fun retryAuthorization() = demoSession
    override suspend fun signIn(activity: Activity) = Unit
    override suspend fun signOut() = Unit
}
