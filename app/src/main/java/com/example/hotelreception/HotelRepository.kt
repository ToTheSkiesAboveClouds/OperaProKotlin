package com.example.hotelreception

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime

// =====================================================================
// РЕПОЗИТОРИЙ (слой Data). Единственное место, где живут данные отеля и где они меняются.
// ViewModel'и только читают data и вызывают функции репозитория.
// Сейчас данные в памяти; во второй половине семестра здесь появится база данных,
// а ViewModel'и и экраны менять не придётся.
// =====================================================================

// Внутренний способ прервать операцию с понятным сообщением
private class Rejected(message: String) : RuntimeException(message)

private fun reject(message: String): Nothing = throw Rejected(message)

private fun ok(message: String, id: Int? = null) = Outcome.Success(message, id)

private fun HotelData.requireReservation(id: Int): Reservation =
    reservationById(id) ?: reject("Бронь №$id не найдена")

private fun HotelData.requireRoom(number: Int): Room =
    roomByNumber(number) ?: reject("Номер $number не найден")

private fun HotelData.updateReservation(id: Int, change: (Reservation) -> Reservation): HotelData =
    copy(reservations = reservations.map { if (it.id == id) change(it) else it })

// Добавляет строку в счёт гостя
private fun HotelData.withEntry(resId: Int, title: String, amount: Int, type: EntryType): HotelData {
    val entry = FolioEntry(nextEntryId, title, amount, type, today)
    return copy(nextEntryId = nextEntryId + 1)
        .updateReservation(resId) { it.copy(folio = it.folio + entry) }
}

class HotelRepository(private val clock: () -> LocalDate = { LocalDate.now() }) {

    // StateFlow хранит текущее состояние и сообщает подписчикам об изменениях
    private val _data = MutableStateFlow(SeedData.create(clock()).withAutoAssignedRooms().first)
    val data: StateFlow<HotelData> = _data.asStateFlow()

    // Общая обёртка: берёт текущие данные, применяет операцию, сохраняет результат.
    // Если операция вызвала reject(...), данные не меняются, а вызывающий получает Failure.
    private fun mutate(block: HotelData.() -> Pair<HotelData, Outcome.Success>): Outcome =
        synchronized(this) {
            try {
                val current = _data.value.copy(today = clock())
                val (next, success) = current.block()
                _data.value = next
                success
            } catch (e: Rejected) {
                Outcome.Failure(e.message ?: "Операция не выполнена")
            }
        }

    // ---------- Брони: номера ----------

    fun autoAssignRooms(): Outcome = mutate {
        val (next, count) = withAutoAssignedRooms()
        if (count == 0) reject("Нет броней, которым можно назначить номер")
        next to ok("Назначено номеров: $count")
    }

    fun assignRoom(resId: Int, roomNumber: Int): Outcome = mutate {
        val res = requireReservation(resId)
        if (res.status != ResStatus.CONFIRMED) reject("Номер назначается только ожидаемой брони")
        val room = requireRoom(roomNumber)
        if (room.roomClass != res.roomClass) reject("Номер $roomNumber другого класса")
        if (room.hk == HkStatus.MAINTENANCE) reject("Номер $roomNumber на ремонте")
        if (!isRoomFree(roomNumber, res.arrival, res.departure, res.id)) reject("Номер $roomNumber занят на эти даты")
        updateReservation(resId) { it.copy(roomNumber = roomNumber) } to ok("Назначен номер $roomNumber")
    }

    fun attachPassport(resId: Int, path: String): Outcome = mutate {
        requireReservation(resId)
        updateReservation(resId) { it.copy(passportPath = path) } to ok("Скан паспорта сохранён")
    }

    // ---------- Брони: заселение и выселение ----------

    fun checkIn(resId: Int): Outcome = mutate {
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
        next to ok("Гость ${res.guest.fullName} заселён в номер $number")
    }

    fun checkOut(resId: Int): Outcome = mutate {
        val res = requireReservation(resId)
        if (res.status != ResStatus.IN_HOUSE) reject("Выселять можно только проживающего гостя")
        val balance = res.balance
        if (balance > 0) reject("Баланс ${balance.tenge()}: гость должен оплатить. Выселение возможно при балансе 0")
        if (balance < 0) reject("Баланс ${balance.tenge()}: вернуть деньги или выполнить услугу. Выселение возможно при балансе 0")
        val number = res.roomNumber
        copy(
            rooms = rooms.map { if (it.number == number) it.copy(hk = HkStatus.DIRTY) else it }
        ).updateReservation(resId) {
            it.copy(status = ResStatus.CHECKED_OUT, checkedOutOn = today)
        } to ok("Гость выселен, номер ${number ?: "—"} теперь Dirty")
    }

