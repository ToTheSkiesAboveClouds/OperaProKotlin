package com.example.hotelreception

import java.time.LocalDateTime

// =====================================================================
// ОПЕРАЦИИ НАД ДАННЫМИ ОТЕЛЯ (слой Domain). Чистые функции без Room, Android и Compose.
// Каждая операция берёт текущие данные и возвращает новые + сообщение об успехе.
// Если нарушено правило, вызывается reject(...): данные не меняются, пользователь видит причину.
// Репозиторий (HotelRepository) только применяет такую операцию внутри транзакции базы данных.
// =====================================================================

// Внутренний способ прервать операцию с понятным сообщением
class Rejected(message: String) : RuntimeException(message)

fun reject(message: String): Nothing = throw Rejected(message)

typealias Step = Pair<HotelData, Outcome.Success>

private fun ok(message: String, id: Int? = null) = Outcome.Success(message, id)

private fun HotelData.requireReservation(id: Int): Reservation =
    reservationById(id) ?: reject("Бронь №$id не найдена")

private fun HotelData.requireRoom(number: Int): Room =
    roomByNumber(number) ?: reject("Номер $number не найден")

// Платежи принимаются только при открытой смене: так каждая операция попадает в кассовый отчёт
private fun HotelData.requireOpenShift(): Shift =
    openShift() ?: reject("Смена не открыта. Откройте смену на главной, чтобы принимать оплаты")

private fun HotelData.updateReservation(id: Int, change: (Reservation) -> Reservation): HotelData =
    copy(reservations = reservations.map { if (it.id == id) change(it) else it })

// Добавляет строку в счёт гостя
private fun HotelData.withEntry(
    resId: Int,
    title: String,
    amount: Int,
    type: EntryType,
    method: PaymentMethod? = null,
    shiftId: Int? = null
): HotelData {
    val entry = FolioEntry(nextEntryId, title, amount, type, today, method, shiftId)
    return copy(nextEntryId = nextEntryId + 1)
        .updateReservation(resId) { it.copy(folio = it.folio + entry) }
}

// ---------- Брони: номера ----------

fun HotelData.opAutoAssignRooms(): Step {
    val (next, count) = withAutoAssignedRooms()
    if (count == 0) reject("Нет броней, которым можно назначить номер")
    return next to ok("Назначено номеров: $count")
}

fun HotelData.opAssignRoom(resId: Int, roomNumber: Int): Step {
    val res = requireReservation(resId)
    if (res.status != ResStatus.CONFIRMED) reject("Номер назначается только ожидаемой брони")
    val room = requireRoom(roomNumber)
    if (room.roomClass != res.roomClass) reject("Номер $roomNumber другого класса")
    if (room.hk == HkStatus.MAINTENANCE) reject("Номер $roomNumber на ремонте")
    if (!isRoomFree(roomNumber, res.arrival, res.departure, res.id)) reject("Номер $roomNumber занят на эти даты")
    return updateReservation(resId) { it.copy(roomNumber = roomNumber) } to ok("Назначен номер $roomNumber")
}

fun HotelData.opAttachPassport(resId: Int, path: String): Step {
    requireReservation(resId)
    return updateReservation(resId) { it.copy(passportPath = path) } to ok("Скан паспорта сохранён")
}

// ---------- Брони: заселение и выселение ----------

fun HotelData.opCheckIn(resId: Int): Step {
    val res = requireReservation(resId)
    if (res.status != ResStatus.CONFIRMED) reject("Гость уже заселён или выселен")
    if (res.arrival.isAfter(today)) reject("Заезд запланирован на ${res.arrival.full()}")
    val number = res.roomNumber ?: reject("Сначала назначьте номер")
    val room = requireRoom(number)
    if (room.hk != HkStatus.CLEAN && room.hk != HkStatus.INSPECTED) {
        reject("Номер $number в статусе ${room.hk.label}: заселять нельзя")
    }
    if (reservations.any { it.roomNumber == number && it.status == ResStatus.IN_HOUSE }) {
        reject("В номере $number ещё проживает другой гость")
    }
    if (res.passportPath == null) reject("Сначала отсканируйте паспорт")

    // При заселении в счёт попадает проживание и скидка лояльности
    var next = withEntry(
        resId, "Проживание: ${nightsText(res.nights)} × ${res.rate.tenge()}", res.stayTotal, EntryType.CHARGE
    )
    if (res.isLoyalty) {
        next = next.withEntry(resId, "Скидка лояльности 10%", res.stayTotal / 10, EntryType.DISCOUNT)
    }
    next = next.updateReservation(resId) {
        it.copy(status = ResStatus.IN_HOUSE, checkedInOn = today)
    }
    return next to ok("Гость ${res.guest.fullName} заселён в номер $number")
}

