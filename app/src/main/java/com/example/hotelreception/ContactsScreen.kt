package com.example.hotelreception

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// =====================================================================
// ЭКРАН "КОНТАКТЫ": справочник внутренних телефонов отделов.
// Все отделы свёрнуты в короткие строки: видно весь список сразу, а нужный отдел
// раскрывается нажатием. Когда вводишь поиск, подходящие отделы раскрываются сами.
// Номера берутся из "базы" (репозитория): если номер там изменится, список обновится.
// =====================================================================

// Какие отделы раскрыты. Хранится одной строкой, чтобы пережить поворот экрана
private const val SEPARATOR = "|"

@Composable
fun ContactsScreen(viewModel: ContactsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expandedNames by rememberSaveable { mutableStateOf("") }
    val expanded = expandedNames.split(SEPARATOR).filter { it.isNotEmpty() }.toSet()
    val searching = state.query.isNotBlank()

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            initialQuery = state.query,
            label = "Отдел, имя или номер",
            onQueryChange = { viewModel.onQueryChange(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.groups.isEmpty()) {
                item { EmptyState("Ничего не найдено") }
            }
            items(items = state.groups, key = { it.name }) { group ->
                val isOpen = searching || group.name in expanded
                ContactGroupCard(
                    group = group,
                    isOpen = isOpen,
                    onToggle = {
                        val next = if (group.name in expanded) expanded - group.name else expanded + group.name
                        expandedNames = next.joinToString(SEPARATOR)
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactGroupCard(group: ContactGroup, isOpen: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Заголовок: название отдела, сколько номеров внутри и стрелка
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${group.entries.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isOpen) "  ▴" else "  ▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isOpen) {
                group.entries.forEach { entry ->
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = HotelIcons.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = entry.label,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                        Text(
                            text = entry.ext,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
