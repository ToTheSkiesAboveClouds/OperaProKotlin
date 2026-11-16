package com.example.hotelreception

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// =====================================================================
// ЯЗЫК ПРИЛОЖЕНИЯ (русский / английский).
//
// Как это работает, простыми словами:
//  1. Выбранный язык лежит в AppLanguage.current. Это состояние Compose (mutableStateOf),
//     поэтому при смене языка все экраны перерисовываются сами.
//  2. Выбор сохраняется в SharedPreferences и вспоминается при запуске.
//  3. Весь текст на экране выводит наша функция Text() из этого файла. Она не рисует текст сама,
//     а сначала пропускает его через tr(): "перевести на текущий язык", и только потом
//     передаёт настоящему Text из Material 3.
//  4. tr() ищет перевод в словаре: точная фраза, шаблон с подстановкой ("Номер {} не найден")
//     или перевод по частям ("Этаж: все" -> "Floor: all").
//  5. Данные (брони, имена гостей, запросы) хранятся как есть, переводится только то,
//     что показывается пользователю.
// =====================================================================

enum class Language(val code: String, val title: String) {
    RU("ru", "Русский"),
    EN("en", "English")
}

object AppLanguage {
    // Состояние Compose: чтение внутри @Composable подписывает экран на изменение языка
    var current: Language by mutableStateOf(Language.RU)
        private set

    private const val PREFS = "hotel_prefs"
    private const val KEY = "language"

    val locale: Locale get() = Locale.forLanguageTag(current.code)

    // Вызывается один раз при запуске (MainActivity): вспоминаем сохранённый язык
    fun load(context: Context) {
        val code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current = Language.entries.firstOrNull { it.code == code } ?: Language.RU
    }

    // Вызывается из переключателя языка: меняем состояние и запоминаем выбор
    fun select(context: Context, language: Language) {
        current = language
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.code)
            .apply()
    }
}

// Наш Text: переводит строку и передаёт её настоящему Text из Material 3
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = tr(text),
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        minLines = minLines,
        style = style
    )
}

// Перевести строку на текущий язык (если перевода нет, вернётся как есть)
fun tr(text: String): String =
    if (AppLanguage.current == Language.EN) toEnglish.translate(text) else toRussian.translate(text)

// ---------------------------------------------------------------------
// Движок перевода
// ---------------------------------------------------------------------

// Шаблон: "Номер {} не найден" -> "Room {1} not found". Вместо {} подставляется что угодно,
// найденный кусок тоже переводится (например, "Dirty" или "3 ночи").
private class Template(source: String, private val target: String) {
    private val parts = source.split("{}")
    val staticLength = parts.sumOf { it.length }
    private val regex = Regex(
        parts.joinToString("(.+?)") { Regex.escape(it) },
        RegexOption.DOT_MATCHES_ALL
    )

    fun tryApply(text: String, translate: (String) -> String): String? {
        val match = regex.matchEntire(text) ?: return null
        var result = target
        match.groupValues.drop(1).forEachIndexed { index, group ->
            result = result.replace("{${index + 1}}", translate(group))
        }
        return result
    }
}

private val separator = Regex(" · |: |, | × | → ")

private class Dictionary(pairs: List<Pair<String, String>>, words: Map<String, String>) {
    private val exact: Map<String, String>
    private val templates: List<Template>
    private val words: Map<String, String> = words
    private val wordRegex: Regex?
    private val cache = ConcurrentHashMap<String, String>()

    init {
        val (templatePairs, exactPairs) = pairs.partition { it.first.contains("{}") }
        exact = exactPairs.toMap()
        // Сначала проверяем самые "конкретные" шаблоны (с самым длинным постоянным текстом)
        templates = templatePairs.map { Template(it.first, it.second) }.sortedByDescending { it.staticLength }
        wordRegex = if (words.isEmpty()) {
            null
        } else {
            val keys = words.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            Regex("(?<![\\p{L}\\p{N}])($keys)(?![\\p{L}\\p{N}])")
        }
    }

