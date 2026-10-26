package com.example.hotelreception

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =====================================================================
// ЭКРАН "НОВЫЙ ЗАПРОС" в отдел: комната, цель, описание, срочность, фото и видео.
// =====================================================================

@Composable
fun NewRequestScreen(
    department: Department,
    viewModel: RequestsViewModel,
    snackbarHostState: SnackbarHostState,
    onSent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var location by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(false) }
    // Список путей к прикреплённым файлам; listSaver нужен, чтобы список пережил поворот экрана
    val attachments: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }

    // Галерея: можно выбрать несколько фото и видео сразу
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val paths = withContext(Dispatchers.IO) {
                    uris.mapNotNull { copyUriToApp(context, it, "request") }
                }
                attachments.addAll(paths)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = department.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Место: номер комнаты") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Цель запроса", style = MaterialTheme.typography.labelMedium)
        ChoiceChips(
            options = department.presets,
            isSelected = { it == title },
            label = { it },
            onClick = { title = it }
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Цель (или свой вариант)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Описание") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        LabeledSwitch(label = "Priority (срочно)", checked = priority, onCheckedChange = { priority = it })

        OutlinedButton(
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Прикрепить фото или видео")
        }
        if (attachments.isNotEmpty()) {
            Text(
                text = "Прикреплено: ${attachments.size}. Нажмите на файл, чтобы убрать.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChoiceChips(
                options = attachments.toList(),
                isSelected = { true },
                label = { path ->
                    val number = attachments.indexOf(path) + 1
                    (if (isVideoPath(path)) "Видео " else "Фото ") + number
                },
                onClick = { path -> attachments.remove(path) }
            )
        }

        Button(
            onClick = {
                val outcome = viewModel.createRequest(
                    department = department,
                    location = location,
                    title = title,
                    description = description,
                    priority = priority,
                    attachments = attachments.map { Attachment(it, isVideoPath(it)) }
                )
                if (outcome is Outcome.Success) onSent()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Отправить", fontWeight = FontWeight.SemiBold)
        }
    }
}
