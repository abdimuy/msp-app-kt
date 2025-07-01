package com.example.msp_app.core.utils

import com.example.msp_app.data.models.payment.Payment
import java.util.Locale

object ThermalPrinting {

    fun formatPaymentsTextForTicket(
        payments: List<Payment>,
        dateStr: String,
        collectorName: String,
        title: String
    ): String {
        val builder = StringBuilder()

        builder.appendLine("=".repeat(32))
        builder.appendLine(centerText(title, 32))
        builder.appendLine("Fecha: $dateStr")
        builder.appendLine("Cobrador: $collectorName")
        builder.appendLine("-".repeat(32))

        builder.appendLine(String.format("%-6s %-16s %8s", "Fecha", "Cliente", "Importe"))

        payments.forEach { pago ->
            val fecha = DateUtils.formatIsoDate(
                pago.FECHA_HORA_PAGO,
                "HH:mm",
                Locale("es", "MX")
            )
            val cliente =
                if (pago.NOMBRE_CLIENTE.length > 16) pago.NOMBRE_CLIENTE.take(16) else pago.NOMBRE_CLIENTE
            val importe = pago.IMPORTE

            builder.appendLine(
                String.format("%-6s %-16s %8s", fecha, cliente, "$%,d".format(importe.toInt()))
            )
        }

        builder.appendLine("-".repeat(32))
        builder.appendLine("Total pagos: ${payments.size}")
        builder.appendLine(
            "Total importe: $${
                "%,d".format(payments.sumOf { it.IMPORTE }.toInt())
            }"
        )
        builder.appendLine("=".repeat(32))
        builder.appendLine("!!!GRACIAS POR SU PREFERENCIA!!!")

        return builder.toString()
    }

    private fun centerText(text: String, width: Int): String {
        val padding = (width - text.length) / 2
        return " ".repeat(padding.coerceAtLeast(0)) + text
    }
}