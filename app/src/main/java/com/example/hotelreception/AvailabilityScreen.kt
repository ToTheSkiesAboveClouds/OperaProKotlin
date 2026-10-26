package com.example.hotelreception

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// =====================================================================
// ЭКРАН "ДОСТУПНОСТЬ НОМЕРОВ": таблица на неделю на весь экран.
// Открывается нажатием на карточку "Загрузка отеля" на главной.
// =====================================================================

private val classColumnWidth = 84.dp

@Composable
fun AvailabilityScreen(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Загрузка отеля: ${state.stats.occupancyPercent}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Сколько номеров каждого класса свободно в каждый из 7 дней. " +
                "Красный фон: мест нет, оранжевый: остался один номер.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        AvailabilityTable(days = state.days, rows = state.availability)
    }
}

// Таблица: строки — классы номеров, столбцы — даты. Ширина дня подстраивается под экран.
@Composable
private fun AvailabilityTable(days: List<LocalDate>, rows: List<AvailabilityRow>) {
    val locale = AppLanguage.locale
    BoxWithConstraints {
        val dayWidth: Dp = ((maxWidth - classColumnWidth) / days.size.coerceAtLeast(1)).coerceAtLeast(44.dp)
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Класс",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(classColumnWidth)
                )
                days.forEach { day ->
                    Column(
                        modifier = Modifier.width(dayWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = day.short(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.width(classColumnWidth)) {
                        Text(
                            text = row.roomClass.code,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "всего ${row.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    row.free.forEach { free -> AvailabilityCell(free, dayWidth) }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun AvailabilityCell(free: Int, width: Dp) {
    val background = when {
        free == 0 -> DebtRed.copy(alpha = 0.25f)
        free == 1 -> WarnAmber.copy(alpha = 0.25f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$free",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