fun HotelData.opCheckOut(resId: Int): Step {
    val res = requireReservation(resId)
    if (res.status != ResStatus.IN_HOUSE) reject("Выселять можно только проживающего гостя")
    val balance = res.balance
    if (balance > 0) reject("Баланс ${balance.tenge()}: гость должен оплатить. Выселение возможно при балансе 0")
    if (balance < 0) reject("Баланс ${balance.tenge()}: вернуть деньги или выполнить услугу. Выселение возможно при балансе 0")
    val number = res.roomNumber
    return copy(
        rooms = rooms.map { if (it.number == number) it.copy(hk = HkStatus.DIRTY) else it }
    ).updateReservation(resId) {
        it.copy(status = ResStatus.CHECKED_OUT, checkedOutOn = today)
    } to ok("Гость выселен, номер ${number ?: "—"} теперь Dirty")
}

// ---------- Биллинг ----------

fun HotelData.opAddCharge(resId: Int, title: String, amount: Int): Step {
    val res = requireReservation(resId)
    if (res.status != ResStatus.IN_HOUSE) reject("Услуги добавляются только проживающему гостю")
    if (title.isBlank()) reject("Введите название услуги")
    if (amount <= 0) reject("Сумма должна быть больше нуля")
    return withEntry(resId, title.trim(), amount, EntryType.CHARGE) to ok("Добавлено: ${title.trim()}, ${amount.tenge()}")
}

// Оплата попадает в открытую смену с указанным способом (по умолчанию как в брони)
fun HotelData.opAddPayment(resId: Int, amount: Int, method: PaymentMethod? = null): Step {
    val res = requireReservation(resId)
    if (res.status == ResStatus.CHECKED_OUT) reject("Бронь уже закрыта")
    if (amount <= 0) reject("Сумма должна быть больше нуля")
    val shift = requireOpenShift()
    val how = method ?: res.paymentMethod
    return withEntry(resId, "Оплата: ${how.label}", amount, EntryType.PAYMENT, how, shift.id) to
        ok("Принята оплата ${amount.tenge()} (${how.label})")
}

// Закрыть баланс: если гость должен — принять оплату, если переплатил — вернуть деньги
fun HotelData.opSettleBalance(resId: Int): Step {
    val res = requireReservation(resId)
    if (res.status != ResStatus.IN_HOUSE) reject("Баланс закрывается у проживающего гостя")
    val balance = res.balance
    return when {
        balance > 0 -> {
            val shift = requireOpenShift()
            withEntry(
                resId, "Оплата остатка: ${res.paymentMethod.label}", balance, EntryType.PAYMENT,
                res.paymentMethod, shift.id
            ) to ok("Принята оплата ${balance.tenge()}, баланс 0")
        }
        balance < 0 -> {
            val shift = requireOpenShift()
            withEntry(resId, "Возврат гостю", -balance, EntryType.REFUND, res.paymentMethod, shift.id) to
                ok("Возвращено ${(-balance).tenge()}, баланс 0")
        }
        else -> reject("Баланс уже равен 0")
    }
}

// ---------- Брони: создание, правка, удаление ----------

// Проверка полей гостя, общая для создания и правки брони
private fun validateGuestFields(d: ReservationDraft) {
    if (d.firstName.isBlank() || d.lastName.isBlank()) reject("Введите имя и фамилию гостя")
    if (d.phone.count { it.isDigit() } < 7) reject("Введите телефон (минимум 7 цифр)")
    if (d.email.isNotBlank() && !d.email.contains("@")) reject("Почта указана неверно")
}

