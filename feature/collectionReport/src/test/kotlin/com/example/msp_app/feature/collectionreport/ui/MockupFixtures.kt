package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.collectionreport.domain.DeltaChip
import com.example.msp_app.feature.collectionreport.domain.DeltaDirection
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.Timeline
import com.example.msp_app.feature.collectionreport.domain.TimelineBucket
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.math.BigDecimal
import java.time.Instant

/**
 * Datos EXACTOS del mockup (`docs/design/reporte-cobranza-mockup.html`, task-6-brief.md
 * "Datos del mockup para goldens") — compartidos entre el compose-test de comportamiento
 * ([CollectionReportContentTest]) y los goldens Roborazzi
 * (`screenshot/CollectionReportTopSectionScreenshotTest`), para que ambos ejerzan la MISMA
 * fixture visual/numérica en vez de divergir por accidente.
 */
internal object MockupFixtures {

    private const val DIA_START_HOUR = 8
    private val DIA_HOUR_VALUES = listOf(30, 100, 72, 58, 44, 30, 22, 26, 18)
    private const val DIA_HIGHLIGHT_INDEX = 1

    private val SEMANA_DAY_LABELS = listOf("lun", "mar", "mié", "jue", "vie")
    private val SEMANA_DAY_VALUES = listOf(74, 87, 100, 89, 64)

    const val COBRADOR = "Gabriel Roque"

    fun heroDia(): HeroUi = HeroUi(
        overline = "Cobrado · vie 7 ago",
        delta = DeltaChip("▲ 12% vs ayer", DeltaDirection.UP),
        monto = money("18300"),
        insight = Insight.Daily(count = 32, progressPct = 91, projection = money("19800")),
        progress = 0.91f,
        goalCap = money("20000"),
        sparkline = Timeline(
            buckets = DIA_HOUR_VALUES.mapIndexed { index, value ->
                val hour = DIA_START_HOUR + index
                TimelineBucket(
                    label = "${hour}h",
                    total = money(value.toString()),
                    count = 0,
                    hour = hour
                )
            },
            highlightIndex = DIA_HIGHLIGHT_INDEX
        ),
        wells = listOf(
            HeroWell("Efectivo en mano", money("12100")),
            HeroWell("Ticket prom.", money("572"))
        )
    )

    fun heroSemana(): HeroUi = HeroUi(
        overline = "Cobrado · ciclo actual",
        delta = DeltaChip("▲ 6% vs ciclo", DeltaDirection.UP),
        monto = money("118400"),
        insight = Insight.Weekly(count = 214, progressPct = 91, cycleDay = 5, cycleDays = 5),
        progress = 0.91f,
        goalCap = money("130000"),
        sparkline = Timeline(
            buckets = SEMANA_DAY_VALUES.mapIndexed { index, value ->
                TimelineBucket(
                    label = SEMANA_DAY_LABELS[index],
                    total = money(value.toString()),
                    count = 0
                )
            },
            highlightIndex = SEMANA_DAY_VALUES.lastIndex
        ),
        wells = listOf(
            HeroWell("Efectivo en mano", money("79900")),
            HeroWell("Ticket prom.", money("553"))
        )
    )

    /** Filas de pago Día (mockup `PAYS`) — nombres/ventas/montos/método EXACTOS del mockup. */
    fun paymentsDia(): List<PaymentRowUi> = listOf(
        paymentRow(
            "p-ml",
            "María López Hernández",
            "Muebles Bahía",
            "09:12",
            "1200",
            PaymentMethod.EFECTIVO
        ),
        paymentRow(
            "p-jp",
            "Juan Pérez Ramírez",
            "Recámara Diana",
            "09:40",
            "850",
            PaymentMethod.TRANSFERENCIA
        ),
        paymentRow(
            "p-rm",
            "Rosa Martínez Cruz",
            "Sala Toscana",
            "10:05",
            "1500",
            PaymentMethod.EFECTIVO
        ),
        paymentRow(
            "p-ps",
            "Pedro Sánchez Ortiz",
            "Comedor Roble",
            "11:20",
            "2000",
            PaymentMethod.EFECTIVO
        )
    )

    /** Resumen por día Semana (mockup `DAYS`) — etiquetas/montos/conteos/iniciales EXACTOS. */
    fun daysSemana(): List<DayRowUi> = listOf(
        DayRowUi("lun 3 ago", money("21300"), 39, "L3", isToday = false),
        DayRowUi("mar 4 ago", money("24800"), 46, "M4", isToday = false),
        DayRowUi("mié 5 ago", money("28600"), 51, "M5", isToday = false),
        DayRowUi("jue 6 ago", money("25400"), 46, "J6", isToday = false),
        DayRowUi("vie 7 ago (hoy)", money("18300"), 32, "V7", isToday = true)
    )

    fun stateDia(masked: Boolean = false, error: String? = null): CollectionReportUiState =
        CollectionReportUiState(
            period = ReportPeriod.DIA,
            loading = false,
            error = error,
            cobrador = COBRADOR,
            rangeLabel = "viernes 7 ago 2026",
            pendingCount = 3,
            masked = masked,
            hero = heroDia(),
            efectivo = TileUi("Efectivo", money("12100"), 22),
            transferencia = TileUi("Transferencia", money("6200"), 10),
            condonado = ChipUi("Condonado", amount = money("1400")),
            visitas = ChipUi("Visitas", count = 14),
            detail = DetailUi.Payments(paymentsDia())
        )

    fun stateSemana(masked: Boolean = false): CollectionReportUiState = CollectionReportUiState(
        period = ReportPeriod.SEMANA,
        loading = false,
        cobrador = COBRADOR,
        rangeLabel = "semana · lun 3 – vie 7 ago · 5 días",
        pendingCount = 3,
        masked = masked,
        hero = heroSemana(),
        efectivo = TileUi("Efectivo", money("79900"), 146),
        transferencia = TileUi("Transferencia", money("38500"), 68),
        condonado = ChipUi("Condonado", amount = money("9200")),
        visitas = ChipUi("Visitas", count = 71),
        detail = DetailUi.Days(daysSemana())
    )

    // Siempre `synced = true` — los tests que necesitan una fila "por subir" parten de aquí
    // con `.copy(synced = false)` (ver [paymentsDia] + los tests de `DetailList`), en vez de
    // sumar un séptimo parámetro a este helper (LongParameterList).
    private fun paymentRow(
        id: String,
        cliente: String,
        ventaLabel: String,
        hora: String,
        monto: String,
        method: PaymentMethod
    ): PaymentRowUi = PaymentRowUi(
        id = id,
        cliente = cliente,
        ventaLabel = ventaLabel,
        paidAt = paidAt(hora),
        amount = money(monto),
        method = method,
        synced = true
    )

    private fun paidAt(hora: String): Instant = AppTime.parseWireFormat("2026-08-07T$hora:00")

    private fun money(value: String) = Money.of(BigDecimal(value))
}