    fun translate(text: String): String {
        if (text.none { it.isLetter() }) return text
        cache[text]?.let { return it }
        val result = compute(text)
        if (cache.size > 3000) cache.clear()
        cache[text] = result
        return result
    }

    private fun compute(text: String): String {
        // 1. Точная фраза
        exact[text]?.let { return it }
        // 2. Шаблон с подстановкой
        for (template in templates) {
            template.tryApply(text) { translate(it) }?.let { return it }
        }
        // 3. Строка из нескольких частей: "Этаж: все", "Бронь №5 · DBC"
        splitAndTranslate(text)?.let { return it }
        // 4. Отдельные слова (имена, статусы)
        return wordRegex?.replace(text) { words.getValue(it.value) } ?: text
    }

    private fun splitAndTranslate(text: String): String? {
        val matches = separator.findAll(text).toList()
        if (matches.isEmpty()) return null
        val builder = StringBuilder()
        var last = 0
        var changed = false
        for (match in matches) {
            val piece = text.substring(last, match.range.first)
            val translated = translate(piece)
            if (translated != piece) changed = true
            builder.append(translated).append(match.value)
            last = match.range.last + 1
        }
        val tail = text.substring(last)
        val translatedTail = translate(tail)
        if (translatedTail != tail) changed = true
        builder.append(translatedTail)
        return if (changed) builder.toString() else null
    }
}

// ---------------------------------------------------------------------
// Словарь: русский -> английский
// ---------------------------------------------------------------------

