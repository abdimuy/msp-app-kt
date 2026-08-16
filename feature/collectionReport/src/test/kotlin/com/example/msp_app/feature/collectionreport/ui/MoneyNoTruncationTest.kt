package com.example.msp_app.feature.collectionreport.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.domain.model.Money
import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val LARGE_AMOUNT = Money.of(BigDecimal("1234567.89"))
private val LARGE_AMOUNT_TEXT = formatMoneyMxn(LARGE_AMOUNT.amount)
private const val LARGE_FONT_SCALE = 2.0f

/**
 * Aserción dura (Task 11, task-11-brief.md "MoneyNoTruncationTest ... el dinero reflowea, no se
 * trunca"): montos GRANDES (`$1,234,567.89`) en el tablero REAL (hero/tiles/fila de detalle),
 * no en [com.example.msp_app.core.designsystem.component.MspMoneyText] aislado — ese primitivo
 * ya tiene su propia garantía formal en `:core:designsystem`
 * ([com.example.msp_app.core.designsystem.component.MoneyNoTruncationTest]); este test verifica
 * la INTEGRACIÓN: que ningún `Modifier` que el piloto le pone alrededor (padding fijo del hero,
 * `weight(1f)` de `DuoTiles`, ancho de fila de `DetailList`) reintroduce un corte al combinar
 * `fontScale = 2.0` con una cifra de 7 dígitos — el caso real que un cobrador con mucha cartera
 * puede llegar a ver.
 *
 * Mismo método del gate de `:core:designsystem` (`GetTextLayoutResult` vía semántica, NO
 * píxeles/Roborazzi — `hasVisualOverflow`/`didOverflowWidth` da falsos positivos con texto que
 * cabe en una línea más angosta que su contenedor, ver KDoc de esa clase): localiza el nodo por
 * el texto formateado EXACTO ([LARGE_AMOUNT_TEXT], determinista vía `formatMoneyMxn`) y lee
 * `isLineEllipsized` de cada línea.
 *
 * `@GraphicsMode(NATIVE)` + qualifiers `w360dp-h800dp-xhdpi` — mismo motivo que el test gemelo
 * de `:core:designsystem`: sin Native Graphics las métricas de `TextLayoutResult` no son reales.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class MoneyNoTruncationTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el monto hero grande no trunca a escala 2 en el tablero real`() {
        val base = MockupFixtures.stateDia()
        val state = base.copy(hero = base.hero.copy(monto = LARGE_AMOUNT))

        renderTablero(state)

        assertMoneyNotTruncated()
    }

    @Test
    fun `el monto del tile Efectivo grande no trunca a escala 2 en el tablero real`() {
        val base = MockupFixtures.stateDia()
        val state = base.copy(efectivo = base.efectivo.copy(amount = LARGE_AMOUNT))

        renderTablero(state)

        assertMoneyNotTruncated()
    }

    @Test
    fun `el monto del tile Transferencia grande no trunca a escala 2 en el tablero real`() {
        val base = MockupFixtures.stateDia()
        val state = base.copy(transferencia = base.transferencia.copy(amount = LARGE_AMOUNT))

        renderTablero(state)

        assertMoneyNotTruncated()
    }

    @Test
    fun `el monto de una fila de pago del detalle no trunca a escala 2 en el tablero real`() {
        val base = MockupFixtures.stateDia()
        val payments = (base.detail as DetailUi.Payments).rows
        val withLargeFirstRow = payments.toMutableList().also { rows ->
            rows[0] = rows[0].copy(amount = LARGE_AMOUNT)
        }
        val state = base.copy(detail = DetailUi.Payments(withLargeFirstRow))

        renderTablero(state)

        assertMoneyNotTruncated()
    }

    @Test
    fun `el monto de una fila del resumen Semana no trunca a escala 2 en el tablero real`() {
        val base = MockupFixtures.stateSemana()
        val days = (base.detail as DetailUi.Days).rows
        val withLargeFirstRow = days.toMutableList().also { rows ->
            rows[0] = rows[0].copy(amount = LARGE_AMOUNT)
        }
        val state = base.copy(detail = DetailUi.Days(withLargeFirstRow))

        renderTablero(state)

        // El tablero es una `LazyColumn` (ver el KDoc de [CollectionReportContent]): el resumen
        // por día vive muy por debajo del pliegue — a `fontScale = 2.0`, más todavía — así que
        // el renglón NI SIQUIERA está compuesto hasta que la lista lo trae. Sin este scroll la
        // búsqueda por texto no encontraría nada. `hasScrollToIndexAction()` identifica a la
        // lista perezosa sin ambigüedad (la tira de días, `horizontalScroll`, no la expone).
        composeTestRule
            .onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText(LARGE_AMOUNT_TEXT))

        assertMoneyNotTruncated()
    }

    @Suppress("LongParameterList")
    private fun renderTablero(state: CollectionReportUiState) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, LARGE_FONT_SCALE)
            ) {
                MspTheme(animateColors = false) {
                    CollectionReportContent(
                        state = state,
                        onMenuClick = {},
                        onPrivacyToggle = {},
                        onThemeToggle = {},
                        onPeriodSelect = {},
                        onHeroClick = {},
                        onSparkBarClick = {},
                        onEfectivoClick = {},
                        onTransferenciaClick = {},
                        onCondonadoClick = {},
                        onVisitasClick = {},
                        onSortSelect = {},
                        onPaymentRowClick = {},
                        onDayRowClick = {}
                    )
                }
            }
        }
    }

    private fun assertMoneyNotTruncated() {
        val layoutResults = mutableListOf<TextLayoutResult>()
        // `useUnmergedTree = true` (fix de dispositivo, Task 1): las filas de pago/día son
        // `clickable` — eso fusiona TODOS sus Text descendientes (nombre, subtítulo, monto,
        // pill, saldo...) en un solo nodo de semántica mergeado. Sin desmergear, `onNode`
        // resuelve ese nodo COMPUESTO y `GetTextLayoutResult` entrega el layout del PRIMER
        // Text hijo en orden de árbol — que ya NO es necesariamente el monto (p. ej. desde que
        // el tile de método reemplazó el avatar de iniciales, el primer Text pasó a ser el
        // NOMBRE del cliente) — un falso positivo/negativo de la aserción, no del layout real.
        // Con el árbol sin fusionar, la query encuentra el `Text` HOJA que realmente contiene
        // [LARGE_AMOUNT_TEXT], sin ambigüedad.
        composeTestRule
            .onNode(hasText(LARGE_AMOUNT_TEXT), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layoutResults) }
        val layout = layoutResults.first()
        for (line in 0 until layout.lineCount) {
            assertFalse(
                "la linea $line de '$LARGE_AMOUNT_TEXT' no debe terminar en ellipsis " +
                    "(digitos truncados) en el tablero real",
                layout.isLineEllipsized(line)
            )
        }
    }
}
