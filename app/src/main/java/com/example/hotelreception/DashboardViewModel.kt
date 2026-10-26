package com.example.hotelreception

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

// Всё, что нужно экрану "Главная": статистика дня и таблица доступности на 7 дней
data class DashboardUiState(
    val today: LocalDate,
    val stats: DayStats,
    val days: List<LocalDate>,
    val availability: List<AvailabilityRow>
)

private fun HotelData.toDashboardState(): DashboardUiState {
    val (days, rows) = availability(7)
    return DashboardUiState(today, dayStats(), days, rows)
}

class DashboardViewModel(
    repo: HotelRepository = HotelRepository.instance
) : HotelViewModel() {

    // Данные репозитория превращаются в состояние экрана при каждом изменении
    val state: StateFlow<DashboardUiState> = repo.data
        .map { it.toDashboardState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.data.value.toDashboardState()
        )
}
