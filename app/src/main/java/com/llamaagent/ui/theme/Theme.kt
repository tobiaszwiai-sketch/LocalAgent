package com.llamaagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0A1B3D),
    primaryContainer = Color(0xFF243B6B),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFF9DCBFF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE3E3E6),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFE3E3E6),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFFC3C7D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3A5BDC),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E)
)

@Composable
fun LlamaAgentTheme(
    darkTheme: Boolean = true, // ciemny motyw domyślnie
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
