package com.example.msp_app.features.payments.utils

import com.example.msp_app.core.models.PaymentMethod
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.visit.Visit
import com.example.msp_app.features.payments.models.ForgivenessTextData
import com.example.msp_app.features.payments.models.PaymentLineData
import com.example.msp_app.features.payments.models.PaymentTextData
import com.example.msp_app.features.payments.models.VisitLineData
import com.example.msp_app.features.payments.models.VisitTextData
import com.example.msp_app.features.payments.screens.PaymentMethodBreakdown
import java.util.Locale

object ReportFormatters {

    fun formatPaymentsTextList(payments: List<Payment>): PaymentTextData {
        val lines = payments.map { payment ->
            val formattedDate = DateUtils.formatIsoDate(
                payment.FECHA_HORA_PAGO,
                "dd/MM/yy HH:mm",
                Locale("es", "MX")
            )
            PaymentLineData(
                date = formattedDate,
                client = payment.NOMBRE_CLIENTE,
                amount = payment.IMPORTE,
                paymentMethod = PaymentMethod.fromId(payment.FORMA_COBRO_ID),
            )
        }

        val totalCount = payments.size
        val totalAmount = payments.sumOf { it.IMPORTE }
        val breakdownByMethod = payments
            .groupBy { PaymentMethod.fromId(it.FORMA_COBRO_ID) }
            .map { (method, payments) ->
                PaymentMethodBreakdown(
                    method = method,
                    count = payments.size,
                    amount = payments.sumOf { it.IMPORTE }
                )
            }

        return PaymentTextData(lines, totalCount, totalAmount, breakdownByMethod)
    }

    fun formatPaymentsTextForTicket(
        payments: List<Payment>,
        dateStr: String,
        collectorName: String,
        title: String,
        forgiveness: List<Payment>
    ): String {
        val builder = StringBuilder()

        builder.appendLine("=".repeat(32))
        builder.appendLine(ThermalPrinting.centerText(title, 32))
        builder.appendLine("Fecha: $dateStr")
        builder.appendLine("Cobrador: $collectorName")
        builder.appendLine("-".repeat(32))
        builder.appendLine(String.format("%-8s %-14s %8s", "Fecha", "Cliente", "Importe"))

        payments.forEach { pago ->
            val date = DateUtils.formatIsoDate(pago.FECHA_HORA_PAGO, "dd/MM/yy", Locale("es", "MX"))
            val client = pago.NOMBRE_CLIENTE.take(14)
            val amount = "$%,d".format(pago.IMPORTE.toInt())

            builder.appendLine(String.format("%-8s %-14s %8s", date, client, amount))
        }
        builder.appendLine("-".repeat(32))
        val total = payments.sumOf { it.IMPORTE }.toInt()
        val cash =
            payments.filter { PaymentMethod.fromId(it.FORMA_COBRO_ID) == PaymentMethod.PAGO_EN_EFECTIVO }
        val transfers =
            payments.filter { PaymentMethod.fromId(it.FORMA_COBRO_ID) == PaymentMethod.PAGO_CON_TRANSFERENCIA }

        val totalCash = cash.sumOf { it.IMPORTE }.toInt()
        val totalTransfers = transfers.sumOf { it.IMPORTE }.toInt()

        builder.appendLine("Total pagos: ${payments.size}")
        builder.appendLine("Total importe: $%,d".format(total))
        builder.appendLine("Efectivo (${cash.size} pagos): $%,d".format(totalCash))
        builder.appendLine("Transferencia (${transfers.size} pagos): $%,d".format(totalTransfers))

        builder.appendLine(" ".repeat(32))

        if (forgiveness.isNotEmpty()) {
            builder.appendLine("-".repeat(32))
            builder.appendLine("Condonaciones:")
            builder.appendLine(" ".repeat(32))
            forgiveness.forEach { pago ->
                val date =
                    DateUtils.formatIsoDate(pago.FECHA_HORA_PAGO, "dd/MM", Locale("es", "MX"))
                val client =
                    pago.NOMBRE_CLIENTE.takeIf { it.length <= 16 } ?: pago.NOMBRE_CLIENTE.take(16)
                builder.appendLine(
                    String.format(
                        "%-6s %-16s %8s",
                        date,
                        client,
                        "$%,d".format(pago.IMPORTE.toInt())
                    )
                )
            }
            builder.appendLine(" ".repeat(32))
            val totalForgiveness = forgiveness.sumOf { it.IMPORTE }.toInt()
            builder.appendLine("Total condonado: $%,d".format(totalForgiveness))
        }

        return builder.toString()
    }

    fun formatForgivenessTextList(forgiveness: List<Payment>): ForgivenessTextData {
        val lines = forgiveness.map { payment ->
            val formattedDate = DateUtils.formatIsoDate(
                payment.FECHA_HORA_PAGO,
                "dd/MM/yy HH:mm",
                Locale("es", "MX")
            )
            PaymentLineData(
                date = formattedDate,
                client = payment.NOMBRE_CLIENTE,
                amount = payment.IMPORTE,
                paymentMethod = PaymentMethod.fromId(payment.FORMA_COBRO_ID),
            )
        }

        val totalCount = forgiveness.size
        val totalAmount = forgiveness.sumOf { it.IMPORTE }

        return ForgivenessTextData(lines, totalCount, totalAmount)
    }

    fun formatVisitsTextList(visits: List<Visit>): VisitTextData {
        val lines = visits.map {
            VisitLineData(
                date = DateUtils.formatIsoDate(it.FECHA, "dd/MM/yy HH:mm", Locale("es", "MX")),
                collector = it.COBRADOR,
                type = it.TIPO_VISITA,
                note = it.NOTA ?: "-"
            )
        }
        return VisitTextData(lines, lines.size)
    }
}