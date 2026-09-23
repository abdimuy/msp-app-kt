package com.example.msp_app.feature.pagos.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.CuentaDelAbono
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.ui.components.CONTINUAR_CON_LA_CUENTA_TAG
import com.example.msp_app.feature.pagos.ui.components.HOJA_DE_ABONO_TAG
import com.example.msp_app.feature.pagos.ui.components.HojaDeAbono
import com.example.msp_app.feature.pagos.ui.components.OPCION_DE_CUENTA_TAG
import com.example.msp_app.feature.pagos.ui.components.VELO_DEL_ABONO_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **"Continuar" no queda debajo de la barra de navegación.**
 *
 * ## El defecto que esto fija
 *
 * El dueño lo vio en vidrio en un SM-A256E: con la hoja "¿A cuál cuenta?"
 * arriba, el botón "Continuar" queda **debajo de la ventana de navegación de
 * SystemUI** y no se puede tocar. No es que el toque no haga nada — la ventana
 * del sistema se come el evento y ni siquiera entra al proceso, así que no hay
 * una sola línea en logcat: la app viva y el botón muerto. Es el mismo defecto
 * que `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` midió arriba, del otro
 * lado de la pantalla.
 *
 * [HojaDeAbono] era la **única rota de las cinco hojas** de `DetalleCliente`:
 * tres heredan el `systemBarsPadding()` de `DetalleClienteContent` por estar
 * DENTRO de su `Column` padeado, y `HojaDeLaFicha` se salva sola porque el
 * `ModalBottomSheet` de M3 1.3.0 ya aplica `safeDrawing.only(Bottom)`. Esta no
 * es M3 y se invoca FUERA de ese `Column`, así que caía entre las dos redes.
 *
 * ## Por qué el inset se despacha a mano
 *
 * En Robolectric **el inset vale cero** salvo que alguien lo despache: una
 * composición de test nunca recibe el `WindowInsets` que en el teléfono le llega
 * del sistema. Así que se construye uno de verdad —`Insets.of(0, 0, 0, alto)`
 * sobre `Type.navigationBars()`— y se despacha con
 * `ViewCompat.dispatchApplyWindowInsets` sobre las vistas `AndroidComposeView`,
 * que es donde Compose instala su listener. Mecanismo copiado de
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest.montarElGrafo()`.
 *
 * **Con la sonda de control positivo que ese test trae**, y por la misma razón:
 * si el inset no llegara a Compose, `navigationBarsPadding()` no movería nada,
 * el botón terminaría dentro de la pantalla igual y **el test daría verde sin
 * haber probado nada**. La sonda lee lo que ESTA composición ve y se exige antes
 * de medir un solo píxel. La regla del repo: una ausencia no es un hallazgo
 * hasta probar que el método la habría encontrado.
 *
 * ## Lo que NO se puede cobrar desde aquí — el dueño lo preguntó
 *
 * **Que el dedo alcance el botón.** `performClick` invoca la acción de
 * semántica del nodo; **no cruza el sistema de ventanas de Android**, que es
 * exactamente la capa donde el defecto ocurre. Un test de Compose jamás puede
 * probar que el tap llega: por eso lo que se mide acá es **geometría** —dónde
 * termina el botón respecto del borde del inset—, que es la condición necesaria
 * y lo único observable. La prueba de que el tap llega fue el barrido a mano en
 * el teléfono, y no se puede automatizar en JVM.
 *
 * **Ningún golden sirve.** En Robolectric el inset vale cero salvo que se
 * despache a mano, y Roborazzi no lo despacha: una foto de esta hoja se ve
 * idéntica con y sin el arreglo. Fotografiar el defecto es imposible.
 *
 * **El orden de `navigationBarsPadding()` dentro de la cadena.** El padding va
 * después del `.background(...)` para que suban los botones y no el fondo
 * (precedente: `BlurredActionBar.kt:126`). Ese orden es de **dibujo**, y los dos
 * órdenes producen el MISMO `LayoutNode` con las mismas `boundsInRoot` — la
 * semántica no lo ve. Lo más cerca que llega este archivo es
 * `la hoja sigue pegada al borde de abajo`, que descarta el otro arreglo
 * equivocado: colgar el inset del `Box` de afuera y despegar la hoja del borde.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaHojaDelAbonoNoQuedaBajoLaBarraTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Lo que ESTA composición ve de `WindowInsets.navigationBars`, en px. */
    private var insetDeAbajoVisto: Int = -1

    /**
     * Lo mismo para `statusBars`. Sin esta segunda sonda,
     * `el inset de arriba no le mete una franja muerta encima del titulo`
     * pasaría en verde con un `systemBarsPadding()`: si el inset de arriba nunca
     * llegó, vale cero y las dos cadenas miden igual.
     */
    private var insetDeArribaVisto: Int = -1

    /** El `AndroidComposeView` que hospeda la composición: por ahí entra el inset. */
    private var vistaDeLaComposicion: View? = null

    @Test
    fun `los cuatro testTag de la hoja llegan a semantics`() {
        // Control positivo de las mediciones de abajo. Los cuatro `testTag` de
        // `HojaDeAbono` nacieron sin un solo consumidor en el repo, y el del
        // botón compite con el `PRIMARY_FIELD_BUTTON_TAG` que
        // `MspPrimaryFieldButton` se pone solo: si el del design system ganara,
        // `onNodeWithTag(CONTINUAR_CON_LA_CUENTA_TAG)` no encontraría nada y los
        // tests de geometría tronarían por la razón equivocada.
        montarConLasDosBarras()

        assertEquals("el velo no llegó a semantics", 1, cuantos(VELO_DEL_ABONO_TAG))
        assertEquals("la hoja no llegó a semantics", 1, cuantos(HOJA_DE_ABONO_TAG))
        assertEquals("el botón no llegó a semantics", 1, cuantos(CONTINUAR_CON_LA_CUENTA_TAG))
        assertEquals(
            "la hoja no pintó una opción por cuenta cobrable",
            cuentas().size,
            cuantos(OPCION_DE_CUENTA_TAG)
        )
    }

    @Test
    fun `el boton continuar termina arriba del borde de la barra de navegacion`() {
        montarConLasDosBarras()
        exigeQueLosInsetsLlegaron()

        val insetAbajo = enPx(ALTO_DE_LA_BARRA_DE_NAVEGACION)
        val raiz = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val boton = composeTestRule.onNodeWithTag(CONTINUAR_CON_LA_CUENTA_TAG)
            .fetchSemanticsNode().boundsInRoot
        // El borde superior de la ventana del sistema. Todo lo que termine
        // debajo de esta línea es un control que el dedo no alcanza.
        val bordeDeLaBarra = raiz.bottom - insetAbajo

        // Control de reversión: sin el `navigationBarsPadding()` de la cadena, el
        // fondo del botón termina a un `spacing.md` (16dp) del borde de la
        // pantalla — muy por DEBAJO de esta línea con una barra de 48dp — y esta
        // aserción se pone roja.
        assertTrue(
            "\"Continuar\" termina en y=${boton.bottom.toInt()} y la barra de navegación " +
                "arranca en y=${bordeDeLaBarra.toInt()}: el botón queda debajo de la ventana " +
                "del sistema y el tap no entra al proceso. Sube el control, no bajes el inset",
            boton.bottom <= bordeDeLaBarra
        )
    }

    /**
     * **La red contra el arreglo equivocado**, y no un control de reversión: hoy
     * también pasa.
     *
     * Colgar el inset del `Box` de afuera en vez de la hoja despegaría el velo
     * del borde y dejaría una franja del fondo de la PANTALLA entre el velo y la
     * barra. Eso sí se puede medir: el velo es `fillMaxSize()` sin padding, así
     * que si alguien le mete el inset al contenedor, el velo se encoge y esta
     * aserción se pone roja.
     *
     * Lo que NO se mide aquí es que el FONDO de la hoja llegue pegado al borde.
     * El `testTag` va al final de la cadena, después del `padding`, así que las
     * `boundsInRoot` del nodo son las del contenido ya padeado y **nunca**
     * alcanzan `raiz.bottom` — ni con el arreglo ni sin él. Que el `background`
     * se pinte a sangre bajo la barra es una afirmación sobre PÍXELES, y en
     * Robolectric no hay píxeles que mirar; vive en el orden de la cadena y en
     * el precedente de `BlurredActionBar`.
     */
    @Test
    fun `el inset lo pone la hoja y no el contenedor de afuera`() {
        montarConLasDosBarras()
        exigeQueLosInsetsLlegaron()

        val raiz = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val velo = composeTestRule.onNodeWithTag(VELO_DEL_ABONO_TAG)
            .fetchSemanticsNode().boundsInRoot
        val hoja = composeTestRule.onNodeWithTag(HOJA_DE_ABONO_TAG)
            .fetchSemanticsNode().boundsInRoot

        assertEquals(
            "el velo termina en y=${velo.bottom.toInt()} y la pantalla en " +
                "y=${raiz.bottom.toInt()}: el inset se colgó del contenedor de afuera y " +
                "encogió la pantalla entera en vez de subir los botones de la hoja",
            raiz.bottom,
            velo.bottom,
            TOLERANCIA_EN_PX
        )
        assertEquals(
            "el contenido de la hoja no subió exactamente la barra más su propio " +
                "spacing.md: o falta el inset, o se aplicó dos veces",
            raiz.bottom - enPx(ALTO_DE_LA_BARRA_DE_NAVEGACION) - enPx(PADDING_DE_LA_HOJA),
            hoja.bottom,
            TOLERANCIA_EN_PX
        )
    }

    @Test
    fun `el inset de arriba no le mete una franja muerta encima del titulo`() {
        montarConLasDosBarras()
        exigeQueLosInsetsLlegaron()

        val hoja = composeTestRule.onNodeWithTag(HOJA_DE_ABONO_TAG)
            .fetchSemanticsNode().boundsInRoot
        val titulo = composeTestRule.onNodeWithText(TITULO_DE_LA_HOJA)
            .fetchSemanticsNode().boundsInRoot

        // Por qué `navigationBarsPadding()` y no `systemBarsPadding()`: la hoja
        // arranca pegada abajo y nunca toca la barra de estado, así que el inset
        // de arriba solo le metería una franja muerta encima del título. Con
        // `systemBarsPadding()` la separación sería `spacing.md` + el inset de
        // arriba (40dp acá), y esto se pone rojo.
        val separacion = titulo.top - hoja.top
        assertTrue(
            "entre el borde de la hoja y el título hay ${separacion.toInt()}px, más que el " +
                "inset de arriba (${enPx(ALTO_DE_LA_BARRA_DE_ESTADO)}px): la cadena está " +
                "consumiendo el inset superior en una hoja que no llega ahí",
            separacion < enPx(ALTO_DE_LA_BARRA_DE_ESTADO)
        )
    }

    // -----------------------------------------------------------------------

    /**
     * Monta [HojaDeAbono] **suelta** —sin `DetalleClienteContent`, que es quien
     * padea a las otras hojas— y le despacha un inset de barra de navegación de
     * verdad, más uno de barra de estado que le da filo al último test.
     *
     * La sonda vive DENTRO de la composición porque lo que hay que probar es lo
     * que ella ve, no lo que el test despachó: son dos cosas distintas, y la
     * segunda sin la primera no prueba nada.
     */
    private fun montarConLasDosBarras() {
        val cobrables = cuentas()
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HojaDeAbono(
                        cuentas = cobrables,
                        elegida = cobrables.first().ventaId,
                        onElegir = {},
                        onContinuar = {},
                        onCerrar = {}
                    )
                    // Sonda del control positivo: lo que ESTA composición ve. No pinta.
                    val densidad = LocalDensity.current
                    val abajo = WindowInsets.navigationBars.getBottom(densidad)
                    val arriba = WindowInsets.statusBars.getTop(densidad)
                    val vista = LocalView.current
                    SideEffect {
                        insetDeAbajoVisto = abajo
                        insetDeArribaVisto = arriba
                        vistaDeLaComposicion = vista
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        despacharLosInsets()
        composeTestRule.waitForIdle()
    }

    /**
     * El inset va sobre el `AndroidComposeView` y **no** sobre el `DecorView`:
     * es ahí donde Compose instala su listener, es lo que hace el sistema en el
     * teléfono, y es lo único que una composición de test nunca recibe sola.
     */
    private fun despacharLosInsets() {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                Insets.of(0, 0, 0, enPx(ALTO_DE_LA_BARRA_DE_NAVEGACION))
            )
            .setInsets(
                WindowInsetsCompat.Type.statusBars(),
                Insets.of(0, enPx(ALTO_DE_LA_BARRA_DE_ESTADO), 0, 0)
            )
            .build()
        val raizDeVistas = requireNotNull(vistaDeLaComposicion) {
            "la composición no publicó su `LocalView`: sin vista no hay a quién despacharle " +
                "el inset, y la medición no probaría nada"
        }.rootView
        composeTestRule.runOnUiThread {
            vistasDeCompose(raizDeVistas).forEach { vista ->
                ViewCompat.dispatchApplyWindowInsets(vista, insets)
            }
        }
    }

    /**
     * El control positivo, exigido antes de medir un solo píxel: con el inset en
     * cero, `navigationBarsPadding()` no mueve nada y **las tres mediciones
     * pasarían en verde sobre la hoja rota**.
     */
    private fun exigeQueLosInsetsLlegaron() {
        assertEquals(
            "el inset de la barra de navegación no llegó a Compose: la medición no probaría nada",
            enPx(ALTO_DE_LA_BARRA_DE_NAVEGACION),
            insetDeAbajoVisto
        )
        assertEquals(
            "el inset de la barra de estado no llegó a Compose: `systemBarsPadding()` mediría " +
                "igual que `navigationBarsPadding()` y el último test no probaría nada",
            enPx(ALTO_DE_LA_BARRA_DE_ESTADO),
            insetDeArribaVisto
        )
    }

    private fun vistasDeCompose(raiz: View): List<View> {
        val encontradas = mutableListOf<View>()
        if (raiz.javaClass.simpleName == "AndroidComposeView") encontradas += raiz
        if (raiz is ViewGroup) {
            for (i in 0 until raiz.childCount) encontradas += vistasDeCompose(raiz.getChildAt(i))
        }
        return encontradas
    }

    private fun cuantos(tag: String): Int =
        composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun enPx(dp: Dp): Int = with(composeTestRule.density) { dp.roundToPx() }

    /** Las dos cuentas cobrables del mock: con una sola, esta hoja ni aparece. */
    private fun cuentas(): List<VentaDelCliente> =
        CuentaDelAbono.cobrables(PagosFixtures.detalleCliente().ventas)

    private companion object {

        /**
         * Alto de la barra de navegación con el que se despacha. Los 48dp del
         * modo de tres botones del SM-A256E, donde el dueño midió el defecto. Lo
         * que importa es que sea **mayor que el `spacing.md` (16dp)** de la
         * hoja: con un inset más chico que ese padding el botón ya caería arriba
         * del borde por accidente y el test pasaría sin el arreglo.
         */
        val ALTO_DE_LA_BARRA_DE_NAVEGACION = 48.dp

        /**
         * Alto de la barra de estado. Existe solo para separar
         * `navigationBarsPadding()` de `systemBarsPadding()`, y también tiene
         * que superar al `spacing.md` de la hoja para que la diferencia se vea.
         */
        val ALTO_DE_LA_BARRA_DE_ESTADO = 40.dp

        /**
         * El `spacing.md` que la hoja se pone por dentro, después del inset.
         * Se escribe aquí para poder afirmar que el contenido subió **el inset
         * MÁS este padding** y no uno de los dos: con sólo el inset, alguien
         * pudo haberlo puesto en lugar del padding en vez de antes.
         */
        val PADDING_DE_LA_HOJA = 16.dp

        /** Medio píxel: los bordes se acumulan en `Float` y se comparan en px enteros. */
        const val TOLERANCIA_EN_PX = 0.5f

        const val TITULO_DE_LA_HOJA = "¿A cuál cuenta?"
    }
}