    // ---------- Биллинг ----------

    fun addCharge(resId: Int, title: String, amount: Int): Outcome = mutate {
        val res = requireReservation(resId)
        if (res.status != ResStatus.IN_HOUSE) reject("Услуги добавляются только проживающему гостю")
        if (title.isBlank()) reject("Введите название услуги")
        if (amount <= 0) reject("Сумма должна быть больше нуля")
        withEntry(resId, title.trim(), amount, EntryType.CHARGE) to ok("Добавлено: ${title.trim()}, ${amount.tenge()}")
    }

    fun addPayment(resId: Int, amount: Int): Outcome = mutate {
        val res = requireReservation(resId)
        if (res.status == ResStatus.CHECKED_OUT) reject("Бронь уже закрыта")
        if (amount <= 0) reject("Сумма должна быть больше нуля")
        withEntry(resId, "Оплата: ${res.paymentMethod.label}", amount, EntryType.PAYMENT) to
            ok("Принята оплата ${amount.tenge()}")
    }

    // Закрыть баланс: если гость должен — принять оплату, если переплатил — вернуть деньги
    fun settleBalance(resId: Int): Outcome = mutate {
        val res = requireReservation(resId)
        if (res.status != ResStatus.IN_HOUSE) reject("Баланс закрывается у проживающего гостя")
        val balance = res.balance
        when {
            balance > 0 ->
                withEntry(resId, "Оплата остатка: ${res.paymentMethod.label}", balance, EntryType.PAYMENT) to
                    ok("Принята оплата ${balance.tenge()}, баланс 0")
            balance < 0 ->
                withEntry(resId, "Возврат гостю", -balance, EntryType.REFUND) to
                    ok("Возвращено ${(-balance).tenge()}, баланс 0")
            else -> reject("Баланс уже равен 0")
        }
    }

    // ---------- Новая бронь ----------

    fun createReservation(d: ReservationDraft): Outcome = mutate {
        if (d.firstName.isBlank() || d.lastName.isBlank()) reject("Введите имя и фамилию гостя")
        if (d.phone.count { it.isDigit() } < 7) reject("Введите телефон (минимум 7 цифр)")
        if (d.email.isNotBlank() && !d.email.contains("@")) reject("Почта указана неверно")
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
        val entries = if (d.deposit > 0) {
            listOf(FolioEntry(nextEntryId, "Депозит", d.deposit, EntryType.PAYMENT, today))
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
        assigned to ok("Бронь №$id создана", id)
    }

    // ---------- Номера: статус уборки ----------

    fun setHk(roomNumber: Int, newStatus: HkStatus): Outcome = mutate {
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
        copy(rooms = rooms.map { if (it.number == roomNumber) it.copy(hk = newStatus) else it }) to
            ok("Номер $roomNumber: ${newStatus.label}")
    }

    // ---------- Запросы в отделы ----------

    fun createRequest(
        department: Department,
        location: String,
        title: String,
        description: String,
        priority: Boolean,
        attachments: List<Attachment>
    ): Outcome = mutate {
        if (location.isBlank()) reject("Укажите номер комнаты или место")
        if (title.isBlank()) reject("Укажите цель запроса")
        val request = ServiceRequest(
            id = nextRequestId,
            department = department,
            location = location.trim(),
            title = title.trim(),
            description = description.trim(),
            priority = priority,
            attachments = attachments,
            createdBy = CURRENT_USER,
            createdAt = LocalDateTime.now(),
            status = RequestStatus.NEW
        )
        copy(requests = requests + request, nextRequestId = nextRequestId + 1) to
            ok("Запрос отправлен в ${department.title}", request.id)
    }

    fun advanceRequest(id: Int, newStatus: RequestStatus): Outcome = mutate {
        val request = requests.firstOrNull { it.id == id } ?: reject("Запрос не найден")
        if (newStatus.ordinal <= request.status.ordinal) reject("Статус нельзя вернуть назад")
        copy(requests = requests.map { if (it.id == id) it.copy(status = newStatus) else it }) to
            ok("Запрос №$id: ${newStatus.label}")
    }

    companion object {
        // Один общий экземпляр на всё приложение (позже его заменит внедрение зависимостей)
        val instance: HotelRepository by lazy { HotelRepository() }
    }
}
