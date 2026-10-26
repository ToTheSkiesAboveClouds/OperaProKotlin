package com.example.hotelreception

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =====================================================================
// ЭКРАН "КАРТОЧКА БРОНИ": информация о госте, тариф, паспорт, заселение/выселение.
// =====================================================================

@Composable
fun ReservationDetailsScreen(
    resId: Int,
    viewModel: ReservationsViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenBilling: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    val reservation = state.all.firstOrNull { it.id == resId }
    if (reservation == null) {
        EmptyState(text = "Бронь №$resId не найдена", modifier = modifier)
    } else {
        ReservationDetailsContent(
            res = reservation,
            state = state,
            viewModel = viewModel,
            onOpenBilling = onOpenBilling,
            modifier = modifier
        )
    }
}

@Composable
private fun ReservationDetailsContent(
    res: Reservation,
    state: ReservationsUiState,
    viewModel: ReservationsViewModel,
    onOpenBilling: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRoomDialog by rememberSaveable { mutableStateOf(false) }

    // Камера: возвращает маленький снимок (Bitmap), сохраняем его в файл в фоновом потоке
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { saveBitmap(context, bitmap, "passport_${res.id}") }
                viewModel.attachPassport(res.id, path)
            }
        }
    }
    // Галерея: выбираем готовый скан паспорта
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyUriToApp(context, uri, "passport_${res.id}") }
                if (path != null) viewModel.attachPassport(res.id, path)
            }
        }
    }

    val room = state.rooms.firstOrNull { it.number == res.roomNumber }
    val roomReady = room != null && (room.hk == HkStatus.CLEAN || room.hk == HkStatus.INSPECTED)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = res.guest.fullName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Бронь №${res.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(text = res.status.label, color = res.status.color())
        }

        SectionCard(title = "Проживание") {
            InfoRow("Класс номера", "${res.roomClass.code} · ${res.roomClass.title}")
            InfoRow("Номер", res.roomNumber?.toString() ?: "не назначен")
            if (room != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Статус уборки", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HkBadge(status = room.hk)
                }
            }
            InfoRow("Заезд", res.arrival.full())
            InfoRow("Выезд", res.departure.full())
            InfoRow("Ночей", res.nights.toString())
            InfoRow("Пожелание гостя", res.wish.label)
            if (res.status == ResStatus.CONFIRMED) {
                OutlinedButton(onClick = { showRoomDialog = true }) {
                    Text(if (res.roomNumber == null) "Назначить номер" else "Сменить номер")
                }
            }
        }

        SectionCard(title = "Тариф и оплата") {
            InfoRow("Тариф (rate)", "${res.ratePlan.code} · ${res.ratePlan.title}")
            InfoRow("Цена за ночь", res.rate.tenge())
            InfoRow("Всего за проживание", res.stayTotal.tenge(), bold = true)
            InfoRow("Способ оплаты", res.paymentMethod.label)
            InfoRow(
                label = "Баланс",
                value = "${res.balance.tenge()} · ${balanceLabel(res.balance)}",
                bold = true,
                valueColor = balanceColor(res.balance)
            )
        }

        SectionCard(title = "Гость и контакты") {
            InfoRow("Телефон", res.guest.phone)
            InfoRow("Почта", res.guest.email.ifBlank { "—" })
            InfoRow("Агентство", res.agency.ifBlank { "—" })
            InfoRow("Компания", res.company.ifBlank { "—" })
            InfoRow("Программа лояльности", if (res.isLoyalty) "Участник (скидка 10%)" else "Нет")
        }

        SectionCard(title = "Паспорт") {
            PassportImage(path = res.passportPath)
            if (res.status != ResStatus.CHECKED_OUT) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try {
                            camera.launch(null)
                        } catch (e: ActivityNotFoundException) {
                            viewModel.showMessage("Камера недоступна на этом устройстве")
                        }
                    }) { Text("Сканировать (камера)") }
                    OutlinedButton(onClick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Из галереи") }
                }
            }
        }

        if (res.status == ResStatus.CONFIRMED) {
            SectionCard(title = "Готовность к заселению") {
                ChecklistRow(
                    done = !res.arrival.isAfter(state.today),
                    text = "Дата заезда наступила (${res.arrival.full()})"
                )
                ChecklistRow(done = res.roomNumber != null, text = "Номер назначен")
                ChecklistRow(
                    done = roomReady,
                    text = "Номер готов: Clean или Inspected" + (room?.let { " (сейчас ${it.hk.label})" } ?: "")
                )
                ChecklistRow(done = res.passportPath != null, text = "Паспорт отсканирован")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onOpenBilling, modifier = Modifier.weight(1f)) {
                Text("Биллинг")
            }
            if (res.status == ResStatus.CONFIRMED) {
                Button(onClick = { viewModel.checkIn(res.id) }, modifier = Modifier.weight(1f)) {
                    Text("Заселить")
                }
            }
            if (res.status == ResStatus.IN_HOUSE) {
                Button(onClick = { viewModel.checkOut(res.id) }, modifier = Modifier.weight(1f)) {
                    Text("Выселить")
                }
            }
        }
    }

    if (showRoomDialog) {
        // Список пересчитывается только когда меняются брони или номера
        val freeRooms = remember(state.all, state.rooms) { viewModel.freeRoomsFor(res.id) }
        AlertDialog(
            onDismissRequest = { showRoomDialog = false },
            title = { Text("Назначить номер (${res.roomClass.code})") },
            text = {
                if (freeRooms.isEmpty()) {
                    Text("Нет свободных номеров этого класса на даты брони")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(items = freeRooms, key = { it.number }) { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.assignRoom(res.id, candidate.number)
                                        showRoomDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "№${candidate.number} · этаж ${candidate.floor}",
                                    modifier = Modifier.weight(1f)
                                )
                                HkBadge(status = candidate.hk)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRoomDialog = false }) { Text("Закрыть") } }
        )
    }
}
