package com.example.hotelreception

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomFilters(
    val occupancy: Set<Occupancy> = emptySet(), // пусто = показывать все
    val hk: HkStatus? = null,
    val floor: Int? = null // null = все этажи
)

// Номер для списка: сам номер + вычисленная занятость + кто в нём живёт/заезжает
data class RoomItem(val room: Room, val occupancy: Occupancy, val guest: String?)

data class RoomsUiState(
    val items: List<RoomItem>,
    val filters: RoomFilters,
    val hkCounts: Map<HkStatus, Int>,
    val occupancyCounts: Map<Occupancy, Int>,
    val floors: List<Int>,
    val isCleaning: Boolean
)

private fun buildState(data: HotelData, filters: RoomFilters, cleaning: Boolean): RoomsUiState {
    val all = data.rooms.map { room ->
        RoomItem(
            room = room,
            occupancy = data.occupancyOf(room.number),
            guest = data.currentReservationIn(room.number)?.guest?.lastName
        )
    }
    val visible = all
        .filter { filters.occupancy.isEmpty() || it.occupancy in filters.occupancy }
        .filter { filters.hk == null || it.room.hk == filters.hk }
        .filter { filters.floor == null || it.room.floor == filters.floor }
    return RoomsUiState(
        items = visible,
        filters = filters,
        hkCounts = HkStatus.entries.associateWith { s -> all.count { it.room.hk == s } },
        occupancyCounts = Occupancy.entries.associateWith { o -> all.count { it.occupancy == o } },
        floors = all.map { it.room.floor }.distinct().sorted(),
        isCleaning = cleaning
    )
}

class RoomsViewModel(
    private val repo: HotelRepository = HotelRepository.instance
) : HotelViewModel() {

    private val filters = MutableStateFlow(RoomFilters())
    private val cleaning = MutableStateFlow(false)

    val state: StateFlow<RoomsUiState> = combine(repo.data, filters, cleaning) { data, f, c ->
        buildState(data, f, c)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildState(repo.data.value, filters.value, false)
    )

    fun toggleOccupancy(occupancy: Occupancy) = filters.update {
        val next = if (occupancy in it.occupancy) it.occupancy - occupancy else it.occupancy + occupancy
        it.copy(occupancy = next)
    }

    // Выбор статуса уборки в выпадающем списке (null = все статусы)
    fun selectHkFilter(status: HkStatus?) = filters.update { it.copy(hk = status) }

    // Выбор этажа в выпадающем списке (null = все этажи)
    fun selectFloor(floor: Int?) = filters.update { it.copy(floor = floor) }

    fun setHk(roomNumber: Int, status: HkStatus) = report(repo.setHk(roomNumber, status))

    // Корутины из HW1: горничные убирают все Dirty номера одновременно (по 600 мс на номер)
    fun cleanAllDirty() {
        val dirty = repo.data.value.rooms.filter { it.hk == HkStatus.DIRTY }.map { it.number }
        if (dirty.isEmpty()) {
            report(Outcome.Failure("Нет номеров в статусе Dirty"))
            return
        }
        if (cleaning.value) return
        viewModelScope.launch {
            cleaning.value = true
            dirty.map { number ->
                async {
                    delay(600)
                    repo.setHk(number, HkStatus.CLEAN)
                }
            }.awaitAll()
            cleaning.value = false
            report(Outcome.Success("Убрано номеров: ${dirty.size} (статус Clean)"))
        }
    }
}