fun HotelData.opCreateReservation(d: ReservationDraft): Step {
    validateGuestFields(d)
    if (d.nights !in 1..60) reject("Ночей может быть от 1 до 60")
    if (d.arrival.isBefore(today)) reject("Дата заезда уже прошла")
    if (d.deposit < 0) reject("Депозит не может быть отрицательным")

    val departure = d.arrival.plusDays(d.nights.toLong())
    var date = d.arrival
    while (date.isBefore(departure)) {
        if (freeCount(d.roomClass, date) < 1) {
            reject("Нет свободных номеров ${d.roomClass.code} на ${date.full()}")
        }
        date = date.plusDays(1)
    }

    val id = nextReservationId
    // Депозит — это оплата, поэтому для него нужна открытая смена
    val entries = if (d.deposit > 0) {
        val shift = requireOpenShift()
        listOf(
            FolioEntry(nextEntryId, "Депозит", d.deposit, EntryType.PAYMENT, today, d.paymentMethod, shift.id)
        )
    } else {
        emptyList()
    }
    val reservation = Reservation(
        id = id,
        guest = Guest(d.firstName.trim(), d.lastName.trim(), d.phone.trim(), d.email.trim()),
        roomClass = d.roomClass,
        roomNumber = null,
        arrival = d.arrival,
        departure = departure,
        status = ResStatus.CONFIRMED,
        ratePlan = d.ratePlan,
        rate = d.roomClass.baseRate * d.ratePlan.percent / 100,
        paymentMethod = d.paymentMethod,
        agency = d.agency.trim(),
        company = d.company.trim(),
        isLoyalty = d.isLoyalty,
        wish = d.wish,
        passportPath = null,
        checkedInOn = null,
        checkedOutOn = null,
        folio = entries
    )
    val created = copy(
        reservations = reservations + reservation,
        nextReservationId = id + 1,
        nextEntryId = nextEntryId + entries.size
    )
    // Если заезд скоро, система сразу подбирает номер по классу и пожеланию гостя
    val (assigned, _) = created.withAutoAssignedRooms()
    return assigned to ok("Бронь №$id создана", id)
}

// Правка брони. Ожидаемую бронь можно менять целиком (даты, класс, тариф),
// у проживающего гостя только контакты и пожелания, выселенную менять нельзя.
fun HotelData.opUpdateReservation(resId: Int, d: ReservationDraft): Step {
    val res = requireReservation(resId)
    if (res.status == ResStatus.CHECKED_OUT) reject("Выселенную бронь редактировать нельзя")
    validateGuestFields(d)
    val guest = Guest(d.firstName.trim(), d.lastName.trim(), d.phone.trim(), d.email.trim())

    if (res.status == ResStatus.IN_HOUSE) {
        return updateReservation(resId) {
            it.copy(
                guest = guest,
                paymentMethod = d.paymentMethod,
                agency = d.agency.trim(),
                company = d.company.trim(),
                wish = d.wish
            )
        } to ok("Бронь №$resId обновлена")
    }

    if (d.nights !in 1..60) reject("Ночей может быть от 1 до 60")
    if (d.arrival.isBefore(today) && d.arrival != res.arrival) reject("Дата заезда уже прошла")
    val departure = d.arrival.plusDays(d.nights.toLong())

    // Свободные номера считаем без самой этой брони, иначе она «занимала бы место» сама у себя
    var date = d.arrival
    while (date.isBefore(departure)) {
        val ownPlace = if (
            res.roomClass == d.roomClass && !res.arrival.isAfter(date) && date.isBefore(res.departure)
        ) 1 else 0
        if (freeCount(d.roomClass, date) + ownPlace < 1) {
            reject("Нет свободных номеров ${d.roomClass.code} на ${date.full()}")
        }
        date = date.plusDays(1)
    }

    val sameRoomOk = res.roomNumber != null &&
        d.roomClass == res.roomClass &&
        isRoomFree(res.roomNumber, d.arrival, departure, res.id)
    val updated = updateReservation(resId) {
        it.copy(
            guest = guest,
            roomClass = d.roomClass,
            roomNumber = if (sameRoomOk) it.roomNumber else null,
            arrival = d.arrival,
            departure = departure,
            ratePlan = d.ratePlan,
            rate = d.roomClass.baseRate * d.ratePlan.percent / 100,
            paymentMethod = d.paymentMethod,
            agency = d.agency.trim(),
            company = d.company.trim(),
            isLoyalty = d.isLoyalty,
            wish = d.wish
        )
    }
    val (assigned, _) = updated.withAutoAssignedRooms()
    return assigned to ok("Бронь №$resId обновлена")
}

