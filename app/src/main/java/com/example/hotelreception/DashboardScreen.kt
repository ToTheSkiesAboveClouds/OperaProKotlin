package com.example.hotelreception

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// =====================================================================
// ЭКРАН "ГЛАВНАЯ": статистика дня. Все четыре карточки нажимаются:
//   Arrivals       -> Брони / Arrivals
//   Departures     -> Брони / In-house, выселенные
//   Гостей в отеле -> Брони / In-house, проживающие
//   Загрузка       -> отдельный экран с таблицей доступности
// Экран только показывает состояние и сообщает о нажатиях (state hoisting).
// =====================================================================

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenArrivals: () -> Unit,
    onOpenDeparted: () -> Unit,
    onOpenInHouse: () -> Unit,
    onOpenAvailability: () -> Unit,
    modifier: Modifier = Modifier
) {
    // collectAsStateWithLifecycle: подписка живёт, только пока экран виден (lifecycle-aware)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сегодня, ${state.today.full()}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Нажмите на карточку, чтобы открыть список",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Выбор языка стоит здесь, а не в верхней панели
                LanguageMenu()
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProgressStatCard(
                    title = "Arrivals (actual / expected)",
                    actual = stats.arrivalsActual,
                    expected = stats.arrivalsExpected,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onOpenArrivals
                )
                ProgressStatCard(
                    title = "Departures (actual / expected)",
                    actual = stats.departuresActual,
                    expected = stats.departuresExpected,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onOpenDeparted
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Гостей в отеле",
                    value = "${stats.inHouse}",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onOpenInHouse
                )
                StatCard(
                    label = "Загрузка отеля · доступность номеров ›",
                    value = "${stats.occupancyPercent}%",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onOpenAvailability
                )
            }
        }
        item {
            SectionCard(title = "Состояние номеров") {
                RoomStatusOverview(counts = stats.hkCounts)
            }
        }
    }
}

// Состояние номеров: цветная полоса пропорций и плитки по каждому статусу.
// Плиток по две в ряд, поэтому видны все пять статусов, включая Maintenance.
@Composable
private fun RoomStatusOverview(counts: Map<HkStatus, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
        ) {
            HkStatus.entries.forEach { status ->
                val count = counts[status] ?: 0
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight()
                            .background(status.color())
                    )
                }
            }
        }
        HkStatus.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { status ->
                    StatusTile(status = status, count = counts[status] ?: 0, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusTile(status: HkStatus, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = status.color().copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(status.color(), CircleShape)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
