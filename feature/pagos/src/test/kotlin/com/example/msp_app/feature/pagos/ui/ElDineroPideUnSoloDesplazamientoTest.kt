package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.ui.components.BARRA_BLANDA_TAG
import com.example.msp_app.feature.pagos.ui.components.SALDO_DEL_CLIENTE_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El `SALDO TOTAL` ya NO cabe sin desplazar, y esto ancla cuánto hay que
 * desplazar.**
 *
 * ## Lo que se abandonó, cuándo y por qué — porque lo refutado no se borra
 *
 * Hasta el **2026-09-24** esta pantalla tenía una regla dura: *"a escala 1.0 el
 * `SALDO TOTAL` tiene que caber entero sobre la barra"*. Se defendió durante dos
 * días con dp medidos y costó tres recortes al mapa —152 → 118 → 96 dp— más un
 * reparto de la barra apretado al límite (96 + 66 = 162 exactos, margen cero).
 * El test que la cobraba se llamaba `ElDineroNoSeMeteBajoLaBarraTest` y vivía en
 * este mismo paquete.
 *
 * **El 2026-09-25 el dueño la retiró**, y con el número delante. Vio el
 * resultado de aquel reparto en su teléfono y dijo *"está horrible y no se
 * parece en nada al mock"*. La causa no fue la medición sino el mock: estaba
 * dibujado a 390×844 —proporciones de iPhone— sobre una pantalla real de
 * 360×744, así que prometía un mapa que nunca cupo. Rehecho a las medidas
 * reales, y viendo marcada la línea de flotación, **eligió el mapa alto
 * sabiendo que el dinero se iba abajo del pliegue**.
 *
 * Así que aquella regla **ya no rige**. Quien llegue al KDoc viejo por un `git
 * log` tiene que leer esto y no seguir peleando una batalla que ya se resolvió
 * en el otro sentido.
 *
 * ## Lo que rige ahora: "no se aleja más"
 *
 * Cambiar "cabe" por "no empeora" no es aflojar la guarda, es cambiarle la
 * pregunta. Lo que sigue siendo inaceptable es que alguien meta algo arriba del
 * dinero y **nadie se entere**: eso es lo que pasó tres veces antes de que
 * existiera una medición. El número de abajo es el desplazamiento que hoy hace
 * falta, medido; si sube, esta prueba se cae.
 *
 * ## El número, y de dónde sale
 *
 * A `NORMAL`, gráficos NATIVOS, `w360dp-h800dp`, con la dirección **real** del
 * padrón y nota puesta:
 *
 * | | dp |
 * |---|---|
 * | El bloque del saldo termina en | 739.5 |
 * | La banda de la barra empieza en | 582.0 |
 * | **Hay que desplazar** | **157.5** |
 *
 * Es poco más de un dedo. Y no es gratis: antes era cero.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElDineroPideUnSoloDesplazamientoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `con nota, el dinero no pide mas desplazamiento del medido`() {
        cliente(PagosFixtures.fichaDelCliente())

        assertTrue(
            "hay que desplazar " + desplazamiento() + " para ver el SALDO TOTAL entero, y el " +
                "tope medido es " + TOPE + ": algo creció arriba del dinero y lo alejó más",
            desplazamiento() <= TOPE
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
    fun `con nota LARGA el dinero tampoco se aleja mas`() {
        cliente(PagosFixtures.fichaDelCliente().copy(nota = NOTA_QUE_NO_CABE))

        assertTrue(
            "con la nota larga hay que desplazar " + desplazamiento() + " contra un tope de " +
                TOPE,
            desplazamiento() <= TOPE
        )
    }

    /**
     * **El control positivo, y no es una formalidad.**
     *
     * Un `SALDO_DEL_CLIENTE_TAG` mal escrito devolvería `Rect.Zero` —la trampa
     * que este repo ya pagó— y "0 ≤ 157.5" pasaría en verde sin haber medido
     * nada. Acá se exige que el desplazamiento sea **positivo**: hoy el dinero
     * está abajo del pliegue, así que un cero significa que el selector no
     * encontró el nodo.
     *
     * El día que esto se ponga rojo por dar cero o menos será porque el dinero
     * volvió a caber sin desplazar, y entonces lo correcto es volver a la
     * afirmación dura, no borrar esto.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `control positivo - el dinero SI esta abajo del pliegue`() {
        cliente(PagosFixtures.fichaDelCliente())

        assertTrue(
            "el desplazamiento medido es " + desplazamiento() + ": o el dinero volvió a caber " +
                "sin desplazar —y entonces hay que subir esto a afirmación dura— o el " +
                "selector no encontró el bloque del saldo y esta prueba no mide nada",
            desplazamiento() > 0.dp
        )
    }

    /**
     * Cuánto hay que desplazar para que el bloque del saldo quede entero arriba
     * de la banda de la barra.
     */
    private fun desplazamiento(): Dp {
        val saldo = composeTestRule.onNodeWithTag(SALDO_DEL_CLIENTE_TAG)
            .getUnclippedBoundsInRoot()
        val barra = composeTestRule.onNodeWithTag(BARRA_BLANDA_TAG).getUnclippedBoundsInRoot()
        return saldo.bottom - barra.top
    }

    private fun cliente(ficha: FichaDelCliente) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, FontSizeLevel.NORMAL.nominalScale),
                LocalFontSizeLevel provides FontSizeLevel.NORMAL
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
        /**
         * El desplazamiento medido, **más el aire de un renglón**.
         *
         * No se ancla al dp exacto para que un cambio tipográfico de medio
         * píxel no ponga rojo el repo; se ancla al tope. Lo que tiene que
         * dispararlo es algo que **crezca de verdad** arriba del dinero — una
         * sección nueva, una tarjeta más alta—, no el redondeo.
         */
        val TOPE = 172.dp

        /** Una nota que no cabe en los dos renglones asomados de la tarjeta. */
        const val NOTA_QUE_NO_CABE =
            "Trabaja de noche y casi nunca está en la mañana; atiende la suegra, que no " +
                "recibe dinero. El portón de atrás da a la calle sin nombre y hay perro " +
                "suelto hasta las once."
    }
}
