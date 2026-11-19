package com.example.homeinvasion.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.homeinvasion.HomeInvasionApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

data class PushStatus(val error: String? = null, val subscribed: Boolean = false)

interface DevicePreferences {
    val alertsEnabled: StateFlow<Boolean>
    fun installationId(): String
    fun registrationRemovalPending(): Boolean
    fun setRegistrationRemovalPending(pending: Boolean)
    fun setAlertsEnabled(enabled: Boolean)
}

@Singleton
class SharedPreferencesDevicePreferences @Inject constructor(@ApplicationContext context: Context) : DevicePreferences {
    private val preferences = context.getSharedPreferences("home_watch", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    override val alertsEnabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    @Synchronized
    override fun installationId(): String = preferences.getString(KEY_INSTALLATION_ID, null) ?: UUID.randomUUID().toString().also {
        preferences.edit { putString(KEY_INSTALLATION_ID, it) }
    }

    override fun registrationRemovalPending(): Boolean = preferences.getBoolean(KEY_REMOVAL_PENDING, false)

    override fun setRegistrationRemovalPending(pending: Boolean) {
        preferences.edit { putBoolean(KEY_REMOVAL_PENDING, pending) }
    }

    override fun setAlertsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        mutableEnabled.value = enabled
    }

    companion object {
        private const val KEY_ENABLED = "demo_alerts_enabled"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_REMOVAL_PENDING = "registration_removal_pending"
    }
}

@Singleton
class RealtimeDeviceRegistrationRepository @Inject constructor(private val client: FirebaseClient) : DeviceRegistrationRepository {
    override suspend fun register(session: HomeSession, installationId: String, fid: String) {
        val database = client.database ?: error(AuthRepository.FIREBASE_DATABASE_SETUP_MESSAGE)
        database.getReference("homes/${session.homeId}/devices/${session.uid}/$installationId")
            .setValue(mapOf("fid" to fid, "enabled" to true, "updatedAt" to System.currentTimeMillis()))
            .await()
    }

    override suspend fun remove(session: HomeSession, installationId: String) {
        val database = client.database ?: error(AuthRepository.FIREBASE_DATABASE_SETUP_MESSAGE)
        database.getReference("homes/${session.homeId}/devices/${session.uid}/$installationId").removeValue().await()
    }
}

interface DeviceRegistrationRepository {
    suspend fun register(session: HomeSession, installationId: String, fid: String)
    suspend fun remove(session: HomeSession, installationId: String)
}

interface PushCoordinator {
    val status: StateFlow<PushStatus>
    fun hasNotificationPermission(): Boolean
    suspend fun setEnabled(session: HomeSession, enabled: Boolean)
    suspend fun reconcile(session: HomeSession)
    suspend fun removeBeforeSignOut(session: HomeSession)
    suspend fun onRegistered(fid: String)
    fun shouldDisplayMessage(homeId: String, ownerUid: String): Boolean
}

@Singleton
class FirebasePushCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: FirebaseClient,
    private val preferences: DevicePreferences,
    private val registrations: DeviceRegistrationRepository,
    private val authRepository: AuthRepository,
) : PushCoordinator {
    private val mutableStatus = MutableStateFlow(PushStatus())
    private val mutations = Mutex()
    override val status: StateFlow<PushStatus> = mutableStatus.asStateFlow()

    override fun hasNotificationPermission(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        val channelEnabled = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(HomeInvasionApplication.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && channelEnabled
    }

    override suspend fun setEnabled(session: HomeSession, enabled: Boolean) = mutations.withLock {
        if (enabled && !hasNotificationPermission()) error("Allow notifications in Android settings to receive motion alerts.")
        preferences.setAlertsEnabled(enabled)
        if (!enabled) cancelMotionNotifications()
        if (!enabled) preferences.setRegistrationRemovalPending(true)
        reconcileLocked(session)
    }

    override suspend fun reconcile(session: HomeSession) = mutations.withLock { reconcileLocked(session) }

    private suspend fun reconcileLocked(session: HomeSession) {
        try {
            val messaging = client.messaging ?: error(AuthRepository.FIREBASE_SETUP_MESSAGE)
            val topic = "home-${session.homeId}"
            if (!preferences.alertsEnabled.value || !hasNotificationPermission()) {
                withRegistrationTimeout { messaging.unsubscribeFromTopic(topic).await() }
                preferences.setRegistrationRemovalPending(false)
                if (preferences.alertsEnabled.value && !hasNotificationPermission()) {
                    mutableStatus.value = PushStatus("Notifications are enabled here, but Android permission is off. Allow notifications in system settings.")
                } else mutableStatus.value = PushStatus()
                return
            }
            withRegistrationTimeout { messaging.subscribeToTopic(topic).await() }
            preferences.setRegistrationRemovalPending(false)
            mutableStatus.value = PushStatus(subscribed = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutableStatus.value = PushStatus(failure.message ?: "Couldn't update this device's notification registration.")
            throw failure
        }
    }

    override suspend fun removeBeforeSignOut(session: HomeSession) = mutations.withLock {
        cancelMotionNotifications()
        val removeRegistration = preferences.alertsEnabled.value || preferences.registrationRemovalPending()
        preferences.setAlertsEnabled(false)
        if (removeRegistration) {
            preferences.setRegistrationRemovalPending(true)
            withRegistrationTimeout { registrations.remove(session, preferences.installationId()) }
            preferences.setRegistrationRemovalPending(false)
        }
        mutableStatus.value = PushStatus()
    }

    override suspend fun onRegistered(fid: String) {
        // FCM topic membership is managed by reconcile(), not device tokens.
    }

    override fun shouldDisplayMessage(homeId: String, ownerUid: String): Boolean {
        val session = (authRepository.session.value as? SessionState.SignedIn)?.session ?: return false
        return preferences.alertsEnabled.value && hasNotificationPermission() && session.homeId == homeId &&
            (ownerUid.isBlank() || session.uid == ownerUid)
    }

    private fun cancelMotionNotifications() {
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter { it.tag?.startsWith("motion:") == true }
                .forEach { manager.cancel(it.tag, it.id) }
        } catch (_: SecurityException) {
            // Notification access can change while the active list is read.
        }
    }

    private suspend fun <T> withRegistrationTimeout(block: suspend () -> T): T = try {
        withTimeout(12_000) { block() }
    } catch (timeout: TimeoutCancellationException) {
        if (!currentCoroutineContext().isActive) throw timeout
        throw IllegalStateException("Updating this device's motion alert registration timed out. Check your connection and retry.", timeout)
    }

    private suspend fun installationId(registerWithFcm: Boolean = false): String = withRegistrationTimeout {
        if (registerWithFcm) (client.messaging ?: error(AuthRepository.FIREBASE_SETUP_MESSAGE)).register().await()
        client.installations?.id?.await() ?: error(AuthRepository.FIREBASE_SETUP_MESSAGE)
    }
}
