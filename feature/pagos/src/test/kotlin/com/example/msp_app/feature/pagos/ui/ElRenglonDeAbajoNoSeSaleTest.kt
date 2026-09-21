package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.ui.components.CONTACTO_EN_LINEA_TAG
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
import com.example.msp_app.feature.pagos.ui.components.SEGMENTO_DEL_RENGLON_TAG
import com.example.msp_app.feature.pagos.ui.components.SEPARADOR_DEL_RENGLON_TAG
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El renglón de abajo no se sale de la fila y el punto medio nunca queda al
 * borde de un renglón.** Dos cosas que antes sólo defendían el KDoc de
 * `RenglonPorSegmentos` y el ojo sobre los goldens.
 *
 * Todo se mide: bordes de nodos en la raíz y renglones del `TextLayoutResult`,
 * con la letra creciendo de verdad y `GraphicsMode.NATIVE` (sin él
 * Robolectric no mide texto y nada se parte).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class ElRenglonDeAbajoNoSeSaleTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * **Una palabra irrompible más ancha que todo el renglón.**
     *
     * Un segmento sin espacios ni guiones no tiene dónde partirse por
     * palabras. La duda era si el `Text` sin `maxLines` se desborda a lo
     * ancho, más allá del `maxWidth`, o si el `LineBreaker` lo parte por
     * caracteres. Esto lo decide midiendo: ningún nodo del renglón se sale del
     * ancho de la fila, ningún renglón del texto pinta más allá del ancho de
     * su nodo, y la palabra llega entera y sin elipsis.
     */
    @Test
    fun `una palabra irrompible mas ancha que el renglon no se sale de la fila`() {
        fila(FontSizeLevel.MUY_GRANDE, ABONO.copy(cuenta = IRROMPIBLE))

        val fila = composeTestRule.onNodeWithTagSinFusionar(CONTACTO_EN_LINEA_TAG).single()
        val segmentos = colocados(SEGMENTO_DEL_RENGLON_TAG)
        segmentos.forEach { nodo ->
            assertTrue(
                "el segmento \"${textoDe(nodo)}\" termina en ${nodo.boundsInRoot.right} y la " +
                    "fila en ${fila.boundsInRoot.right}: se sale de la fila",
                nodo.boundsInRoot.right <= fila.boundsInRoot.right + TOLERANCIA_PX
            )
        }

        val cuenta = segmentos.single { textoDe(it) == IRROMPIBLE }
        val layout = layoutDe(cuenta)
        assertTrue(
            "la palabra irrompible no se partió: sin eso esta prueba no mide el caso que dice",
            layout.lineCount > 1
        )
        (0 until layout.lineCount).forEach { renglon ->
            assertFalse(
                "el renglón $renglon de la cuenta lleva elipsis",
                layout.isLineEllipsized(renglon)
            )
            assertTrue(
                "el renglón $renglon de la cuenta pinta hasta ${layout.getLineRight(renglon)} px " +
                    "y su nodo mide ${layout.size.width} px: se desborda a lo ancho",
                layout.getLineRight(renglon) <= layout.size.width + TOLERANCIA_PX
            )
        }
        assertEquals(
            "la palabra no llega entera",
            IRROMPIBLE.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
        )
    }

    /**
     * **El punto medio vive ENTRE dos segmentos del mismo renglón, nunca en
     * su borde.** Con la cuenta larga y el cobrador de producción, a `GRANDE`
     * el renglón baja segmentos; en cada renglón que resulta, el primero y el
     * último nodo tienen que ser segmentos. Un `·` al final de un renglón o
     * abriendo el siguiente se lee roto.
     */
    @Test
    fun `ningun separador queda al borde de un renglon`() {
        fila(FontSizeLevel.GRANDE, ABONO)

        val nodos = (colocados(SEGMENTO_DEL_RENGLON_TAG) + colocados(SEPARADOR_DEL_RENGLON_TAG))
        val renglones = nodos.groupBy { it.boundsInRoot.top.toInt() / MISMO_RENGLON_PX }
            .toSortedMap()
            .values
            .map { renglon -> renglon.sortedBy { it.boundsInRoot.left } }
        assertTrue(
            "el renglón de abajo no bajó ningún segmento: la prueba no ve el caso que dice",
            renglones.size > 1
        )
        renglones.forEachIndexed { i, renglon ->
            assertFalse(
                "el renglón $i empieza con un separador",
                renglon.first().esSeparador()
            )
            assertFalse(
                "el renglón $i termina con un separador",
                renglon.last().esSeparador()
            )
        }
    }

    // --- Montaje y lecturas ---------------------------------------------------

    private fun fila(nivel: FontSizeLevel, contacto: ContactoDeCobranza) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    ContactoEnLinea(contacto = contacto)
                }
            }
        }
    }

    private fun ComposeContentTestRule.onNodeWithTagSinFusionar(tag: String): List<SemanticsNode> =
        onAllNodes(
            hasTestTag(tag),
            useUnmergedTree = true
        ).fetchSemanticsNodes()

    /** Sólo los nodos que el layout de verdad colocó. */
    private fun colocados(tag: String): List<SemanticsNode> =
        composeTestRule.onNodeWithTagSinFusionar(tag).filter { it.layoutInfo.isPlaced }

    private fun SemanticsNode.esSeparador(): Boolean =
        config.getOrNull(SemanticsProperties.TestTag) == SEPARADOR_DEL_RENGLON_TAG

    private fun textoDe(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    private companion object {

        /** Medio dp en `xhdpi`, por redondeo a píxel. */
        const val TOLERANCIA_PX = 1f

        /** Nodos cuyo tope cae en la misma franja de estos píxeles son un renglón. */
        const val MISMO_RENGLON_PX = 8

        /** Sin espacios ni guiones: no hay dónde partir por palabras. */
        const val IRROMPIBLE = "RECAMARAKINGSIZECHOCOLATEMATRIMONIAL"

        val ABONO = ContactoDeCobranza(
            fecha = Instant.parse("2026-02-18T20:30:00Z"),
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = Money.of(BigDecimal("400.00")),
            tipo = TipoDeContacto.COBRO,
            metodo = MetodoDeCobro.TRANSFERENCIA,
            cobrador = "RUTA 25 - NOE CORTERO",
            cuenta = "Recámara Cántaro King Size"
        )
    }
}
