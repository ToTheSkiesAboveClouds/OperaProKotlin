package com.example.hotelreception

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Статусы номеров: используются везде одинаково (единый стиль)
val FreeGreen = Color(0xFF2E7D32)
val BusyRed = Color(0xFFC62828)
val DirtyOrange = Color(0xFFEF6C00)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B4B72),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F7),
    onPrimaryContainer = Color(0xFF0B2A44),
    secondaryContainer = Color(0xFFF3E7CF),
    onSecondaryContainer = Color(0xFF3A2D12)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC8F0),
    onPrimary = Color(0xFF0B2A44),
    primaryContainer = Color(0xFF1F3F5C),
    onPrimaryContainer = Color(0xFFD3E4F7),
    secondaryContainer = Color(0xFF4A3D22),
    onSecondaryContainer = Color(0xFFF3E7CF)
)

@Composable
fun HotelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
