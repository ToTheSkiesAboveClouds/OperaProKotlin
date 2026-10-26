package com.example.hotelreception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// =====================================================================
// ЭКРАН "БИЛЛИНГ": счёт гостя, услуги, оплаты, баланс.
// Правило как в Opera: при выселении баланс должен быть ровно 0.
// =====================================================================

@Composable
fun BillingScreen(
    resId: Int,
    viewModel: ReservationsViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    val reservation = state.all.firstOrNull { it.id == resId }
    if (reservation == null) {
        EmptyState(text = "Бронь №$resId не найдена", modifier = modifier)
    } else {
        BillingContent(res = reservation, viewModel = viewModel, modifier = modifier)
    }
}

@Composable
private fun BillingContent(res: Reservation, viewModel: ReservationsViewModel, modifier: Modifier) {
    var showChargeDialog by rememberSaveable { mutableStateOf(false) }
    var showPaymentDialog by rememberSaveable { mutableStateOf(false) }
    val balance = res.balance
    val active = res.status != ResStatus.CHECKED_OUT

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BalanceCard(guestName = res.guest.fullName, balance = balance)

        if (active) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showChargeDialog = true },
                    enabled = res.status == ResStatus.IN_HOUSE,
                    modifier = Modifier.weight(1f)
                ) { Text("+ Услуга") }
                OutlinedButton(
                    onClick = { showPaymentDialog = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Принять оплату") }
            }
            if (res.status == ResStatus.IN_HOUSE) {
                FilledTonalButton(
                    onClick = { viewModel.settleBalance(res.id) },
                    enabled = balance != 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            balance > 0 -> "Закрыть баланс: принять ${balance.tenge()}"
                            balance < 0 -> "Закрыть баланс: вернуть ${(-balance).tenge()}"
                            else -> "Баланс закрыт"
                        }
                    )
                }
            }
        }

        SectionCard(title = "Счёт гостя") {
            val lines = res.folioLines()
            if (lines.isEmpty()) {
                Text(
                    text = "Пока нет начислений. Проживание попадёт в счёт при заселении.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider()
                FolioRow(line)
            }
        }

        if (res.status == ResStatus.IN_HOUSE) {
            Button(
                onClick = { viewModel.checkOut(res.id) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Выселить гостя") }
        }
    }

    if (showChargeDialog) {
        AddChargeDialog(
            onDismiss = { showChargeDialog = false },
            onConfirm = { title, amount -> viewModel.addCharge(res.id, title, amount) }
        )
    }
    if (showPaymentDialog) {
        PaymentDialog(
            suggested = if (balance > 0) balance else 0,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount -> viewModel.addPayment(res.id, amount) }
        )
    }
}

// Крупная карточка баланса. Цвет и подсказка зависят от знака баланса.
@Composable
private fun BalanceCard(guestName: String, balance: Int) {
    val color = balanceColor(balance)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = guestName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = balance.tenge(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = balanceLabel(balance),
                style = MaterialTheme.typography.titleSmall,
                color = color
            )
            Text(
                text = when {
                    balance > 0 -> "Гость должен оплатить. Выселение возможно, когда баланс станет 0."
                    balance < 0 -> "Гость оплатил вперёд: услуга ещё не выполнена или не назначена. Выполните услугу или верните деньги."
                    else -> "Мы ничего не должны гостю, и он ничего не должен нам. Можно выселять."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FolioRow(line: FolioLine) {
    val entry = line.entry
    val isPlus = entry.signed > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = entry.date.short(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (isPlus) "+" else "−") + entry.amount.tenge(),
                fontWeight = FontWeight.SemiBold,
                color = if (isPlus) DebtRed else PaidGreen
            )
            when (line.state) {
                PayState.PAID -> StatusBadge("Оплачено", PaidGreen)
                PayState.PARTIAL -> StatusBadge("Частично", WarnAmber)
                PayState.UNPAID -> StatusBadge("Не оплачено", DebtRed)
                null -> Unit
            }
        }
    }
}

@Composable
private fun AddChargeDialog(onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить услугу") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Быстрый выбор", style = MaterialTheme.typography.labelMedium)
                ChoiceChips(
                    options = SERVICE_CATALOG,
                    isSelected = { it.title == title },
                    label = { it.title },
                    onClick = {
                        title = it.title
                        amount = it.price.toString()
                    }
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название услуги") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Сумма, ₸") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(title, amount.toIntOrNull() ?: 0)
                onDismiss()
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun PaymentDialog(suggested: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(if (suggested > 0) suggested.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Принять оплату") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (suggested > 0) {
                    Text("Долг гостя: ${suggested.tenge()}", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Сумма, ₸") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(amount.toIntOrNull() ?: 0)
                onDismiss()
            }) { Text("Принять") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
