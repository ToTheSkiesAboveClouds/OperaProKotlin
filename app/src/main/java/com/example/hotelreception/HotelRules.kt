package com.example.hotelreception

import java.time.LocalDate

// =====================================================================
// ПРАВИЛА ОТЕЛЯ (слой Domain). Чистые функции без Android и Compose:
// их легко читать, тестировать и объяснять на защите.
// =====================================================================

fun HotelData.roomByNumber(number: Int): Room? = rooms.firstOrNull { it.number == number }

fun HotelData.reservationById(id: Int): Reservation? = reservations.firstOrNull { it.id == id }

private fun isActive(r: Reservation) = r.status == ResStatus.CONFIRMED || r.status == ResStatus.IN_HOUSE

// Занятость вычисляется из броней, а не хранится отдельно
fun HotelData.occupancyOf(roomNumber: Int): Occupancy {
    val here = reservations.filter { it.roomNumber == roomNumber }
    return when {
        here.any { it.status == ResStatus.IN_HOUSE } -> Occupancy.OCCUPIED
        here.any {
            it.status == ResStatus.CONFIRMED && !it.arrival.isAfter(today.plusDays(SOON_DAYS))
        } -> Occupancy.ON_ARRIVAL
        else -> Occupancy.VACANT
    }
}

// Бронь, которая сейчас "привязана" к номеру (проживает или скоро заезжает)
fun HotelData.currentReservationIn(roomNumber: Int): Reservation? =
    reservations.firstOrNull { it.roomNumber == roomNumber && it.status == ResStatus.IN_HOUSE }
        ?: reservations
            .filter {
                it.roomNumber == roomNumber && it.status == ResStatus.CONFIRMED &&
                    !it.arrival.isAfter(today.plusDays(SOON_DAYS))
            }
            .minByOrNull { it.arrival }

// Свободен ли номер на период [arrival, departure). День выезда одного гостя = день заезда другого.
fun HotelData.isRoomFree(
    roomNumber: Int,
    arrival: LocalDate,
    departure: LocalDate,
    exceptId: Int? = null
): Boolean = reservations.none { r ->
    r.id != exceptId &&
        r.roomNumber == roomNumber &&
        isActive(r) &&
        r.arrival.isBefore(departure) &&
        arrival.isBefore(r.departure)
}

// Подходящие номера для брони: тот же класс, не на ремонте, свободны на даты
fun HotelData.candidateRooms(res: Reservation): List<Room> =
    rooms.filter {
        it.roomClass == res.roomClass &&
            it.hk != HkStatus.MAINTENANCE &&
            isRoomFree(it.number, res.arrival, res.departure, res.id)
    }

private fun readiness(hk: HkStatus): Int = when (hk) {
    HkStatus.INSPECTED -> 0
    HkStatus.CLEAN -> 1
    else -> 2
}

// Лучший номер для авто-назначения:
// 1) сейчас никто не живёт, 2) готовность (Inspected → Clean → Dirty), 3) пожелание гостя по этажу
fun HotelData.bestRoomFor(res: Reservation): Room? =
    candidateRooms(res)
        .filter { it.hk != HkStatus.PICKUP } // Pickup — запас, автоматически не отдаём
        .sortedWith(
            compareBy<Room> { occupancyOf(it.number) == Occupancy.OCCUPIED }
                .thenBy { readiness(it.hk) }
                .thenBy {
                    when (res.wish) {
                        RoomWish.HIGH_FLOOR -> -it.floor
                        RoomWish.LOW_FLOOR -> it.floor
                        RoomWish.NONE -> 0
                    }
                }
                .thenBy { it.number }
        )
        .firstOrNull()

// Автоматически назначает номера всем броням, у которых заезд скоро. Возвращает новые данные и число назначений.
fun HotelData.withAutoAssignedRooms(): Pair<HotelData, Int> {
    var data = this
    var count = 0
    val pending = reservations
        .filter {
            it.status == ResStatus.CONFIRMED &&
                it.roomNumber == null &&
                !it.arrival.isAfter(today.plusDays(SOON_DAYS))
        }
        .sortedBy { it.arrival }
    for (res in pending) {
        val room = data.bestRoomFor(res) ?: continue
        data = data.copy(
            reservations = data.reservations.map {
                if (it.id == res.id) it.copy(roomNumber = room.number) else it
            }
        )
        count++
    }
    return data to count
}

// ---------- Доступность по датам ----------

data class AvailabilityRow(
    val roomClass: RoomClass,
    val total: Int,        // всего номеров класса
    val free: List<Int>    // сколько свободно на каждую из дат
)

// Сколько номеров класса свободно на ночь date
fun HotelData.freeCount(roomClass: RoomClass, date: LocalDate): Int {
    val usable = rooms.count { it.roomClass == roomClass && it.hk != HkStatus.MAINTENANCE }
    val booked = reservations.count {
        it.roomClass == roomClass && isActive(it) &&
            !it.arrival.isAfter(date) && date.isBefore(it.departure)
    }
    return (usable - booked).coerceAtLeast(0)
}

