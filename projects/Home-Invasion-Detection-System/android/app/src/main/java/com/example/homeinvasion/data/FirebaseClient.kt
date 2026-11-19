package com.example.homeinvasion.data

import android.content.Context
import com.example.homeinvasion.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseClient @Inject constructor(@ApplicationContext context: Context) {
    val applicationContext: Context = context.applicationContext
    private val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    private val realtimeDatabase = app?.let {
        val overrideUrl = BuildConfig.REALTIME_DATABASE_URL.takeIf { it.isNotBlank() }
        if (overrideUrl == null && it.options.databaseUrl.isNullOrBlank()) null
        else (overrideUrl?.let { url -> FirebaseDatabase.getInstance(it, url) } ?: FirebaseDatabase.getInstance(it))
            .apply { setPersistenceEnabled(true) }
    }
    val configured: Boolean get() = app != null
    val database: FirebaseDatabase? get() = realtimeDatabase
    val storage: FirebaseStorage? get() = app?.let(FirebaseStorage::getInstance)
    val messaging: FirebaseMessaging? get() = app?.let { FirebaseMessaging.getInstance() }
    val installations: FirebaseInstallations? get() = app?.let(FirebaseInstallations::getInstance)
}