// Удаление брони. Проживающего гостя удалить нельзя: сначала выселение.
fun HotelData.opDeleteReservation(resId: Int): Step {
    val res = requireReservation(resId)
    if (res.status == ResStatus.IN_HOUSE) reject("Гость проживает. Сначала выселите его, потом бронь можно удалить")
    return copy(reservations = reservations.filter { it.id != resId }) to
        ok("Бронь №$resId удалена")
}

// ---------- Номера: статус уборки ----------

fun HotelData.opSetHk(roomNumber: Int, newStatus: HkStatus): Step {
    val room = requireRoom(roomNumber)
    if (room.hk == newStatus) reject("Номер $roomNumber уже в статусе ${newStatus.label}")
    val occupancy = occupancyOf(roomNumber)
    if (newStatus == HkStatus.INSPECTED && room.hk != HkStatus.CLEAN) {
        reject("Inspected ставится только после Clean: проверяет старшая горничная")
    }
    if ((newStatus == HkStatus.MAINTENANCE || newStatus == HkStatus.PICKUP) && occupancy == Occupancy.OCCUPIED) {
        reject("В номере $roomNumber живёт гость")
    }
    if (newStatus == HkStatus.MAINTENANCE && occupancy == Occupancy.ON_ARRIVAL) {
        reject("На номер $roomNumber назначен скорый заезд, сначала переназначьте его")
    }
    return copy(rooms = rooms.map { if (it.number == roomNumber) it.copy(hk = newStatus) else it }) to
        ok("Номер $roomNumber: ${newStatus.label}")
}

// ---------- Запросы в отделы ----------

private fun validateRequestFields(location: String, title: String) {
    if (location.isBlank()) reject("Укажите номер комнаты или место")
    if (title.isBlank()) reject("Укажите цель запроса")
}

fun HotelData.opCreateRequest(
    department: Department,
    location: String,
    title: String,
    description: String,
    priority: Boolean,
    attachments: List<Attachment>,
    now: LocalDateTime = LocalDateTime.now()
): Step {
    validateRequestFields(location, title)
    val request = ServiceRequest(
        id = nextRequestId,
        department = department,
        location = location.trim(),
        title = title.trim(),
        description = description.trim(),
        priority = priority,
        attachments = attachments,
        createdBy = CURRENT_USER,
        createdAt = now,
        status = RequestStatus.NEW
    )
    return copy(requests = requests + request, nextRequestId = nextRequestId + 1) to
        ok("Запрос отправлен в ${department.title}", request.id)
}

fun HotelData.opUpdateRequest(
    id: Int,
    department: Department,
    location: String,
    title: String,
    description: String,
    priority: Boolean,
    attachments: List<Attachment>
): Step {
    val request = requests.firstOrNull { it.id == id } ?: reject("Запрос не найден")
    if (request.status == RequestStatus.DONE) reject("Закрытый запрос редактировать нельзя")
    validateRequestFields(location, title)
    val changed = request.copy(
        department = department,
        location = location.trim(),
        title = title.trim(),
        description = description.trim(),
        priority = priority,
        attachments = attachments
    )
    return copy(requests = requests.map { if (it.id == id) changed else it }) to
        ok("Запрос №$id обновлён", id)
}

