package com.example.hotelreception

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class ReservationsUiState(
    val filters: ReservationFilters,
    val items: List<Reservation>,      // уже отфильтрованный список для текущей вкладки
    val all: List<Reservation>,        // все брони (для экранов карточки и биллинга)
    val rooms: List<Room>,             // номера (чтобы показать готовность номера при заселении)
    val today: LocalDate,
    val shiftOpen: Boolean      // есть ли открытая кассовая смена (без неё оплаты не принимаются)
)

private fun buildState(data: HotelData, filters: ReservationFilters) = ReservationsUiState(
    filters = filters,
    items = data.filterReservations(filters),
    all = data.reservations,
    rooms = data.rooms,
    today = data.today,
    shiftOpen = data.openShift() != null
)

// Отвечает за "Ресепшн": Arrivals, In-house, все брони, карточка брони, биллинг, новая бронь.
class ReservationsViewModel(
    private val repo: HotelRepository = HotelRepository.instance
) : HotelViewModel() {

    // Фильтры — часть состояния: переживают поворот экрана
    private val filters = MutableStateFlow(ReservationFilters())

    val state: StateFlow<ReservationsUiState> = combine(repo.data, filters) { data, f ->
        buildState(data, f)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildState(repo.data.value, filters.value)
    )

    // ---------- События интерфейса: фильтры ----------
    fun selectTab(tab: ResTab) = filters.update { it.copy(tab = tab) }
    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }
    fun selectArrivalRange(range: ArrivalRange) = filters.update { it.copy(arrivalRange = range) }
    fun selectInHouseMode(mode: InHouseMode) = filters.update { it.copy(inHouseMode = mode) }
    // Сбрасывает только фильтры из всплывающего окна (вкладка и поиск остаются)
    fun resetFilters() = filters.update {
        it.copy(arrivalRange = ArrivalRange.TODAY, inHouseMode = InHouseMode.IN, status = null, dateFrom = null, dateTo = null)
    }

    // Быстрые переходы с главной: карточки открывают нужную вкладку с нужным фильтром
    fun showArrivals() = filters.update { it.copy(tab = ResTab.ARRIVALS, arrivalRange = ArrivalRange.TODAY) }
    fun showInHouse() = filters.update { it.copy(tab = ResTab.IN_HOUSE, inHouseMode = InHouseMode.IN) }
    fun showDeparted() = filters.update { it.copy(tab = ResTab.IN_HOUSE, inHouseMode = InHouseMode.OUT) }
    fun selectStatus(status: ResStatus?) = filters.update { it.copy(status = status) }
    fun selectDateFrom(date: LocalDate?) = filters.update { it.copy(dateFrom = date) }
    fun selectDateTo(date: LocalDate?) = filters.update { it.copy(dateTo = date) }

    // ---------- Действия над бронями (каждое выполняется в корутине через репозиторий) ----------
    fun autoAssign() = launchReport { repo.autoAssignRooms() }
    fun assignRoom(resId: Int, roomNumber: Int) = launchReport { repo.assignRoom(resId, roomNumber) }
    fun attachPassport(resId: Int, path: String) = launchReport { repo.attachPassport(resId, path) }
    fun checkIn(resId: Int) = launchReport { repo.checkIn(resId) }
    fun checkOut(resId: Int) = launchReport { repo.checkOut(resId) }
    fun addCharge(resId: Int, title: String, amount: Int) = launchReport { repo.addCharge(resId, title, amount) }
    fun addPayment(resId: Int, amount: Int, method: PaymentMethod?) =
        launchReport { repo.addPayment(resId, amount, method) }
    fun settleBalance(resId: Int) = launchReport { repo.settleBalance(resId) }

    // Создание, правка и удаление брони. onDone вызывается, когда база ответила (для навигации).
    fun createReservation(draft: ReservationDraft, onDone: (Outcome) -> Unit) =
        launchReport(onDone) { repo.createReservation(draft) }
    fun updateReservation(resId: Int, draft: ReservationDraft, onDone: (Outcome) -> Unit) =
        launchReport(onDone) { repo.updateReservation(resId, draft) }
    fun deleteReservation(resId: Int, onDone: (Outcome) -> Unit) =
        launchReport(onDone) { repo.deleteReservation(resId) }

    // Номера, которые можно назначить этой брони (тот же класс, свободны на даты)
    fun freeRoomsFor(resId: Int): List<Room> {
        val data = repo.data.value
        val res = data.reservationById(resId) ?: return emptyList()
        return data.candidateRooms(res).sortedBy { it.number }
    }
}
