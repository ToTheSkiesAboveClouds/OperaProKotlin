package com.example.hotelreception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotelScreen(viewModel: HotelViewModel = viewModel()) {
    // UI только читает состояние и вызывает функции ViewModel
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Almaty Grand · Ресепшн") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = "Занято",
                        value = "${state.occupiedCount} / ${state.rooms.size}",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "К уборке",
                        value = state.dirtyCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Выручка",
                        value = state.revenue.tenge(),
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }

            item { SearchField(query = state.query, onQueryChange = viewModel::onQueryChange) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.onlyFree,
                        onCheckedChange = viewModel::onOnlyFreeChange
                    )
                    Text("Только свободные", modifier = Modifier.weight(1f))
                    Button(
                        onClick = viewModel::cleanRooms,
                        enabled = state.dirtyCount > 0 && !state.isCleaning
                    ) {
                        Text(if (state.isCleaning) "Убираем…" else "Убрать (${state.dirtyCount})")
                    }
                }
            }

            item {
                StateDemoCard(
                    vmCounter = state.vmCounter,
                    onVmIncrement = viewModel::incrementCounter
                )
            }

            items(items = state.visibleRooms, key = { it.number }) { room ->
                RoomCard(
                    room = room,
                    booking = state.bookings[room.number],
                    onClick = { viewModel.selectRoom(room.number) }
                )
            }

            if (state.visibleRooms.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ничего не найдено")
                    }
                }
            }
        }
    }

    // ---------- Диалоги ----------
    val selectedRoom = state.selectedRoom?.let { number ->
        state.rooms.firstOrNull { it.number == number }
    }
    if (selectedRoom != null) {
        val booking = state.bookings[selectedRoom.number]
        if (booking == null) {
            CheckInDialog(
                room = selectedRoom,
                onConfirm = { name, nights, vip ->
                    viewModel.checkIn(selectedRoom.number, name, nights, vip)
                },
                onDismiss = { viewModel.selectRoom(null) }
            )
        } else {
            ManageBookingDialog(
                booking = booking,
                onAddService = { order -> viewModel.addService(selectedRoom.number, order) },
                onCheckOut = { viewModel.checkOut(selectedRoom.number) },
                onDismiss = { viewModel.selectRoom(null) }
            )
        }
    }

    state.receipt?.let { receipt ->
        ReceiptDialog(booking = receipt, onDismiss = viewModel::dismissReceipt)
    }
}

// Поля формы — локальное состояние UI, поэтому rememberSaveable:
// пережить поворот надо, а во ViewModel они не нужны.
@Composable
private fun CheckInDialog(
    room: Room,
    onConfirm: (name: String, nights: Int, isVip: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var nights by rememberSaveable { mutableStateOf("1") }
    var isVip by rememberSaveable { mutableStateOf(false) }

    val nightsInt = nights.toIntOrNull()
    val canConfirm = name.isNotBlank() && nightsInt != null && nightsInt in 1..30 && !room.isDirty

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заселение в №${room.number}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${room.type.label}, ${room.type.pricePerNight.tenge()}/ночь")
                if (room.isDirty) {
                    Text(
                        "Номер ещё не убран. Сначала нажми «Убрать».",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя гостя") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = nights,
                    onValueChange = { nights = it.filter(Char::isDigit) },
                    label = { Text("Ночей (1–30)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isVip, onCheckedChange = { isVip = it })
                    Text("VIP-гость (скидка 10%)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, nightsInt ?: 1, isVip) },
                enabled = canConfirm
            ) { Text("Заселить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun ManageBookingDialog(
    booking: Booking,
    onAddService: (ServiceOrder) -> Unit,
    onCheckOut: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("№${booking.room.number} · ${booking.guest.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow("Проживание", "${booking.nights} ноч. · ${booking.roomCost.tenge()}")
                if (booking.orders.isEmpty()) {
                    Text("Услуг пока нет", style = MaterialTheme.typography.bodySmall)
                } else {
                    // Если услуг много, этот список прокручивается сам (макс. высота 160dp)
                    Column(
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        booking.orders.forEach { InfoRow("+ ${it.title}", it.price.tenge()) }
                    }
                }
                OutlinedButton(
                    onClick = { onAddService(ServiceOrder.Food("Завтрак в номер", 6_000)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Еда в номер · 6 000 ₸") }
                OutlinedButton(
                    onClick = { onAddService(ServiceOrder.Laundry(3)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Прачечная (3 шт.) · 4 500 ₸") }
                OutlinedButton(
                    onClick = { onAddService(ServiceOrder.Taxi) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Такси · 4 000 ₸") }
                InfoRow("Итого сейчас", booking.total.tenge(), bold = true)
            }
        },
        confirmButton = { Button(onClick = onCheckOut) { Text("Выселить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun ReceiptDialog(booking: Booking, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Счёт · ${booking.guest.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoRow(
                    "${booking.nights} ноч. × ${booking.room.type.pricePerNight.tenge()}",
                    booking.roomCost.tenge()
                )
                booking.orders.forEach { InfoRow(it.title, it.price.tenge()) }
                if (booking.discount > 0) {
                    InfoRow("VIP-скидка 10%", "−${booking.discount.tenge()}")
                }
                InfoRow("ИТОГО К ОПЛАТЕ", booking.total.tenge(), bold = true)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } }
    )
}
