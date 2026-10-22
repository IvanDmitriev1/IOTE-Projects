package com.example.rgbstripcontroller.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF087E72),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEEEA),
    onPrimaryContainer = Color(0xFF172421),
    secondary = Color(0xFF4A635E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7E4),
    onSecondaryContainer = Color(0xFF1C2826),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF172421),
    surface = Color(0xFFF6F8F7),
    onSurface = Color(0xFF172421),
    surfaceVariant = Color(0xFFE9EFED),
    onSurfaceVariant = Color(0xFF394744),
    outline = Color(0xFF586864),
    outlineVariant = Color(0xFFE1E8E5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD1C3),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFDDEEEA),
    secondary = Color(0xFFB8CCC6),
    onSecondary = Color(0xFF20332F),
    secondaryContainer = Color(0xFF354A45),
    onSecondaryContainer = Color(0xFFDCE7E4),
    background = Color(0xFF101816),
    onBackground = Color(0xFFE1E8E5),
    surface = Color(0xFF101816),
    onSurface = Color(0xFFE1E8E5),
    surfaceVariant = Color(0xFF293532),
    onSurfaceVariant = Color(0xFFC0CCC8),
    outline = Color(0xFF899692),
    outlineVariant = Color(0xFF3F4B47),
)

@Composable
fun RgbStripTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

