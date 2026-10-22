package com.example.rgbstripcontroller

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rgbstripcontroller.ui.theme.RgbStripTheme
import com.example.rgbstripcontroller.ui.control.ControlScreen
import com.example.rgbstripcontroller.ui.control.ControlViewModel
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ControlViewModel by viewModels()
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted)
            showControls()
        else
            finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        ) {
            showControls()
        } else {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    private fun showControls() {
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            RgbStripTheme {
                ControlScreen(
                    state = state,
                    onEndpointChanged = viewModel::onEndpointChanged,
                    onPowerChanged = viewModel::onPowerChanged,
                    onRedChanged = viewModel::onRedChanged,
                    onGreenChanged = viewModel::onGreenChanged,
                    onBlueChanged = viewModel::onBlueChanged,
                    onApply = viewModel::onApply,
                    onRainbow = viewModel::onRainbow,
                )
            }
        }
    }
}
