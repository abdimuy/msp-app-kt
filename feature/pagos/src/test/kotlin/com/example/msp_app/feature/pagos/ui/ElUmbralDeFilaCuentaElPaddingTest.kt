package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.CONTROL_SEGMENTADO_TAG
import com.example.msp_app.feature.pagos.ui.components.ControlSegmentado
import com.example.msp_app.feature.pagos.ui.components.ETIQUETA_DEL_SEGMENTO_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El umbral fila/rejilla mide contra el ancho útil real, no contra el
 * ancho natural sin marco — el defecto de los 8 dp.**
 *
 * `ControlSegmentado` decide "fila" comparando el ancho NATURAL de cada
 * opción (medido con [ContenidoDelSegmento], sin el `.padding(horizontal =
 * MspTheme.spacing.xs)` que [Segmento] le pone a cada lado en la pasada real)
 * contra la porción que le tocaría en una fila. Sin sumar esos `2 × xs` (8dp:
 * `MspTheme.spacing.xs` = 4dp) al ancho natural antes de comparar, una
 * etiqueta cuyo ancho cae entre `porción − 8dp` y `porción` se juzga "cabe en
 * fila" aquí, pero en la pasada real sólo dispone de `porción − 8dp` para su
 * texto — se comprime y su rótulo baja a dos renglones DENTRO de la fila, que
 * es justo el recorte silencioso que la Ronda de arreglo 1 (Task 4) vino a
 * evitar.
 *
 * ## La reproducción, medida — no adivinada
 *
 * Con dos opciones ("Filtro" y una etiqueta de `N` "M" repetidas) a
 * `w220dp-h800dp-mdpi`, `N=10` es el punto exacto de la frontera: con el
 * defecto sembrado (revertido el `+ 2 × xs`, confirmado corriendo este mismo
 * archivo con el cambio deshecho y `git diff` limpio después), el control
 * mide 50dp de alto —una sola fila— y la etiqueta larga cae en DOS
 * renglones (`lineCount == 2`). Con el arreglo puesto, el control mide 102dp
 * —pasó a rejilla, la fila y la rejilla NO caben en el mismo alto— y la
 * etiqueta vuelve a un solo renglón. `N=8` y `N=9` (que sí caben con
 * holgura) y `N=11`/`N=12` (que ya no caben ni con el arreglo) se dejan como
 * control: nunca se comprimen en ningún lado del arreglo.
 *
 * `ControlSegmentado` es `internal`: este test vive en el mismo módulo y lo
 * ejercita directamente con `T = String`, sin pasar por `FiltroDeContactos`
 * —cuyas cuatro etiquetas reales nunca caen en esta banda de 8dp, así que no
 * la habrían probado.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w220dp-h800dp-mdpi")
class ElUmbralDeFilaCuentaElPaddingTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var etiquetaLarga by mutableStateOf("M".repeat(N_HOLGADO))

    @Test
    fun `ninguna etiqueta baja a dos renglones, caiga en fila o en rejilla`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                ControlSegmentado(
                    opciones = listOf(OPCION_CORTA, etiquetaLarga),
                    seleccionado = OPCION_CORTA,
                    conteos = mapOf(OPCION_CORTA to 0, etiquetaLarga to 0),
                    etiquetaDe = { it },
                    tagDe = { "seg_$it" },
                    onElegir = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        for (n in N_HOLGADO..N_APRETADO) {
            etiquetaLarga = "M".repeat(n)
            composeTestRule.waitForIdle()
            etiquetasColocadas().forEach { nodo ->
                val layout = layoutDe(nodo)
                assertEquals(
                    "con N=$n el rótulo bajó a ${layout.lineCount} renglones dentro de su acomodo",
                    1,
                    layout.lineCount
                )
            }
        }
    }

    /**
     * **El punto exacto de la frontera (`N=10`), con su rojo sembrado.**
     * Repite la medición de arriba sólo para `N_FRONTERA` y además cobra que
     * el control SÍ decidió rejilla (102dp, dos renglones reales) y no una
     * fila comprimida (50dp) — la mitad del defecto que "ningún rótulo baja a
     * dos renglones" por sí sola no distingue: un control que decidiera fila
     * y por pura suerte no comprimiera el texto pasaría esa prueba sin cerrar
     * el defecto real.
     */
    @Test
    fun `en la frontera el control pasa a rejilla y no comprime la etiqueta`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                ControlSegmentado(
                    opciones = listOf(OPCION_CORTA, etiquetaLarga),
                    seleccionado = OPCION_CORTA,
                    conteos = mapOf(OPCION_CORTA to 0, etiquetaLarga to 0),
                    etiquetaDe = { it },
                    tagDe = { "seg_$it" },
                    onElegir = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        etiquetaLarga = "M".repeat(N_FRONTERA)
        composeTestRule.waitForIdle()

        val altoDelControl = composeTestRule.onNodeWithTag(CONTROL_SEGMENTADO_TAG)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "el control quedó en $altoDelControl, una sola fila: la etiqueta de $N_FRONTERA " +
                "letras no cabía y debió pasar a rejilla",
            altoDelControl > ALTO_DE_UNA_SOLA_FILA
        )
        etiquetasColocadas().forEach { nodo ->
            assertEquals(
                "el rótulo de la frontera se comprimió a pesar de la rejilla",
                1,
                layoutDe(nodo).lineCount
            )
        }
    }

    private fun etiquetasColocadas(): List<SemanticsNode> = composeTestRule.onAllNodes(
        hasTestTag(ETIQUETA_DEL_SEGMENTO_TAG),
        useUnmergedTree = true
    ).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    private companion object {
        const val OPCION_CORTA = "Filtro"

        /** Cabe con holgura en cualquiera de los dos lados del arreglo. */
        const val N_HOLGADO = 8

        /** La frontera exacta: medida corriendo este archivo con y sin el arreglo. */
        const val N_FRONTERA = 10

        /** Ya no cabe ni con el arreglo puesto — control de que la rejilla sigue funcionando. */
        const val N_APRETADO = 12

        /** 50dp a `mdpi` (densidad 1) — el alto de una sola fila, sin escalar. */
        const val ALTO_DE_UNA_SOLA_FILA = 50f
    }
}
