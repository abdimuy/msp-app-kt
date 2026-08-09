package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.SheetKind
import com.example.msp_app.feature.collectionreport.ui.SheetUi
import com.example.msp_app.feature.collectionreport.ui.components.SheetBody
import com.example.msp_app.feature.collectionreport.ui.components.deriveSheetContent
import org.junit.Test

private val SHEET_PREVIEW_SHAPE = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * Golden baseline (light+dark @1.0, Tier 1) del CONTENIDO de cada `SheetKind` del mockup
 * (`docs/design/reporte-cobranza-mockup.html` §10, datos EXACTOS de [MockupFixtures]).
 *
 * Captura [SheetBody] (título/subtítulo/filas) envuelto en una tarjeta que replica el radio
 * 24dp del mockup, NO el `ModalBottomSheet` (M3) real — gotcha del brief ("ModalBottomSheet
 * en Roborazzi puede ser complicado"), verificado empíricamente: `captureRoboImage` solo
 * toma el `Canvas` de la ventana raíz, y M3 monta el `ModalBottomSheet` en un `Popup` en OTRA
 * ventana → un golden capturado así sale en blanco. El comportamiento real (abrir/cerrar el
 * `ModalBottomSheet` de verdad, `masked`) vive en `ui/components/ReportSheetsTest`, que SÍ lo
 * ejerce completo vía `ComposeTestRule` (sus queries de semántica cruzan ventanas sin
 * problema). Ver KDoc de [SheetBody] para el detalle completo de esta decisión.
 */
class ReportSheetsScreenshotTest : CollectionReportScreenshotTest() {

    @Test
    fun `sheet hero dia light`() {
        capture(name = "collection_report_sheet_hero_light", dark = false) {
            Sheet(SheetKind.HERO, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet hero dia dark`() {
        capture(name = "collection_report_sheet_hero_dark", dark = true) {
            Sheet(SheetKind.HERO, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet efectivo light`() {
        capture(name = "collection_report_sheet_efectivo_light", dark = false) {
            Sheet(SheetKind.EFECTIVO, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet efectivo dark`() {
        capture(name = "collection_report_sheet_efectivo_dark", dark = true) {
            Sheet(SheetKind.EFECTIVO, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet transferencia light`() {
        capture(name = "collection_report_sheet_transferencia_light", dark = false) {
            Sheet(SheetKind.TRANSFERENCIA, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet transferencia dark`() {
        capture(name = "collection_report_sheet_transferencia_dark", dark = true) {
            Sheet(SheetKind.TRANSFERENCIA, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet condonado light`() {
        capture(name = "collection_report_sheet_condonado_light", dark = false) {
            Sheet(SheetKind.CONDONADO, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet condonado dark`() {
        capture(name = "collection_report_sheet_condonado_dark", dark = true) {
            Sheet(SheetKind.CONDONADO, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet visitas light`() {
        capture(name = "collection_report_sheet_visitas_light", dark = false) {
            Sheet(SheetKind.VISITAS, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet visitas dark`() {
        capture(name = "collection_report_sheet_visitas_dark", dark = true) {
            Sheet(SheetKind.VISITAS, state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet dia del ciclo light`() {
        capture(name = "collection_report_sheet_dia_light", dark = false) {
            Sheet(SheetKind.DIA_CICLO, argument = "1", state = MockupFixtures.stateSemana())
        }
    }

    @Test
    fun `sheet dia del ciclo dark`() {
        capture(name = "collection_report_sheet_dia_dark", dark = true) {
            Sheet(SheetKind.DIA_CICLO, argument = "1", state = MockupFixtures.stateSemana())
        }
    }

    @Test
    fun `sheet pago light`() {
        capture(name = "collection_report_sheet_pago_light", dark = false) {
            Sheet(SheetKind.PAGO, argument = "p-ml", state = MockupFixtures.stateDia())
        }
    }

    @Test
    fun `sheet pago dark`() {
        capture(name = "collection_report_sheet_pago_dark", dark = true) {
            Sheet(SheetKind.PAGO, argument = "p-ml", state = MockupFixtures.stateDia())
        }
    }
}

@Composable
private fun Sheet(kind: SheetKind, state: CollectionReportUiState, argument: String? = null) {
    val content = deriveSheetContent(SheetUi(kind, argument), state)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SHEET_PREVIEW_SHAPE)
            .background(MspTheme.colors.surface)
            .padding(top = MspTheme.spacing.sm)
    ) {
        SheetBody(content = content, masked = state.masked)
    }
}
