package com.example.homeinvasion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HomeInvasionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Motion alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Alerts when the home sensor detects motion" },
        )
    }

    companion object {
        const val CHANNEL_ID = "motion_alerts"
    }
}
