package com.example.hotelreception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

// =====================================================================
// ЭКРАН "БРОНИ": три вкладки — Arrivals, In-house, Все брони.
// На виду только вкладки и поиск. Остальные фильтры спрятаны в окно (значок фильтра).
// Фильтры лежат во ViewModel (переживают поворот), текст поиска — в самом поле.
// =====================================================================

@Composable
fun ReservationsScreen(
    viewModel: ReservationsViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenReservation: (Int) -> Unit,
    onNewReservation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    val filters = state.filters
    // Открыто ли окно фильтров и диалог выбора даты: простое временное состояние интерфейса
    var showFilters by rememberSaveable { mutableStateOf(false) }
    // Какой календарь открыт: 0 — нет, 1 — дата начала, 2 — дата окончания
    var openedPicker by rememberSaveable { mutableStateOf(0) }

    // Сколько фильтров из окна сейчас включено (показывается цифрой на значке)
    val activeFilters = when (filters.tab) {
        ResTab.ARRIVALS -> if (filters.arrivalRange != ArrivalRange.TODAY) 1 else 0
        ResTab.IN_HOUSE -> if (filters.inHouseMode != InHouseMode.IN) 1 else 0
        ResTab.ALL -> (if (filters.status != null) 1 else 0) + (if (filters.dateFrom != null) 1 else 0) + (if (filters.dateTo != null) 1 else 0)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChoiceChips(
                    options = ResTab.entries.toList(),
                    isSelected = { it == filters.tab },
                    label = { it.title },
                    onClick = { viewModel.selectTab(it) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchField(
                        initialQuery = filters.query,
                        label = "Бронь, комната, гость",
                        onQueryChange = { viewModel.onQueryChange(it) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterButton(activeCount = activeFilters, onClick = { showFilters = true })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Найдено: ${state.items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (filters.tab == ResTab.ARRIVALS) {
                        TextButton(onClick = { viewModel.autoAssign() }) {
                            Text("Назначить номера автоматически")
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.items.isEmpty()) {
                    item {
                        EmptyState(
                            when (filters.tab) {
                                ResTab.ARRIVALS -> "Нет ожидаемых заездов"
                                ResTab.IN_HOUSE -> "Здесь пока никого нет"
                                ResTab.ALL -> "Ничего не найдено"
                            }
                        )
                    }
                }
                items(items = state.items, key = { it.id }) { reservation ->
                    ReservationCard(
                        res = reservation,
                        onClick = { onOpenReservation(reservation.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onNewReservation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(HotelIcons.Add, contentDescription = tr("Новая бронь"))
        }
    }

    if (showFilters) {
        FilterDialog(
            title = "Фильтры: ${filters.tab.title}",
            onDismiss = { showFilters = false },
            onReset = { viewModel.resetFilters() }
        ) {
            when (filters.tab) {
                ResTab.ARRIVALS -> {
                    Text("Какие заезды показать", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(
                        options = ArrivalRange.entries.toList(),
                        isSelected = { it == filters.arrivalRange },
                        label = { it.label },
                        onClick = { viewModel.selectArrivalRange(it) },
                        wrap = true
                    )
                }

                ResTab.IN_HOUSE -> {
                    Text("Какие гости", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(
                        options = InHouseMode.entries.toList(),
                        isSelected = { it == filters.inHouseMode },
                        label = { it.label },
                        onClick = { viewModel.selectInHouseMode(it) },
                        wrap = true
                    )
                }

                ResTab.ALL -> {
                    Text("Статус брони", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(
                        options = listOf<ResStatus?>(null) + ResStatus.entries,
                        isSelected = { it == filters.status },
                        label = {
                            when (it) {
                                null -> "Все"
                                ResStatus.CONFIRMED -> "Будущие"
                                ResStatus.IN_HOUSE -> "Проживают"
                                ResStatus.CHECKED_OUT -> "Прошлые"
                            }
                        },
                        onClick = { viewModel.selectStatus(it) },
                        wrap = true
                    )
                    Text("Даты брони", style = MaterialTheme.typography.labelLarge)
                    DateFilterRow(
                        label = "Начало",
                        date = filters.dateFrom,
                        onPick = { openedPicker = 1 },
                        onClear = { viewModel.selectDateFrom(null) }
                    )
                    DateFilterRow(
                        label = "Окончание",
                        date = filters.dateTo,
                        onPick = { openedPicker = 2 },
                        onClear = { viewModel.selectDateTo(null) }
                    )
                }
            }
        }
    }

    if (openedPicker != 0) {
        val isStart = openedPicker == 1
        HotelDatePickerDialog(
            initial = (if (isStart) filters.dateFrom else filters.dateTo) ?: state.today,
            onDismiss = { openedPicker = 0 },
            onPick = { if (isStart) viewModel.selectDateFrom(it) else viewModel.selectDateTo(it) }
        )
    }
}

// Строка выбора даты: подпись, кнопка с датой и крестик «убрать»
@Composable
private fun DateFilterRow(label: String, date: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        FilterChip(
            selected = date != null,
            onClick = onPick,
            label = { Text(date?.full() ?: "Выбрать дату") }
        )
        if (date != null) {
            TextButton(onClick = onClear) { Text("Убрать") }
        }
    }
}
