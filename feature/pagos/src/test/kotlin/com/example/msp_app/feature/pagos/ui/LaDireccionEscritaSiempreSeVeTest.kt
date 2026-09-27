package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.ui.components.DIRECCION_DEL_TELON_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **La dirección escrita se ve SIEMPRE, y entera.**
 *
 * ## La regresión que esto cierra
 *
 * `f8621920` retiró el renglón de dirección del bloque de identidad del detalle
 * de cliente. El argumento escrito era que el cuadro de la puerta, tres dedos
 * más abajo, ya la decía *"más grande y entera"*. El dueño lo refutó mirando su
 * teléfono:
 *
 * > *"¿Por qué quitaste el texto normal de la dirección? Eso no se puede quitar
 * > nunca, siempre se tiene que ver la dirección escrita."*
 *
 * Y lo agrava que el renglón existía por una petición previa suya: *"la
 * dirección cuando se entra en detalles del cliente ni se ve casi"*. Se arregló
 * una queja rompiendo la anterior.
 *
 * ## Por qué el argumento era falso, y por qué nadie lo vio
 *
 * Dos cosas a la vez, y ninguna se ve con el fixture compartido:
 *
 * 1. **El cuadro no puede decirla entera.** Mide 130 dp a escala nominal y
 *    **40 dp** a `GRANDE` y `MUY_GRANDE`, donde le cabe un renglón con elipsis.
 * 2. **`calle` no es "la calle".** `DIRS_CLIENTES.CALLE` trae calle, número,
 *    colonia y población —msp-api la compone así en `buildCalle`—, o sea la
 *    dirección casi completa en una sola cadena larga.
 *
 * El fixture de `PagosFixtures` trae `"C. Hidalgo 214"` + `"Centro"`: veintiséis
 * caracteres que caben en cualquier parte. Con eso, el cuadro **sí** decía la
 * dirección completa y el golden lo confirmaba. **El fixture escondía el
 * defecto**, y por eso este archivo usa [CALLE_REAL]: la forma que el campo
 * tiene en producción.
 *
 * ## Qué se afirma acá, y contra qué trampa
 *
 * Que existe un nodo visible que dice la dirección **completa**, a escala
 * nominal y a 2.0. Se compara contra el tamaño del nodo y con
 * `assertIsDisplayed`, no contra un `Rect` metido en la pantalla: un nodo fuera
 * del viewport devuelve `Rect.Zero` y cumpliría cualquier aserción de
 * contención — la trampa que este repo ya pagó una vez.
 *
 * [GraphicsMode.Mode.NATIVE] no es adorno: sin métricas de fuente reales, un
 * texto recortado por el layout no se recorta, y `el cuadro por si solo no
 * alcanza` —que es el corazón de esto— daría verde contra el código roto.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaDireccionEscritaSiempreSeVeTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `la direccion escrita completa se ve a escala NORMAL`() {
        cliente()

        composeTestRule.onNodeWithTag(DIRECCION_DEL_TELON_TAG)
            .assertIsDisplayed()
            .assertTextEquals(DIRECCION_COMPLETA)
        noEsUnRectanguloVacio()
    }

    /**
     * **Y a 2.0**, que es donde el cuadro se aprieta a 40 dp y deja de ser un
     * sitio donde un dato pueda vivir. Si la regla del dueño se rompe en alguna
     * escala, se rompe en ésta.
     */
    @Test
    fun `la direccion escrita completa se ve a escala MUY GRANDE`() {
        cliente(nivel = FontSizeLevel.MUY_GRANDE)

        composeTestRule.onNodeWithTag(DIRECCION_DEL_TELON_TAG)
            .assertIsDisplayed()
            .assertTextEquals(DIRECCION_COMPLETA)
        noEsUnRectanguloVacio()
    }

    /**
     * **El cuadro por sí solo no alcanza**, que es la afirmación que
     * `f8621920` dio por buena sin medirla.
     *
     * Se mide de la forma que no depende de píxeles: la dirección completa
     * aparece **una sola vez** en toda la pantalla, y el nodo que la dice es el
     * renglón de dirección. Quitar ese renglón deja el conteo en cero y esto se
     * pone rojo — que es exactamente la regresión que se reportó.
     *
     * El control positivo es el `assertEquals(1, …)`: un selector ciego daría
     * cero y también fallaría, así que el verde sólo se consigue encontrándola.
     */
    @Test
    fun `el cuadro por si solo no alcanza - la direccion completa solo la dice el renglon`() {
        cliente()

        assertEquals(
            "la dirección escrita completa no aparece en ninguna parte de la pantalla",
            1,
            composeTestRule.onAllNodesWithText(DIRECCION_COMPLETA, substring = true)
                .fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag(DIRECCION_DEL_TELON_TAG).assertTextEquals(DIRECCION_COMPLETA)
    }

    /**
     * **El fondo ya no puede repetir nada, y por eso este test cambió de
     * pregunta.**
     *
     * Había una seña sobre el mapa —calle y ruta— y este test vigilaba que no
     * dijera lo mismo que la tarjeta de abajo. Desde el 2026-09-25 esa seña no
     * existe: el fondo lleva `TelonDelNombre`, que dice el nombre y la
     * dirección completa, y **la tarjeta de identidad dejó de decir la
     * dirección**. O sea que la duplicación se cerró por construcción y no por
     * vigilancia.
     *
     * Lo que sí hay que seguir vigilando es lo contrario: que la dirección no
     * se diga **dos veces** ahora que cambió de dueño. Eso es lo que mide
     * `la direccion escrita aparece una sola vez en la pantalla`.
     */
    @Test
    fun `la tarjeta de identidad ya no dice la direccion`() {
        cliente()

        assertEquals(
            "la tarjeta volvió a decir la dirección que el telón ya dice arriba: es la " +
                "duplicación que el dueño llamó \"un error de copiado\"",
            1,
            composeTestRule.onAllNodesWithText(DIRECCION_COMPLETA).fetchSemanticsNodes().size
        )
    }

    /**
     * El renglón de dirección tiene **tamaño**, y no es un `Rect.Zero`
     * disfrazado de nodo visible.
     *
     * Va aparte y no como una tercera aserción suelta porque es el control que
     * hace que las otras signifiquen algo: `assertIsDisplayed` mira el viewport
     * pero un nodo de área cero también "está dentro".
     */
    private fun noEsUnRectanguloVacio() {
        val bordes = composeTestRule.onNodeWithTag(
            DIRECCION_DEL_TELON_TAG
        ).getUnclippedBoundsInRoot()
        assertTrue(
            "el renglón de dirección mide " + (bordes.right - bordes.left) + " x " +
                (bordes.bottom - bordes.top) + ": es un rectángulo vacío, no un texto",
            bordes.right - bordes.left > ANCHO_MINIMO && bordes.bottom - bordes.top > ALTO_MINIMO
        )
    }

    private fun cliente(nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    Pantalla(conDireccionReal())
                }
            }
        }
    }

    @Composable
    private fun Pantalla(detalle: DetalleCliente) {
        DetalleClienteContent(
            state = DetalleClienteUiState(cargando = false, detalle = detalle),
            onAtras = {},
            onAbrirVenta = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onAlternarTema = {},
            onAlternarPrivacidad = {},
            fichaDelCliente = AccionesDeLaFicha(onEditar = {})
        )
    }

    private fun conDireccionReal(): DetalleCliente =
        PagosFixtures.detalleCliente().copy(calle = CALLE_REAL, ciudad = CIUDAD_REAL, zona = RUTA)

    private companion object {
        /**
         * La forma REAL del campo, no la del fixture compartido.
         *
         * msp-api compone `DIRS_CLIENTES.CALLE` como
         * `NOMBRE_CALLE + " " + NUM_EXT + "\n" + COLONIA + ", " + POBLACION`
         * (`internal/ventas/infra/microsip/cliente_writer.go`), y
         * `RoomVentasAdapter` aplana el salto de línea a un espacio. Lo que la
         * pantalla recibe es esto: largo, con comas propias y con la colonia
         * adentro.
         */
        const val CALLE_REAL = "C. Miguel Hidalgo y Costilla 214 Col. Emiliano Zapata, Tehuacán"

        /** `Sale.CIUDAD`, que en Microsip es la POBLACIÓN. */
        const val CIUDAD_REAL = "Tehuacán"

        const val RUTA = "ruta 25"

        /** Lo que `DetalleCliente.direccion` arma, y lo que no puede faltar. */
        const val DIRECCION_COMPLETA = "$CALLE_REAL, $CIUDAD_REAL"

        /**
         * Pisos de "esto es un texto y no un rectángulo vacío". No son criterios
         * de diseño: son el umbral que separa un nodo con contenido de un
         * `Rect.Zero`, que es lo único que se afirma.
         */
        val ANCHO_MINIMO = 100.dp
        val ALTO_MINIMO = 8.dp
    }
}
