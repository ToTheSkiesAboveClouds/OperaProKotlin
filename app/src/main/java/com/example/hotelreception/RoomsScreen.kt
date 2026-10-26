package com.example.hotelreception

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

// =====================================================================
// ЭКРАН "НОМЕРА": состояние всех номеров цветами + три переключателя занятости.
// Цвета: Inspected — зелёный, Clean — синий, Dirty — красный, Pickup — жёлтый, Maintenance — чёрный.
// =====================================================================

@Composable
fun RoomsScreen(
    viewModel: RoomsViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    // Какой номер сейчас открыт в диалоге (хранится только номер, а не весь объект)
    var selectedNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    val filters = state.filters

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Три переключателя: Vacant / Occupied / On arrival (можно включить несколько)
            ChoiceChips(
                options = Occupancy.entries.toList(),
                isSelected = { it in filters.occupancy },
                label = { "${it.label} (${state.occupancyCounts[it] ?: 0})" },
                onClick = { viewModel.toggleOccupancy(it) }
            )
            // Два выпадающих списка вместо десяти кнопок: этаж и статус уборки
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownFilter(
                    label = "Этаж",
                    options = listOf<Int?>(null) + state.floors,
                    selected = filters.floor,
                    optionLabel = { if (it == null) "все" else "$it" },
                    onSelect = { viewModel.selectFloor(it) },
                    modifier = Modifier.weight(1f)
                )
                DropdownFilter(
                    label = "Статус",
                    options = listOf<HkStatus?>(null) + HkStatus.entries,
                    selected = filters.hk,
                    optionLabel = { if (it == null) "все" else "${it.label} ${state.hkCounts[it] ?: 0}" },
                    onSelect = { viewModel.selectHkFilter(it) },
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = { viewModel.cleanAllDirty() },
                enabled = !state.isCleaning
            ) {
                Text(if (state.isCleaning) "Горничные убирают…" else "Убрать все Dirty (${state.hkCounts[HkStatus.DIRTY] ?: 0})")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = state.items, key = { it.room.number }) { item ->
                RoomTile(item = item, onClick = { selectedNumber = item.room.number })
            }
        }
    }

    val selected = state.items.firstOrNull { it.room.number == selectedNumber }
    if (selected != null) {
        RoomStatusDialog(
            item = selected,
            onSet = { status ->
                viewModel.setHk(selected.room.number, status)
                selectedNumber = null
            },
            onDismiss = { selectedNumber = null }
        )
    }
}

@Composable
private fun RoomStatusDialog(
    item: RoomItem,
    onSet: (HkStatus) -> Unit,
    onDismiss: () -> Unit
) {
    val room = item.room
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Номер ${room.number} · ${room.roomClass.code}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow("Занятость", item.occupancy.label)
                InfoRow("Гость", item.guest ?: "—")
                InfoRow("Этаж", room.floor.toString())
                Text(
                    text = "Сменить статус уборки:",
                    style = MaterialTheme.typography.labelLarge
                )
                HkStatus.entries.forEach { status ->
                    // Inspected можно поставить только после Clean (проверка старшей горничной)
                    val allowed = status != room.hk && (status != HkStatus.INSPECTED || room.hk == HkStatus.CLEAN)
                    OutlinedButton(
                        onClick = { onSet(status) },
                        enabled = allowed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(status.color(), CircleShape)
                            )
                            Text(text = status.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
