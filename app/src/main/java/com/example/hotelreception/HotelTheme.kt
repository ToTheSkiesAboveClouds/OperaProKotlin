package com.example.hotelreception

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// =====================================================================
// ТЕМА Material 3 и цвета статусов. Цвета заданы один раз и используются
// везде одинаково (единый стиль): экраны сами цвета не выбирают.
// =====================================================================

// Акцентный цвет приложения: синий из логотипа Qonaev (0C4273).
// Им подсвечиваются выбранный раздел в нижней панели, выбранные фильтры и главные кнопки.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0C4273),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E4F7),
    onPrimaryContainer = Color(0xFF062A4D),
    secondary = Color(0xFF0C4273),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBBD5F2),   // подсветка выбранного (нижняя панель, чипы)
    onSecondaryContainer = Color(0xFF062A4D),
    tertiary = Color(0xFF2F6DA5),
    background = Color(0xFFF6F9FC),
    onBackground = Color(0xFF111C26),
    surface = Color(0xFFF6F9FC),
    onSurface = Color(0xFF111C26),
    surfaceVariant = Color(0xFFDEE6EE),
    onSurfaceVariant = Color(0xFF41505F),
    outline = Color(0xFF73818F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5FA),
    surfaceContainer = Color(0xFFEAF0F6),
    surfaceContainerHigh = Color(0xFFE4EBF2),
    surfaceContainerHighest = Color(0xFFDDE5ED)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC8F0),
    onPrimary = Color(0xFF062A4D),
    primaryContainer = Color(0xFF16406B),
    onPrimaryContainer = Color(0xFFD4E4F7),
    secondary = Color(0xFF9CC8F0),
    onSecondary = Color(0xFF062A4D),
    secondaryContainer = Color(0xFF1F4E7E),
    onSecondaryContainer = Color(0xFFD4E4F7),
    tertiary = Color(0xFF8FB8E0),
    background = Color(0xFF0F1A24),
    onBackground = Color(0xFFE3EAF1),
    surface = Color(0xFF0F1A24),
    onSurface = Color(0xFFE3EAF1),
    surfaceVariant = Color(0xFF2C3946),
    onSurfaceVariant = Color(0xFFBAC7D4),
    outline = Color(0xFF8593A0),
    surfaceContainerLowest = Color(0xFF0A131B),
    surfaceContainerLow = Color(0xFF14212C),
    surfaceContainer = Color(0xFF18262F),
    surfaceContainerHigh = Color(0xFF1F2E3A),
    surfaceContainerHighest = Color(0xFF273745)
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

// ---------- Цвета статусов уборки ----------
// Inspected — зелёный, Clean — синий, Dirty — красный, Pickup — жёлтый, Maintenance — чёрный
fun HkStatus.color(): Color = when (this) {
    HkStatus.INSPECTED -> Color(0xFF2E7D32)
    HkStatus.CLEAN -> Color(0xFF1565C0)
    HkStatus.DIRTY -> Color(0xFFC62828)
    HkStatus.PICKUP -> Color(0xFFF9A825)
    HkStatus.MAINTENANCE -> Color(0xFF212121)
}

// Цвет текста на плашке статуса (на жёлтом читается только тёмный)
fun HkStatus.onColor(): Color =
    if (this == HkStatus.PICKUP) Color(0xFF212121) else Color.White

fun Occupancy.color(): Color = when (this) {
    Occupancy.VACANT -> Color(0xFF607D8B)
    Occupancy.OCCUPIED -> Color(0xFF6A1B9A)
    Occupancy.ON_ARRIVAL -> Color(0xFF00838F)
}

fun ResStatus.color(): Color = when (this) {
    ResStatus.CONFIRMED -> Color(0xFF1565C0)
    ResStatus.IN_HOUSE -> Color(0xFF2E7D32)
    ResStatus.CHECKED_OUT -> Color(0xFF757575)
}

fun RequestStatus.color(): Color = when (this) {
    RequestStatus.NEW -> Color(0xFF1565C0)
    RequestStatus.ACCEPTED -> Color(0xFF2E7D32)
    RequestStatus.DONE -> Color(0xFF757575)
}

val DebtRed = Color(0xFFC62828)
val PaidGreen = Color(0xFF2E7D32)
val PrepaidBlue = Color(0xFF1565C0)
val WarnAmber = Color(0xFFEF6C00)