private val toEnglish = Dictionary(
    pairs = listOf(
        // Task 3: смены, правка, удаление
        "Смена не открыта" to "Shift is not open",
        "Смена не открыта: оплаты не принимаются" to "Shift is not open: payments are not accepted",
        "Смена №{}" to "Shift #{1}",
        "Смена №{} открыта" to "Shift #{1} is open",
        "Смена №{} · открыта" to "Shift #{1} · open",
        "Смена №{} не найдена" to "Shift #{1} not found",
        "Смена не найдена" to "Shift not found",
        "Кассовая смена" to "Cash shift",
        "Кассовая смена ›" to "Cash shift ›",
        "Кассовая смена не открыта: оплаты и возвраты не принимаются. Откройте смену на главной." to "Cash shift is not open: payments and refunds are not accepted. Open a shift on the home screen.",
        "Пока смена закрыта, оплаты, возвраты и депозиты принимать нельзя. Откройте смену и укажите остаток наличных в кассе." to "While the shift is closed, payments, refunds and deposits cannot be accepted. Open a shift and enter the cash in the register.",
        "Открыть смену" to "Open shift",
        "Открыть" to "Open",
        "Закрыть смену" to "Close shift",
        "Закрыть смену №{}" to "Close shift #{1}",
        "Операции текущей смены" to "Current shift operations",
        "Пока нет операций. Примите оплату в биллинге брони, и она появится здесь" to "No operations yet. Accept a payment in a booking's billing and it will appear here",
        "Статистика закрытых смен" to "Closed shift statistics",
        "Смен закрыто" to "Shifts closed",
        "Принято за всё время" to "Received in total",
        "Смен с недостачей" to "Shifts with a shortage",
        "Сумма разниц в кассе" to "Total cash difference",
        "История смен" to "Shift history",
        "Закрытых смен пока нет" to "No closed shifts yet",
        "Сбросить демо-данные" to "Reset demo data",
        "Сбросить демо-данные?" to "Reset demo data?",
        "Сбросить" to "Reset",
        "Все брони, запросы, номера и смены будут заменены стартовыми данными. Это нельзя отменить." to "All bookings, requests, rooms and shifts will be replaced with the starting data. This cannot be undone.",
        "Остаток на начало" to "Opening cash",
        "Принято всего" to "Total received",
        "Принято за смену" to "Received this shift",
        "Принято" to "Received",
        "Возвраты" to "Refunds",
        "Операций" to "Operations",
        "Операции ({})" to "Operations ({1})",
        "Ожидается наличных в кассе" to "Expected cash in register",
        "Ожидается наличных" to "Expected cash",
        "Ожидается в кассе" to "Expected in register",
        "Должно быть в кассе" to "Should be in register",
        "Насчитано" to "Counted",
        "Насчитано наличных, ₸" to "Counted cash, ₸",
        "Наличные в кассе, ₸" to "Cash in register, ₸",
        "Разница" to "Difference",
        "Результат" to "Result",
        "Касса сошлась" to "Register balanced",
        "Излишек {}" to "Surplus {1}",
        "Недостача {}" to "Shortage {1}",
        "Причина разницы (обязательно)" to "Reason for the difference (required)",
        "Напишите причину разницы" to "Write the reason for the difference",
        "Введите сумму" to "Enter an amount",
        "Введите сумму (можно 0)" to "Enter an amount (0 is allowed)",
        "Введите сумму больше нуля" to "Enter an amount above zero",
        "Комментарий" to "Comment",
        "Комментарий к смене" to "Shift comment",
        "Комментария нет" to "No comment",
        "Изменить комментарий" to "Edit comment",
        "Комментарий сохранён" to "Comment saved",
        "Кассир" to "Cashier",
        "Открыта" to "Open",
        "Закрыта" to "Closed",
        "ещё открыта" to "still open",
        "Возвраты наличными" to "Cash refunds",
        "Другие возвраты" to "Other refunds",
        "В этой смене нет операций" to "No operations in this shift",
        "Удалить смену" to "Delete shift",
        "Удалить смену?" to "Delete shift?",
        "Смена №{} исчезнет из истории и статистики. Платежи в счетах гостей останутся." to "Shift #{1} will disappear from the history and statistics. Payments in guest folios will remain.",
        "Сохранить" to "Save",
        "Сохранить изменения" to "Save changes",
        "Изменить" to "Edit",
        "Удалить" to "Delete",
        "Отмена" to "Cancel",
        "Удалить бронь?" to "Delete booking?",
        "Бронь №{} ({}) и её счёт будут удалены без возможности восстановления." to "Booking #{1} ({2}) and its folio will be permanently deleted.",
        "Удалить запрос?" to "Delete request?",
        "Запрос «{}» ({}) будет удалён без возможности восстановления." to "Request \"{1}\" ({2}) will be permanently deleted.",
        "Изменить бронь №{}" to "Edit booking #{1}",
        "Изменить запрос №{}" to "Edit request #{1}",
        "Введите имя" to "Enter a first name",
        "Введите фамилию" to "Enter a last name",
        "Минимум 7 цифр" to "At least 7 digits",
        "Почта указана неверно" to "Email is invalid",
        "От 1 до 60 ночей" to "From 1 to 60 nights",
        "Укажите номер комнаты или место" to "Enter a room number or place",
        "Укажите цель запроса" to "Enter the request purpose",
        "Гость проживает: даты, класс и тариф изменить нельзя" to "Guest is in-house: dates, class and rate cannot be changed",
        "Бронь №{} обновлена" to "Booking #{1} updated",
        "Бронь №{} удалена" to "Booking #{1} deleted",
        "Запрос №{} обновлён" to "Request #{1} updated",
        "Запрос №{} удалён" to "Request #{1} deleted",
        "Выселенную бронь редактировать нельзя" to "A checked-out booking cannot be edited",
        "Гость проживает. Сначала выселите его, потом бронь можно удалить" to "The guest is in-house. Check them out first, then the booking can be deleted",
        "Закрытый запрос редактировать нельзя" to "A closed request cannot be edited",
        "Нет открытой смены" to "There is no open shift",
        "Смена уже открыта. Сначала закройте её" to "A shift is already open. Close it first",
        "Смена не открыта. Откройте смену на главной, чтобы принимать оплаты" to "Shift is not open. Open a shift on the home screen to accept payments",
        "Остаток в кассе не может быть отрицательным" to "Cash in register cannot be negative",
        "Слишком большая сумма в кассе" to "The cash amount is too large",
        "Введите, сколько наличных насчитали в кассе" to "Enter how much cash was counted in the register",
        "В кассе разница {}: напишите причину в комментарии" to "The register differs by {1}: write the reason in the comment",
        "Комментарий к открытой смене пишется при закрытии" to "The comment for an open shift is written when closing it",
        "При разнице в кассе комментарий обязателен" to "A comment is required when the register differs",
        "Открытую смену удалить нельзя. Сначала закройте её" to "An open shift cannot be deleted. Close it first",
        "Смена №{} открыта, в кассе {}" to "Shift #{1} opened, {2} in the register",
        "Смена №{} закрыта, касса сошлась" to "Shift #{1} closed, register balanced",
        "Смена №{} закрыта, излишек {}" to "Shift #{1} closed, surplus {2}",
        "Смена №{} закрыта, недостача {}" to "Shift #{1} closed, shortage {2}",
        "Смена №{} удалена из истории" to "Shift #{1} removed from history",
        "Принята оплата {} ({})" to "Payment {1} accepted ({2})",
        "Демо-данные восстановлены" to "Demo data restored",
        "Способ оплаты" to "Payment method",

        // Главная и доступность
        "Главная" to "Home",
        "Брони" to "Bookings",
        "Номера" to "Rooms",
        "Запросы" to "Requests",
        "Контакты" to "Contacts",
        "Контакты отделов" to "Department contacts",
        "Бронь №{}" to "Booking #{1}",
        "Биллинг · бронь №{}" to "Billing · booking #{1}",
        "Новая бронь" to "New booking",
        "Доступность номеров" to "Room availability",
        "Новый запрос" to "New request",
        "Сегодня, {}" to "Today, {1}",
        "Нажмите на карточку, чтобы открыть список" to "Tap a card to open the list",
        "Гостей в отеле" to "Guests in house",
        "Загрузка отеля · доступность номеров ›" to "Occupancy · room availability ›",
        "Состояние номеров" to "Room status",
        "Загрузка отеля: {}%" to "Occupancy: {1}%",
        "Сколько номеров каждого класса свободно в каждый из 7 дней. Красный фон: мест нет, оранжевый: остался один номер." to
            "How many rooms of each class are free on each of the next 7 days. Red background: no rooms left, orange: one room left.",
        "Класс" to "Class",
        "всего {}" to "total {1}",
        // Общие слова и кнопки
        "Очистить" to "Clear",
        "ОК" to "OK",
        "Отмена" to "Cancel",
        "Добавить" to "Add",
        "Принять" to "Accept",
        "Закрыть" to "Close",
        "Отправить" to "Send",
        "Фильтры" to "Filters",
        "Фильтры: {}" to "Filters: {1}",
        "Готово" to "Done",
        "Сбросить" to "Reset",
        "Убрать" to "Remove",
        "Назад" to "Back",
        "Все" to "All",
        "все" to "all",
        "Нет" to "No",
        "Ничего не найдено" to "Nothing found",
        "Загрузка…" to "Loading…",
        "Вы" to "You",
        "Дата" to "Date",
        "Выбрать дату" to "Pick a date",
        "Даты брони" to "Reservation dates",
        "Начало" to "Start",
        "Окончание" to "End",
        "Найдено: {}" to "Found: {1}",
        // Счёт и биллинг
        "Бронь №{} не найдена" to "Booking #{1} not found",
        "+ Услуга" to "+ Service",
        "Принять оплату" to "Accept payment",
        "Закрыть баланс: принять {}" to "Settle balance: receive {1}",
        "Закрыть баланс: вернуть {}" to "Settle balance: refund {1}",
        "Баланс закрыт" to "Balance settled",
        "Счёт гостя" to "Guest folio",
        "Пока нет начислений. Проживание попадёт в счёт при заселении." to "No charges yet. The stay is added to the folio at check-in.",
        "Выселить гостя" to "Check out guest",
        "Гость должен оплатить. Выселение возможно, когда баланс станет 0." to "The guest must pay. Check-out is possible when the balance is 0.",
        "Гость оплатил вперёд: услуга ещё не выполнена или не назначена. Выполните услугу или верните деньги." to
            "The guest has prepaid: the service is not yet rendered or assigned. Provide the service or refund the money.",
        "Мы ничего не должны гостю, и он ничего не должен нам. Можно выселять." to "We owe the guest nothing and the guest owes us nothing. Ready for check-out.",
        "Оплачено" to "Paid",
        "Частично" to "Partial",
        "Не оплачено" to "Unpaid",
        "Добавить услугу" to "Add service",
        "Быстрый выбор" to "Quick pick",
        "Название услуги" to "Service name",
        "Сумма, ₸" to "Amount, ₸",
        "Долг гостя: {}" to "Guest debt: {1}",
        "Гость должен" to "Guest owes",
        "Оплачено вперёд" to "Prepaid",
        "Расчёт закрыт" to "Settled",
        "Баланс" to "Balance",
        "Депозит" to "Deposit",
        // Карточки
        "Скан паспорта" to "Passport scan",
        "Скан паспорта не загружен" to "Passport scan not uploaded",
        "Номер {}" to "Room {1}",
        "Номер не назначен" to "Room not assigned",
        "Вложений: {}" to "Attachments: {1}",
        // Контакты
        "Отдел, имя или номер" to "Department, name or number",
        "Бронирование" to "Reservations",
        "Рестораны и SPA" to "Restaurants and SPA",
        "Номера (Sales)" to "Rooms (Sales)",
        "Конференц-залы и мероприятия" to "Conference halls and events",
        // Время и ночи
        "только что" to "just now",
        "{} мин назад" to "{1} min ago",
        "{} ч назад" to "{1} h ago",
        "вчера" to "yesterday",
        "{} дн. назад" to "{1} d ago",
        "{} ночь" to "{1} night",
        "{} ночи" to "{1} nights",
        "{} ночей" to "{1} nights",
        "Ночей" to "Nights",
        // Фильтры броней
        "Все брони" to "All bookings",
        "Сегодня" to "Today",
        "7 дней" to "7 days",
        "Все будущие" to "All upcoming",
        "Проживают" to "In house",
        "Выселены (3 дня)" to "Checked out (3 days)",
        "Бронь, комната, гость" to "Booking, room, guest",
        "Назначить номера автоматически" to "Assign rooms automatically",
        "Нет ожидаемых заездов" to "No expected arrivals",
        "Здесь пока никого нет" to "Nobody here yet",
        "Какие заезды показать" to "Which arrivals to show",
        "Какие гости" to "Which guests",
        "Статус брони" to "Booking status",
        "Будущие" to "Upcoming",
        "Прошлые" to "Past",
        // Модели
        "Стандарт" to "Standard",
        "Стандарт, раздельные кровати" to "Standard, twin beds",
        "Улучшенный стандарт" to "Improved standard",
        "Полулюкс" to "Junior suite",
        "Люкс" to "Suite",
        "Ожидается" to "Expected",
        "Проживает" to "In house",
        "Выселен" to "Checked out",
        "Наличные" to "Cash",
        "Карта" to "Card",
        "Перевод" to "Transfer",
        "Счёт компании" to "Company account",
        "Лучший доступный" to "Best available",
        "Онлайн-тариф" to "Online rate",
        "Корпоративный" to "Corporate",
        "Без пожеланий" to "No preference",
        "Высокий этаж" to "High floor",
        "Низкий этаж" to "Low floor",
        "Завтрак в номер" to "Breakfast in room",
        "Ужин в номер" to "Dinner in room",
        "Стирка (3 вещи)" to "Laundry (3 items)",
        "Такси до аэропорта" to "Airport taxi",
        "Мини-бар" to "Minibar",
        "SPA-процедура" to "SPA treatment",
        "Поздний выезд" to "Late check-out",
        "Уборка номера" to "Room cleaning",
        "Доп. полотенца" to "Extra towels",
        "Доп. подушки" to "Extra pillows",
        "Принести утюг" to "Bring an iron",
        "Вещи оставили в номере" to "Items left in the room",
        "Заказ напитков" to "Drinks order",
        "Убрать посуду" to "Clear dishes",
        "Не работает кондиционер" to "Air conditioner not working",
        "Протечка" to "Leak",
        "Не горит свет" to "Light not working",
        "Сломан замок" to "Broken lock",
        "Нет Wi-Fi" to "No Wi-Fi",
        "Не работает ТВ" to "TV not working",
        "Проблема с принтером" to "Printer problem",
        "Сбой PMS" to "PMS failure",
        // Новый запрос
        "Место: номер комнаты" to "Location: room number",
        "Цель запроса" to "Request subject",
        "Цель (или свой вариант)" to "Subject (or your own)",
        "Описание" to "Description",
        "Priority (срочно)" to "Priority (urgent)",
        "Прикрепить фото или видео" to "Attach photo or video",
        "Прикреплено: {}. Нажмите на файл, чтобы убрать." to "Attached: {1}. Tap a file to remove it.",
        "Видео {}" to "Video {1}",
        "Фото {}" to "Photo {1}",
        // Новая бронь
        "Гость" to "Guest",
        "Имя" to "First name",
        "Фамилия" to "Last name",
        "Телефон" to "Phone",
        "Почта (необязательно)" to "Email (optional)",
        "Участник программы лояльности (скидка 10%)" to "Loyalty member (10% discount)",
        "Номер и даты" to "Room and dates",
        "Класс номера" to "Room class",
        "Заезд: {}" to "Arrival: {1}",
        "Пожелание гостя" to "Guest preference",
        "Тариф и оплата" to "Rate and payment",
        "Тариф (rate)" to "Rate plan",
        "Способ оплаты" to "Payment method",
        "Депозит, ₸ (необязательно)" to "Deposit, ₸ (optional)",
        "Агентство (необязательно)" to "Agency (optional)",
        "Компания (необязательно)" to "Company (optional)",
        "Стоимость" to "Cost",
        "Цена за ночь" to "Price per night",
        "Итого за проживание" to "Total for the stay",
        "Создать бронь" to "Create booking",
        // Запросы
        "Мои" to "Mine",
        "Активные" to "Active",
        "Закрытые" to "Closed",
        "Номер комнаты или цель" to "Room number or subject",
        "Отдел" to "Department",
        "Запросов нет" to "No requests",
        "Место" to "Location",
        "Статус" to "Status",
        "Приоритет" to "Priority",
        "Высокий" to "High",
        "Обычный" to "Normal",
        "Создал" to "Created by",
        "Когда" to "When",
        "Вложения" to "Attachments",
        "Принять в работу" to "Accept",
        "Отметить выполненным" to "Mark as done",
        "Гость просит 2 полотенца" to "Guest asks for 2 towels",
        "Шумит и не охлаждает" to "Noisy and not cooling",
        "Ресепшн" to "Reception",
        "Не печатает бланки регистрации" to "Not printing registration forms",
        "2 порции, без глютена" to "2 portions, gluten-free",
        "После выезда гостя" to "After guest check-out",
        "Течёт кран в ванной, номер закрыт" to "Bathroom tap is leaking, room closed",
        // Карточка брони
        "Проживание" to "Stay",
        "Номер" to "Room",
        "не назначен" to "not assigned",
        "Статус уборки" to "Housekeeping status",
        "Заезд" to "Arrival",
        "Выезд" to "Departure",
        "Назначить номер" to "Assign room",
        "Сменить номер" to "Change room",
        "Всего за проживание" to "Total for the stay",
        "Гость и контакты" to "Guest and contacts",
        "Почта" to "Email",
        "Агентство" to "Agency",
        "Компания" to "Company",
        "Программа лояльности" to "Loyalty program",
        "Участник (скидка 10%)" to "Member (10% discount)",
        "Паспорт" to "Passport",
        "Камера недоступна на этом устройстве" to "Camera is not available on this device",
        "Сканировать (камера)" to "Scan (camera)",
        "Из галереи" to "From gallery",
        "Готовность к заселению" to "Check-in readiness",
        "Дата заезда наступила ({})" to "Arrival date reached ({1})",
        "Номер назначен" to "Room assigned",
        "Номер готов: Clean или Inspected" to "Room ready: Clean or Inspected",
        " (сейчас {})" to " (now {1})",
        "Паспорт отсканирован" to "Passport scanned",
        "Биллинг" to "Billing",
        "Заселить" to "Check in",
        "Выселить" to "Check out",
        "Назначить номер ({})" to "Assign room ({1})",
        "Нет свободных номеров этого класса на даты брони" to "No free rooms of this class for the booking dates",
        "№{} · этаж {}" to "#{1} · floor {2}",
        // Номера
        "Этаж" to "Floor",
        "Горничные убирают…" to "Housekeepers are cleaning…",
        "Убрать все Dirty ({})" to "Clean all Dirty ({1})",
        "Занятость" to "Occupancy",
        "Сменить статус уборки:" to "Change housekeeping status:",
        "Нет номеров в статусе Dirty" to "No rooms with Dirty status",
        "Убрано номеров: {} (статус Clean)" to "Rooms cleaned: {1} (status Clean)",
        // Данные для демонстрации
        "Проживание: {} × {}" to "Stay: {1} × {2}",
        "Оплата: {}" to "Payment: {1}",
        "Оплата: Карта (с предоплатой за химчистку)" to "Payment: Card (prepaid for dry cleaning)",
        // Сообщения (операции над бронями, номерами, запросами)
        "Номер {} не найден" to "Room {1} not found",
        "Операция не выполнена" to "Operation failed",
        "Нет броней, которым можно назначить номер" to "No bookings that can be assigned a room",
        "Назначено номеров: {}" to "Rooms assigned: {1}",
        "Номер назначается только ожидаемой брони" to "A room can only be assigned to an expected booking",
        "Номер {} другого класса" to "Room {1} is of a different class",
        "Номер {} на ремонте" to "Room {1} is under maintenance",
        "Номер {} занят на эти даты" to "Room {1} is occupied on these dates",
        "Назначен номер {}" to "Room {1} assigned",
        "Скан паспорта сохранён" to "Passport scan saved",
        "Гость уже заселён или выселен" to "The guest is already checked in or out",
        "Заезд запланирован на {}" to "Arrival is planned for {1}",
        "Сначала назначьте номер" to "Assign a room first",
        "Номер {} в статусе {}: заселять нельзя" to "Room {1} is {2}: cannot check in",
        "В номере {} ещё проживает другой гость" to "Another guest is still staying in room {1}",
        "Сначала отсканируйте паспорт" to "Scan the passport first",
        "Скидка лояльности 10%" to "Loyalty discount 10%",
        "Гость {} заселён в номер {}" to "Guest {1} checked in to room {2}",
        "Выселять можно только проживающего гостя" to "Only an in-house guest can be checked out",
        "Баланс {}: гость должен оплатить. Выселение возможно при балансе 0" to "Balance {1}: the guest must pay. Check-out is possible at balance 0",
        "Баланс {}: вернуть деньги или выполнить услугу. Выселение возможно при балансе 0" to
            "Balance {1}: refund the money or provide the service. Check-out is possible at balance 0",
        "Гость выселен, номер {} теперь Dirty" to "Guest checked out, room {1} is now Dirty",
        "Услуги добавляются только проживающему гостю" to "Services can only be added to an in-house guest",
        "Введите название услуги" to "Enter the service name",
        "Сумма должна быть больше нуля" to "The amount must be greater than zero",
        "Добавлено: {}, {}" to "Added: {1}, {2}",
        "Бронь уже закрыта" to "The booking is already closed",
        "Принята оплата {}" to "Payment received: {1}",
        "Баланс закрывается у проживающего гостя" to "The balance can only be settled for an in-house guest",
        "Оплата остатка: {}" to "Payment of the remainder: {1}",
        "Принята оплата {}, баланс 0" to "Payment received: {1}, balance 0",
        "Возврат гостю" to "Refund to guest",
        "Возвращено {}, баланс 0" to "Refunded {1}, balance 0",
        "Баланс уже равен 0" to "The balance is already 0",
        "Введите имя и фамилию гостя" to "Enter the guest's first and last name",
        "Введите телефон (минимум 7 цифр)" to "Enter a phone number (at least 7 digits)",
        "Почта указана неверно" to "The email is invalid",
        "Ночей может быть от 1 до 60" to "Nights must be from 1 to 60",
        "Дата заезда уже прошла" to "The arrival date has already passed",
        "Депозит не может быть отрицательным" to "The deposit cannot be negative",
        "Нет свободных номеров {} на {}" to "No free {1} rooms on {2}",
        "Бронь №{} создана" to "Booking #{1} created",
        "Номер {} уже в статусе {}" to "Room {1} is already {2}",
        "Inspected ставится только после Clean: проверяет старшая горничная" to
            "Inspected can only be set after Clean: it is checked by the senior housekeeper",
        "В номере {} живёт гость" to "A guest is staying in room {1}",
        "На номер {} назначен скорый заезд, сначала переназначьте его" to "Room {1} has an upcoming arrival assigned, reassign it first",
        "Номер {}: {}" to "Room {1}: {2}",
        "Укажите номер комнаты или место" to "Enter a room number or location",
        "Укажите цель запроса" to "Enter the request subject",
        "Запрос отправлен в {}" to "Request sent to {1}",
        "Запрос не найден" to "Request not found",
        "Статус нельзя вернуть назад" to "The status cannot be moved back",
        "Запрос №{}: {}" to "Request #{1}: {2}"
    ),
    // Имена из демонстрационных данных
    words = mapOf(
        "Айгерим" to "Aigerim", "Сапарова" to "Saparova", "Дмитрий" to "Dmitry", "Орлов" to "Orlov",
        "Марат" to "Marat", "Ахметов" to "Akhmetov", "КазМунайГаз" to "KazMunayGas", "Анна" to "Anna",
        "Ким" to "Kim", "Олег" to "Oleg", "Петров" to "Petrov", "Ильяс" to "Ilyas", "Касымов" to "Kassymov",
        "Алия" to "Aliya", "Бекова" to "Bekova", "Нурлан" to "Nurlan", "Жаксыбеков" to "Zhaksybekov",
        "Ерлан" to "Yerlan", "Темирханов" to "Temirkhanov", "Алмас" to "Almas", "Нурмуханов" to "Nurmukhanov",
        "Дана" to "Dana", "Ержанова" to "Yerzhanova", "Анеля" to "Anelya", "Чина" to "Chyna"
    )
)

