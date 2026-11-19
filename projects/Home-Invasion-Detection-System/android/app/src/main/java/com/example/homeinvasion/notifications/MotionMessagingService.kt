package com.example.homeinvasion.notifications

import android.app.PendingIntent
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.homeinvasion.HomeInvasionApplication
import com.example.homeinvasion.MainActivity
import com.example.homeinvasion.R
import com.example.homeinvasion.data.PushCoordinator
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MotionMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushCoordinator: PushCoordinator
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onRegistered(installationId: String) {
        serviceScope.launch { pushCoordinator.onRegistered(installationId) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "motion") return
        val eventId = message.data["eventId"]?.takeIf(String::isNotBlank) ?: return
        val homeId = message.data["homeId"]?.takeIf(String::isNotBlank) ?: return
        val ownerUid = message.data["ownerUid"]?.takeIf(String::isNotBlank).orEmpty()
        if (!pushCoordinator.shouldDisplayMessage(homeId, ownerUid)) return
        if (!pushCoordinator.hasNotificationPermission()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
            data = "homewatch://motion/$eventId".toUri()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, HomeInvasionApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Motion detected")
            .setContentText("Open Home Watch to view the snapshot.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify("motion:$homeId:$eventId", 0, notification)
        } catch (_: SecurityException) {
            // Notification permission can be revoked after the check above.
        }
    }
}
