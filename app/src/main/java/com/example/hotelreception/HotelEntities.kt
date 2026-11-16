package com.example.hotelreception

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime

// =====================================================================
// ТАБЛИЦЫ БАЗЫ ДАННЫХ (Room Entity) и преобразование в модели приложения.
// Entity описывает, как данные лежат в SQLite. Остальной код приложения работает
// с обычными моделями из Models.kt, а переводом между ними занимаются функции ниже.
// =====================================================================

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val number: Int,
    val floor: Int,
    val roomClass: RoomClass,
    val hk: HkStatus
)

@Entity(tableName = "reservations")
data class ReservationEntity(
    @PrimaryKey val id: Int,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val roomClass: RoomClass,
    val roomNumber: Int?,
    val arrival: LocalDate,
    val departure: LocalDate,
    val status: ResStatus,
    val ratePlan: RatePlan,
    val rate: Int,
    val paymentMethod: PaymentMethod,
    val agency: String,
    val company: String,
    val isLoyalty: Boolean,
    val wish: RoomWish,
    val passportPath: String?,
    val checkedInOn: LocalDate?,
    val checkedOutOn: LocalDate?
)

// Строка счёта гостя. Связана с бронью: при удалении брони её счёт удаляется сам (CASCADE)
@Entity(
    tableName = "folio",
    foreignKeys = [
        ForeignKey(
            entity = ReservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["reservationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reservationId")]
)
data class FolioEntity(
    @PrimaryKey val id: Int,
    val reservationId: Int,
    val title: String,
    val amount: Int,
    val type: EntryType,
    val date: LocalDate,
    val method: PaymentMethod?,
    val shiftId: Int?
)

// Бронь вместе со счётом: Room сам собирает связь «одна бронь, много строк счёта»
data class ReservationWithFolio(
    @Embedded val reservation: ReservationEntity,
    @Relation(parentColumn = "id", entityColumn = "reservationId")
    val folio: List<FolioEntity>
)

@Entity(tableName = "requests")
data class RequestEntity(
    @PrimaryKey val id: Int,
    val department: Department,
    val location: String,
    val title: String,
    val description: String,
    val priority: Boolean,
    val attachments: List<Attachment>,
    val createdBy: String,
    val createdAt: LocalDateTime,
    val status: RequestStatus
)

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val id: Int,
    val openedBy: String,
    val openedAt: LocalDateTime,
    val openingCash: Int,
    val status: ShiftStatus,
    val closedAt: LocalDateTime?,
    val cash: Int,
    val card: Int,
    val transfer: Int,
    val company: Int,
    val cashRefunds: Int,
    val otherRefunds: Int,
    val paymentsCount: Int,
    val countedCash: Int?,
    val note: String
)

// ---------- Конвертеры типов: как Room хранит даты и вложения ----------

class Converters {
    @TypeConverter
    fun dateToText(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun textToDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun dateTimeToText(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun textToDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }

    // Вложения запроса храним одной строкой: «v|путь» для видео, «i|путь» для фото, по строке на файл
    @TypeConverter
    fun attachmentsToText(value: List<Attachment>): String =
        value.joinToString("\n") { (if (it.isVideo) "v|" else "i|") + it.path }

    @TypeConverter
    fun textToAttachments(value: String): List<Attachment> =
        if (value.isBlank()) {
            emptyList()
        } else {
            value.split("\n").map { Attachment(path = it.drop(2), isVideo = it.startsWith("v|")) }
        }
}

// ---------- Entity <-> модели приложения ----------

fun Room.toEntity() = RoomEntity(number, floor, roomClass, hk)
fun RoomEntity.toModel() = Room(number, floor, roomClass, hk)

fun FolioEntry.toEntity(reservationId: Int) =
    FolioEntity(id, reservationId, title, amount, type, date, method, shiftId)

fun FolioEntity.toModel() = FolioEntry(id, title, amount, type, date, method, shiftId)

fun Reservation.toEntity() = ReservationEntity(
    id = id,
    firstName = guest.firstName,
    lastName = guest.lastName,
    phone = guest.phone,
    email = guest.email,
    roomClass = roomClass,
    roomNumber = roomNumber,
    arrival = arrival,
    departure = departure,
    status = status,
    ratePlan = ratePlan,
    rate = rate,
    paymentMethod = paymentMethod,
    agency = agency,
    company = company,
    isLoyalty = isLoyalty,
    wish = wish,
    passportPath = passportPath,
    checkedInOn = checkedInOn,
    checkedOutOn = checkedOutOn
)

fun ReservationWithFolio.toModel() = Reservation(
    id = reservation.id,
    guest = Guest(reservation.firstName, reservation.lastName, reservation.phone, reservation.email),
    roomClass = reservation.roomClass,
    roomNumber = reservation.roomNumber,
    arrival = reservation.arrival,
    departure = reservation.departure,
    status = reservation.status,
    ratePlan = reservation.ratePlan,
    rate = reservation.rate,
    paymentMethod = reservation.paymentMethod,
    agency = reservation.agency,
    company = reservation.company,
    isLoyalty = reservation.isLoyalty,
    wish = reservation.wish,
    passportPath = reservation.passportPath,
    checkedInOn = reservation.checkedInOn,
    checkedOutOn = reservation.checkedOutOn,
    folio = folio.sortedBy { it.id }.map { it.toModel() }
)

fun ServiceRequest.toEntity() = RequestEntity(
    id, department, location, title, description, priority, attachments, createdBy, createdAt, status
)

fun RequestEntity.toModel() = ServiceRequest(
    id, department, location, title, description, priority, attachments, createdBy, createdAt, status
)

fun Shift.toEntity() = ShiftEntity(
    id = id,
    openedBy = openedBy,
    openedAt = openedAt,
    openingCash = openingCash,
    status = status,
    closedAt = closedAt,
    cash = totals.cash,
    card = totals.card,
    transfer = totals.transfer,
    company = totals.company,
    cashRefunds = totals.cashRefunds,
    otherRefunds = totals.otherRefunds,
    paymentsCount = totals.paymentsCount,
    countedCash = countedCash,
    note = note
)

fun ShiftEntity.toModel() = Shift(
    id = id,
    openedBy = openedBy,
    openedAt = openedAt,
    openingCash = openingCash,
    status = status,
    closedAt = closedAt,
    totals = ShiftTotals(cash, card, transfer, company, cashRefunds, otherRefunds, paymentsCount),
    countedCash = countedCash,
    note = note
)
