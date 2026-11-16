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
// ЭКРАН "НОВАЯ БРОНЬ" / "РЕДАКТИРОВАНИЕ БРОНИ": одна форма на оба случая (editId != null = правка).
// Все поля — rememberSaveable: если повернуть телефон посреди заполнения, введённое не пропадёт.
// Ошибки под полями показываются после первой попытки сохранить, правила проверяет и репозиторий.
// =====================================================================

@Composable
fun NewReservationScreen(
    viewModel: ReservationsViewModel,
    snackbarHostState: SnackbarHostState,
    onCreated: (Int) -> Unit,
    modifier: Modifier = Modifier,
    editId: Int? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    // Правим существующую бронь или создаём новую
    val editing = editId?.let { id -> state.all.firstOrNull { it.id == id } }
    // У проживающего гостя можно менять только контакты, оплату и пожелания
    val limited = editing?.status == ResStatus.IN_HOUSE

    // Состояние формы живёт в самом экране (при правке стартует со значений брони)
    var firstName by rememberSaveable { mutableStateOf(editing?.guest?.firstName ?: "") }
    var lastName by rememberSaveable { mutableStateOf(editing?.guest?.lastName ?: "") }
    var phone by rememberSaveable { mutableStateOf(editing?.guest?.phone ?: "") }
    var email by rememberSaveable { mutableStateOf(editing?.guest?.email ?: "") }
    var roomClass by rememberSaveable { mutableStateOf(editing?.roomClass ?: RoomClass.DBC) }
    var arrival by rememberSaveable { mutableStateOf<LocalDate?>(editing?.arrival) }
    var nights by rememberSaveable { mutableStateOf(editing?.nights?.toString() ?: "1") }
    var ratePlan by rememberSaveable { mutableStateOf(editing?.ratePlan ?: RatePlan.BAR) }
    var method by rememberSaveable { mutableStateOf(editing?.paymentMethod ?: PaymentMethod.CARD) }
    var agency by rememberSaveable { mutableStateOf(editing?.agency ?: "") }
    var company by rememberSaveable { mutableStateOf(editing?.company ?: "") }
    var loyalty by rememberSaveable { mutableStateOf(editing?.isLoyalty ?: false) }
    var wish by rememberSaveable { mutableStateOf(editing?.wish ?: RoomWish.NONE) }
    var deposit by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val arrivalDate = arrival ?: state.today
    val nightsCount = nights.toIntOrNull() ?: 0
    val rate = roomClass.baseRate * ratePlan.percent / 100

    // Проверка полей (тексты ошибок под полями). null = поле заполнено верно
    val firstNameError = if (firstName.isBlank()) "Введите имя" else null
    val lastNameError = if (lastName.isBlank()) "Введите фамилию" else null
    val phoneError = if (phone.count { it.isDigit() } < 7) "Минимум 7 цифр" else null
    val emailError = if (email.isNotBlank() && !email.contains("@")) "Почта указана неверно" else null
    val nightsError = if (nightsCount !in 1..60) "От 1 до 60 ночей" else null
    val formValid = listOf(firstNameError, lastNameError, phoneError, emailError, if (limited) null else nightsError)
        .all { it == null }

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
                isError = showErrors && firstNameError != null,
                supportingText = errorText(showErrors, firstNameError),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Фамилия") },
                isError = showErrors && lastNameError != null,
                supportingText = errorText(showErrors, lastNameError),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Телефон") },
                isError = showErrors && phoneError != null,
                supportingText = errorText(showErrors, phoneError),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Почта (необязательно)") },
                isError = showErrors && emailError != null,
                supportingText = errorText(showErrors, emailError),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            LabeledCheckbox(
                label = "Участник программы лояльности (скидка 10%)",
                checked = loyalty,
                onCheckedChange = { if (!limited) loyalty = it }
            )
        }

        SectionCard(title = "Номер и даты") {
            Text("Класс номера", style = MaterialTheme.typography.labelMedium)
            ChoiceChips(
                options = RoomClass.entries.toList(),
                isSelected = { it == roomClass },
                label = { it.code },
                onClick = { if (!limited) roomClass = it }
            )
            Text(
                text = "${roomClass.code}: ${roomClass.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { showDatePicker = true }, enabled = !limited, modifier = Modifier.fillMaxWidth()) {
                Text("Заезд: ${arrivalDate.full()}")
            }
            OutlinedTextField(
                value = nights,
                onValueChange = { nights = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text("Ночей") },
                enabled = !limited,
                isError = showErrors && !limited && nightsError != null,
                supportingText = errorText(showErrors && !limited, nightsError),
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
                onClick = { if (!limited) ratePlan = it }
            )
            Text("Способ оплаты", style = MaterialTheme.typography.labelMedium)
            ChoiceChips(
                options = PaymentMethod.entries.toList(),
                isSelected = { it == method },
                label = { it.label },
                onClick = { method = it }
            )
            if (editId == null) OutlinedTextField(
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

        if (limited) {
            Text(
                text = "Гость проживает: даты, класс и тариф изменить нельзя",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = {
                showErrors = true
                if (!formValid) return@Button
                val draft = ReservationDraft(
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
                // Ответ приходит, когда база записала данные: тогда переходим дальше
                val finish: (Outcome) -> Unit = { outcome ->
                    if (outcome is Outcome.Success) onCreated(outcome.id ?: editId ?: 0)
                }
                if (editId == null) {
                    viewModel.createReservation(draft, finish)
                } else {
                    viewModel.updateReservation(editId, draft, finish)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (editId == null) "Создать бронь" else "Сохранить изменения", fontWeight = FontWeight.SemiBold)
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

// Текст ошибки под полем (null = ничего не показываем)
private fun errorText(show: Boolean, error: String?): (@Composable () -> Unit)? =
    if (show && error != null) { { Text(error) } } else null
