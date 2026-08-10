package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.ui.ForgivenessRowUi
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.SheetKind
import com.example.msp_app.feature.collectionreport.ui.SheetUi
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Cobertura de [deriveSheetContent] (pura, Task 8) — cada [SheetKind] produce el cuerpo
 * correcto a partir del resto de [com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState]
 * (ver su KDoc: "completo > inventado") — y de [ReportSheets] (compose): abrir/cerrar,
 * `masked`. El golden visual vive en `screenshot/ReportSheetsScreenshotTest`.
 */
@OptIn(ExperimentalMaterial3Api::class)
class ReportSheetsTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun money(value: String) = Money.of(BigDecimal(value))

    // region — deriveSheetContent (pura) -------------------------------------------------

    @Test
    fun `el sheet hero en Dia trae cobrado, ritmo con proyeccion, mejor momento y falta para meta`() {
        val content = deriveSheetContent(SheetUi(SheetKind.HERO), MockupFixtures.stateDia())

        assertEquals("Resumen del día", content.title)
        assertEquals(4, content.rows.size)
        assertEquals(money("18300"), content.rows[0].amount)
        assertEquals("91% de la meta", content.rows[0].subtitle)
        assertEquals(money("19800"), content.rows[1].amount)
        assertEquals(money("1700"), content.rows[3].amount)
    }

    @Test
    fun `el sheet hero en Semana no trae proyeccion, muestra guion en Ritmo`() {
        val content = deriveSheetContent(SheetUi(SheetKind.HERO), MockupFixtures.stateSemana())

        assertEquals("Resumen del ciclo", content.title)
        val ritmo = content.rows[1]
        assertNull(ritmo.amount)
        assertEquals("—", ritmo.text)
    }

    @Test
    fun `el sheet Efectivo en Dia solo trae las filas de pagos en efectivo`() {
        val content = deriveSheetContent(SheetUi(SheetKind.EFECTIVO), MockupFixtures.stateDia())

        assertEquals("Efectivo", content.title)
        assertEquals(3, content.rows.size)
        assertTrue(content.rows.all { it.amount != null })
        assertEquals("María López Hernández", content.rows[0].title)
    }

    @Test
    fun `el sheet Transferencia en Dia solo trae la fila de pago por transferencia`() {
        val content =
            deriveSheetContent(SheetUi(SheetKind.TRANSFERENCIA), MockupFixtures.stateDia())

        assertEquals(1, content.rows.size)
        assertEquals("Juan Pérez Ramírez", content.rows[0].title)
        assertEquals(money("850"), content.rows[0].amount)
    }

    @Test
    fun `el sheet Efectivo en Semana no tiene desglose por pago, solo el total`() {
        val content = deriveSheetContent(SheetUi(SheetKind.EFECTIVO), MockupFixtures.stateSemana())

        assertTrue(content.rows.isEmpty())
        assertTrue(content.subtitle.contains("146 pagos"))
    }

    @Test
    fun `el sheet Condonado trae cliente y monto de cada condonacion`() {
        val content = deriveSheetContent(SheetUi(SheetKind.CONDONADO), MockupFixtures.stateDia())

        assertEquals("Condonado", content.title)
        assertEquals(3, content.rows.size)
        assertEquals("Ana Ruiz", content.rows[0].title)
        assertEquals(money("600"), content.rows[0].amount)
    }

    // Fix round 1 (Important 2, honestidad): `Forgiveness.motivo` llega VACÍO en producción
    // (auditado: sin fuente real en v27 ni en el backend Go) — el sheet NUNCA debe mostrar un
    // motivo fabricado. `MockupFixtures.condonadoRows()` ya refleja esa realidad (motivo
    // vacío); este test lo hace explícito.
    @Test
    fun `el sheet Condonado con motivo vacio (produccion real) omite la linea de subtitulo`() {
        val content = deriveSheetContent(SheetUi(SheetKind.CONDONADO), MockupFixtures.stateDia())

        assertTrue(content.rows.all { it.subtitle == null })
    }

    // Cobertura hacia adelante: si algún día SÍ hay una fuente real de motivo (columna nueva,
    // enriquecimiento), `deriveSheetContent` debe mostrarlo tal cual — la omisión de arriba es
    // condicional al valor vacío, no un `null` hardcodeado.
    @Test
    fun `el sheet Condonado con motivo no vacio SI lo muestra`() {
        val state = MockupFixtures.stateDia().copy(
            condonadoRows = listOf(
                ForgivenessRowUi(
                    cliente = "Ana Ruiz",
                    motivo = "saldo mínimo · autorizado",
                    amount = money("600")
                )
            )
        )

        val content = deriveSheetContent(SheetUi(SheetKind.CONDONADO), state)

        assertEquals("saldo mínimo · autorizado", content.rows[0].subtitle)
    }

    @Test
    fun `el sheet Visitas trae cliente y nota, sin monto`() {
        val content = deriveSheetContent(SheetUi(SheetKind.VISITAS), MockupFixtures.stateDia())

        assertEquals("Visitas", content.title)
        assertEquals("14 visitas", content.subtitle)
        assertEquals(3, content.rows.size)
        assertEquals("Carlos Vega", content.rows[0].title)
        assertNull(content.rows[0].amount)
    }

    @Test
    fun `el sheet de dia del ciclo lista los pagos individuales del dia pedido`() {
        val content =
            deriveSheetContent(SheetUi(SheetKind.DIA_CICLO, "1"), MockupFixtures.stateSemana())

        assertEquals("mar 4 ago", content.title)
        assertTrue(content.subtitle.contains("46 pagos"))
        // Índice 1 == "mar 4 ago" en la fixture: 3 pagos individuales, cada uno con avatar de
        // iniciales + nombre real + monto (nada truncado, todos listados).
        assertEquals(3, content.rows.size)
        assertEquals("Verónica Castillo Ramos", content.rows[0].title)
        assertEquals(money("1500"), content.rows[0].amount)
        assertTrue(content.rows.all { it.avatar })
        assertTrue(content.rows.all { it.leading != null && it.amount != null })
    }

    @Test
    fun `el sheet de dia del ciclo de un dia sin pagos muestra su total pero lista vacia`() {
        // Día válido (índice 1, "mar 4 ago") pero sin pagos individuales cargados: el sheet
        // conserva título/subtítulo del resumen y cae a un estado vacío sano, sin fabricar filas.
        val state = MockupFixtures.stateSemana().copy(
            dayPayments = List(MockupFixtures.daysSemana().size) { emptyList() }
        )

        val content = deriveSheetContent(SheetUi(SheetKind.DIA_CICLO, "1"), state)

        assertEquals("mar 4 ago", content.title)
        assertTrue(content.subtitle.contains("46 pagos"))
        assertTrue(content.rows.isEmpty())
    }

    @Test
    fun `el sheet de dia del ciclo con indice invalido no truena y cae a un titulo neutro`() {
        val content =
            deriveSheetContent(SheetUi(SheetKind.DIA_CICLO, "99"), MockupFixtures.stateSemana())

        assertEquals("Día", content.title)
        assertEquals("", content.subtitle)
        assertTrue(content.rows.isEmpty())
    }

    @Test
    fun `el sheet de pago trae importe, forma, venta y estado del pago encontrado`() {
        val content = deriveSheetContent(SheetUi(SheetKind.PAGO, "p-ml"), MockupFixtures.stateDia())

        assertEquals("Detalle de pago", content.title)
        assertTrue(content.subtitle.startsWith("María López Hernández"))
        assertEquals(4, content.rows.size)
        assertEquals(money("1200"), content.rows[0].amount)
        assertEquals("Efectivo", content.rows[1].text)
        assertEquals("Muebles Bahía", content.rows[2].text)
        assertEquals("Sincronizado", content.rows[3].text)
    }

    @Test
    fun `el sheet de pago con id desconocido no truena y no trae filas`() {
        val content =
            deriveSheetContent(SheetUi(SheetKind.PAGO, "no-existe"), MockupFixtures.stateDia())

        assertEquals("", content.subtitle)
        assertTrue(content.rows.isEmpty())
    }

    // endregion

    // region — ReportSheets (compose) ----------------------------------------------------

    @Test
    fun `sin sheet abierto no renderiza nada`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                ReportSheets(state = MockupFixtures.stateDia(), onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText("Condonado").assertDoesNotExist()
    }

    @Test
    fun `abrir el sheet Condonado muestra su titulo, subtitulo y filas`() {
        val state = MockupFixtures.stateDia().copy(sheet = SheetUi(SheetKind.CONDONADO))
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                ReportSheets(state = state, onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText("Condonado").assertExists()
        composeTestRule.onNodeWithText("Ana Ruiz").assertExists()
        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("600"))).assertExists()
    }

    @Test
    fun `con masked verdadero los montos del sheet se muestran enmascarados`() {
        val state = MockupFixtures.stateDia(
            masked = true
        ).copy(sheet = SheetUi(SheetKind.CONDONADO))
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                ReportSheets(state = state, onDismiss = {})
            }
        }

        composeTestRule.onAllNodesWithText(MASKED_MONEY)[0].assertExists()
        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("600"))).assertDoesNotExist()
    }

    @Test
    fun `abrir el sheet de dia del ciclo lista los pagos individuales del dia`() {
        val state = MockupFixtures.stateSemana().copy(sheet = SheetUi(SheetKind.DIA_CICLO, "1"))
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                ReportSheets(state = state, onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText("mar 4 ago").assertExists()
        composeTestRule.onNodeWithText("Verónica Castillo Ramos").assertExists()
        composeTestRule.onNodeWithText("Héctor Domínguez León").assertExists()
        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("1500"))).assertExists()
    }

    @Test
    fun `el sheet vacio muestra un mensaje en vez de nada`() {
        val state = MockupFixtures.stateDia().copy(sheet = SheetUi(SheetKind.DIA_CICLO, "99"))
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                ReportSheets(state = state, onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText("Sin datos aún").assertExists()
    }

    // endregion
}
