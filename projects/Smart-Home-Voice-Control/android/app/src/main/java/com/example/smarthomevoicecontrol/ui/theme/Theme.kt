package com.example.smarthomevoicecontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF087E72)
private val LightColors = lightColorScheme(primary = Teal, secondary = Color(0xFF426A64), tertiary = Color(0xFF52606D))
private val DarkColors = darkColorScheme(primary = Color(0xFF6ED8C9), secondary = Color(0xFFA5D0C7), tertiary = Color(0xFFB9C8DA))

@Composable
fun SmartHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
