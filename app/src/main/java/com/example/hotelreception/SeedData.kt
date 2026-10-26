package com.example.hotelreception

import java.time.LocalDate
import java.time.LocalDateTime

// Начальные данные для демо. Даты считаются от "сегодня", поэтому приложение
// всегда выглядит живым: сегодня есть заезды, выезды и проживающие гости.
// Позже (вторая половина семестра) этот файл заменится базой данных.
object SeedData {

    fun create(today: LocalDate, now: LocalDateTime = LocalDateTime.now()): HotelData {
        var entryId = 1
        fun entry(title: String, amount: Int, type: EntryType, date: LocalDate) =
            FolioEntry(entryId++, title, amount, type, date)

        // Проживание + скидка лояльности + полная оплата при заселении
        fun paidStay(nights: Int, rate: Int, loyalty: Boolean, date: LocalDate, method: PaymentMethod): List<FolioEntry> {
            val total = nights * rate
            val list = mutableListOf(entry("Проживание: ${nightsText(nights)} × ${rate.tenge()}", total, EntryType.CHARGE, date))
            var net = total
            if (loyalty) {
                list += entry("Скидка лояльности 10%", total / 10, EntryType.DISCOUNT, date)
                net -= total / 10
            }
            list += entry("Оплата: ${method.label}", net, EntryType.PAYMENT, date)
            return list
        }

        fun reservation(
            id: Int,
            first: String,
            last: String,
            phone: String,
            roomClass: RoomClass,
            room: Int?,
            arrivalOffset: Int,
            departureOffset: Int,
            status: ResStatus,
            plan: RatePlan = RatePlan.BAR,
            method: PaymentMethod = PaymentMethod.CARD,
            agency: String = "",
            company: String = "",
            loyalty: Boolean = false,
            wish: RoomWish = RoomWish.NONE,
            inOffset: Int? = null,
            outOffset: Int? = null,
            folio: (Int) -> List<FolioEntry> = { emptyList() }
        ): Reservation {
            val rate = roomClass.baseRate * plan.percent / 100
            val nights = departureOffset - arrivalOffset
            return Reservation(
                id = id,
                guest = Guest(first, last, phone, "guest$id@mail.kz"),
                roomClass = roomClass,
                roomNumber = room,
                arrival = today.plusDays(arrivalOffset.toLong()),
                departure = today.plusDays(departureOffset.toLong()),
                status = status,
                ratePlan = plan,
                rate = rate,
                paymentMethod = method,
                agency = agency,
                company = company,
                isLoyalty = loyalty,
                wish = wish,
                passportPath = null,
                checkedInOn = inOffset?.let { today.plusDays(it.toLong()) },
                checkedOutOn = outOffset?.let { today.plusDays(it.toLong()) },
                folio = folio(nights)
            )
        }

        fun rate(roomClass: RoomClass, plan: RatePlan) = roomClass.baseRate * plan.percent / 100

        val reservations = listOf(
            // ----- Проживают -----
            reservation(
                100101, "Айгерим", "Сапарова", "+7 701 555 01 01", RoomClass.SKD, 105, -2, 2, ResStatus.IN_HOUSE,
                loyalty = true, inOffset = -2
            ) { n ->
                paidStay(n, rate(RoomClass.SKD, RatePlan.BAR), true, today.minusDays(2), PaymentMethod.CARD) +
                    entry("Завтрак в номер", 6_000, EntryType.CHARGE, today.minusDays(1))
            },
            reservation(
                100102, "Дмитрий", "Орлов", "+7 702 555 02 02", RoomClass.DBC, 101, -1, 2, ResStatus.IN_HOUSE,
                method = PaymentMethod.CASH, inOffset = -1
            ) { n -> paidStay(n, rate(RoomClass.DBC, RatePlan.BAR), false, today.minusDays(1), PaymentMethod.CASH) },
            reservation(
                100103, "Sarah", "Johnson", "+1 415 555 03 03", RoomClass.DBE, 104, -3, 0, ResStatus.IN_HOUSE,
                plan = RatePlan.WEB, agency = "Booking.com", inOffset = -3
            ) { n ->
                paidStay(n, rate(RoomClass.DBE, RatePlan.WEB), false, today.minusDays(3), PaymentMethod.CARD) +
                    entry("Мини-бар", 3_500, EntryType.CHARGE, today.minusDays(1))
            },
            reservation(
                100104, "Марат", "Ахметов", "+7 705 555 04 04", RoomClass.TWC, 203, -4, 0, ResStatus.IN_HOUSE,
                plan = RatePlan.CORP, method = PaymentMethod.COMPANY, company = "КазМунайГаз", inOffset = -4
            ) { n -> paidStay(n, rate(RoomClass.TWC, RatePlan.CORP), false, today.minusDays(4), PaymentMethod.COMPANY) },
            reservation(
                100105, "Wei", "Zhang", "+86 138 5555 0505", RoomClass.SKC, 306, -1, 3, ResStatus.IN_HOUSE,
                company = "Huawei", inOffset = -1
            ) { n ->
                // Оплачено больше, чем начислено: баланс < 0 (химчистка оплачена, но ещё не выполнена)
                listOf(
                    entry("Проживание: ${nightsText(n)} × ${rate(RoomClass.SKC, RatePlan.BAR).tenge()}", n * rate(RoomClass.SKC, RatePlan.BAR), EntryType.CHARGE, today.minusDays(1)),
                    entry("Оплата: Карта (с предоплатой за химчистку)", n * rate(RoomClass.SKC, RatePlan.BAR) + 30_000, EntryType.PAYMENT, today.minusDays(1))
                )
            },

            // ----- Выселены -----
            reservation(
                100106, "Анна", "Ким", "+7 707 555 06 06", RoomClass.DBC, 102, -3, 0, ResStatus.CHECKED_OUT,
                inOffset = -3, outOffset = 0
            ) { n -> paidStay(n, rate(RoomClass.DBC, RatePlan.BAR), false, today.minusDays(3), PaymentMethod.CARD) },
            reservation(
                100107, "Олег", "Петров", "+7 708 555 07 07", RoomClass.DBE, 304, -5, -1, ResStatus.CHECKED_OUT,
                method = PaymentMethod.CASH, inOffset = -5, outOffset = -1
            ) { n -> paidStay(n, rate(RoomClass.DBE, RatePlan.BAR), false, today.minusDays(5), PaymentMethod.CASH) },
            reservation(
                100116, "Ильяс", "Касымов", "+7 777 555 16 16", RoomClass.DBC, 201, -10, -7, ResStatus.CHECKED_OUT,
                plan = RatePlan.WEB, inOffset = -10, outOffset = -7
            ) { n -> paidStay(n, rate(RoomClass.DBC, RatePlan.WEB), false, today.minusDays(10), PaymentMethod.CARD) },
            reservation(
                100117, "Emily", "Clark", "+44 20 5555 1717", RoomClass.SKC, 606, -14, -11, ResStatus.CHECKED_OUT,
                agency = "Expedia", loyalty = true, inOffset = -14, outOffset = -11
            ) { n -> paidStay(n, rate(RoomClass.SKC, RatePlan.BAR), true, today.minusDays(14), PaymentMethod.CARD) },

            // ----- Ожидаются: сегодня (номера назначит система при запуске) -----
            reservation(
                100108, "Алия", "Бекова", "+7 700 555 08 08", RoomClass.DBC, null, 0, 2, ResStatus.CONFIRMED,
                plan = RatePlan.WEB
            ) { n -> listOf(entry("Депозит", n * rate(RoomClass.DBC, RatePlan.WEB), EntryType.PAYMENT, today.minusDays(5))) },
            reservation(
                100109, "Tom", "Becker", "+49 30 5555 0909", RoomClass.DBE, null, 0, 3, ResStatus.CONFIRMED,
                agency = "Expedia", wish = RoomWish.HIGH_FLOOR
            ),
            reservation(
                100110, "Нурлан", "Жаксыбеков", "+7 701 555 10 10", RoomClass.SKD, null, 0, 1, ResStatus.CONFIRMED,
                plan = RatePlan.CORP, method = PaymentMethod.COMPANY, company = "Halyk Bank", loyalty = true
            ),

            // ----- Ожидаются: позже -----
            reservation(
                100111, "Ерлан", "Темирханов", "+7 747 555 11 11", RoomClass.TWC, null, 1, 3, ResStatus.CONFIRMED,
                plan = RatePlan.WEB
            ),
            reservation(
                100112, "Maria", "Rossi", "+39 06 5555 1212", RoomClass.SKC, null, 2, 5, ResStatus.CONFIRMED,
                agency = "Booking.com", loyalty = true
            ) { _ -> listOf(entry("Депозит", 100_000, EntryType.PAYMENT, today.minusDays(2))) },
            reservation(
                100113, "Алмас", "Нурмуханов", "+7 775 555 13 13", RoomClass.DBE, null, 3, 4, ResStatus.CONFIRMED,
                method = PaymentMethod.CASH
            ),
            reservation(
                100114, "Min", "Li", "+86 139 5555 1414", RoomClass.SKD, null, 5, 9, ResStatus.CONFIRMED,
                plan = RatePlan.CORP, method = PaymentMethod.COMPANY, company = "Huawei"
            ),
            reservation(
                100115, "Дана", "Ержанова", "+7 706 555 15 15", RoomClass.DBC, null, 6, 8, ResStatus.CONFIRMED,
                plan = RatePlan.WEB, wish = RoomWish.LOW_FLOOR
            )
        )

        val hkOverrides = mapOf(
            102 to HkStatus.DIRTY, 202 to HkStatus.DIRTY, 306 to HkStatus.DIRTY, 403 to HkStatus.DIRTY,
            301 to HkStatus.CLEAN, 304 to HkStatus.CLEAN, 305 to HkStatus.CLEAN, 402 to HkStatus.CLEAN,
            206 to HkStatus.PICKUP,
            504 to HkStatus.MAINTENANCE
        )

        // 6 этажей по 6 номеров: x01, x02 — DBC; x03 — TWC; x04 — DBE; x05 — SKD; x06 — SKC
        val rooms = (1..6).flatMap { floor ->
            (1..6).map { i ->
                val roomClass = when (i) {
                    1, 2 -> RoomClass.DBC
                    3 -> RoomClass.TWC
                    4 -> RoomClass.DBE
                    5 -> RoomClass.SKD
                    else -> RoomClass.SKC
                }
                val number = floor * 100 + i
                Room(number, floor, roomClass, hkOverrides[number] ?: HkStatus.INSPECTED)
            }
        }

        val requests = listOf(
            ServiceRequest(1, Department.HOUSEKEEPING, "105", "Доп. полотенца", "Гость просит 2 полотенца", false, emptyList(), CURRENT_USER, now.minusHours(2), RequestStatus.ACCEPTED),
            ServiceRequest(2, Department.MAINTENANCE, "306", "Не работает кондиционер", "Шумит и не охлаждает", true, emptyList(), CURRENT_USER, now.minusMinutes(40), RequestStatus.NEW),
            ServiceRequest(3, Department.IT, "Ресепшн", "Проблема с принтером", "Не печатает бланки регистрации", false, emptyList(), "Анеля (Shift Leader)", now.minusHours(5), RequestStatus.ACCEPTED),
            ServiceRequest(4, Department.ROOM_SERVICE, "104", "Завтрак в номер", "2 порции, без глютена", false, emptyList(), CURRENT_USER, now.minusHours(26), RequestStatus.DONE),
            ServiceRequest(5, Department.HOUSEKEEPING, "102", "Уборка номера", "После выезда гостя", false, emptyList(), "Анеля (Shift Leader)", now.minusMinutes(15), RequestStatus.NEW),
            ServiceRequest(6, Department.MAINTENANCE, "504", "Протечка", "Течёт кран в ванной, номер закрыт", true, emptyList(), CURRENT_USER, now.minusHours(30), RequestStatus.ACCEPTED)
        )

        // Список внутренних номеров (с бумажного листа ресепшена). В будущем — из базы данных.
        val contacts = listOf(
            ContactGroup("Бронирование", listOf(ContactEntry("Reservation", "2009"), ContactEntry("Alika", "2010"), ContactEntry("Andrei", "2030"), ContactEntry("Almat", "2008"))),
            ContactGroup("Рестораны и SPA", listOf(ContactEntry("Room Service", "2032"), ContactEntry("Restaurant", "2033"), ContactEntry("Restaurant Manager", "2031"), ContactEntry("SPA", "2062"))),
            ContactGroup("Housekeeping", listOf(ContactEntry("Housekeeping", "2045"), ContactEntry("Housekeeping", "2046"), ContactEntry("Housekeeping", "2047"), ContactEntry("HK Supervisor", "2048"))),
            ContactGroup("Sales", listOf(ContactEntry("Leila", "2016"))),
            ContactGroup("Номера (Sales)", listOf(ContactEntry("Alima", "2014"), ContactEntry("Botakoz", "2012"), ContactEntry("Diana", "2013"))),
            ContactGroup("Конференц-залы и мероприятия", listOf(ContactEntry("Gulnaz", "2068"), ContactEntry("Botakoz", "2012"), ContactEntry("Akliya", "2015"), ContactEntry("Чина", "2037"))),
            ContactGroup("Finances", listOf(ContactEntry("Mervert", "2017"), ContactEntry("Kamar", "2020"))),
            ContactGroup("Security / IT / Engineering", listOf(ContactEntry("Security", "2065"), ContactEntry("IT / Orken", "2027"), ContactEntry("Engineer", "2059"), ContactEntry("Chief Engineer", "2057")))
        )

        return HotelData(
            today = today,
            rooms = rooms,
            reservations = reservations,
            requests = requests,
            contacts = contacts,
            nextReservationId = (reservations.maxOf { it.id }) + 1,
            nextEntryId = entryId,
            nextRequestId = requests.maxOf { it.id } + 1
        )
    }
}
