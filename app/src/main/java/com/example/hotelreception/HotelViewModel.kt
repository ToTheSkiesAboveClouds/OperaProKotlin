package com.example.hotelreception

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =====================================================================
// ОБЩАЯ ОСНОВА ДЛЯ ВСЕХ ViewModel'ей приложения.
// Умеет одно: хранить короткое сообщение для пользователя (Snackbar) —
// результат последнего действия ("Гость заселён", "Баланс не 0" и т.п.).
// Сообщение лежит в StateFlow, поэтому не пропадает при повороте экрана.
// =====================================================================
abstract class HotelViewModel : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    // Для сообщений, которые рождаются в экране (например, "камера недоступна")
    fun showMessage(text: String) {
        _message.value = text
    }

    // Показывает результат операции и возвращает его, чтобы экран мог на него отреагировать
    protected fun report(outcome: Outcome): Outcome {
        _message.value = when (outcome) {
            is Outcome.Success -> outcome.message
            is Outcome.Failure -> outcome.reason
        }
        return outcome
    }

    // Запускает операцию с базой данных в корутине (на главном потоке БД не трогаем),
    // показывает результат в Snackbar и сообщает экрану о результате через onDone.
    protected fun launchReport(onDone: (Outcome) -> Unit = {}, block: suspend () -> Outcome) {
        viewModelScope.launch {
            val outcome = report(block())
            onDone(outcome)
        }
    }
}
