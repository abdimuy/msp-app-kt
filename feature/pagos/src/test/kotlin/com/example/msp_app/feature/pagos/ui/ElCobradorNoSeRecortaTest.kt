package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
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
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
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
 * **El nombre del cobrador sale entero, a las tres escalas.**
 *
 * Principio 9 de la rama: *un dato a medias es un dato falso; antes de
 * truncar, apilar*. El renglón de abajo era un solo `Text` con `maxLines` y
 * elipsis, y a `MUY_GRANDE` el cobrador salía `Maris…`. El caso real que lo
 * originó es `RUTA 25 - NOE CORTERO` junto a una cuenta larga: con eso el
 * nombre se partía o se recortaba desde `GRANDE`.
 *
 * Se afirma lo que el ojo ve y no sólo que el texto llegue al nodo: ningún
 * renglón del nombre lleva elipsis y el último termina después de la última
 * letra. Y si el nombre se parte, tiene que haber bajado antes a un renglón
 * propio: partirse sólo se vale cuando ni solo cabe en todo el ancho (a
 * `MUY_GRANDE` en 360 dp, `RUTA 25 - NOE CORTERO` no cabe y se parte en
 * palabras).
 *
 * `GraphicsMode.NATIVE` por lo mismo que `LaFilaCaeSobreUnaSolaLineaBaseTest`:
 * sin él Robolectric no mide texto y nada se parte nunca.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class ElCobradorNoSeRecortaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a escala normal el cobrador sale entero`() = entero(FontSizeLevel.NORMAL)

    @Test
    fun `a escala grande el cobrador sale entero`() = entero(FontSizeLevel.GRANDE)

    @Test
    fun `a escala muy grande el cobrador sale entero`() = entero(FontSizeLevel.MUY_GRANDE)

    private fun entero(nivel: FontSizeLevel) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    ContactoEnLinea(contacto = ABONO)
                }
            }
        }

        val nodos = composeTestRule
            .onAllNodes(hasText(COBRADOR, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("el cobrador tiene que estar en un solo nodo de texto", 1, nodos.size)

        val nodo = nodos.single()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val layout = resultados.single()
        val texto = layout.layoutInput.text.text
        val inicio = texto.indexOf(COBRADOR)
        val fin = inicio + COBRADOR.length
        val primero = layout.getLineForOffset(inicio)
        val ultimo = layout.getLineForOffset(fin - 1)

        (primero..ultimo).forEach { renglon ->
            assertFalse(
                "a ${nivel.name} el cobrador lleva elipsis en su renglón $renglon: \"$texto\"",
                layout.isLineEllipsized(renglon)
            )
        }
        assertTrue(
            "a ${nivel.name} el cobrador no se pinta completo: \"$texto\"",
            layout.getLineEnd(ultimo, visibleEnd = true) >= fin
        )
        // Partirse sólo se vale si ni solo en todo el ancho cabe: entonces
        // tiene que haber bajado a un renglón PROPIO, pegado al borde de la
        // pista. Un cobrador partido a media fila, al lado de la cuenta, es el
        // defecto que esto cierra.
        if (primero != ultimo) {
            assertEquals(
                "a ${nivel.name} el cobrador se partió empezando a media línea, detrás de " +
                    "otro segmento: \"$texto\"",
                layout.getLineStart(primero),
                inicio
            )
            assertEquals(
                "a ${nivel.name} el cobrador se partió sin haber bajado antes a un renglón " +
                    "propio: \"$texto\"",
                izquierdaDeLaPista(),
                nodo.boundsInRoot.left,
                1f
            )
        }
    }

    /** El borde izquierdo de la pista "qué pasó": donde arranca el título. */
    private fun izquierdaDeLaPista(): Float = composeTestRule
        .onAllNodes(hasText("Abono"), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .single()
        .boundsInRoot.left

    private companion object {

        /** El nombre tal como llega de producción. No se normaliza. */
        const val COBRADOR = "RUTA 25 - NOE CORTERO"

        val ABONO = ContactoDeCobranza(
            id = "abono-recamara-cantaro",
            fecha = Instant.parse("2026-02-18T20:30:00Z"),
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = Money.of(BigDecimal("400.00")),
            tipo = TipoDeContacto.COBRO,
            metodo = MetodoDeCobro.TRANSFERENCIA,
            cobrador = COBRADOR,
            cuenta = "Recámara Cántaro King Size",
            ubicacion = UbicacionDelCobro(lat = 19.0433, lng = -98.1981)
        )
    }
}
