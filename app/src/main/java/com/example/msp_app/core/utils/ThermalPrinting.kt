package com.example.msp_app.core.utils

object ThermalPrinting {
    fun centerText(text: String, width: Int): String {
        val padding = (width - text.length) / 2
        return " ".repeat(padding.coerceAtLeast(0)) + text
    }
}