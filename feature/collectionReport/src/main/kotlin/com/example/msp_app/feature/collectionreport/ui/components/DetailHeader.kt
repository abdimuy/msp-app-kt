package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspSegmentChips
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.DetailSort
import com.example.msp_app.feature.collectionreport.ui.DetailUi

// Opciones del segment de orden — índice 1:1 con [DetailSort.ordinal] (`HORA` = 0, `NOMBRE` = 1).
// El primer label es period-aware ("Hora" en Día, "Fecha" en Semana, ver Task 4: el toggle
// ahora también gobierna el ciclo, no solo el detalle Día); el segundo se conserva "Nombre" en
// ambos periodos (mismo texto, no hace falta un "A–Z" aparte).
private val DIA_SORT_OPTIONS = listOf("Hora", "Nombre")
private val SEMANA_SORT_OPTIONS = listOf("Fecha", "Nombre")

/**
 * Encabezado del bloque de detalle (mockup `.lhdr`): etiqueta en `type.sectionLabel`
 * ("Pagos del día · N" en Día, "Resumen por día · N días" en Semana — el conteo sale de
 * `detail.rows.size`, el mismo dato que ya renderiza [DetailList], sin duplicar un campo
 * aparte en [com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState]) +
 * el segment Hora/Fecha·Nombre — Task 4: presente en AMBOS periodos ahora ([DetailUi.Days]
 * (Semana) YA no es "siempre cronológico, nada que reordenar"; reordena los pagos dentro de
 * cada día del ciclo, ver `CollectionReportViewModel.setSort`). El periodo se deriva del TIPO
 * de [detail], no de un parámetro aparte: la correspondencia Día↔`Payments`/Semana↔`Days` ya
 * es 1:1 en `CollectionReportStateBuilder.buildDetailUi`.
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = detailHeaderLabel(detail),
            style = MspTheme.type.sectionLabel,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        MspSegmentChips(
            options = sortOptions(detail),
            selectedIndex = sort.ordinal,
            onSelect = { index -> onSortSelect(DetailSort.entries[index]) },
            equalWidth = false
        )
    }
}

private fun sortOptions(detail: DetailUi): List<String> = when (detail) {
    is DetailUi.Payments -> DIA_SORT_OPTIONS
    is DetailUi.Days -> SEMANA_SORT_OPTIONS
}

private fun detailHeaderLabel(detail: DetailUi): String = when (detail) {
    is DetailUi.Payments -> "Pagos del día · ${detail.rows.size}"
    is DetailUi.Days -> "Resumen por día · ${detail.rows.size} días"
}
