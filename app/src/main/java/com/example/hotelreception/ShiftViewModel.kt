package com.example.hotelreception

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// =====================================================================
// ViewModel кассовой смены. Состояние экрана строится из repo.data (а он идёт из Room),
// поэтому оплата в счёте гостя сразу попадает в итоги смены без ручного обновления.
// =====================================================================
data class ShiftUiState(
    val current: Shift?,                              // открытая смена или null
    val history: List<Shift>,                         // закрытые смены, новые сверху
    val operations: Map<Int, List<ShiftOperation>>,   // операции каждой смены
    val stats: ShiftHistoryStats
)

private fun HotelData.toShiftState(): ShiftUiState {
    val live = shifts.map { withLiveTotals(it) }
    return ShiftUiState(
        current = live.firstOrNull { it.status == ShiftStatus.OPEN },
        history = live.filter { it.status == ShiftStatus.CLOSED }.sortedByDescending { it.id },
        operations = shifts.associate { it.id to shiftOperations(it.id) },
        stats = historyStats(live)
    )
}

class ShiftViewModel(
    private val repo: HotelRepository = HotelRepository.instance
) : HotelViewModel() {

    val state: StateFlow<ShiftUiState> = repo.data
        .map { it.toShiftState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.data.value.toShiftState()
        )

    fun openShift(openingCash: Int, onDone: (Outcome) -> Unit = {}) =
        launchReport(onDone) { repo.openShift(openingCash) }

    fun closeShift(countedCash: Int?, note: String, onDone: (Outcome) -> Unit = {}) =
        launchReport(onDone) { repo.closeShift(countedCash, note) }

    fun updateNote(id: Int, note: String, onDone: (Outcome) -> Unit = {}) =
        launchReport(onDone) { repo.updateShiftNote(id, note) }

    fun deleteShift(id: Int, onDone: (Outcome) -> Unit = {}) =
        launchReport(onDone) { repo.deleteShift(id) }

    fun resetDemoData() = launchReport { repo.resetDemoData() }
}
