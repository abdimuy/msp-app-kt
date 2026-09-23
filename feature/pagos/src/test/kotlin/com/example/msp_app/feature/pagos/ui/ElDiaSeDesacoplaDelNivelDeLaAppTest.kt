package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
import com.example.msp_app.feature.pagos.ui.components.diaDe
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Task 5, hallazgo 3 — la pista "cuándo" se desacopla del `fontScale` del
 * sistema operativo.**
 *
 * `anchoDeCuando()` (versión anterior de `LineaDeContactos.kt`) reservaba
 * `40.dp × LocalFontSizeLevel.current.nominalScale` — el nivel elegido DENTRO
 * de la app. Pero `MainActivity.kt:166` fija el tamaño con el que Compose
 * pinta de verdad como `máx(nivel de la app, fontScale del SO)`: con la app
 * en `NORMAL` (1.0) y el SO en un nivel de accesibilidad más grande, el texto
 * se pinta grande de todos modos y la pista se quedaba reservando el ancho de
 * `NORMAL`. El resultado: `"24 ago"` no cabía en los 40dp reservados —con un
 * espacio de por medio, "24" y "ago" se PARTEN entre dos renglones, y con
 * `maxLines = 1` el segundo queda oculto— y se recorta SIN elipsis
 * (`TextOverflow.Clip` por default): un día mutilado que se sigue leyendo
 * como un día, un dato falso.
 *
 * ## La magnitud, medida (no sólo la fórmula)
 *
 * Con la app en `NORMAL` y el SO en `2.2`, corriendo este archivo con el
 * arreglo revertido (`git stash` del cambio en `LineaDeContactos.kt`, mismo
 * `ABONO` de abajo, a `w360dp-h800dp-xhdpi` — densidad 2.0): la fórmula vieja
 * reservaba **40dp (80px)** — el nivel `NORMAL` de la app, sin el SO — pero
 * el ancho real que `"24 ago"` necesita a `fontScale = 2.2` es **~79dp
 * (158px)**, medido con el arreglo puesto en el mismo escenario. Un déficit
 * de **~39dp, casi la mitad del ancho reservado**.
 *
 * ## Por qué éste, y no [LaFilaCaeSobreUnaSolaLineaBaseTest], desacopla las dos escalas
 *
 * Ese archivo (Task 3) sube LA MISMA escala a `LocalDensity.fontScale` Y a
 * `LocalFontSizeLevel` a la vez —`Density(density, nivel.nominalScale)` junto
 * con `LocalFontSizeLevel provides nivel`—, así que nunca puede exponer un
 * desacople entre las dos: ahí el nivel de la app SIEMPRE explica el tamaño
 * real. Aquí se reproduce la composición real de `MainActivity` —
 * `efectivo = máx(nivelApp.nominalScale, fontScaleDelSO)`, `LocalDensity`
 * recibe `efectivo` y `LocalFontSizeLevel` recibe SÓLO `nivelApp`— para que el
 * caso que `MainActivity` sí puede producir (SO más grande que el nivel
 * elegido) exista en la prueba.
 *
 * ## Por qué `didExceedMaxLines` y no `hasVisualOverflow`
 *
 * Medido también: [TextLayoutResult.hasVisualOverflow] da **falso positivo**
 * aquí — mide `size.width < multiParagraph.width`, y como esta prueba mide el
 * `Text` del día con el ancho GENEROSO que `PistaDeCuando` le pasa a la
 * medición (`Constraints(maxWidth = restricciones.maxWidth)`, casi todo el
 * ancho de la fila, no el ancho final que le toca), el `Text` se achica a su
 * contenido y esa comparación sale `true` SIEMPRE, arreglado o no —
 * confirmado corriendo este archivo con el arreglo puesto: `hasVisualOverflow
 * = true` pero `didExceedMaxLines = false`. `didExceedMaxLines` es la señal
 * correcta: con `maxLines = 1` y un espacio de por medio, un ancho
 * insuficiente hace que el layout interno quiera DOS renglones ("24" / "ago")
 * y `maxLines` esconde el segundo — eso SÍ es `didExceedMaxLines = true`,
 * confirmado con el arreglo revertido.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class ElDiaSeDesacoplaDelNivelDeLaAppTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `con la app en NORMAL y el SO mas grande, el dia se pinta completo`() =
        elDiaNoSeRecorta(FontSizeLevel.NORMAL)

    @Test
    fun `con la app en GRANDE y el SO mas grande, el dia se pinta completo`() =
        elDiaNoSeRecorta(FontSizeLevel.GRANDE)

    @Test
    fun `con la app en MUY_GRANDE y el SO mas grande todavia, el dia se pinta completo`() =
        elDiaNoSeRecorta(FontSizeLevel.MUY_GRANDE)

    private fun elDiaNoSeRecorta(nivelDeLaApp: FontSizeLevel) {
        fila(nivelDeLaApp, FONT_SCALE_DEL_SO, ABONO)

        val dia = composeTestRule.onNodeWithText(diaDe(AppTime.toBusinessDateTime(ABONO.fecha)))
        assertFalse(
            "el día quiso más de un renglón con la app en ${nivelDeLaApp.name} y el SO en " +
                "$FONT_SCALE_DEL_SO: la pista reservaba el ancho del nivel de la app, no el " +
                "del tamaño real con el que Compose pinta, y \"24\"/\"ago\" se partieron",
            layoutDe(dia).multiParagraph.didExceedMaxLines
        )
    }

    /**
     * Reproduce `MainActivity.kt:166`: `LocalDensity` recibe el `fontScale`
     * EFECTIVO (`máx(app, SO)`), pero `LocalFontSizeLevel` recibe SÓLO el
     * nivel de la app — las dos escalas, desacopladas.
     */
    private fun fila(
        nivelDeLaApp: FontSizeLevel,
        fontScaleDelSO: Float,
        contacto: ContactoDeCobranza
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            val efectivo = maxOf(nivelDeLaApp.nominalScale, fontScaleDelSO)
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, efectivo),
                LocalFontSizeLevel provides nivelDeLaApp
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    ContactoEnLinea(contacto = contacto)
                }
            }
        }
    }

    private fun layoutDe(nodo: SemanticsNodeInteraction): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(
            resultados
        )
        return resultados.single()
    }

    private companion object {
        /**
         * Un `fontScale` de SO mayor que los tres niveles de la app
         * (`NORMAL` 1.0, `GRANDE` 1.5, `MUY_GRANDE` 2.0) — el caso donde el
         * SO manda, no el nivel elegido en la app. 2.2 no es un nivel que la
         * app ofrezca, y no hace falta que lo sea: es lo que el accesorio de
         * accesibilidad del teléfono puede pedir por su cuenta.
         */
        const val FONT_SCALE_DEL_SO = 2.2f

        val ABONO = ContactoDeCobranza(
            id = "abono",
            fecha = Instant.parse("2026-08-24T22:45:00Z"),
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = Money.of(BigDecimal("350.00")),
            tipo = TipoDeContacto.COBRO,
            metodo = MetodoDeCobro.TRANSFERENCIA,
            cobrador = "RUTA 25 - NOE CORTERO"
        )
    }
}
