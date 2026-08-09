package com.example.msp_app.features.payments.utils

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.models.PaymentMethod
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.visit.Visit
import com.example.msp_app.features.payments.models.ForgivenessTextData
import com.example.msp_app.features.payments.models.PaymentLineData
import com.example.msp_app.features.payments.models.PaymentMethodBreakdown
import com.example.msp_app.features.payments.models.PaymentTextData
import com.example.msp_app.features.payments.models.VisitLineData
import com.example.msp_app.features.payments.models.VisitTextData
import java.time.LocalDate

/**
 * Half-open range covering all of [startIso, endIso) — [startIso] is midnight of the report
 * date in business zone, [endIso] is midnight of the NEXT day (exclusive). See
 * [ReportFormatters.dateRangeFor].
 */
data class ReportDateRange(val startIso: String, val endIso: String)

object ReportFormatters {

    /**
     * Half-open one-day range `[startOfDay(date), startOfNextDay(date))` in business zone,
     * matching `msp-api`'s `[desde, hasta)` semantics byte-for-byte (see
     * `AppTime.startOfDay`/`AppTime.startOfNextDay` kdoc and
     * `msp-api/docs/module-standards/DATETIME_HANDLING.md`).
     *
     * Replaces the legacy date util's `parseLocalDateToIso(date)` (start, computed in the
     * DEVICE zone via `ZoneId.systemDefault()`) + `addToIsoDate(addToIsoDate(iso, 1,
     * DAYS), -1, SECONDS)` (end, "+1 day -1 second"). That legacy pattern had two independent
     * bugs (`date-lib-audit.md`): (1) the day boundary was anchored to the device's timezone,
     * not the business zone — a cobrador with a misconfigured/roaming phone could see the wrong
     * payments in a "daily" report; (2) `addToIsoDate` round-trips through `LocalDateTime`,
     * dropping the UTC offset carried by the input string and silently re-interpreting the
     * result as UTC (bug #3) — the "-1 second" end (`23:59:59`) could land at the wrong instant
     * by exactly the device's UTC offset, and always missed the last second/millisecond of the
     * day regardless.
     *
     * Pure and side-effect free — this is the function under test in
     * `ReportFormattersDateRangeTest` (boundary, device-zone independence, old-vs-new
     * characterization).
     */
    fun dateRangeFor(date: LocalDate): ReportDateRange = ReportDateRange(
        startIso = AppTime.toWireFormat(AppTime.startOfDay(date)),
        endIso = AppTime.toWireFormat(AppTime.startOfNextDay(date))
    )

    /**
     * The default report date for an unopened "today" report — business zone, NEVER device
     * zone. Canonical call site for the "today" default used by `DailyReportScreen` (initial
     * `LaunchedEffect`) and `RouteMapScreen` (initial `selectedDate`), replacing the bare
     * `LocalDate.now()` / the legacy date util's `getCurrentDate()` calls that both anchored to
     * `ZoneId.systemDefault()` (bug #1: a device near midnight in another zone opened the
     * report on the wrong business day).
     */
    fun todayForReport(clock: AppClock = AppClock.System): LocalDate =
        AppTime.todayInBusinessZone(clock)

    fun formatPaymentsTextList(payments: List<Payment>): PaymentTextData {
        val lines = payments.map { payment ->
            val formattedDate = AppTime.formatIsoForDisplay(
                payment.FECHA_HORA_PAGO,
                "dd/MM/yy HH:mm"
            )
            PaymentLineData(
                date = formattedDate,
                client = payment.NOMBRE_CLIENTE,
                amount = payment.IMPORTE,
                paymentMethod = PaymentMethod.fromId(payment.FORMA_COBRO_ID)
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
            val date = AppTime.formatIsoForDisplay(pago.FECHA_HORA_PAGO, "dd/MM/yy")
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
                    AppTime.formatIsoForDisplay(pago.FECHA_HORA_PAGO, "dd/MM")
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
            val formattedDate = AppTime.formatIsoForDisplay(
                payment.FECHA_HORA_PAGO,
                "dd/MM/yy HH:mm"
            )
            PaymentLineData(
                date = formattedDate,
                client = payment.NOMBRE_CLIENTE,
                amount = payment.IMPORTE,
                paymentMethod = PaymentMethod.fromId(payment.FORMA_COBRO_ID)
            )
        }

        val totalCount = forgiveness.size
        val totalAmount = forgiveness.sumOf { it.IMPORTE }

        return ForgivenessTextData(lines, totalCount, totalAmount)
    }

    fun formatVisitsTextList(visits: List<Visit>): VisitTextData {
        val lines = visits.map {
            VisitLineData(
                date = AppTime.formatIsoForDisplay(it.FECHA, "dd/MM/yy HH:mm"),
                collector = it.COBRADOR,
                type = it.TIPO_VISITA,
                note = it.NOTA ?: "-"
            )
        }
        return VisitTextData(lines, lines.size)
    }
}
