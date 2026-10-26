package com.example.hotelreception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

// =====================================================================
// ЭКРАН "НОВАЯ БРОНЬ": форма. Все поля — rememberSaveable: если повернуть телефон
// посреди заполнения, введённое не пропадёт. Проверку данных делает репозиторий.
// =====================================================================

@Composable
fun NewReservationScreen(
    viewModel: ReservationsViewModel,
    snackbarHostState: SnackbarHostState,
    onCreated: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    // Состояние формы живёт в самом экране
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var roomClass by rememberSaveable { mutableStateOf(RoomClass.DBC) }
    var arrival by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var nights by rememberSaveable { mutableStateOf("1") }
    var ratePlan by rememberSaveable { mutableStateOf(RatePlan.BAR) }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CARD) }
    var agency by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var loyalty by rememberSaveable { mutableStateOf(false) }
    var wish by rememberSaveable { mutableStateOf(RoomWish.NONE) }
    var deposit by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val arrivalDate = arrival ?: state.today
    val nightsCount = nights.toIntOrNull() ?: 0
    val rate = roomClass.baseRate * ratePlan.percent / 100

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "Гость") {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("Имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Фамилия") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Телефон") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Почта (необязательно)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            LabeledCheckbox(
                label = "Участник программы лояльности (скидка 10%)",
                checked = loyalty,
                onCheckedChange = { loyalty = it }
            )
        }

        SectionCard(title = "Номер и даты") {
            Text("Класс номера", style = MaterialTheme.typography.labelMedium)
            ChoiceChips(
                options = RoomClass.entries.toList(),
                isSelected = { it == roomClass },
                label = { it.code },
                onClick = { roomClass = it }
            )
            Text(
                text = "${roomClass.code}: ${roomClass.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Заезд: ${arrivalDate.full()}")
            }
            OutlinedTextField(
                value = nights,
                onValueChange = { nights = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text("Ночей") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Пожелание гостя", style = MaterialTheme.typography.labelMedium)
            ChoiceChips(
                options = RoomWish.entries.toList(),
                isSelected = { it == wish },
                label = { it.label },
                onClick = { wish = it }
            )
        }

        SectionCard(title = "Тариф и оплата") {
            Text("Тариф (rate)", style = MaterialTheme.typography.labelMedium)
            ChoiceChips(
                options = RatePlan.entries.toList(),
                isSelected = { it == ratePlan },
                label = { "${it.code} ${it.percent}%" },
                onClick = { ratePlan = it }
            )
            Text("Способ оплаты", style = MaterialTheme.typography.labelMedium)
            ChoiceChips(
                options = PaymentMethod.entries.toList(),
                isSelected = { it == method },
                label = { it.label },
                onClick = { method = it }
            )
            OutlinedTextField(
                value = deposit,
                onValueChange = { deposit = it.filter { ch -> ch.isDigit() } },
                label = { Text("Депозит, ₸ (необязательно)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = agency,
                onValueChange = { agency = it },
                label = { Text("Агентство (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("Компания (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard(title = "Стоимость") {
            InfoRow("Цена за ночь", rate.tenge())
            InfoRow("Ночей", nightsCount.toString())
            InfoRow("Итого за проживание", (rate * nightsCount).tenge(), bold = true)
        }

        Button(
            onClick = {
                val outcome = viewModel.createReservation(
                    ReservationDraft(
                        firstName = firstName,
                        lastName = lastName,
                        phone = phone,
                        email = email,
                        roomClass = roomClass,
                        arrival = arrivalDate,
                        nights = nightsCount,
                        ratePlan = ratePlan,
                        paymentMethod = method,
                        agency = agency,
                        company = company,
                        isLoyalty = loyalty,
                        wish = wish,
                        deposit = deposit.toIntOrNull() ?: 0
                    )
                )
                if (outcome is Outcome.Success && outcome.id != null) {
                    onCreated(outcome.id)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Создать бронь", fontWeight = FontWeight.SemiBold)
        }
    }

    if (showDatePicker) {
        HotelDatePickerDialog(
            initial = arrivalDate,
            onDismiss = { showDatePicker = false },
            onPick = { arrival = it }
        )
    }
}
