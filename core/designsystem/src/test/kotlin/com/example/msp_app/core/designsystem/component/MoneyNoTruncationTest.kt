package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val MONEY_TEXT_TAG = "msp_money_no_truncation_test_tag"

/**
 * "Ancho de teléfono típico" (task-10-brief.md) — el mismo `w360dp` que
 * [com.example.msp_app.core.designsystem.screenshot.MspScreenshotTest] fija
 * como qualifier de TODA la matriz de goldens del catálogo.
 */
private val TYPICAL_PHONE_WIDTH = 360.dp

/**
 * Ancho angosto bajo presión real (el mismo que
 * [com.example.msp_app.core.designsystem.screenshot.MspHeroTodayCardScreenshotTest]
 * ya usa para evidenciar visualmente el reflow) — a diferencia de ese golden
 * (a `fontScale` normal), aquí se combina con [LARGE_FONT_SCALE] para forzar
 * el wrap real a más de una línea y verificarlo por layout, no solo por ojo.
 */
private val NARROW_WIDTH_UNDER_PRESSURE = 180.dp
private const val LARGE_FONT_SCALE = 2.0f
private val LARGE_AMOUNT = BigDecimal("1234567.89")

/**
 * Aserción dura de layout (Task 10, spec §5: "terminado es imposible si el
 * dinero se corta en grande"). [MspMoneyText] con un monto grande a
 * `fontScale = 2.0` **nunca se trunca con ellipsis** — ni en un ancho de
 * teléfono típico ([TYPICAL_PHONE_WIDTH], 360dp) ni bajo presión real de
 * espacio ([NARROW_WIDTH_UNDER_PRESSURE], 180dp, donde SÍ tiene que reflowear
 * a más de una línea para caber).
 *
 * La garantía viene de construcción: [MspMoneyText] no pasa `maxLines` ni
 * `TextOverflow.Ellipsis` al `Text` interno (`softWrap = true`, sin límite de
 * líneas) — pero este test la fija como regresión formal, leyendo el
 * `TextLayoutResult` real vía la semantic action `GetTextLayoutResult` (no
 * por píxeles, no por Roborazzi): `isLineEllipsized(línea) == false` para
 * TODAS las líneas es la señal correcta de "no se cortó ningún dígito" —
 * `TextLayoutResult.hasVisualOverflow`/`didOverflowWidth` se investigó y
 * descartó a propósito: por definición (`size.width < multiParagraph.width`)
 * da `true` en cuanto el texto NO usa el ancho completo disponible (p. ej.
 * cabe en una sola línea más angosta que el contenedor), que es la situación
 * NORMAL/deseada, no una señal de corte — usarlo habría hecho fallar el test
 * con dinero perfectamente legible.
 *
 * `@GraphicsMode(NATIVE)` + qualifiers `w360dp-h800dp-xhdpi` (idéntico a
 * [com.example.msp_app.core.designsystem.screenshot.MspScreenshotTest]):
 * verificado en debug que sin Robolectric Native Graphics las métricas de
 * fuente son basura (un `TextLayoutResult` de ~14×35px para un string que
 * mide cientos de px reales) — este test necesita medidas de layout REALES,
 * no solo semántica, así que no puede quedarse con el modo legacy que basta
 * para `component/MspStatusChipTest` y compañía.
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
    fun `el monto hero grande no trunca a escala 2 en ancho tipico de telefono`() {
        val layout = layoutForAmountHero(width = TYPICAL_PHONE_WIDTH)

        assertNoLineEllipsized(layout)
    }

    @Test
    fun `el monto hero grande reflowea a varias lineas sin truncar bajo presion real de espacio`() {
        val layout = layoutForAmountHero(width = NARROW_WIDTH_UNDER_PRESSURE)

        assertTrue(
            "a 180dp/escala 2.0 el monto tiene que reflowear a mas de una linea para caber " +
                "(evidencia de que SI hubo presion de layout, no que cupo por casualidad)",
            layout.lineCount > 1
        )
        assertNoLineEllipsized(layout)
    }

    @Test
    fun `el monto en amountRow tambien reflowea sin truncar a escala 2 en ancho tipico`() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, LARGE_FONT_SCALE)
            ) {
                MspTheme(animateColors = false) {
                    Box(modifier = Modifier.width(TYPICAL_PHONE_WIDTH)) {
                        MspMoneyText(
                            amount = LARGE_AMOUNT,
                            modifier = Modifier.testTag(MONEY_TEXT_TAG)
                        )
                    }
                }
            }
        }

        assertNoLineEllipsized(fetchMoneyTextLayout())
    }

    private fun layoutForAmountHero(width: Dp): TextLayoutResult {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, LARGE_FONT_SCALE)
            ) {
                MspTheme(animateColors = false) {
                    Box(modifier = Modifier.width(width)) {
                        MspMoneyText(
                            amount = LARGE_AMOUNT,
                            style = MspTheme.type.amountHero,
                            modifier = Modifier.testTag(MONEY_TEXT_TAG)
                        )
                    }
                }
            }
        }
        return fetchMoneyTextLayout()
    }

    private fun assertNoLineEllipsized(layout: TextLayoutResult) {
        for (line in 0 until layout.lineCount) {
            assertFalse(
                "la linea $line no debe terminar en ellipsis (digitos truncados)",
                layout.isLineEllipsized(line)
            )
        }
    }

    private fun fetchMoneyTextLayout(): TextLayoutResult {
        val layoutResults = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNodeWithTag(MONEY_TEXT_TAG)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layoutResults) }
        return layoutResults.first()
    }
}
