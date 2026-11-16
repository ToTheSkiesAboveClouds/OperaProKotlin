package com.example.hotelreception

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime

// =====================================================================
// РЕПОЗИТОРИЙ (слой Data). Единственный вход к базе данных Room.
//  - data: StateFlow со всеми данными отеля. Он строится из Flow таблиц Room, поэтому после
//    любой записи в базу все экраны обновляются сами.
//  - suspend-функции ниже меняют данные. Каждая выполняется одной транзакцией:
//    прочитать всё -> применить операцию из HotelOperations.kt -> записать изменения.
//    Если операция нарушила правило, транзакция откатывается, а пользователь видит причину.
// ViewModel'и и экраны про Room ничего не знают: они работают только с этим классом.
// =====================================================================

// Собирает все данные отеля из строк таблиц
private fun assemble(
    rooms: List<RoomEntity>,
    reservations: List<ReservationWithFolio>,
    requests: List<RequestEntity>,
    shifts: List<ShiftEntity>,
    today: LocalDate
): HotelData {
    val reservationModels = reservations.map { it.toModel() }
    val requestModels = requests.map { it.toModel() }
    val shiftModels = shifts.map { it.toModel() }
    return HotelData(
        today = today,
        rooms = rooms.map { it.toModel() },
        reservations = reservationModels,
        requests = requestModels,
        contacts = SeedData.contacts,
        shifts = shiftModels,
        // Следующие номера считаем от самых больших существующих, поэтому они не повторяются
        nextReservationId = (reservationModels.maxOfOrNull { it.id } ?: 100_000) + 1,
        nextEntryId = (reservationModels.flatMap { it.folio }.maxOfOrNull { it.id } ?: 0) + 1,
        nextRequestId = (requestModels.maxOfOrNull { it.id } ?: 0) + 1,
        nextShiftId = (shiftModels.maxOfOrNull { it.id } ?: 0) + 1
    )
}

// Разница между старыми и новыми данными: что добавить или изменить, что удалить
private data class Diff<T>(val upserts: List<T>, val deletedIds: List<Int>)

private fun <T> diff(old: List<T>, new: List<T>, id: (T) -> Int): Diff<T> {
    val oldById = old.associateBy(id)
    val newIds = new.map(id).toSet()
    return Diff(
        upserts = new.filter { oldById[id(it)] != it },
        deletedIds = oldById.keys.filter { it !in newIds }
    )
}

class HotelRepository private constructor(
    private val db: HotelDatabase,
    scope: CoroutineScope,
    private val clock: () -> LocalDate,
    initial: HotelData
) {
    private val dao = db.dao()

    // Flow из четырёх таблиц склеивается в один объект HotelData. stateIn делает из него StateFlow:
    // у него всегда есть текущее значение, и его удобно собирать в ViewModel.
    val data: StateFlow<HotelData> = combine(
        dao.observeRooms(),
        dao.observeReservations(),
        dao.observeRequests(),
        dao.observeShifts()
    ) { rooms, reservations, requests, shifts ->
        assemble(rooms, reservations, requests, shifts, clock())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, initial)

    // Читает все данные из базы один раз
    private suspend fun load(): HotelData = loadAll(dao, clock())

    // Записывает в базу только то, что изменилось между old и new
    private suspend fun persist(old: HotelData, new: HotelData) {
        persistChanges(dao, old, new)
    }

    // Общая обёртка: транзакция «прочитать -> изменить -> записать».
    // Если операция вызвала reject(...), транзакция откатывается, а вызывающий получает Failure.
    private suspend fun mutate(block: HotelData.() -> Step): Outcome =
        try {
            db.withTransaction {
                val current = load()
                val (next, success) = current.block()
                persist(current, next)
                success
            }
        } catch (e: Rejected) {
            Outcome.Failure(e.message ?: "Операция не выполнена")
        }

    // ---------- Брони: номера, заселение, выселение ----------

    suspend fun autoAssignRooms() = mutate { opAutoAssignRooms() }
    suspend fun assignRoom(resId: Int, roomNumber: Int) = mutate { opAssignRoom(resId, roomNumber) }
    suspend fun attachPassport(resId: Int, path: String) = mutate { opAttachPassport(resId, path) }
    suspend fun checkIn(resId: Int) = mutate { opCheckIn(resId) }
    suspend fun checkOut(resId: Int) = mutate { opCheckOut(resId) }

    // ---------- Биллинг ----------

    suspend fun addCharge(resId: Int, title: String, amount: Int) = mutate { opAddCharge(resId, title, amount) }
    suspend fun addPayment(resId: Int, amount: Int, method: PaymentMethod?) =
        mutate { opAddPayment(resId, amount, method) }
    suspend fun settleBalance(resId: Int) = mutate { opSettleBalance(resId) }

    // ---------- Брони: создание, правка, удаление ----------

    suspend fun createReservation(draft: ReservationDraft) = mutate { opCreateReservation(draft) }
    suspend fun updateReservation(resId: Int, draft: ReservationDraft) = mutate { opUpdateReservation(resId, draft) }
    suspend fun deleteReservation(resId: Int) = mutate { opDeleteReservation(resId) }

    // ---------- Номера ----------

    suspend fun setHk(roomNumber: Int, status: HkStatus) = mutate { opSetHk(roomNumber, status) }

    // ---------- Запросы в отделы ----------

    suspend fun createRequest(
        department: Department,
        location: String,
        title: String,
        description: String,
        priority: Boolean,
        attachments: List<Attachment>
    ) = mutate { opCreateRequest(department, location, title, description, priority, attachments) }

    suspend fun updateRequest(
        id: Int,
        department: Department,
        location: String,
        title: String,
        description: String,
        priority: Boolean,
        attachments: List<Attachment>
    ) = mutate { opUpdateRequest(id, department, location, title, description, priority, attachments) }

    suspend fun deleteRequest(id: Int) = mutate { opDeleteRequest(id) }
    suspend fun advanceRequest(id: Int, status: RequestStatus) = mutate { opAdvanceRequest(id, status) }

    // ---------- Кассовая смена ----------

    suspend fun openShift(openingCash: Int) = mutate { opOpenShift(CURRENT_USER, openingCash, LocalDateTime.now()) }
    suspend fun closeShift(countedCash: Int?, note: String) =
        mutate { opCloseShift(countedCash, note, LocalDateTime.now()) }
    suspend fun updateShiftNote(id: Int, note: String) = mutate { opUpdateShiftNote(id, note) }
    suspend fun deleteShift(id: Int) = mutate { opDeleteShift(id) }

    // ---------- Сброс демо-данных ----------

    // Очищает базу и заново записывает стартовые данные (даты снова считаются от сегодняшнего дня)
    suspend fun resetDemoData(): Outcome {
        db.withTransaction {
            clearAll(dao)
            persistChanges(dao, emptyData(clock()), freshSeed(clock()))
        }
        return Outcome.Success("Демо-данные восстановлены")
    }

    companion object {
        @Volatile
        private var _instance: HotelRepository? = null

        // Один общий экземпляр на всё приложение. Создаётся в HotelApp.onCreate.
        val instance: HotelRepository
            get() = checkNotNull(_instance) { "HotelRepository не создан: проверьте HotelApp в манифесте" }

        fun init(context: Context, clock: () -> LocalDate = { LocalDate.now() }) {
            if (_instance != null) return
            val db = HotelDatabase.create(context)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            // Один раз при запуске: если база пустая, заполняем её, и читаем текущие данные.
            // Таблицы маленькие, поэтому ждать приходится доли секунды.
            val initial = runBlocking(Dispatchers.IO) {
                val dao = db.dao()
                db.withTransaction {
                    if (dao.roomCount() == 0) {
                        persistChanges(dao, emptyData(clock()), freshSeed(clock()))
                    }
                }
                loadAll(dao, clock())
            }
            _instance = HotelRepository(db, scope, clock, initial)
        }
    }
}

