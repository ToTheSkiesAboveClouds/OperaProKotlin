package com.example.hotelreception

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// =====================================================================
// МОДЕЛИ ДАННЫХ (слой Data). Здесь нет ни Compose, ни Android — только Kotlin.
// Все классы неизменяемые (val): состояние меняется через copy().
// =====================================================================

// ---------- Номера ----------

// Классы номеров как в Opera: код, название, базовая цена за ночь (тенге)
enum class RoomClass(val code: String, val title: String, val baseRate: Int) {
    DBC("DBC", "Стандарт", 28_000),
    TWC("TWC", "Стандарт, раздельные кровати", 28_000),
    DBE("DBE", "Улучшенный стандарт", 36_000),
    SKD("SKD", "Полулюкс", 52_000),
    SKC("SKC", "Люкс", 80_000)
}

// Статус уборки (Housekeeping)
enum class HkStatus(val label: String) {
    INSPECTED("Inspected"), // убрано и проверено старшей горничной
    CLEAN("Clean"),         // убрано младшей горничной
    DIRTY("Dirty"),         // нужна уборка
    PICKUP("Pickup"),       // номер в запасе (держим для гостя)
    MAINTENANCE("Maintenance") // ремонт
}

// Занятость номера. Не хранится, а вычисляется из броней (см. HotelRules.kt)
enum class Occupancy(val label: String) {
    VACANT("Vacant"),         // никто не живёт и скоро не заедет
    OCCUPIED("Occupied"),     // кто-то проживает
    ON_ARRIVAL("On arrival")  // сейчас пусто, но скоро заезд
}

data class Room(
    val number: Int,
    val floor: Int,
    val roomClass: RoomClass,
    val hk: HkStatus
)

// ---------- Бронирования ----------

enum class ResStatus(val label: String) {
    CONFIRMED("Ожидается"),
    IN_HOUSE("Проживает"),
    CHECKED_OUT("Выселен")
}

enum class PaymentMethod(val label: String) {
    CASH("Наличные"),
    CARD("Карта"),
    TRANSFER("Перевод"),
    COMPANY("Счёт компании")
}

// Тариф (rate): процент от базовой цены класса
enum class RatePlan(val code: String, val title: String, val percent: Int) {
    BAR("BAR", "Лучший доступный", 100),
    WEB("WEB", "Онлайн-тариф", 90),
    CORP("CORP", "Корпоративный", 85)
}

// Пожелание гостя, учитывается при авто-назначении номера
enum class RoomWish(val label: String) {
    NONE("Без пожеланий"),
    HIGH_FLOOR("Высокий этаж"),
    LOW_FLOOR("Низкий этаж")
}

