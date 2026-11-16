package com.example.hotelreception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// =====================================================================
// ЭКРАН "ДЕТАЛИ СМЕНЫ": итоги закрытой смены, список операций, комментарий (можно изменить),
// удаление смены с подтверждением. Итоги закрытой смены записаны при закрытии,
// поэтому удаление брони позже их не меняет.
// =====================================================================

@Composable
fun ShiftDetailsScreen(
    shiftId: Int,
    viewModel: ShiftViewModel,
    snackbarHostState: SnackbarHostState,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    val shift = (state.history + listOfNotNull(state.current)).firstOrNull { it.id == shiftId }
    if (shift == null) {
        EmptyState("Смена №$shiftId не найдена", modifier = modifier)
        return
    }

    var showNoteDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val t = shift.totals
    val difference = shift.difference ?: 0
    val operations = state.operations[shift.id].orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary") {
            SectionCard(title = "Смена №${shift.id}") {
                InfoRow("Кассир", shift.openedBy)
                InfoRow("Открыта", shift.openedAt.stamp())
                InfoRow("Закрыта", shift.closedAt?.stamp() ?: "ещё открыта")
                HorizontalDivider()
                InfoRow("Остаток на начало", shift.openingCash.tenge())
                InfoRow("Наличные", t.cash.tenge())
                InfoRow("Карта", t.card.tenge())
                InfoRow("Перевод", t.transfer.tenge())
                InfoRow("Счёт компании", t.company.tenge())
                InfoRow("Принято всего", t.received.tenge(), bold = true)
                InfoRow("Возвраты наличными", t.cashRefunds.tenge())
                InfoRow("Другие возвраты", t.otherRefunds.tenge())
                HorizontalDivider()
                InfoRow("Должно быть в кассе", shift.expectedCash.tenge())
                if (shift.countedCash != null) {
                    InfoRow("Насчитано", shift.countedCash.tenge())
                    InfoRow(
                        "Результат",
                        differenceLabel(difference),
                        bold = true,
                        valueColor = differenceColor(difference)
                    )
                }
            }
        }
        item(key = "note") {
            SectionCard(title = "Комментарий") {
                Text(
                    text = shift.note.ifBlank { "Комментария нет" },
                    color = if (shift.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.Unspecified
                )
                if (shift.status == ShiftStatus.CLOSED) {
                    OutlinedButton(onClick = { showNoteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Изменить комментарий")
                    }
                }
            }
        }
        item(key = "ops-title") {
            Text("Операции (${operations.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (operations.isEmpty()) {
            item(key = "ops-empty") { EmptyState("В этой смене нет операций") }
        }
        items(items = operations, key = { "op-${it.entry.id}" }) { op ->
            InfoRow(
                label = "${op.guestName} · ${op.entry.method?.label ?: "Карта"}",
                value = (if (op.entry.type == EntryType.PAYMENT) "+" else "−") + op.entry.amount.tenge()
            )
        }
        if (shift.status == ShiftStatus.CLOSED) {
            item(key = "delete") {
                OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Удалить смену", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showNoteDialog) {
        NoteDialog(
            initial = shift.note,
            required = difference != 0,
            onDismiss = { showNoteDialog = false },
            onConfirm = { note -> viewModel.updateNote(shift.id, note) }
        )
    }
    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Удалить смену?",
            text = "Смена №${shift.id} исчезнет из истории и статистики. Платежи в счетах гостей останутся.",
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteShift(shift.id) { outcome -> if (outcome is Outcome.Success) onDeleted() }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun NoteDialog(initial: String, required: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by rememberSaveable { mutableStateOf(initial) }
    var showError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Комментарий к смене") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(if (required) "Причина разницы (обязательно)" else "Комментарий") },
                isError = showError && required && note.isBlank(),
                supportingText = if (showError && required && note.isBlank()) { { Text("Напишите причину разницы") } } else null,
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (required && note.isBlank()) {
                    showError = true
                } else {
                    onConfirm(note)
                    onDismiss()
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
