package com.example.hotelreception

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Всё состояние экрана в одном неизменяемом объекте
data class HotelUiState(
    val rooms: List<Room> = emptyList(),
    val bookings: Map<Int, Booking> = emptyMap(), // номер комнаты -> бронь
    val query: String = "",
    val onlyFree: Boolean = false,
    val selectedRoom: Int? = null,
    val receipt: Booking? = null,                 // последний счёт после выселения
    val revenue: Int = 0,
    val isCleaning: Boolean = false,
    val vmCounter: Int = 0                        // для демо поворота экрана
) {
    // Фильтрация из HW1 (filter), теперь с поиском
    val visibleRooms: List<Room>
        get() = rooms.filter { room ->
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                room.number.toString().contains(q) ||
                room.type.label.contains(q, ignoreCase = true)
            val matchesFree = !onlyFree || room.number !in bookings
            matchesQuery && matchesFree
        }

    val dirtyCount: Int get() = rooms.count { it.isDirty }
    val occupiedCount: Int get() = bookings.size
}

class HotelViewModel : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<HotelUiState> = _state.asStateFlow()

    // ---------- UI-события ----------
    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onOnlyFreeChange(value: Boolean) = _state.update { it.copy(onlyFree = value) }

    fun selectRoom(number: Int?) = _state.update { it.copy(selectedRoom = number) }

    fun dismissReceipt() = _state.update { it.copy(receipt = null) }

    fun incrementCounter() = _state.update { it.copy(vmCounter = it.vmCounter + 1) }

    // ---------- Логика ресепшна (из HW1) ----------
    fun checkIn(roomNumber: Int, name: String, nights: Int, isVip: Boolean) {
        _state.update { s ->
            val room = s.rooms.firstOrNull { it.number == roomNumber }
            if (room == null || room.isDirty || roomNumber in s.bookings ||
                name.isBlank() || nights < 1
            ) {
                return@update s
            }
            val booking = Booking(room, Guest(name.trim(), isVip), nights)
            s.copy(bookings = s.bookings + (roomNumber to booking), selectedRoom = null)
        }
    }

    fun addService(roomNumber: Int, order: ServiceOrder) {
        _state.update { s ->
            val booking = s.bookings[roomNumber] ?: return@update s
            val updated = booking.copy(orders = booking.orders + order)
            s.copy(bookings = s.bookings + (roomNumber to updated))
        }
    }

    fun checkOut(roomNumber: Int) {
        _state.update { s ->
            val booking = s.bookings[roomNumber] ?: return@update s
            s.copy(
                bookings = s.bookings - roomNumber,
                rooms = s.rooms.map { if (it.number == roomNumber) it.copy(isDirty = true) else it },
                revenue = s.revenue + booking.total,
                receipt = booking,
                selectedRoom = null
            )
        }
    }

    // Корутины из HW1: горничные убирают все номера параллельно
    fun cleanRooms() {
        val dirty = _state.value.rooms.filter { it.isDirty }.map { it.number }
        if (dirty.isEmpty() || _state.value.isCleaning) return

        viewModelScope.launch {
            _state.update { it.copy(isCleaning = true) }
            dirty.map { number -> async { delay(700); number } }.awaitAll()
            _state.update { s ->
                s.copy(
                    rooms = s.rooms.map { if (it.number in dirty) it.copy(isDirty = false) else it },
                    isCleaning = false
                )
            }
        }
    }

    // ---------- Начальные данные ----------
    private fun initialState(): HotelUiState {
        val rooms = (1..5).flatMap { floor ->
            (1..4).map { i ->
                val type = when (i) {
                    3 -> RoomType.DELUXE
                    4 -> RoomType.SUITE
                    else -> RoomType.STANDARD
                }
                Room(number = floor * 100 + i, floor = floor, type = type)
            }
        }.map { if (it.number == 304) it.copy(isDirty = true) else it }

        val seeded = listOf(
            Booking(rooms.first { it.number == 101 }, Guest("Айгерим", isVip = true), 3),
            Booking(rooms.first { it.number == 203 }, Guest("Дмитрий", isVip = false), 2)
        ).associateBy { it.room.number }

        return HotelUiState(rooms = rooms, bookings = seeded)
    }
}