data class Guest(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

// Строка счёта гостя (folio). amount всегда > 0, знак определяет тип.
enum class EntryType(val sign: Int) {
    CHARGE(1),    // услуга/проживание: гость должен
    REFUND(1),    // возврат денег гостю: возвращает баланс к нулю снизу
    PAYMENT(-1),  // оплата
    DISCOUNT(-1)  // скидка
}

data class FolioEntry(
    val id: Int,
    val title: String,
    val amount: Int,
    val type: EntryType,
    val date: LocalDate
) {
    val signed: Int get() = amount * type.sign
}

enum class PayState { PAID, PARTIAL, UNPAID }

// Строка счёта + вычисленный статус оплаты (только для CHARGE)
data class FolioLine(val entry: FolioEntry, val state: PayState?)

data class Reservation(
    val id: Int,                       // номер бронирования
    val guest: Guest,
    val roomClass: RoomClass,
    val roomNumber: Int?,              // null = номер ещё не назначен
    val arrival: LocalDate,
    val departure: LocalDate,
    val status: ResStatus,
    val ratePlan: RatePlan,
    val rate: Int,                     // цена за ночь по тарифу
    val paymentMethod: PaymentMethod,
    val agency: String,                // "" = нет
    val company: String,               // "" = нет
    val isLoyalty: Boolean,            // участник программы лояльности
    val wish: RoomWish,
    val passportPath: String?,         // путь к скану паспорта (null = не сканирован)
    val checkedInOn: LocalDate?,
    val checkedOutOn: LocalDate?,
    val folio: List<FolioEntry>
) {
    val nights: Int get() = ChronoUnit.DAYS.between(arrival, departure).toInt()
    val stayTotal: Int get() = nights * rate

    // Баланс: > 0 гость должен нам, < 0 гость оплатил вперёд, = 0 всё закрыто
    val balance: Int get() = folio.sumOf { it.signed }

    // Какие услуги оплачены: оплаты и скидки погашают услуги по порядку
    fun folioLines(): List<FolioLine> {
        var credit = folio.filter { it.type == EntryType.PAYMENT || it.type == EntryType.DISCOUNT }.sumOf { it.amount } -
            folio.filter { it.type == EntryType.REFUND }.sumOf { it.amount }
        if (credit < 0) credit = 0
        return folio.map { entry ->
            if (entry.type != EntryType.CHARGE) {
                FolioLine(entry, null)
            } else {
                val covered = minOf(credit, entry.amount)
                credit -= covered
                val state = when {
                    covered >= entry.amount -> PayState.PAID
                    covered > 0 -> PayState.PARTIAL
                    else -> PayState.UNPAID
                }
                FolioLine(entry, state)
            }
        }
    }
}

// Каталог услуг для быстрого добавления в счёт
data class ServiceItem(val title: String, val price: Int)

val SERVICE_CATALOG = listOf(
    ServiceItem("Завтрак в номер", 6_000),
    ServiceItem("Ужин в номер", 12_000),
    ServiceItem("Стирка (3 вещи)", 4_500),
    ServiceItem("Такси до аэропорта", 8_000),
    ServiceItem("Мини-бар", 3_500),
    ServiceItem("SPA-процедура", 15_000),
    ServiceItem("Поздний выезд", 10_000)
)

// Данные формы "Новая бронь"
data class ReservationDraft(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val roomClass: RoomClass,
    val arrival: LocalDate,
    val nights: Int,
    val ratePlan: RatePlan,
    val paymentMethod: PaymentMethod,
    val agency: String,
    val company: String,
    val isLoyalty: Boolean,
    val wish: RoomWish,
    val deposit: Int
)

// ---------- Запросы в отделы (аналог приложения Sara) ----------

enum class Department(val title: String, val presets: List<String>) {
    HOUSEKEEPING(
        "Housekeeping",
        listOf("Уборка номера", "Доп. полотенца", "Доп. подушки", "Принести утюг", "Вещи оставили в номере")
    ),
    ROOM_SERVICE(
        "Room Service",
        listOf("Завтрак в номер", "Заказ напитков", "Убрать посуду")
    ),
    MAINTENANCE(
        "Maintenance",
        listOf("Не работает кондиционер", "Протечка", "Не горит свет", "Сломан замок")
    ),
    IT(
        "IT",
        listOf("Нет Wi-Fi", "Не работает ТВ", "Проблема с принтером", "Сбой PMS")
    )
}

enum class RequestStatus(val label: String, val isOpen: Boolean) {
    NEW("New", true),
    ACCEPTED("Accepted", true),
    DONE("Done", false)
}

data class Attachment(val path: String, val isVideo: Boolean)

data class ServiceRequest(
    val id: Int,
    val department: Department,
    val location: String,          // номер комнаты или место ("Лобби")
    val title: String,
    val description: String,
    val priority: Boolean,
    val attachments: List<Attachment>,
    val createdBy: String,
    val createdAt: LocalDateTime,
    val status: RequestStatus
)

// ---------- Список внутренних телефонов ----------

data class ContactEntry(val label: String, val ext: String)
data class ContactGroup(val name: String, val entries: List<ContactEntry>)

// ---------- Все данные отеля одним объектом ----------

data class HotelData(
    val today: LocalDate,
    val rooms: List<Room>,
    val reservations: List<Reservation>,
    val requests: List<ServiceRequest>,
    val contacts: List<ContactGroup>,
    val nextReservationId: Int,
    val nextEntryId: Int,
    val nextRequestId: Int
)

// ---------- Результат операции ----------

sealed interface Outcome {
    data class Success(val message: String, val id: Int? = null) : Outcome
    data class Failure(val reason: String) : Outcome
}

const val CURRENT_USER = "Вы"

// За сколько дней до заезда система сама назначает номер
const val SOON_DAYS = 1L
