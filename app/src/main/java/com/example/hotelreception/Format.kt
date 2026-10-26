package com.example.hotelreception

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Небольшие функции форматирования, общие для всего приложения.

// 25000 -> "25 000 ₸", -3000 -> "-3 000 ₸"
fun Int.tenge(): String = "%,d".format(Locale.US, this).replace(',', ' ') + " ₸"

private val shortFormat = DateTimeFormatter.ofPattern("dd.MM")
private val fullFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun LocalDate.short(): String = format(shortFormat)
fun LocalDate.full(): String = format(fullFormat)

// "5 мин назад", "2 ч назад", "вчера"
fun LocalDateTime.ago(now: LocalDateTime = LocalDateTime.now()): String {
    val minutes = Duration.between(this, now).toMinutes()
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 60 * 24 -> "${minutes / 60} ч назад"
        minutes < 60 * 48 -> "вчера"
        else -> "${minutes / (60 * 24)} дн. назад"
    }
}

// "1 ночь", "2 ночи", "5 ночей"
fun nightsText(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "ночей"
        n % 10 == 1 -> "ночь"
        n % 10 in 2..4 -> "ночи"
        else -> "ночей"
    }
    return "$n $word"
}
