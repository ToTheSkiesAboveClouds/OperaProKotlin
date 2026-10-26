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
    val today: LocalDate
)

private fun buildState(data: HotelData, filters: ReservationFilters) = ReservationsUiState(
    filters = filters,
    items = data.filterReservations(filters),
    all = data.reservations,
    rooms = data.rooms,
    today = data.today
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

    // ---------- Действия над бронями ----------
    fun autoAssign() = report(repo.autoAssignRooms())
    fun assignRoom(resId: Int, roomNumber: Int) = report(repo.assignRoom(resId, roomNumber))
    fun attachPassport(resId: Int, path: String) = report(repo.attachPassport(resId, path))
    fun checkIn(resId: Int) = report(repo.checkIn(resId))
    fun checkOut(resId: Int) = report(repo.checkOut(resId))
    fun addCharge(resId: Int, title: String, amount: Int) = report(repo.addCharge(resId, title, amount))
    fun addPayment(resId: Int, amount: Int) = report(repo.addPayment(resId, amount))
    fun settleBalance(resId: Int) = report(repo.settleBalance(resId))
    fun createReservation(draft: ReservationDraft) = report(repo.createReservation(draft))

    // Номера, которые можно назначить этой брони (тот же класс, свободны на даты)
    fun freeRoomsFor(resId: Int): List<Room> {
        val data = repo.data.value
        val res = data.reservationById(resId) ?: return emptyList()
        return data.candidateRooms(res).sortedBy { it.number }
    }
}
