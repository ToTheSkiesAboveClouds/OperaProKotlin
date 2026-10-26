package com.example.hotelreception

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ContactsUiState(
    val query: String,
    val groups: List<ContactGroup>
)

private fun buildState(data: HotelData, query: String): ContactsUiState {
    val q = query.trim()
    val groups = if (q.isEmpty()) {
        data.contacts
    } else {
        data.contacts.mapNotNull { group ->
            if (group.name.contains(q, ignoreCase = true)) {
                group
            } else {
                val found = group.entries.filter {
                    it.label.contains(q, ignoreCase = true) || it.ext.contains(q)
                }
                if (found.isEmpty()) null else group.copy(entries = found)
            }
        }
    }
    return ContactsUiState(query, groups)
}

// Только список внутренних телефонов (справочник для сотрудников), без логики.
// Данные берутся из репозитория, поэтому при изменении "базы" список обновится сам.
class ContactsViewModel(
    repo: HotelRepository = HotelRepository.instance
) : HotelViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<ContactsUiState> = combine(repo.data, query) { data, q ->
        buildState(data, q)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildState(repo.data.value, query.value)
    )

    fun onQueryChange(value: String) {
        query.value = value
    }
}
