package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import com.example.msp_app.core.designsystem.component.MSP_SOFT_EDGE_SOLID_STOP
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.ui.components.BARRA_BLANDA_TAG
import com.example.msp_app.feature.pagos.ui.components.DOCK_DE_ACCIONES_TAG
import com.example.msp_app.feature.pagos.ui.components.SALDO_DEL_CLIENTE_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El `SALDO TOTAL` cabe entero arriba de la barra — con el mapa de fondo y con
 * el degradado puestos.**
 *
 * Éste es el criterio del dueño, dicho en esta pasada como condición de parada:
 * *"el mapa cede, el dinero no"*. Es el hermano de
 * `LaFichaSeVeYSeTocaTest.con nota, el SALDO TOTAL cabe entero…`, y existe
 * aparte porque lo que hay que medir **cambió de nodo**.
 *
 * ## Por qué ya no se mide contra el canto del dock
 *
 * Porque desde el rediseño **la barra no tiene canto**. Se disuelve hacia
 * arriba: arranca transparente, y el contenido de atrás se ve a través de ella
 * **a propósito** —palabras del dueño: *"que se vaya difuminando para que se vea
 * un poco lo de atrás"*—. "Donde empieza la barra" y "donde la barra tapa"
 * dejaron de ser el mismo renglón.
 *
 * La línea de flotación es el **punto opaco**: el [MSP_SOFT_EDGE_SOLID_STOP] del
 * alto de la banda, donde el degradado de tres paradas termina de cerrar. Se
 * calcula con la constante del design system y **no con un 0.44 escrito a
 * mano**, que es cómo se llega al día en que la implementación cambia y la
 * prueba sigue verde.
 *
 * ## Los dp, medidos, y de dónde salió cada uno
 *
 * A `NORMAL`, gráficos NATIVOS, `w360dp-h800dp`, con la dirección **real** del
 * padrón (dos renglones) y con nota puesta:
 *
 * | | Saldo termina en | Punto opaco | Margen |
 * |---|---|---|---|
 * | Antes del rediseño (banda de 100 dp, dock con canto) | 651.0 | 655.0 | 4.0 |
 * | Fondo a 261 dp (lo que daría el mock) | 736.0 | 645.4 | **−90.6** |
 * | Fondo a 152 dp + degradado de 80 | **638.0** | **645.4** | **7.4** |
 *
 * El presupuesto entero de la pantalla, medido quitando la banda vieja y
 * volviendo a medir, es `M + 0.56·T ≤ 258` — con `M` el alto visible del fondo y
 * `T` el alto total de la barra. Las dos cosas que el dueño aprobó compiten por
 * los mismos dp, y por eso este test las mide juntas: subir cualquiera de las
 * dos por su cuenta pone esto en rojo.
 *
 * ## Y el control positivo
 *
 * A `GRANDE` el saldo **no cabe**, igual que antes del rediseño, y se afirma
 * para que el límite quede medido. Es además lo que impide que un
 * `SALDO_DEL_CLIENTE_TAG` mal escrito —que devolvería `Rect.Zero`— deje pasar
 * los dos de arriba en verde: acá el mismo tag tiene que dar un rectángulo que
 * NO cabe, y sólo un nodo real puede fallar.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElDineroNoSeMeteBajoLaBarraTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `con nota, el SALDO TOTAL cabe entero arriba del tramo solido`() {
        cliente(PagosFixtures.fichaDelCliente())

        val saldo = bordesDe(SALDO_DEL_CLIENTE_TAG)
        assertTrue(
            "el saldo total termina en " + saldo.bottom + " y la barra se vuelve opaca en " +
                puntoOpaco() + ": el dinero quedó tapado",
            saldo.bottom.value <= puntoOpaco()
        )
    }

    /**
     * **Y con la nota LARGA tampoco**, que es el caso que de verdad aprieta: la
     * nota del fixture cabe en los dos renglones asomados, así que la de arriba
     * mide la tarjeta en su estado más barato. Con una nota que no cabe, la
     * tarjeta paga además el renglón del *"Ver más"*.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `con nota LARGA, el SALDO TOTAL tambien cabe entero`() {
        cliente(PagosFixtures.fichaDelCliente().copy(nota = NOTA_QUE_NO_CABE))

        val saldo = bordesDe(SALDO_DEL_CLIENTE_TAG)
        assertTrue(
            "el saldo total termina en " + saldo.bottom + " y la barra se vuelve opaca en " +
                puntoOpaco() + ": con la nota larga el dinero se tapa",
            saldo.bottom.value <= puntoOpaco()
        )
    }

    /**
     * **Y también cabe arriba de los BOTONES**, que es la lectura dura.
     *
     * No es redundante con la de arriba: mide el otro extremo. Si algún día
     * alguien quita el degradado, esta afirmación sigue siendo la que vale, y si
     * alguien lo alarga, la de arriba se rompe primero. Entre las dos no queda
     * una franja en la que el dinero pueda esconderse sin que nada se ponga
     * rojo.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `con nota, el SALDO TOTAL cabe tambien arriba de los botones`() {
        cliente(PagosFixtures.fichaDelCliente())

        val saldo = bordesDe(SALDO_DEL_CLIENTE_TAG)
        val botones = bordesDe(DOCK_DE_ACCIONES_TAG)
        assertTrue(
            "el saldo total termina en " + saldo.bottom + " y los botones empiezan en " +
                botones.top,
            saldo.bottom <= botones.top
        )
    }

    /**
     * **A `GRANDE` NO cabe**, igual que antes del rediseño. No se maquilla: el
     * límite queda medido, y el día que esto se ponga rojo será porque ya cabe y
     * hay que subirlo a afirmación.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `control positivo - a escala GRANDE el saldo sigue sin caber`() {
        cliente(PagosFixtures.fichaDelCliente(), FontSizeLevel.GRANDE)

        val saldo = bordesDe(SALDO_DEL_CLIENTE_TAG)
        assertTrue(
            "el saldo total termina en " + saldo.bottom + " y la barra se vuelve opaca en " +
                puntoOpaco() + ": a GRANDE ya cabe, sube esto a afirmación",
            saldo.bottom.value > puntoOpaco()
        )
    }

    /**
     * Dónde el degradado de la barra termina de cerrar, en dp desde el tope de
     * la raíz. Sale de la banda medida y de la constante del design system.
     */
    private fun puntoOpaco(): Float {
        val barra = bordesDe(BARRA_BLANDA_TAG)
        return barra.top.value + (barra.bottom.value - barra.top.value) * MSP_SOFT_EDGE_SOLID_STOP
    }

    private fun bordesDe(tag: String): DpRect =
        composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun cliente(ficha: FichaDelCliente, nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
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
                            detalle = PagosFixtures.detalleCliente().copy(ficha = ficha)
                        ),
                        onAtras = {},
                        onAbrirVenta = {},
                        onRegistrarAbono = {},
                        onRegistrarVisita = {},
                        onVerContactos = {},
                        onAlternarTema = {},
                        onAlternarPrivacidad = {}
                    )
                }
            }
        }
    }

    private companion object {
        /** Una nota que no cabe en los dos renglones asomados de la tarjeta. */
        const val NOTA_QUE_NO_CABE =
            "Trabaja de noche y casi nunca está en la mañana; atiende la suegra, que no " +
                "recibe dinero. El portón de atrás da a la calle sin nombre y hay perro " +
                "suelto hasta las once."
    }
}
