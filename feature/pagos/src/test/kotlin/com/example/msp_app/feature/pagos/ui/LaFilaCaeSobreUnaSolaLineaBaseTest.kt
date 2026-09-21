package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.example.msp_app.feature.pagos.ui.components.PIN_DEL_CONTACTO_TAG
import com.example.msp_app.feature.pagos.ui.components.PUNTO_DEL_ESTADO_TAG
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **La fila cae sobre UNA línea base: la primera del título.**
 *
 * El defecto que esto cierra (`task-3-brief.md`): la hora, el punto de estado y
 * el pin se empujaban cada uno con su propio `padding(top = …)` —`sm + xs`,
 * `md × escala` y `sm`—, tres números cocinados por separado que perseguían a
 * mano la primera línea del título. En cuanto la letra crecía o el título se iba
 * a dos renglones, los tres se despegaban, y un golden no lo demuestra: una foto
 * se ve "más o menos bien" hasta que alguien pone las dos lado a lado. Esto lo
 * **mide**.
 *
 * ## La regla que se cobra, en dos formas
 *
 * - Lo que tiene letra —**el día** y **el importe**— comparte línea base con el
 *   título: su `FirstBaseline`, llevado a coordenadas de la raíz, es el mismo.
 * - Lo que no tiene letra —**el punto** y **el pin**— se centra en el renglón
 *   de esa misma línea: su centro vertical es el centro de la primera línea del
 *   título, que se lee del `TextLayoutResult` del propio título y no se calcula.
 *
 * ## Por qué la letra crece de verdad aquí
 *
 * [MspTheme] no lee [LocalFontSizeLevel]; quien agranda la letra en la app es
 * el `fontScale` de la raíz de composición. Si el test sólo subiera el nivel,
 * el título seguiría del mismo alto y el defecto —que es justamente que el
 * título crece y los paddings no— nunca aparecería. Por eso [fila] pone el
 * `fontScale` igual que los goldens (`PagosScreenshotTest.capture`).
 *
 * ## Por qué `GraphicsMode.NATIVE`
 *
 * Sin él, Robolectric no mide texto de verdad: toda etiqueta cabe en un
 * renglón y el caso de los dos renglones —el que más importa— no se puede
 * montar. Con el modo nativo el texto se mide con la fuente real, igual que
 * en los goldens.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class LaFilaCaeSobreUnaSolaLineaBaseTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a escala normal los cuatro caen en la linea del titulo`() =
        cuatroEnLinea(FontSizeLevel.NORMAL, ABONO)

    @Test
    fun `a escala grande los cuatro caen en la linea del titulo`() =
        cuatroEnLinea(FontSizeLevel.GRANDE, ABONO)

    @Test
    fun `a escala muy grande los cuatro caen en la linea del titulo`() =
        cuatroEnLinea(FontSizeLevel.MUY_GRANDE, ABONO)

    /**
     * **Con el título en dos renglones, los cuatro se quedan en el PRIMERO.**
     *
     * Es el caso que los paddings nunca podían seguir: el bloque del título
     * dobla su alto y cualquier cosa centrada contra el bloque —o empujada
     * desde el borde de la fila— baja a media altura, entre los dos renglones.
     * Se exige además que el título ocupe de verdad dos renglones: sin eso,
     * una etiqueta que cupiera en uno dejaría este test en verde sin probar
     * nada.
     */
    @Test
    fun `con el titulo en dos renglones los cuatro caen en el primero`() =
        cuatroEnLinea(FontSizeLevel.NORMAL, ABONO_DE_TITULO_LARGO, renglones = 2)

    /** Lo mismo a la escala donde el título se parte con menos texto. */
    @Test
    fun `con el titulo en dos renglones a muy grande los cuatro caen en el primero`() =
        cuatroEnLinea(FontSizeLevel.MUY_GRANDE, ABONO_DE_TITULO_LARGO, renglones = 2)

    // --- La medición ----------------------------------------------------------

    private fun cuatroEnLinea(
        nivel: FontSizeLevel,
        contacto: ContactoDeCobranza,
        renglones: Int = 1
    ) {
        fila(nivel, contacto)

        val titulo = composeTestRule.onNodeWithText(contacto.etiqueta, useUnmergedTree = true)
        val layout = layoutDe(titulo)
        assertEquals(
            "el título de prueba debía ocupar $renglones renglón(es) y ocupó ${layout.lineCount}: " +
                "sin eso esta medición no prueba el caso que dice probar",
            renglones,
            layout.lineCount
        )

        val lineaBase = lineaBaseEnRaiz(titulo)
        val densidad = composeTestRule.density.density
        val tope = titulo.getUnclippedBoundsInRoot().top.value
        val centroDelRenglon = tope + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f / densidad

        assertEquals(
            "el día no está sobre la línea base del título a ${nivel.name}",
            lineaBase,
            lineaBaseEnRaiz(composeTestRule.onNodeWithText(DIA, useUnmergedTree = true)),
            TOLERANCIA
        )
        assertEquals(
            "el importe no está sobre la línea base del título a ${nivel.name}",
            lineaBase,
            lineaBaseEnRaiz(composeTestRule.onNodeWithText(IMPORTE, useUnmergedTree = true)),
            TOLERANCIA
        )
        assertEquals(
            "el punto de estado no está centrado en la primera línea del título a ${nivel.name}",
            centroDelRenglon,
            centroEnRaiz(
                composeTestRule.onNodeWithTag(PUNTO_DEL_ESTADO_TAG, useUnmergedTree = true)
            ),
            TOLERANCIA
        )
        assertEquals(
            "el pin no está centrado en la primera línea del título a ${nivel.name}",
            centroDelRenglon,
            centroEnRaiz(
                composeTestRule.onNodeWithTag(PIN_DEL_CONTACTO_TAG, useUnmergedTree = true)
            ),
            TOLERANCIA
        )
    }

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

    /** La primera línea base del nodo, en dp desde el tope de la raíz. */
    private fun lineaBaseEnRaiz(nodo: SemanticsNodeInteraction): Float =
        nodo.getUnclippedBoundsInRoot().top.value + nodo.getAlignmentLinePosition(FirstBaseline).value

    /** El centro vertical del nodo, en dp desde el tope de la raíz. */
    private fun centroEnRaiz(nodo: SemanticsNodeInteraction): Float =
        nodo.getUnclippedBoundsInRoot().let { (it.top.value + it.bottom.value) / 2f }

    private fun layoutDe(nodo: SemanticsNodeInteraction): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(
            resultados
        )
        return resultados.single()
    }

    private companion object {

        /**
         * Un dp —dos píxeles en `xhdpi`—. Lo que se cobra es un despegue de
         * varios dp (el defecto movía el punto `md × escala`); comparar al
         * float exacto sería frágil ante el redondeo a píxel.
         */
        const val TOLERANCIA = 1.0f

        /** `2026-09-11T22:45Z` es el 11 de septiembre a las 16:45 en zona de negocio. */
        const val DIA = "11 sep"
        const val IMPORTE = "$350"

        val ABONO = ContactoDeCobranza(
            fecha = Instant.parse("2026-09-11T22:45:00Z"),
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = Money.of(BigDecimal("350.00")),
            tipo = TipoDeContacto.COBRO,
            metodo = MetodoDeCobro.TRANSFERENCIA,
            cobrador = "Marisol Vega",
            cuenta = "Recámara Cántaro King Size",
            ubicacion = UbicacionDelCobro(lat = 19.0433, lng = -98.1981)
        )

        /**
         * El mismo abono con una etiqueta que no cabe en un renglón a 360 dp.
         * En producción un abono dice "Abono"; aquí se alarga a propósito para
         * tener, en la misma fila, título de dos renglones E importe y pin.
         */
        val ABONO_DE_TITULO_LARGO = ABONO.copy(
            etiqueta = "Abono de la semana que dejó con la vecina de enfrente porque salió"
        )
    }
}