fun HotelData.opDeleteRequest(id: Int): Step {
    requests.firstOrNull { it.id == id } ?: reject("Запрос не найден")
    return copy(requests = requests.filter { it.id != id }) to ok("Запрос №$id удалён")
}

fun HotelData.opAdvanceRequest(id: Int, newStatus: RequestStatus): Step {
    val request = requests.firstOrNull { it.id == id } ?: reject("Запрос не найден")
    if (newStatus.ordinal <= request.status.ordinal) reject("Статус нельзя вернуть назад")
    return copy(requests = requests.map { if (it.id == id) it.copy(status = newStatus) else it }) to
        ok("Запрос №$id: ${newStatus.label}")
}

// ---------- Кассовая смена ----------

fun HotelData.opOpenShift(openedBy: String, openingCash: Int, now: LocalDateTime = LocalDateTime.now()): Step {
    if (openShift() != null) reject("Смена уже открыта. Сначала закройте её")
    if (openingCash < 0) reject("Остаток в кассе не может быть отрицательным")
    if (openingCash > 100_000_000) reject("Слишком большая сумма в кассе")
    val shift = Shift(
        id = nextShiftId,
        openedBy = openedBy,
        openedAt = now,
        openingCash = openingCash,
        status = ShiftStatus.OPEN,
        closedAt = null,
        totals = ShiftTotals(),
        countedCash = null,
        note = ""
    )
    return copy(shifts = shifts + shift, nextShiftId = nextShiftId + 1) to
        ok("Смена №${shift.id} открыта, в кассе ${openingCash.tenge()}", shift.id)
}

// Закрытие смены: итоги записываются в смену навсегда. Если в кассе не сходится, нужен комментарий.
fun HotelData.opCloseShift(countedCash: Int?, note: String, now: LocalDateTime = LocalDateTime.now()): Step {
    val shift = openShift() ?: reject("Нет открытой смены")
    if (countedCash == null || countedCash < 0) reject("Введите, сколько наличных насчитали в кассе")
    val live = withLiveTotals(shift)
    val difference = countedCash - live.expectedCash
    if (difference != 0 && note.isBlank()) {
        reject("В кассе разница ${difference.tenge()}: напишите причину в комментарии")
    }
    val closed = live.copy(
        status = ShiftStatus.CLOSED,
        closedAt = now,
        countedCash = countedCash,
        note = note.trim()
    )
    val text = when {
        difference == 0 -> "Смена №${shift.id} закрыта, касса сошлась"
        difference > 0 -> "Смена №${shift.id} закрыта, излишек ${difference.tenge()}"
        else -> "Смена №${shift.id} закрыта, недостача ${(-difference).tenge()}"
    }
    return copy(shifts = shifts.map { if (it.id == shift.id) closed else it }) to ok(text, shift.id)
}

fun HotelData.opUpdateShiftNote(id: Int, note: String): Step {
    val shift = shifts.firstOrNull { it.id == id } ?: reject("Смена не найдена")
    if (shift.status != ShiftStatus.CLOSED) reject("Комментарий к открытой смене пишется при закрытии")
    if ((shift.difference ?: 0) != 0 && note.isBlank()) reject("При разнице в кассе комментарий обязателен")
    return copy(shifts = shifts.map { if (it.id == id) it.copy(note = note.trim()) else it }) to
        ok("Комментарий сохранён", id)
}

// Удаляется только закрытая смена; платежи в счетах гостей остаются, но больше не привязаны к смене
fun HotelData.opDeleteShift(id: Int): Step {
    val shift = shifts.firstOrNull { it.id == id } ?: reject("Смена не найдена")
    if (shift.status == ShiftStatus.OPEN) reject("Открытую смену удалить нельзя. Сначала закройте её")
    return copy(
        shifts = shifts.filter { it.id != id },
        reservations = reservations.map { res ->
            if (res.folio.any { it.shiftId == id }) {
                res.copy(folio = res.folio.map { if (it.shiftId == id) it.copy(shiftId = null) else it })
            } else {
                res
            }
        }
    ) to ok("Смена №$id удалена из истории")
}
