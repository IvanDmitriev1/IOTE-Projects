package com.example.homeinvasion.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.homeinvasion.data.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onCheckAccess: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showRationale by rememberSaveable { mutableStateOf(false) }
    val permissionPreferences = remember(context) { context.getSharedPreferences("home_watch", android.content.Context.MODE_PRIVATE) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setAlertsEnabled(true) else viewModel.onPermissionDenied()
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermission() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Motion alerts", style = MaterialTheme.typography.titleMedium)
                            Text("Notify this phone when the sensor detects motion.", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.busy) CircularProgressIndicator()
                        else Switch(
                            checked = state.alertsEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) viewModel.setAlertsEnabled(false)
                                else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    val activity = context as? Activity
                                    when {
                                        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS) -> showRationale = true
                                        permissionPreferences.getBoolean(KEY_PERMISSION_REQUESTED, false) -> context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                        else -> {
                                            permissionPreferences.edit { putBoolean(KEY_PERMISSION_REQUESTED, true) }
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                } else viewModel.setAlertsEnabled(true)
                            },
                        )
                    }
                    if (!state.notificationPermissionGranted) {
                        Spacer(Modifier.height(8.dp))
                        Text("Android notifications are off for this app.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }) { Text("Open Android notification settings") }
                    }
                    if (state.alertsEnabled && state.notificationPermissionGranted) {
                        Text(
                            if (state.subscribed) "Subscribed to motion alerts." else "Waiting for notification subscription…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.error?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.setAlertsEnabled(state.alertsEnabled) }) { Text("Retry") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showRationale) AlertDialog(
        onDismissRequest = { showRationale = false },
        title = { Text("Allow motion alerts") },
        text = { Text("Android notification access is needed to alert this phone when motion is detected.") },
        confirmButton = {
            TextButton(onClick = {
                showRationale = false
                if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else viewModel.setAlertsEnabled(true)
            }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not now") } },
    )
}

private const val KEY_PERMISSION_REQUESTED = "notification_permission_requested"
