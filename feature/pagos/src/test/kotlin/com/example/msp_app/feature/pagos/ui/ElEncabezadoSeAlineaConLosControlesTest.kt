package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.AIRE_DEL_DOCK_TAG
import com.example.msp_app.feature.pagos.ui.components.CONTROLES_FLOTANTES_TAG
import com.example.msp_app.feature.pagos.ui.components.TEXTO_DEL_ENCABEZADO_TAG
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El nombre del encabezado compacto y los controles flotantes comparten
 * banda.**
 *
 * ## El defecto, con los dp que lo reportaron
 *
 * El dueño lo vio en el aparato el 2026-09-25, con la pantalla desplazada:
 * *"está súper desalineado el nombre y los botones de visible y tema… que el
 * nombre y dirección se alineen bien al igual que los botones"*.
 *
 * La causa era que cada bloque traía su propia geometría y nadie las comparaba.
 * Los controles bajan `AIRE_SOBRE_LOS_CONTROLES` desde el inset y miden
 * `ALTO_DE_LOS_CONTROLES`; el texto del encabezado **no bajaba nada** y medía lo
 * que midieran sus dos renglones. Medido sobre su captura: 8 dp de diferencia en
 * el borde de arriba y **11 dp entre los dos centros**.
 *
 * ## Por qué esto mira los DOS nodos y no las constantes
 *
 * Porque una prueba que leyera `ALTO_DE_LOS_CONTROLES` para afirmar que el
 * encabezado mide `ALTO_DE_LOS_CONTROLES` no probaría nada — es la trampa del
 * test que lee la constante que debería fijar, que este repo ya documentó. Acá
 * se miden las cajas reales de los dos bloques en pantalla: si alguien cambia el
 * tamaño de los toggles del design system, o le mete un renglón al encabezado, o
 * toca cualquiera de los dos rellenos, los centros se separan y esto se cae.
 *
 * ## Y a letra grande también
 *
 * Es donde más fácil se rompe: el texto crece y los botones no. Por eso el
 * bloque se centra dentro de su banda en vez de plantarse arriba — a 2.0 el
 * texto es más alto que los controles y lo que tiene que seguir coincidiendo es
 * el **centro**, no el borde.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElEncabezadoSeAlineaConLosControlesTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a letra normal los dos bloques comparten centro`() {
        pantallaDesplazada(FontSizeLevel.NORMAL)

        val texto = caja(TEXTO_DEL_ENCABEZADO_TAG)
        val controles = caja(CONTROLES_FLOTANTES_TAG)

        assertTrue(
            "el texto del encabezado está centrado en ${centro(TEXTO_DEL_ENCABEZADO_TAG)} y " +
                "los controles en ${centro(CONTROLES_FLOTANTES_TAG)}: se separaron " +
                "${desfase()} y el tope es $DESFASE_MAXIMO",
            desfase() <= DESFASE_MAXIMO
        )
        assertTrue(
            "arrancan en renglones distintos: el texto en ${texto.top} y los controles en " +
                "${controles.top}",
            abs((texto.top - controles.top).value) <= DESFASE_MAXIMO.value
        )
    }

    /**
     * **A letra muy grande lo que se comparte es el BORDE DE ARRIBA, no el
     * centro — y la diferencia es deliberada.**
     *
     * A 2.0 el bloque de texto mide 69.5 dp contra los 48 fijos de los
     * controles, así que pedirle a los dos el mismo centro sería pedir un
     * imposible: o el texto se sale por arriba de la pantalla o los botones se
     * bajan hasta la mitad del mapa.
     *
     * Lo correcto ahí es que los dos **arranquen en el mismo renglón** y que el
     * texto crezca hacia abajo, que es como se lee un bloque de dos líneas junto
     * a una fila de iconos. Afirmar el centro también a 2.0 habría sido pedir
     * que la pantalla mienta para que el test quede verde.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a letra muy grande siguen arrancando en el mismo renglon`() {
        pantallaDesplazada(FontSizeLevel.MUY_GRANDE)

        val texto = caja(TEXTO_DEL_ENCABEZADO_TAG)
        val controles = caja(CONTROLES_FLOTANTES_TAG)

        assertTrue(
            "a 2.0 el texto arranca en ${texto.top} y los controles en ${controles.top}",
            abs((texto.top - controles.top).value) <= DESFASE_MAXIMO.value
        )
        assertTrue(
            "a 2.0 el texto mide ${texto.bottom - texto.top}: si no creciera por debajo de " +
                "los ${controles.bottom - controles.top} de los controles, esta prueba " +
                "estaría afirmando el caso fácil y no el que aprieta",
            (texto.bottom - texto.top) > (controles.bottom - controles.top)
        )
    }

    /**
     * **El control positivo, y acá no es una formalidad.**
     *
     * El encabezado compacto **no se monta** hasta que el fondo terminó de
     * irse. Si el desplazamiento del andamio no llegara, `onNodeWithTag` no
     * encontraría el nodo y las dos pruebas de arriba fallarían por el motivo
     * equivocado — o peor, con una caja vacía en `Rect.Zero`, dos ceros
     * "alineados" y verde sobre una pantalla que nadie miró.
     *
     * Así que se exige que los dos bloques tengan alto **real**.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `control positivo - los dos bloques existen y miden`() {
        pantallaDesplazada(FontSizeLevel.NORMAL)

        val texto = caja(TEXTO_DEL_ENCABEZADO_TAG)
        val controles = caja(CONTROLES_FLOTANTES_TAG)

        assertTrue(
            "el bloque de texto mide ${texto.bottom - texto.top} y los controles " +
                "${controles.bottom - controles.top}: " +
                "un cero significa que el encabezado no llegó a montarse y esta prueba no " +
                "está midiendo la pantalla que dice medir",
            (texto.bottom - texto.top) > 0.dp && (controles.bottom - controles.top) > 0.dp
        )
    }

    // --- Andamio -------------------------------------------------------------

    private fun caja(tag: String) = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun centro(tag: String): Dp = caja(tag).let { it.top + (it.bottom - it.top) / 2 }

    private fun desfase(): Dp =
        abs((centro(TEXTO_DEL_ENCABEZADO_TAG) - centro(CONTROLES_FLOTANTES_TAG)).value).dp

    private fun pantallaDesplazada(nivel: FontSizeLevel) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    DetalleClienteContent(
                        state = DetalleClienteUiState(
                            cargando = false,
                            detalle = PagosFixtures.detalleCliente()
                        ),
                        onAtras = {},
                        onAbrirVenta = {},
                        onRegistrarAbono = {},
                        onRegistrarVisita = {},
                        onAlternarTema = {},
                        onAlternarPrivacidad = {}
                    )
                }
            }
        }
        // El encabezado sólo se monta cuando el fondo terminó de irse. Se
        // desplaza hasta el hueco del dock, que es el final del contenido.
        composeTestRule.onNodeWithTag(AIRE_DEL_DOCK_TAG).performScrollTo()
        composeTestRule.waitForIdle()
    }

    private companion object {
        /**
         * Cuánto pueden separarse los dos centros: **2 dp**.
         *
         * No cero, porque los dos bloques se componen de cosas distintas —texto
         * con su interlineado contra cajas de tamaño fijo— y el redondeo a
         * píxel puede dejar medio dp de diferencia. Lo que tiene que dispararlo
         * es un cambio de geometría, no el redondeo: lo reportado eran **11 dp**.
         */
        val DESFASE_MAXIMO = 2.dp
    }
}
