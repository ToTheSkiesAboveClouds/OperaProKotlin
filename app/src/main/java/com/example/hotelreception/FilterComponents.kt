@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.hotelreception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

// =====================================================================
// КОМПОНЕНТЫ ДЛЯ ФИЛЬТРОВ. Нужны, чтобы на экранах не было десятков кнопок:
//  - DropdownFilter: выпадающий список (один выбор из многих)
//  - FilterButton + FilterDialog: значок фильтра, который открывает окно с фильтрами
//  - SegmentedChoice: две крупные кнопки-переключателя (Мои / Все)
// Состояние выбора у них нет: значение приходит параметром, событие уходит лямбдой.
// =====================================================================

// Выпадающий список. Открыт он или закрыт — мелкое состояние только этого компонента, поэтому remember
@Composable
fun <T> DropdownFilter(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$label: ${optionLabel(selected)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(text = " ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

// Значок фильтра; цифра в углу показывает, сколько фильтров сейчас включено
@Composable
fun FilterButton(activeCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        BadgedBox(
            badge = {
                if (activeCount > 0) {
                    Badge { Text("$activeCount") }
                }
            }
        ) {
            Icon(HotelIcons.Filter, contentDescription = tr("Фильтры"))
        }
    }
}

// Всплывающее окно с фильтрами. Сами фильтры передаются в content
@Composable
fun FilterDialog(
    title: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // Окно почти на всю ширину экрана, чтобы кнопки помещались целиком
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onReset) { Text("Сбросить") } }
    )
}

// Выбор языка приложения (RU / EN). Стоит на главной, чтобы не загромождать верхнюю панель
@Composable
fun LanguageMenu(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { open = true }) {
            Text(text = "${AppLanguage.current.code.uppercase()} ▾", fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.title) },
                    onClick = {
                        open = false
                        AppLanguage.select(context, language)
                    }
                )
            }
        }
    }
}

// Крупный переключатель из нескольких кнопок в одной плашке
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = { Text(text = label(option), maxLines = 1) }
            )
        }
    }
}
