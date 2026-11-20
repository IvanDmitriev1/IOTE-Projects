package com.example.smarthomevoicecontrol

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.smarthomevoicecontrol.features.home.VoiceControlViewModel
import com.example.smarthomevoicecontrol.features.setup.PiSetupViewModel
import com.example.smarthomevoicecontrol.ui.SmartHomeApp
import com.example.smarthomevoicecontrol.ui.theme.SmartHomeTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val container get() = (application as SmartHomeApplication).container
    private val voiceViewModel: VoiceControlViewModel by viewModels { Factory { VoiceControlViewModel(container.configStore, container.piClient) } }
    private val setupViewModel: PiSetupViewModel by viewModels { Factory { PiSetupViewModel(container.configStore, container.piClient) } }
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingNetworkTarget = TARGET_NONE

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when (pendingNetworkTarget) {
            TARGET_VOICE -> if (granted) voiceViewModel.executePendingTranscript() else voiceViewModel.onLocalNetworkPermissionDenied()
            TARGET_SETUP -> setupViewModel.onTestPermissionResult(granted)
        }
        pendingNetworkTarget = TARGET_NONE
    }

    private val speechRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            voiceViewModel.onRecognitionCancelled()
            return@registerForActivityResult
        }
        val transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (transcript.isNullOrEmpty()) {
            voiceViewModel.onRecognitionCancelled("No words were recognized.")
        } else if (needsLocalNetworkPermission()) {
            voiceViewModel.queueTranscript(transcript)
            pendingNetworkTarget = TARGET_VOICE
            permissionRequest.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            voiceViewModel.executeTranscript(transcript)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNetworkTarget = savedInstanceState?.getInt(KEY_PENDING_TARGET) ?: TARGET_NONE
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.US)?.let { ttsReady = it >= TextToSpeech.LANG_AVAILABLE }
            }
        }
        setContent {
            SmartHomeTheme {
                SmartHomeApp(
                    voiceViewModel = voiceViewModel,
                    setupViewModel = setupViewModel,
                    onListen = ::startRecognition,
                    onTestConnection = ::testConnection,
                    onOpenSettings = ::openAppSettings,
                    onSpeak = { text -> if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice-summary") }
                )
            }
        }
    }

    private fun startRecognition() {
        if (!voiceViewModel.beginListening()) return
        tts?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a sensor or light command")
        }
        try {
            speechRequest.launch(intent)
        } catch (_: ActivityNotFoundException) {
            voiceViewModel.onRecognitionCancelled("No speech recognition provider is available.")
        }
    }

    private fun testConnection() {
        if (needsLocalNetworkPermission()) {
            if (pendingNetworkTarget != TARGET_NONE) return
            setupViewModel.requestTestPermission()
            pendingNetworkTarget = TARGET_SETUP
            permissionRequest.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else setupViewModel.testConnection()
    }

    private fun needsLocalNetworkPermission() = Build.VERSION.SDK_INT >= 37 &&
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_PENDING_TARGET, pendingNetworkTarget)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private class Factory<T : ViewModel>(private val create: () -> T) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }

    private companion object {
        const val TARGET_NONE = 0
        const val TARGET_VOICE = 1
        const val TARGET_SETUP = 2
        const val KEY_PENDING_TARGET = "pending_local_network_target"
    }
}
