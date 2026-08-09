package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.feature.collectionreport.domain.DeltaChip
import com.example.msp_app.feature.collectionreport.domain.DeltaDirection
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.Timeline
import com.example.msp_app.feature.collectionreport.domain.TimelineBucket
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.math.BigDecimal

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

    fun stateDia(masked: Boolean = false, error: String? = null): CollectionReportUiState =
        CollectionReportUiState(
            period = ReportPeriod.DIA,
            loading = false,
            error = error,
            cobrador = COBRADOR,
            rangeLabel = "viernes 7 ago 2026",
            pendingCount = 3,
            masked = masked,
            hero = heroDia()
        )

    fun stateSemana(masked: Boolean = false): CollectionReportUiState = CollectionReportUiState(
        period = ReportPeriod.SEMANA,
        loading = false,
        cobrador = COBRADOR,
        rangeLabel = "semana · lun 3 – vie 7 ago · 5 días",
        pendingCount = 3,
        masked = masked,
        hero = heroSemana()
    )

    private fun money(value: String) = Money.of(BigDecimal(value))
}
