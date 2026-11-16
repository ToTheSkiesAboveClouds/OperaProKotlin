package com.example.hotelreception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// =====================================================================
// ЭКРАН "КАССОВАЯ СМЕНА" (дополнительная функция проекта).
// Сверху текущая смена: открыть, смотреть живые итоги, закрыть. Ниже статистика и история закрытых смен.
// Экран только показывает ShiftUiState и сообщает о нажатиях во ViewModel.
// =====================================================================

@Composable
fun ShiftScreen(
    viewModel: ShiftViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenShift: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    var showOpenDialog by rememberSaveable { mutableStateOf(false) }
    var showCloseDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    val current = state.current

    // Ключи (key) делают список стабильным: при изменении смен Compose не перерисовывает остальные
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "current") {
            if (current == null) {
                NoShiftCard(onOpen = { showOpenDialog = true })
            } else {
                CurrentShiftCard(shift = current, onClose = { showCloseDialog = true })
            }
        }

        if (current != null) {
            item(key = "ops-title") {
                Text("Операции текущей смены", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            val ops = state.operations[current.id].orEmpty()
            if (ops.isEmpty()) {
                item(key = "ops-empty") {
                    EmptyState("Пока нет операций. Примите оплату в биллинге брони, и она появится здесь")
                }
            }
            items(items = ops, key = { "op-${it.entry.id}" }) { op -> OperationRow(op) }
        }

        item(key = "stats") { HistoryStatsCard(state.stats) }

        item(key = "history-title") {
            Text("История смен", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (state.history.isEmpty()) {
            item(key = "history-empty") { EmptyState("Закрытых смен пока нет") }
        }
        items(items = state.history, key = { "shift-${it.id}" }) { shift ->
            ShiftHistoryCard(shift = shift, onClick = { onOpenShift(shift.id) })
        }

        item(key = "reset") {
            TextButton(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Сбросить демо-данные")
            }
        }
    }

    if (showOpenDialog) {
        OpenShiftDialog(
            onDismiss = { showOpenDialog = false },
            onConfirm = { cash -> viewModel.openShift(cash) }
        )
    }
    if (showCloseDialog && current != null) {
        CloseShiftDialog(
            shift = current,
            onDismiss = { showCloseDialog = false },
            onConfirm = { counted, note -> viewModel.closeShift(counted, note) }
        )
    }
    if (showResetDialog) {
        ConfirmDialog(
            title = "Сбросить демо-данные?",
            text = "Все брони, запросы, номера и смены будут заменены стартовыми данными. Это нельзя отменить.",
            confirmLabel = "Сбросить",
            onConfirm = {
                showResetDialog = false
                viewModel.resetDemoData()
            },
            onDismiss = { showResetDialog = false }
        )
    }
}

// ---------- Карточки ----------

@Composable
private fun NoShiftCard(onOpen: () -> Unit) {
    SectionCard(title = "Смена не открыта") {
        Text(
            text = "Пока смена закрыта, оплаты, возвраты и депозиты принимать нельзя. Откройте смену и укажите остаток наличных в кассе.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Открыть смену") }
    }
}

@Composable
private fun CurrentShiftCard(shift: Shift, onClose: () -> Unit) {
    val t = shift.totals
    SectionCard(title = "Смена №${shift.id} · открыта") {
        Text(
            text = "${shift.openedBy}, с ${shift.openedAt.stamp()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        InfoRow("Остаток на начало", shift.openingCash.tenge())
        HorizontalDivider()
        PaymentTotals(t)
        HorizontalDivider()
        InfoRow("Принято всего", t.received.tenge(), bold = true)
        InfoRow("Возвраты", t.refunds.tenge())
        InfoRow("Ожидается наличных в кассе", shift.expectedCash.tenge(), bold = true)
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Закрыть смену") }
    }
}

// Строки «по способам оплаты»: показываем только те, где были деньги, плюс наличные всегда
@Composable
private fun PaymentTotals(t: ShiftTotals) {
    InfoRow("Наличные", t.cash.tenge())
    if (t.card > 0) InfoRow("Карта", t.card.tenge())
    if (t.transfer > 0) InfoRow("Перевод", t.transfer.tenge())
    if (t.company > 0) InfoRow("Счёт компании", t.company.tenge())
}

@Composable
private fun OperationRow(op: ShiftOperation) {
    val entry = op.entry
    val isPayment = entry.type == EntryType.PAYMENT
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(op.guestName, fontWeight = FontWeight.Medium)
                Text(
                    text = "Бронь №${op.reservationId} · ${entry.method?.label ?: "Карта"} · ${entry.date.short()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = (if (isPayment) "+" else "−") + entry.amount.tenge(),
                fontWeight = FontWeight.Bold,
                color = if (isPayment) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun HistoryStatsCard(stats: ShiftHistoryStats) {
    SectionCard(title = "Статистика закрытых смен") {
        InfoRow("Смен закрыто", stats.closedCount.toString())
        InfoRow("Принято за всё время", stats.receivedTotal.tenge())
        InfoRow("Смен с недостачей", stats.shortageCount.toString())
        InfoRow(
            "Сумма разниц в кассе",
            signedTenge(stats.differenceTotal),
            valueColor = differenceColor(stats.differenceTotal)
        )
    }
}

@Composable
private fun ShiftHistoryCard(shift: Shift, onClick: () -> Unit) {
    val difference = shift.difference ?: 0
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Смена №${shift.id}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusBadge(
                    text = differenceLabel(difference),
                    color = differenceColor(difference)
                )
            }
            Text(
                text = "${shift.openedAt.stamp()} — ${shift.closedAt?.stamp() ?: ""} · ${shift.openedBy}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            InfoRow("Принято", shift.totals.received.tenge())
            InfoRow("Операций", shift.totals.paymentsCount.toString())
        }
    }
}

// ---------- Диалоги ----------

@Composable
private fun OpenShiftDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var cash by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val value = cash.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Открыть смену") },
        text = {
            OutlinedTextField(
                value = cash,
                onValueChange = { cash = it.filter { ch -> ch.isDigit() }.take(9) },
                label = { Text("Наличные в кассе, ₸") },
                isError = showError && value == null,
                supportingText = if (showError && value == null) { { Text("Введите сумму (можно 0)") } } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (value == null) {
                    showError = true
                } else {
                    onConfirm(value)
                    onDismiss()
                }
            }) { Text("Открыть") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun CloseShiftDialog(shift: Shift, onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var counted by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val value = counted.toIntOrNull()
    val difference = value?.let { it - shift.expectedCash }
    val noteMissing = difference != null && difference != 0 && note.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Закрыть смену №${shift.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Ожидается в кассе", shift.expectedCash.tenge(), bold = true)
                OutlinedTextField(
                    value = counted,
                    onValueChange = { counted = it.filter { ch -> ch.isDigit() }.take(9) },
                    label = { Text("Насчитано наличных, ₸") },
                    isError = showError && value == null,
                    supportingText = if (showError && value == null) { { Text("Введите сумму") } } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (difference != null) {
                    InfoRow(
                        "Разница",
                        signedTenge(difference),
                        bold = true,
                        valueColor = differenceColor(difference)
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (difference != null && difference != 0) "Причина разницы (обязательно)" else "Комментарий") },
                    isError = showError && noteMissing,
                    supportingText = if (showError && noteMissing) { { Text("Напишите причину разницы") } } else null,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (value == null || noteMissing) {
                    showError = true
                } else {
                    onConfirm(value, note)
                    onDismiss()
                }
            }) { Text("Закрыть смену") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ---------- Вспомогательные функции показа ----------

fun signedTenge(value: Int): String = (if (value > 0) "+" else if (value < 0) "−" else "") + kotlin.math.abs(value).tenge()

fun differenceLabel(difference: Int): String = when {
    difference == 0 -> "Касса сошлась"
    difference > 0 -> "Излишек ${difference.tenge()}"
    else -> "Недостача ${(-difference).tenge()}"
}

@Composable
fun differenceColor(difference: Int): Color = when {
    difference == 0 -> Color(0xFF2E7D32)
    difference > 0 -> Color(0xFFEF6C00)
    else -> MaterialTheme.colorScheme.error
}