// ---------- Вспомогательные функции работы с базой ----------

private fun emptyData(today: LocalDate) = HotelData(
    today = today, rooms = emptyList(), reservations = emptyList(), requests = emptyList(),
    contacts = SeedData.contacts, shifts = emptyList(),
    nextReservationId = 100_001, nextEntryId = 1, nextRequestId = 1, nextShiftId = 1
)

// Стартовые данные с уже назначенными номерами для скорых заездов
private fun freshSeed(today: LocalDate): HotelData =
    SeedData.create(today).withAutoAssignedRooms().first

private suspend fun loadAll(dao: HotelDao, today: LocalDate): HotelData =
    assemble(dao.getRooms(), dao.getReservations(), dao.getRequests(), dao.getShifts(), today)

private suspend fun clearAll(dao: HotelDao) {
    dao.clearReservations() // счета удаляются вместе с бронями (CASCADE)
    dao.clearRooms()
    dao.clearRequests()
    dao.clearShifts()
}

private suspend fun persistChanges(dao: HotelDao, old: HotelData, new: HotelData) {
    // Номера
    val rooms = diff(old.rooms, new.rooms) { it.number }
    if (rooms.upserts.isNotEmpty()) dao.upsertRooms(rooms.upserts.map { it.toEntity() })

    // Брони: сначала сами брони, потом их счета (счёт ссылается на бронь)
    val reservations = diff(old.reservations, new.reservations) { it.id }
    if (reservations.deletedIds.isNotEmpty()) dao.deleteReservations(reservations.deletedIds)
    if (reservations.upserts.isNotEmpty()) dao.upsertReservations(reservations.upserts.map { it.toEntity() })

    val oldFolio = old.reservations.flatMap { res -> res.folio.map { res.id to it } }
    val newFolio = new.reservations.flatMap { res -> res.folio.map { res.id to it } }
    val folio = diff(oldFolio, newFolio) { it.second.id }
    val removedWithReservation = reservations.deletedIds.toSet()
    val folioToDelete = folio.deletedIds.filter { id ->
        oldFolio.firstOrNull { it.second.id == id }?.first !in removedWithReservation
    }
    if (folioToDelete.isNotEmpty()) dao.deleteFolio(folioToDelete)
    if (folio.upserts.isNotEmpty()) dao.upsertFolio(folio.upserts.map { (resId, entry) -> entry.toEntity(resId) })

    // Запросы
    val requests = diff(old.requests, new.requests) { it.id }
    if (requests.deletedIds.isNotEmpty()) dao.deleteRequests(requests.deletedIds)
    if (requests.upserts.isNotEmpty()) dao.upsertRequests(requests.upserts.map { it.toEntity() })

    // Смены
    val shifts = diff(old.shifts, new.shifts) { it.id }
    if (shifts.deletedIds.isNotEmpty()) dao.deleteShifts(shifts.deletedIds)
    if (shifts.upserts.isNotEmpty()) dao.upsertShifts(shifts.upserts.map { it.toEntity() })
}
