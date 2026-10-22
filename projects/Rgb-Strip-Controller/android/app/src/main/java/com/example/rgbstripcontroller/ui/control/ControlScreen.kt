package com.example.rgbstripcontroller.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import com.example.rgbstripcontroller.R
import com.example.rgbstripcontroller.data.model.MAX_RGB_CHANNEL_VALUE
import com.example.rgbstripcontroller.data.model.LightState
import com.example.rgbstripcontroller.data.model.RainbowDirection
import com.example.rgbstripcontroller.ui.theme.RgbStripTheme
import java.util.Locale

@Composable
fun ControlScreen(
    state: ControlUiState,
    onEndpointChanged: (String) -> Unit,
    onPowerChanged: (Boolean) -> Unit,
    onRedChanged: (Int) -> Unit,
    onGreenChanged: (Int) -> Unit,
    onBlueChanged: (Int) -> Unit,
    onApply: () -> Unit,
    onRainbow: (RainbowDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val invalidUrl = state.showEndpointError
    val powerLabel = stringResource(R.string.power)
    val powerState = stringResource(if (state.light.isOn) R.string.selected_on else R.string.selected_off)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .consumeWindowInsets(insets),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.screen_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.screen_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = state.endpointUrl,
                            onValueChange = onEndpointChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.controller_url)) },
                            placeholder = { Text(stringResource(R.string.controller_url_placeholder)) },
                            singleLine = true,
                            enabled = !state.isSending,
                            isError = invalidUrl,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            supportingText = if (invalidUrl) {
                                { Text(stringResource(R.string.invalid_url)) }
                            } else {
                                null
                            },
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 76.dp)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = powerLabel,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = powerState,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.light.isOn,
                            onCheckedChange = onPowerChanged,
                            enabled = !state.isSending,
                            modifier = Modifier.semantics {
                                contentDescription = powerLabel
                                stateDescription = powerState
                            },
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(state.light.selectedColor()),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = stringResource(R.string.selected_color),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.light.hexValue(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(
                                    R.string.rgb_values,
                                    state.light.red,
                                    state.light.green,
                                    state.light.blue,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.color),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ChannelSlider(
                            label = stringResource(R.string.red),
                            value = state.light.red,
                            maximum = MAX_RGB_CHANNEL_VALUE,
                            enabled = !state.isSending,
                            onValueChanged = onRedChanged,
                        )
                        ChannelSlider(
                            label = stringResource(R.string.green),
                            value = state.light.green,
                            maximum = MAX_RGB_CHANNEL_VALUE,
                            enabled = !state.isSending,
                            onValueChanged = onGreenChanged,
                        )
                        ChannelSlider(
                            label = stringResource(R.string.blue),
                            value = state.light.blue,
                            maximum = MAX_RGB_CHANNEL_VALUE,
                            enabled = !state.isSending,
                            onValueChanged = onBlueChanged,
                        )
                        Text(
                            text = stringResource(R.string.rgb_values_local),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                Button(
                    onClick = onApply,
                    enabled = state.canApply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = stringResource(if (state.isSending) R.string.applying else R.string.apply),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Button(
                    onClick = { onRainbow(RainbowDirection.FORWARD) },
                    enabled = state.canSetRainbow,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text(stringResource(R.string.rainbow_forward)) }
                Button(
                    onClick = { onRainbow(RainbowDirection.REVERSE) },
                    enabled = state.canSetRainbow,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text(stringResource(R.string.rainbow_reverse)) }
                Text(
                    text = stringResource(state.status.messageResource()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ApplyStatus.messageResource(): Int = when (this) {
    ApplyStatus.Idle -> R.string.status_idle
    ApplyStatus.Sending -> R.string.applying
    ApplyStatus.Succeeded -> R.string.status_succeeded
    ApplyStatus.Failed -> R.string.status_failed
}

private fun LightState.selectedColor(): Color = Color(
    red = red / MAX_RGB_CHANNEL_VALUE.toFloat(),
    green = green / MAX_RGB_CHANNEL_VALUE.toFloat(),
    blue = blue / MAX_RGB_CHANNEL_VALUE.toFloat(),
)

private fun LightState.hexValue(): String =
    String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue)

@Preview(name = "Light", showSystemUi = true)
@Composable
private fun ControlScreenLightPreview() {
    RgbStripTheme(darkTheme = false) {
        ControlScreen(
            state = ControlUiState(),
            onEndpointChanged = {},
            onPowerChanged = {},
            onRedChanged = {},
            onGreenChanged = {},
            onBlueChanged = {},
            onApply = {},
            onRainbow = {},
        )
    }
}

@Preview(name = "Dark", showSystemUi = true)
@Composable
private fun ControlScreenDarkPreview() {
    RgbStripTheme(darkTheme = true) {
        ControlScreen(
            state = ControlUiState(),
            onEndpointChanged = {},
            onPowerChanged = {},
            onRedChanged = {},
            onGreenChanged = {},
            onBlueChanged = {},
            onApply = {},
            onRainbow = {},
        )
    }
}

@Preview(name = "Large text", fontScale = 2f)
@Composable
private fun ControlScreenLargeTextPreview() {
    RgbStripTheme(darkTheme = false) {
        ControlScreen(
            state = ControlUiState(),
            onEndpointChanged = {},
            onPowerChanged = {},
            onRedChanged = {},
            onGreenChanged = {},
            onBlueChanged = {},
            onApply = {},
            onRainbow = {},
        )
    }
}

