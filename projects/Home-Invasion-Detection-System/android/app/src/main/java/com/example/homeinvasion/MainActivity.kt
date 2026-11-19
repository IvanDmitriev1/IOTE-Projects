package com.example.homeinvasion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.homeinvasion.ui.HomeWatchApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val pendingEventId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingEventId.value = intent?.getStringExtra(EXTRA_EVENT_ID)
        setContent {
            val eventId = pendingEventId.collectAsStateWithLifecycle().value
            HomeWatchApp(eventId = eventId, onEventConsumed = { if (pendingEventId.value == it) pendingEventId.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingEventId.value = intent.getStringExtra(EXTRA_EVENT_ID)
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}
