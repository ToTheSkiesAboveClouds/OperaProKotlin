package com.example.hotelreception

// =====================================================================
// ПРАВИЛА КАССОВОЙ СМЕНЫ (слой Domain). Чистые функции: считают итоги смены
// из счетов гостей. Здесь нет ни Compose, ни Room, поэтому их легко проверить.
// =====================================================================

// Единственная открытая смена (если есть)
fun HotelData.openShift(): Shift? = shifts.firstOrNull { it.status == ShiftStatus.OPEN }

// Операции смены: оплаты и возвраты, которые попали в смену с номером shiftId
fun HotelData.shiftOperations(shiftId: Int): List<ShiftOperation> =
    reservations
        .flatMap { res ->
            res.folio
                .filter { it.shiftId == shiftId && (it.type == EntryType.PAYMENT || it.type == EntryType.REFUND) }
                .map { ShiftOperation(res.id, res.guest.fullName, it) }
        }
        .sortedByDescending { it.entry.id }

// Итоги смены по её операциям
fun HotelData.totalsFor(shiftId: Int): ShiftTotals {
    var totals = ShiftTotals()
    for (op in shiftOperations(shiftId)) {
        val amount = op.entry.amount
        val method = op.entry.method ?: PaymentMethod.CARD
        totals = if (op.entry.type == EntryType.PAYMENT) {
            val withMoney = when (method) {
                PaymentMethod.CASH -> totals.copy(cash = totals.cash + amount)
                PaymentMethod.CARD -> totals.copy(card = totals.card + amount)
                PaymentMethod.TRANSFER -> totals.copy(transfer = totals.transfer + amount)
                PaymentMethod.COMPANY -> totals.copy(company = totals.company + amount)
            }
            withMoney.copy(paymentsCount = totals.paymentsCount + 1)
        } else if (method == PaymentMethod.CASH) {
            totals.copy(cashRefunds = totals.cashRefunds + amount)
        } else {
            totals.copy(otherRefunds = totals.otherRefunds + amount)
        }
    }
    return totals
}

// У открытой смены итоги живые (из счетов), у закрытой берутся записанные при закрытии
fun HotelData.withLiveTotals(shift: Shift): Shift =
    if (shift.status == ShiftStatus.OPEN) shift.copy(totals = totalsFor(shift.id)) else shift

// Статистика по закрытым сменам (для блока «История»)
data class ShiftHistoryStats(
    val closedCount: Int,
    val receivedTotal: Int,
    val shortageCount: Int,     // сколько раз была недостача
    val differenceTotal: Int    // сумма разниц по всем сменам
)

fun historyStats(shifts: List<Shift>): ShiftHistoryStats {
    val closed = shifts.filter { it.status == ShiftStatus.CLOSED }
    return ShiftHistoryStats(
        closedCount = closed.size,
        receivedTotal = closed.sumOf { it.totals.received },
        shortageCount = closed.count { (it.difference ?: 0) < 0 },
        differenceTotal = closed.sumOf { it.difference ?: 0 }
    )
}
