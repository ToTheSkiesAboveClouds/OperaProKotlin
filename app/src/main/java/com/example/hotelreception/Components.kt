package com.example.hotelreception

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

// Форматирование денег: 25000 -> "25 000 ₸"
fun Int.tenge(): String = "%,d".format(Locale.US, this).replace(',', ' ') + " ₸"

// ---------- Reusable #1: бейдж статуса ----------
@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ---------- Reusable #2: поле поиска ----------
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Поиск: номер или тип") },
        leadingIcon = { Icon(HotelIcons.Search, contentDescription = null) }
    )
}

// ---------- Reusable #3: карточка номера ----------
@Composable
fun RoomCard(
    room: Room,
    booking: Booking?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when {
        booking != null -> "Занят" to BusyRed
        room.isDirty -> "Уборка" to DirtyOrange
        else -> "Свободен" to FreeGreen
    }
    val icon = when (room.type) {
        RoomType.STANDARD -> HotelIcons.Home
        RoomType.DELUXE -> HotelIcons.Favorite
        RoomType.SUITE -> HotelIcons.Star
    }
    val subtitle = if (booking != null) {
        val vip = if (booking.guest.isVip) " ★" else ""
        "${booking.guest.name}$vip, ${booking.nights} ноч."
    } else {
        "Этаж ${room.floor} · ${room.type.pricePerNight.tenge()}/ночь"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    imageVector = icon,
                    contentDescription = room.type.label,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "№${room.number} · ${room.type.label}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(text = statusText, color = statusColor)
        }
    }
}

// ---------- Reusable #4: карточка со статистикой ----------
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ---------- Reusable #5: строка "название — значение" (счёт, детали) ----------
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ---------- Демо: remember vs rememberSaveable vs ViewModel ----------
@Composable
fun StateDemoCard(
    vmCounter: Int,
    onVmIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Живёт, пока Composable в композиции. При повороте экрана ОБНУЛИТСЯ.
    var rememberCount by remember { mutableStateOf(0) }
    // Кладётся в Bundle (savedInstanceState). При повороте СОХРАНИТСЯ.
    var savedCount by rememberSaveable { mutableStateOf(0) }
    // vmCounter приходит из ViewModel, которая переживает поворот.

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Демо: поворот экрана", style = MaterialTheme.typography.titleMedium)
            Text(
                "Нажми +1 в каждой строке, затем поверни устройство.",
                style = MaterialTheme.typography.bodySmall
            )
            CounterRow("remember", rememberCount) { rememberCount++ }
            CounterRow("rememberSaveable", savedCount) { savedCount++ }
            CounterRow("ViewModel", vmCounter, onVmIncrement)
        }
    }
}

@Composable
private fun CounterRow(label: String, value: Int, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Text(
            text = value.toString(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Button(onClick = onIncrement) { Text("+1") }
    }
}
