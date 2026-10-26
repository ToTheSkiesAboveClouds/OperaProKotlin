package com.example.hotelreception

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class OwnerFilter(val label: String) {
    MINE("Мои"),
    ALL("Все")
}

data class RequestFilters(
    val owner: OwnerFilter = OwnerFilter.MINE,
    val openOnly: Boolean = true,
    val department: Department? = null,
    val query: String = ""
)

data class RequestsUiState(
    val filters: RequestFilters,
    val items: List<ServiceRequest>
)

private fun buildState(data: HotelData, f: RequestFilters): RequestsUiState {
    val q = f.query.trim()
    val items = data.requests
        .filter { f.owner == OwnerFilter.ALL || it.createdBy == CURRENT_USER }
        .filter { it.status.isOpen == f.openOnly }
        .filter { f.department == null || it.department == f.department }
        .filter {
            q.isEmpty() || it.location.contains(q, ignoreCase = true) || it.title.contains(q, ignoreCase = true)
        }
        // сначала срочные, потом новые
        .sortedWith(compareByDescending<ServiceRequest> { it.priority }.thenByDescending { it.createdAt })
    return RequestsUiState(f, items)
}

class RequestsViewModel(
    private val repo: HotelRepository = HotelRepository.instance
) : HotelViewModel() {

    private val filters = MutableStateFlow(RequestFilters())

    val state: StateFlow<RequestsUiState> = combine(repo.data, filters) { data, f ->
        buildState(data, f)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildState(repo.data.value, filters.value)
    )

    fun selectOwner(owner: OwnerFilter) = filters.update { it.copy(owner = owner) }
    fun selectOpen(openOnly: Boolean) = filters.update { it.copy(openOnly = openOnly) }
    fun selectDepartment(department: Department?) = filters.update { it.copy(department = department) }
    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }

    fun createRequest(
        department: Department,
        location: String,
        title: String,
        description: String,
        priority: Boolean,
        attachments: List<Attachment>
    ) = report(repo.createRequest(department, location, title, description, priority, attachments))

    fun advance(id: Int, status: RequestStatus) = report(repo.advanceRequest(id, status))
}
