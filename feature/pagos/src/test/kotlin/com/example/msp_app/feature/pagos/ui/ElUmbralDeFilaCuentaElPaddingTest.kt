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
import androidx.compose.ui.text.TextLayoutResult
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.ControlSegmentado
import com.example.msp_app.feature.pagos.ui.components.ETIQUETA_DEL_SEGMENTO_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Ningún rótulo del control segmentado baja a dos renglones, apriete lo que
 * apriete el ancho.**
 *
 * ## Lo que este archivo protegía antes, y ya no existe
 *
 * Hasta el **2026-09-22** `ControlSegmentado` medía cada opción y, si los
 * rótulos no cabían repartiendo el ancho, pasaba a una **rejilla de dos
 * renglones**. Ese umbral tenía un defecto de 8 dp: comparaba el ancho NATURAL
 * de la opción (sin el `.padding(horizontal = xs)` que el segmento le pone a
 * cada lado) contra la porción que le tocaría en una fila, así que una etiqueta
 * entre `porción − 8dp` y `porción` se juzgaba "cabe" y en la pasada real se
 * comprimía a dos renglones dentro de la fila. `N=10` a `w220dp-h800dp-mdpi`
 * era el punto exacto de esa frontera, medido con el defecto sembrado y luego
 * revertido.
 *
 * **La rejilla se retiró** por decisión del dueño: con cuatro opciones el 2×2 se
 * comía un bloque entero de pantalla antes de la primera tarjeta, y el control
 * pasó a una sola fila deslizable (`LazyRow`). Con ella se fue el umbral, y con
 * el umbral la prueba de su frontera —«en la frontera el control pasa a rejilla
 * y no comprime la etiqueta»—, que afirmaba que el control crecía por encima de
 * 50 dp y ahora no puede crecer nunca.
 *
 * ## Lo que queda, y su límite
 *
 * Queda el barrido de `N=8..12`: **ningún rótulo baja a dos renglones**. Hoy lo
 * garantiza `maxLines = 1` en el rótulo, así que es una prueba **delgada** — una
 * sola línea de producción la sostiene entera. Se conserva porque esa línea sí
 * se puede perder en una edición, y porque el barrido cubre anchos que ninguna
 * etiqueta real alcanza.
 *
 * Lo que de verdad cobra el acabado del control a letra grande —rótulo y conteo
 * completos, sin elipsis y dentro de la pantalla— es
 * [ElControlSegmentadoNoSeRompeALetraGrandeTest], sobre la pantalla real.
 *
 * `ControlSegmentado` es `internal`: este test vive en el mismo módulo y lo
 * ejercita directamente con `T = String`, sin pasar por `FiltroDeContactos`
 * —cuyas cuatro etiquetas reales nunca llegan a estos anchos.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w220dp-h800dp-mdpi")
class ElUmbralDeFilaCuentaElPaddingTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var etiquetaLarga by mutableStateOf("M".repeat(N_HOLGADO))

    @Test
    fun `ninguna etiqueta baja a dos renglones, apriete lo que apriete el ancho`() {
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

        /** Cabe con holgura. */
        const val N_HOLGADO = 8

        /** Un ancho que ninguna etiqueta real alcanza: el rótulo tampoco se parte ahí. */
        const val N_APRETADO = 12
    }
}
