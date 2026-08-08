package com.example.msp_app.features.sales.domain.models

import com.example.msp_app.core.common.time.AppTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Settlement(
    val cashPrice: Double,
    val shortTermAmount: Double,
    val totalPrice: Double,
    val remainingBalance: Double,
    val date: String
)

data class PaymentResults(
    val amount: Double,
    val category: String,
    val validUntil: String
)

const val DEFAULT_GRACE_PERIOD_DAYS = 14L
private const val SHORT_TERM_DIVISOR = 3
private const val LONG_TERM_DIVISOR = 8.0
private const val SHORT_TERM_START = 4
private const val SHORT_TERM_END = 5
private const val LONG_TERM_OFFSET = 5
private const val MAX_MONTHS = 12
private const val VALIDITY_EXTRA_DAYS = 14L

fun calculatePaymentResult(
    settlement: Settlement,
    now: LocalDateTime = AppTime.nowInBusinessZone(),
    gracePeriodDays: Long = DEFAULT_GRACE_PERIOD_DAYS
): PaymentResults {
    if (settlement.cashPrice == 0.0 && settlement.shortTermAmount == 0.0) {
        if (settlement.totalPrice > 0.0) {
            val totalPaid = settlement.totalPrice - settlement.remainingBalance
            return PaymentResults(
                amount = settlement.totalPrice - totalPaid,
                category = "Precio total",
                validUntil = "-"
            )
        }
        return PaymentResults(
            amount = 0.0,
            category = "No disponible",
            validUntil = "-"
        )
    }

    if (settlement.cashPrice == 0.0) {
        return PaymentResults(
            amount = 0.0,
            category = "No disponible",
            validUntil = "-"
        )
    }

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val saleDate = LocalDate.parse(settlement.date, formatter)

    val saleEndOfDay = saleDate.atTime(LocalTime.MAX)
    val adjustedNow = now.minusDays(gracePeriodDays)
    val elapsedMonths = ChronoUnit.MONTHS.between(saleEndOfDay, adjustedNow) + 1

    if (elapsedMonths <= 0) {
        return PaymentResults(
            amount = settlement.cashPrice - (settlement.totalPrice - settlement.remainingBalance),
            category = "Precio de contado",
            validUntil = saleDate.plusDays(VALIDITY_EXTRA_DAYS)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "MX")))
        )
    }

    val safeShortTermAmount = maxOf(settlement.shortTermAmount, settlement.cashPrice)
    val shortTermInterest = (safeShortTermAmount - settlement.cashPrice) / SHORT_TERM_DIVISOR
    val totalPaid = settlement.totalPrice - settlement.remainingBalance

    val amount = when {
        elapsedMonths <= 1 -> settlement.cashPrice
        elapsedMonths <= 3 -> settlement.cashPrice + (elapsedMonths - 1) * shortTermInterest
        elapsedMonths in SHORT_TERM_START..SHORT_TERM_END -> safeShortTermAmount
        elapsedMonths <= MAX_MONTHS -> {
            val progress = (elapsedMonths - LONG_TERM_OFFSET).toDouble() / LONG_TERM_DIVISOR
            safeShortTermAmount + (settlement.totalPrice - safeShortTermAmount) * progress
        }
        else -> settlement.totalPrice
    } - totalPaid

    val category = when {
        elapsedMonths <= 1 -> "Precio de contado"
        elapsedMonths < MAX_MONTHS -> "Precio a $elapsedMonths meses"
        elapsedMonths == MAX_MONTHS.toLong() -> "Precio Promocional"
        else -> "Precio total"
    }

    val validDate = saleDate.plusMonths(elapsedMonths).plusDays(VALIDITY_EXTRA_DAYS)
    val outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "MX"))
    val formattedDate = validDate.format(outputFormatter)

    return PaymentResults(amount, category, formattedDate)
}
