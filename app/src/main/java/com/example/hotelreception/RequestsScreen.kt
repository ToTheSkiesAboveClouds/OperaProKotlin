package com.example.hotelreception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// =====================================================================
// ЭКРАН "ЗАПРОСЫ" (аналог приложения Sara): запросы в Housekeeping, Room Service,
// Maintenance и IT. Мои / Все, Открытые / Закрытые, поиск по комнате, фильтр по отделу.
// =====================================================================

@Composable
fun RequestsScreen(
    viewModel: RequestsViewModel,
    snackbarHostState: SnackbarHostState,
    onNewRequest: (Department) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    val filters = state.filters
    // Меню выбора отдела: пока открыто, пока не нажали. Поворот его закрывает — это нормально, поэтому remember
    var menuOpen by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Два раздела: слева «Мои / Все», справа «Активные / Закрытые»
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SegmentedChoice(
                        options = OwnerFilter.entries.toList(),
                        selected = filters.owner,
                        label = { it.label },
                        onSelect = { viewModel.selectOwner(it) },
                        modifier = Modifier.weight(1f)
                    )
                    SegmentedChoice(
                        options = listOf(true, false),
                        selected = filters.openOnly,
                        label = { if (it) "Активные" else "Закрытые" },
                        onSelect = { viewModel.selectOpen(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
                SearchField(
                    initialQuery = filters.query,
                    label = "Номер комнаты или цель",
                    onQueryChange = { viewModel.onQueryChange(it) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropdownFilter(
                        label = "Отдел",
                        options = listOf<Department?>(null) + Department.entries,
                        selected = filters.department,
                        optionLabel = { it?.title ?: "все" },
                        onSelect = { viewModel.selectDepartment(it) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Найдено: ${state.items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.items.isEmpty()) {
                    item { EmptyState("Запросов нет") }
                }
                items(items = state.items, key = { it.id }) { request ->
                    RequestCard(request = request, onClick = { selectedId = request.id })
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(onClick = { menuOpen = true }) {
                Icon(HotelIcons.Bell, contentDescription = tr("Новый запрос"))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Department.entries.forEach { department ->
                    DropdownMenuItem(
                        text = { Text(department.title) },
                        onClick = {
                            menuOpen = false
                            onNewRequest(department)
                        }
                    )
                }
            }
        }
    }

    val selected = state.items.firstOrNull { it.id == selectedId }
    if (selected != null) {
        RequestDialog(
            request = selected,
            onAdvance = { status -> viewModel.advance(selected.id, status) },
            onDismiss = { selectedId = null }
        )
    }
}

@Composable
private fun RequestDialog(
    request: ServiceRequest,
    onAdvance: (RequestStatus) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoRow("Отдел", request.department.title)
                InfoRow("Место", request.location)
                InfoRow("Статус", request.status.label)
                InfoRow("Приоритет", if (request.priority) "Высокий" else "Обычный")
                InfoRow("Создал", request.createdBy)
                InfoRow("Когда", request.createdAt.ago())
                if (request.attachments.isNotEmpty()) {
                    InfoRow("Вложения", "${request.attachments.size}")
                }
                if (request.description.isNotBlank()) {
                    Text(
                        text = request.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            // Отдел отвечает на запрос: New → Accepted → Done
            when (request.status) {
                RequestStatus.NEW -> TextButton(onClick = {
                    onAdvance(RequestStatus.ACCEPTED)
                    onDismiss()
                }) { Text("Принять в работу") }

                RequestStatus.ACCEPTED -> TextButton(onClick = {
                    onAdvance(RequestStatus.DONE)
                    onDismiss()
                }) { Text("Отметить выполненным") }

                RequestStatus.DONE -> TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        },
        dismissButton = {
            if (request.status != RequestStatus.DONE) {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}
