@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.hotelreception

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// =====================================================================
// ПЕРЕИСПОЛЬЗУЕМЫЕ КОМПОНЕНТЫ (Composable-функции).
// Они ничего не знают про ViewModel: получают данные и лямбды-события параметрами.
// Один и тот же компонент используется на разных экранах.
// =====================================================================

// ---------- Бейджи ----------

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
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// Сплошная цветная плашка статуса уборки (цвета из HotelTheme.kt)
@Composable
fun HkBadge(status: HkStatus, modifier: Modifier = Modifier, text: String = status.label) {
    Surface(
        color = status.color(),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = status.onColor(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ---------- Поиск ----------

// Текст хранится в самом поле (rememberSaveable переживает поворот),
// а наружу сообщаем о каждом изменении: ViewModel применяет фильтр.
@Composable
fun SearchField(
    initialQuery: String,
    label: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf(initialQuery) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onQueryChange(it)
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(HotelIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = {
                    text = ""
                    onQueryChange("")
                }) {
                    Icon(HotelIcons.Close, contentDescription = tr("Очистить"))
                }
            }
        }
    )
}

// ---------- Карточки со статистикой ----------

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
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

// "Факт / план" с полоской прогресса: Arrivals 2 / 5
@Composable
fun ProgressStatCard(
    title: String,
    actual: Int,
    expected: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val fraction = if (expected == 0) 0f else (actual.toFloat() / expected).coerceIn(0f, 1f)
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                // Всегда 2 строки: карточки Arrivals и Departures одинаковой высоты
                minLines = 2,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "$actual / $expected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------- Строки и секции ----------

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChecklistRow(done: Boolean, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (done) HotelIcons.Check else HotelIcons.Close,
            contentDescription = null,
            tint = if (done) PaidGreen else DebtRed,
            modifier = Modifier.size(20.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------- Выбор из нескольких вариантов (чипы) ----------

@Composable
fun <T> ChoiceChips(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    wrap: Boolean = false      // true: кнопки переносятся на новую строку (для окон), false: строка с прокруткой
) {
    if (wrap) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = isSelected(option),
                    onClick = { onClick(option) },
                    label = { Text(label(option)) }
                )
            }
        }
    } else {
        Row(
            modifier = modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = isSelected(option),
                    onClick = { onClick(option) },
                    label = { Text(label(option)) }
                )
            }
        }
    }
}

// ---------- Переключатели с подписью ----------

@Composable
fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun LabeledCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// ---------- Сообщение пользователю (Snackbar) ----------

// Когда во ViewModel появляется сообщение, показываем его и сообщаем, что оно показано.
@Composable
fun MessageEffect(message: String?, hostState: SnackbarHostState, onConsumed: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            hostState.showSnackbar(tr(message))
            onConsumed()
        }
    }
}

// ---------- Диалог выбора даты ----------

@Composable
fun HotelDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("ОК") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    ) {
        DatePicker(state = pickerState)
    }
}

// ---------- Скан паспорта ----------

@Composable
fun PassportImage(path: String?, modifier: Modifier = Modifier) {
    val bitmap = rememberImageBitmap(path)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = tr("Скан паспорта"),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = if (path == null) "Скан паспорта не загружен" else "Загрузка…",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- Баланс: цвет и подпись ----------

fun balanceColor(balance: Int): Color = when {
    balance > 0 -> DebtRed
    balance < 0 -> PrepaidBlue
    else -> PaidGreen
}

fun balanceLabel(balance: Int): String = when {
    balance > 0 -> "Гость должен"
    balance < 0 -> "Оплачено вперёд"
    else -> "Расчёт закрыт"
}

// ---------- Карточка брони (Arrivals, In-house, Все брони) ----------

@Composable
fun ReservationCard(res: Reservation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = res.guest.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Бронь №${res.id} · ${res.roomClass.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(text = res.status.label, color = res.status.color())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${res.arrival.short()} → ${res.departure.short()} · ${nightsText(res.nights)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = res.roomNumber?.let { "Номер $it" } ?: "Номер не назначен",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (res.roomNumber == null) WarnAmber else MaterialTheme.colorScheme.onSurface
                )
            }
            if (res.status == ResStatus.IN_HOUSE) {
                Text(
                    text = "${balanceLabel(res.balance)}: ${res.balance.tenge()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = balanceColor(res.balance)
                )
            }
        }
    }
}

// ---------- Плитка номера (экран "Номера") ----------

@Composable
fun RoomTile(item: RoomItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = item.room.hk
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = status.color(),
            contentColor = status.onColor()
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${item.room.number}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${item.room.roomClass.code} · ${status.label}",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = item.occupancy.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.guest ?: "—",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

// ---------- Карточка запроса в отдел (Sara) ----------

private fun Department.badge(): String = when (this) {
    Department.HOUSEKEEPING -> "HK"
    Department.ROOM_SERVICE -> "RS"
    Department.MAINTENANCE -> "MT"
    Department.IT -> "IT"
}

@Composable
fun RequestCard(request: ServiceRequest, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = request.department.badge(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${request.location} · ${request.department.title}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${request.createdBy} · ${request.createdAt.ago()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (request.attachments.isNotEmpty()) {
                    Text(
                        text = "Вложений: ${request.attachments.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusBadge(text = request.status.label, color = request.status.color())
                if (request.priority) {
                    StatusBadge(text = "Priority", color = DebtRed)
                }
            }
        }
    }
}
