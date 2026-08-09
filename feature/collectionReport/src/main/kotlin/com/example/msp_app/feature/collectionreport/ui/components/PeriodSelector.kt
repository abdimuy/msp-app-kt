package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspSegmentChips
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod

/** Etiquetas del selector — 1:1 mockup `.period button` ("Día"/"Semana"). */
private val PERIOD_OPTIONS = listOf("Día", "Semana")

/**
 * Selector de periodo Día·Semana (mockup `.period`) sobre [MspSegmentChips] del design
 * system. [ReportPeriod.ordinal] mapea 1:1 con el índice de [PERIOD_OPTIONS]
 * (`DIA` = 0, `SEMANA` = 1) — no se necesita una tabla de mapeo aparte.
 */
@Composable
fun PeriodSelector(
    period: ReportPeriod,
    onSelect: (ReportPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    MspSegmentChips(
        options = PERIOD_OPTIONS,
        selectedIndex = period.ordinal,
        onSelect = { index -> onSelect(ReportPeriod.entries[index]) },
        modifier = modifier
    )
}