// ---------------------------------------------------------------------
// Словарь для русского режима: английские профессиональные термины -> по-русски.
// Всё остальное в этом режиме и так написано по-русски.
// ---------------------------------------------------------------------

private val toRussian = Dictionary(
    pairs = listOf(
        "Arrivals" to "Заезды",
        "In-house" to "Проживающие",
        "Arrivals (actual / expected)" to "Заезды (факт / план)",
        "Departures (actual / expected)" to "Выезды (факт / план)",
        "Priority" to "Срочно",
        "Priority (срочно)" to "Срочно (приоритет)",
        "Тариф (rate)" to "Тариф",
        "HK" to "УБ",
        "RS" to "РС",
        "MT" to "ТО",
        "Номер готов: Clean или Inspected" to "Номер готов: чистый или проверен",
        "Нет номеров в статусе Dirty" to "Нет грязных номеров",
        "Убрать все Dirty ({})" to "Убрать все грязные ({1})",
        "Убрано номеров: {} (статус Clean)" to "Убрано номеров: {1} (статус «Чистый»)",
        "Гость выселен, номер {} теперь Dirty" to "Гость выселен, номер {1} теперь грязный",
        "Inspected ставится только после Clean: проверяет старшая горничная" to
            "«Проверен» ставится только после «Чистый»: проверяет старшая горничная"
    ),
    words = mapOf(
        "Inspected" to "Проверен",
        "Clean" to "Чистый",
        "Dirty" to "Грязный",
        "Pickup" to "Резерв",
        "Maintenance" to "Ремонт",
        "Vacant" to "Свободен",
        "Occupied" to "Занят",
        "On arrival" to "Ожидает заезд",
        "Housekeeping" to "Хаускипинг",
        "Room Service" to "Рум-сервис",
        "New" to "Новый",
        "Accepted" to "Принят",
        "Done" to "Выполнен"
    )
)
