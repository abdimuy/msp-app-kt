package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspSegmentChips
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.DetailSort
import com.example.msp_app.feature.collectionreport.ui.DetailUi

/** Opciones del segment de orden — índice 1:1 con [DetailSort.ordinal] (`HORA` = 0, `NOMBRE` = 1). */
private val SORT_OPTIONS = listOf("Hora", "Nombre")

/**
 * Encabezado del bloque de detalle (mockup `.lhdr`): etiqueta en `type.sectionLabel`
 * ("Pagos del día · N" en Día, "Resumen por día · N días" en Semana — el conteo sale de
 * `detail.rows.size`, el mismo dato que ya renderiza [DetailList], sin duplicar un campo
 * aparte en [com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState]) +
 * el segment Hora·Nombre, presente SOLO cuando [detail] es [DetailUi.Payments] (Día) —
 * [DetailUi.Days] (Semana) siempre es cronológico, no hay nada que reordenar (ver
 * `CollectionReportViewModel.setSort`). El periodo se deriva del TIPO de [detail], no de un
 * parámetro aparte: la correspondencia Día↔`Payments`/Semana↔`Days` ya es 1:1 en
 * `CollectionReportStateBuilder.buildDetailUi`.
 */
@Composable
fun DetailHeader(
    detail: DetailUi,
    sort: DetailSort,
    onSortSelect: (DetailSort) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = detailHeaderLabel(detail),
            style = MspTheme.type.sectionLabel,
            color = MspTheme.colors.onSurfaceMuted
        )
        if (detail is DetailUi.Payments) {
            MspSegmentChips(
                options = SORT_OPTIONS,
                selectedIndex = sort.ordinal,
                onSelect = { index -> onSortSelect(DetailSort.entries[index]) }
            )
        }
    }
}

private fun detailHeaderLabel(detail: DetailUi): String = when (detail) {
    is DetailUi.Payments -> "Pagos del día · ${detail.rows.size}"
    is DetailUi.Days -> "Resumen por día · ${detail.rows.size} días"
}
