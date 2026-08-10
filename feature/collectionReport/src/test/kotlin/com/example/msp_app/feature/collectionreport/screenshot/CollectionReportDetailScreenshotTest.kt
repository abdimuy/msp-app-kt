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
     * saldo + método + "Por subir"). El canvas del test (h800dp) recorta la lista — el golden
     * es una baseline visual del estilo de fila enriquecido y de que la lista crece sin
     * comprimirse; el que TODAS sean alcanzables lo garantiza el `verticalScroll` de la
     * pantalla (probado en el compose-test de orden/scroll).
     */
    @Test
    fun `lista con muchos pagos light`() {
        capture(name = "collection_report_detail_many_light", dark = false) {
            LongPaymentList()
        }
    }

    @Test
    fun `lista con muchos pagos dark`() {
        capture(name = "collection_report_detail_many_dark", dark = true) {
            LongPaymentList()
        }
    }
}

@Composable
private fun LongPaymentList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md)
    ) {
        DetailList(
            detail = DetailUi.Payments(MockupFixtures.manyPaymentsDia()),
            masked = false,
            onPaymentClick = {},
            onDayClick = {}
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
        DetailList(
            detail = state.detail,
            masked = state.masked,
            onPaymentClick = {},
            onDayClick = {}
        )
    }
}
