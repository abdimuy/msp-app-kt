package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.ui.components.CONTROL_SEGMENTADO_TAG
import com.example.msp_app.feature.pagos.ui.components.ETIQUETA_DEL_SEGMENTO_TAG
import com.example.msp_app.feature.pagos.ui.components.FILTRO_TAG
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Ronda de arreglo 1, Task 4 — a letra grande el control pasa a rejilla, no
 * se rompe.**
 *
 * `pagos_bitacora_light_2_0.png` mostraba el defecto antes de este archivo:
 * a `MUY_GRANDE` en 360dp el control perdía margen y borde, y "Promesas 1"
 * quedaba como "Pron", sin número, porque `ControlSegmentado` dejaba de
 * repartir el ancho y montaba la fila entera dentro de un
 * `horizontalScroll` — la `Surface` ya no llenaba la pantalla, y el golden
 * sólo retrataba la posición inicial del scroll.
 *
 * **Pinta la pantalla real, no el control suelto.** Un primer intento montó
 * sólo `FiltrosDeContacto` dentro de un `Box` con el margen de
 * `BitacoraScreen` copiado a mano, y NO reprodujo el defecto: la aritmética
 * de márgenes a mano no es la pantalla real, y esa prueba habría dado verde
 * con el defecto todavía adentro. Aquí se monta [BitacoraContent] con la
 * MISMA fixture que usa `BitacoraMatrixScreenshotTest` — el mismo camino que
 * produjo el golden roto—, así que lo que se mide es exactamente lo que el
 * cobrador ve.
 *
 * Mide, como [ElRenglonDeAbajoNoSeSaleTest]: bordes de nodos con
 * `boundsInRoot` y el `TextLayoutResult` del rótulo, no la apariencia. Un dato
 * a medias es un dato falso — la opción que se esconde es justo la que el
 * conteo existe para avisar.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class ElControlSegmentadoNoSeRompeALetraGrandeTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pinta(nivel: FontSizeLevel) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    BitacoraContent(
                        state = BitacoraUiState(cargando = false, bitacora = BITACORA),
                        onAtras = {}
                    )
                }
            }
        }
    }

    /**
     * **La prueba central de la Ronda de arreglo 1.** A `MUY_GRANDE`, las
     * cuatro etiquetas Y sus cuatro conteos quedan presentes y completos, y
     * ningún segmento queda fuera del borde de la PANTALLA.
     *
     * **Contra la raíz, no contra el propio `CONTROL_SEGMENTADO_TAG`.** Con
     * `horizontalScroll`, la `Surface` de adentro mide su ancho NATURAL —más
     * ancho que la pantalla— y todos sus segmentos caben dentro de ESE ancho
     * por definición: comparar un segmento contra los bordes de su propia
     * `Surface` nunca ve el defecto. Lo que se sale es el control mismo
     * respecto de la pantalla, así que la raíz —que sí conserva el ancho del
     * dispositivo aunque algo adentro se desplace— es la única referencia que
     * lo detecta.
     */
    @Test
    fun `a MUY_GRANDE las cuatro etiquetas y conteos quedan completos y dentro de la pantalla`() {
        pinta(FontSizeLevel.MUY_GRANDE)

        val pantalla = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val conteos = FiltroDeContactos.conteos(BITACORA.contactos)

        FiltroDeContactos.entries.forEach { filtro ->
            val segmento = composeTestRule.onNodeWithTag(FILTRO_TAG + filtro.name)
                .fetchSemanticsNode()
            val bordes = segmento.boundsInRoot
            assertTrue(
                "el segmento ${filtro.etiqueta} empieza en ${bordes.left}, antes de la pantalla " +
                    "(${pantalla.left})",
                bordes.left >= pantalla.left - TOLERANCIA_PX
            )
            assertTrue(
                "el segmento ${filtro.etiqueta} termina en ${bordes.right} y la pantalla en " +
                    "${pantalla.right}: se sale de la pantalla",
                bordes.right <= pantalla.right + TOLERANCIA_PX
            )
            composeTestRule.onNodeWithTag(FILTRO_TAG + filtro.name)
                .assertTextContains(filtro.etiqueta)
            composeTestRule.onNodeWithTag(FILTRO_TAG + filtro.name)
                .assertTextContains((conteos[filtro] ?: 0).toString())
        }

        // El control mismo tampoco se sale — cobra el borde y los márgenes
        // del lado derecho, que es justo lo que el scroll dejaba fuera.
        val control = composeTestRule.onNodeWithTag(CONTROL_SEGMENTADO_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "el control termina en ${control.right} y la pantalla en ${pantalla.right}: " +
                "se sale de la pantalla",
            control.right <= pantalla.right + TOLERANCIA_PX
        )

        // Ningún rótulo lleva elipsis — la mitad del defecto que el
        // `boundsInRoot` de arriba no ve: "Promesas" ya cabía dentro del
        // borde del control cortado a la mitad, sin puntos suspensivos. Con
        // `overflow = TextOverflow.Ellipsis` puesto, Compose no puede pintar
        // el texto más allá de lo que mide su propio nodo sin marcarlo
        // elidido — así que junto con los bordes de arriba (que ya prueban
        // que ese nodo no se sale del control), "sin elipsis" cierra el caso:
        // el rótulo llegó completo Y dentro del control.
        etiquetasColocadas().forEach { etiqueta ->
            val layout = layoutDe(etiqueta)
            (0 until layout.lineCount).forEach { renglon ->
                assertFalse(
                    "el rótulo \"${textoDe(etiqueta)}\" lleva elipsis en el renglón $renglon",
                    layout.isLineEllipsized(renglon)
                )
            }
        }
    }

    // --- Lecturas ---------------------------------------------------------

    private fun etiquetasColocadas(): List<SemanticsNode> = composeTestRule.onAllNodes(
        hasTestTag(ETIQUETA_DEL_SEGMENTO_TAG),
        useUnmergedTree = true
    ).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }

    private fun textoDe(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    private companion object {
        /** Medio dp en `xhdpi`, por redondeo a píxel — igual que [ElRenglonDeAbajoNoSeSaleTest]. */
        const val TOLERANCIA_PX = 1f

        /** La MISMA fixture que produce `pagos_bitacora_*` — 5 contactos: 3 cobros, 2 visitas, 1 promesa. */
        val BITACORA: BitacoraCompleta = PagosFixtures.detalleCliente().let { detalle ->
            BitacoraCompleta(
                clienteId = detalle.clienteId,
                nombre = detalle.nombre,
                direccion = detalle.direccion,
                contactos = detalle.contactos
            )
        }
    }
}
