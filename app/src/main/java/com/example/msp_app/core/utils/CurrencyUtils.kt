package com.example.msp_app.core.utils

import java.text.NumberFormat
import java.util.Locale

fun Double.toCurrency(
    locale: Locale = Locale("es", "MX"),
    noDecimals: Boolean = false
): String {
    val formatter = NumberFormat.getCurrencyInstance(locale).apply {
        if (noDecimals) {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }
    return formatter.format(this)
}

fun Int.toCurrency(
    locale: Locale = Locale("es", "MX"),
    noDecimals: Boolean = false
): String {
    return this.toDouble().toCurrency(locale, noDecimals)
}