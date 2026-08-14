package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.components.DetailHeader
import com.example.msp_app.feature.collectionreport.ui.components.DetailList
import com.example.msp_app.feature.collectionreport.ui.components.DuoTiles
import com.example.msp_app.feature.collectionreport.ui.components.SecondaryChips
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0, Tier 1) del bloque medio del tablero añadido en Task 7:
 * duo Efectivo/Transferencia + chips Condonado/Visitas + detalle — lista de pagos en Día,
 * resumen por día en Semana — con los datos EXACTOS del mockup (`PAYS`/`DAYS`,
 * `docs/design/reporte-cobranza-mockup.html`, ver [MockupFixtures]).
 *
 * Captura SOLO estas piezas (vía [MiddleSection], mismo patrón que el `TopSection` privado
 * de `CollectionReportTopSectionScreenshotTest`) en vez de [com.example.msp_app.feature.collectionreport.ui.CollectionReportContent]
 * completo: el golden queda acotado a lo que esta tarea agrega — el header/hero ya tienen su
 * propia cobertura en `CollectionReportTopSectionScreenshotTest`. La matriz Tier×escala de
 * la pantalla COMPLETA (incluida la composición real vía `CollectionReportScreen`) llega en
 * Task 11 (fidelity gate).
 */
class CollectionReportDetailScreenshotTest : CollectionReportScreenshotTest() {

    @Test
    fun `duo chips y detalle en Dia light`() {
        capture(name = "collection_report_detail_dia_light", dark = false) {
            MiddleSection(MockupFixtures.stateDia())
        }
    }

    @Test
    fun `duo chips y detalle en Dia dark`() {
        capture(name = "collection_report_detail_dia_dark", dark = true) {
            MiddleSection(MockupFixtures.stateDia())
        }
    }

    @Test
    fun `duo chips y detalle en Semana light`() {
        capture(name = "collection_report_detail_semana_light", dark = false) {
            MiddleSection(MockupFixtures.stateSemana())
        }
    }

    @Test
    fun `duo chips y detalle en Semana dark`() {
        capture(name = "collection_report_detail_semana_dark", dark = true) {
            MiddleSection(MockupFixtures.stateSemana())
        }
    }

    /**
     * Golden de la lista LARGA (fix de dispositivo): 23 filas de pago enriquecidas (folio +
     * saldo + método + "Por subir"), EXPANDIDA. El canvas del test (h800dp) recorta la lista —
     * el golden es una baseline visual del estilo de fila enriquecido y de que la lista crece
     * sin comprimirse; el que TODAS sean alcanzables lo garantiza el `verticalScroll` de la
     * pantalla (probado en el compose-test de orden/scroll).
     */
    @Test
    fun `lista con muchos pagos light`() {
        capture(name = "collection_report_detail_many_light", dark = false) {
            LongPaymentList(rows = MANY_ROWS, expanded = true)
        }
    }

    @Test
    fun `lista con muchos pagos dark`() {
        capture(name = "collection_report_detail_many_dark", dark = true) {
            LongPaymentList(rows = MANY_ROWS, expanded = true)
        }
    }

    /**
     * Golden del colapsable con el volumen REAL de producción: 57 pagos de un solo día (domingo
     * 9 ago 2026). COLAPSADO se ven las primeras 5 filas + el control "Ver los 57 pagos" — el
     * estado por default y el que resuelve el encargo (la lista dejó de empujar el tablero).
     */
    @Test
    fun `lista de pagos colapsada light`() {
        capture(name = "collection_report_detail_collapsed_light", dark = false) {
            LongPaymentList(rows = PRODUCTION_DAY_ROWS, expanded = false)
        }
    }

    @Test
    fun `lista de pagos colapsada dark`() {
        capture(name = "collection_report_detail_collapsed_dark", dark = true) {
            LongPaymentList(rows = PRODUCTION_DAY_ROWS, expanded = false)
        }
    }

    /**
     * El otro estado del control: EXPANDIDO, con "Ver menos" y el chevron hacia arriba.
     *
     * Lista de [SHORT_OVERFLOW_ROWS] pagos, no de 57: con el volumen de producción expandido el
     * control cae fuera del canvas de 800dp y el golden sería indistinguible del colapsado —
     * fijaría cero. Con 8 filas caben lista completa Y control, que es justo lo que este golden
     * tiene que congelar. El volumen real expandido ya lo cubren `lista con muchos pagos *` (el
     * estilo de fila con la lista larga) y el compose-test de 57 filas.
     */
    @Test
    fun `lista de pagos expandida light`() {
        capture(name = "collection_report_detail_expanded_light", dark = false) {
            LongPaymentList(rows = SHORT_OVERFLOW_ROWS, expanded = true)
        }
    }

    @Test
    fun `lista de pagos expandida dark`() {
        capture(name = "collection_report_detail_expanded_dark", dark = true) {
            LongPaymentList(rows = SHORT_OVERFLOW_ROWS, expanded = true)
        }
    }

    private companion object {
        const val MANY_ROWS = 23

        /** Volumen real: 57 pagos en un día (domingo 9 ago 2026), el caso que originó el fix. */
        const val PRODUCTION_DAY_ROWS = 57

        /** Con overflow pero corta: expandida cabe entera, control incluido, en 800dp. */
        const val SHORT_OVERFLOW_ROWS = 8
    }
}

@Composable
private fun LongPaymentList(rows: Int, expanded: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md)
    ) {
        DetailList(
            detail = DetailUi.Payments(MockupFixtures.manyPaymentsDia(rows)),
            masked = false,
            onPaymentClick = {},
            onDayClick = {},
            expanded = expanded,
            onToggleExpand = {}
        )
    }
}

@Composable
private fun MiddleSection(state: CollectionReportUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        DuoTiles(
            efectivo = state.efectivo,
            transferencia = state.transferencia,
            masked = state.masked,
            onEfectivoClick = {},
            onTransferenciaClick = {}
        )
        SecondaryChips(
            condonado = state.condonado,
            visitas = state.visitas,
            masked = state.masked,
            onCondonadoClick = {},
            onVisitasClick = {}
        )
        DetailHeader(detail = state.detail, sort = state.sort, onSortSelect = {})
        // `expanded = false`: las fixtures del mockup (4 pagos Día / 5 días Semana) no llegan al
        // umbral del colapsable, así que el golden es idéntico al de antes — sin control.
        DetailList(
            detail = state.detail,
            masked = state.masked,
            onPaymentClick = {},
            onDayClick = {},
            expanded = false,
            onToggleExpand = {}
        )
    }
}
