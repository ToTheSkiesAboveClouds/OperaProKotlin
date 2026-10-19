package com.example.hotelreception

// Модели из HW1, адаптированные под Android.
// Главное отличие: всё иммутабельно (val + data class), чтобы Compose
// и StateFlow замечали изменения через copy().

enum class RoomType(val label: String, val pricePerNight: Int) {
    STANDARD("Standard", 25_000),
    DELUXE("Deluxe", 40_000),
    SUITE("Suite", 75_000)
}

data class Room(
    val number: Int,
    val floor: Int,
    val type: RoomType,
    val isDirty: Boolean = false
)

data class Guest(
    val name: String,
    val isVip: Boolean
)

// Sealed class из HW1: ровно три вида услуг, when по ним без else
sealed class ServiceOrder(val title: String, val price: Int) {
    data class Food(val dish: String, val cost: Int) : ServiceOrder(dish, cost)
    data class Laundry(val items: Int) : ServiceOrder("Прачечная ($items шт.)", items * 1_500)
    object Taxi : ServiceOrder("Такси", 4_000)
}

data class Booking(
    val room: Room,
    val guest: Guest,
    val nights: Int,
    val orders: List<ServiceOrder> = emptyList()
) {
    val roomCost: Int get() = room.type.pricePerNight * nights
    val servicesCost: Int get() = orders.sumOf { it.price }
    val discount: Int get() = if (guest.isVip) ((roomCost + servicesCost) * 0.10).toInt() else 0
    val total: Int get() = roomCost + servicesCost - discount
}
