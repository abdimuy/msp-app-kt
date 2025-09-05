package com.example.msp_app.components.stock

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
fun ProductStock(stock: Int?) {
    val label = availabilityLabelFrom(stock)

    val color = when (label) {
        AvailabilityLabel.OUT_OF_STOCK -> Color(0xFFB00020)
        AvailabilityLabel.LAST_ONE -> Color(0xFFF57C00)
        AvailabilityLabel.LIMITED -> Color(0xFFFFA000)
        AvailabilityLabel.IN_STOCK -> Color(0xFF2E7D32)
        AvailabilityLabel.UNKNOWN -> Color(0xFF455A64)
    }

    Text(
        text = label.text,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

enum class AvailabilityLabel(val text: String) {
    OUT_OF_STOCK("Agotado"),
    LAST_ONE("Última pieza disponible"),
    LIMITED("Disponibilidad limitada"),
    IN_STOCK("Disponible en inventario"),
    UNKNOWN("Consultar disponibilidad")
}

fun availabilityLabelFrom(stock: Int?): AvailabilityLabel = when {
    stock == null || stock < 0 -> AvailabilityLabel.UNKNOWN
    stock == 0 -> AvailabilityLabel.OUT_OF_STOCK
    stock == 1 -> AvailabilityLabel.LAST_ONE
    stock in 2..4 -> AvailabilityLabel.LIMITED
    else -> AvailabilityLabel.IN_STOCK
}