fun HotelData.availability(days: Int = 7): Pair<List<LocalDate>, List<AvailabilityRow>> {
    val dates = (0 until days).map { today.plusDays(it.toLong()) }
    val rows = RoomClass.entries.map { cls ->
        AvailabilityRow(
            roomClass = cls,
            total = rooms.count { it.roomClass == cls },
            free = dates.map { freeCount(cls, it) }
        )
    }
    return dates to rows
}

// ---------- Статистика дня ----------

data class DayStats(
    val arrivalsExpected: Int,
    val arrivalsActual: Int,
    val departuresExpected: Int,
    val departuresActual: Int,
    val inHouse: Int,
    val occupancyPercent: Int,
    val hkCounts: Map<HkStatus, Int>
)

fun HotelData.dayStats(): DayStats {
    // Ожидаемые заезды: у кого дата заезда сегодня (плюс те, кто заехал сегодня с опозданием)
    val arrivalsExpected = reservations.count { it.arrival == today || it.checkedInOn == today }
    val arrivalsActual = reservations.count { it.checkedInOn == today }
    val departuresExpected = reservations.count {
        it.departure == today && (it.status == ResStatus.IN_HOUSE || it.checkedOutOn == today)
    }
    val departuresActual = reservations.count { it.checkedOutOn == today }
    val inHouse = reservations.count { it.status == ResStatus.IN_HOUSE }
    val sellable = rooms.count { it.hk != HkStatus.MAINTENANCE }
    val occupancy = if (sellable == 0) 0 else inHouse * 100 / sellable
    return DayStats(
        arrivalsExpected = arrivalsExpected,
        arrivalsActual = arrivalsActual,
        departuresExpected = departuresExpected,
        departuresActual = departuresActual,
        inHouse = inHouse,
        occupancyPercent = occupancy,
        hkCounts = HkStatus.entries.associateWith { status -> rooms.count { it.hk == status } }
    )
}

// ---------- Фильтры списков броней ----------

enum class ResTab(val title: String) {
    ARRIVALS("Arrivals"),
    IN_HOUSE("In-house"),
    ALL("Все брони")
}

enum class ArrivalRange(val label: String) {
    TODAY("Сегодня"),
    WEEK("7 дней"),
    ALL("Все будущие")
}

enum class InHouseMode(val label: String) {
    IN("Проживают"),
    OUT("Выселены (3 дня)"),
    BOTH("Все")
}

data class ReservationFilters(
    val tab: ResTab = ResTab.ARRIVALS,
    val query: String = "",
    val arrivalRange: ArrivalRange = ArrivalRange.TODAY,
    val inHouseMode: InHouseMode = InHouseMode.IN,
    val status: ResStatus? = null,
    val dateFrom: LocalDate? = null,   // дата начала брони (заезд) не раньше этой
    val dateTo: LocalDate? = null      // дата окончания брони (выезд) не позже этой
)

private fun matchesQuery(r: Reservation, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return r.id.toString().contains(q) ||
        (r.roomNumber?.toString()?.contains(q) ?: false) ||
        r.guest.fullName.contains(q, ignoreCase = true)
}

fun HotelData.filterReservations(f: ReservationFilters): List<Reservation> {
    val base = when (f.tab) {
        ResTab.ARRIVALS -> reservations
            .filter { it.status == ResStatus.CONFIRMED }
            .filter {
                when (f.arrivalRange) {
                    ArrivalRange.TODAY -> !it.arrival.isAfter(today)
                    ArrivalRange.WEEK -> !it.arrival.isAfter(today.plusDays(7))
                    ArrivalRange.ALL -> true
                }
            }
            .sortedBy { it.arrival }

        ResTab.IN_HOUSE -> reservations
            .filter {
                val living = it.status == ResStatus.IN_HOUSE
                val departed = it.status == ResStatus.CHECKED_OUT &&
                    it.checkedOutOn != null && !it.checkedOutOn.isBefore(today.minusDays(3))
                when (f.inHouseMode) {
                    InHouseMode.IN -> living
                    InHouseMode.OUT -> departed
                    InHouseMode.BOTH -> living || departed
                }
            }
            .sortedWith(compareBy<Reservation> { it.status != ResStatus.IN_HOUSE }.thenBy { it.roomNumber ?: 0 })

        ResTab.ALL -> reservations
            .filter { f.status == null || it.status == f.status }
            .filter { f.dateFrom == null || !it.arrival.isBefore(f.dateFrom) }
            .filter { f.dateTo == null || !it.departure.isAfter(f.dateTo) }
            .sortedByDescending { it.arrival }
    }
    return base.filter { matchesQuery(it, f.query) }
}
