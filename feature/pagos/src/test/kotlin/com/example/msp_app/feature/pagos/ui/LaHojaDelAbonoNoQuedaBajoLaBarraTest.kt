package com.example.msp_app.feature.pagos.ui

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * **"Continuar" no queda debajo de la barra de navegación.**
 *
 * ## El defecto que esto fija
 *
 * El dueño lo vio en vidrio en un SM-A256E: con la hoja "¿A cuál cuenta?"
 * arriba, el botón "Continuar" queda **debajo de la ventana de navegación de
 * SystemUI** y no se puede tocar. No es que el toque no haga nada — la ventana
 * del sistema se come el evento y ni siquiera entra al proceso, así que no hay
 * una sola línea en logcat: la app viva y el botón muerto.
 *
 * ## De `navigationBarsPadding()` a M3, y por qué el mecanismo de prueba cambió
 *
 * Task del 2026-09-26 (misma animación que `HojaDeLaFicha`): [HojaDeAbono] pasó
 * de un `Box` a mano con su propio `navigationBarsPadding()` a un
 * `ModalBottomSheet` de M3. La versión vieja de este archivo despachaba el
 * inset sobre el `AndroidComposeView` de la composición de AFUERA porque la
 * hoja vivía en ESE árbol. Ya no: M3 monta el `ModalBottomSheet` en su propio
 * `Dialog` (`ModalBottomSheetDialogWrapper`, con su propia `Window`), así que el
 * inset hay que despacharlo sobre el `AndroidComposeView` de ESE diálogo —se
 * encuentra con `ShadowDialog.getLatestDialog()`, el mecanismo con el que
 * Robolectric deja inspeccionar cualquier diálogo mostrado.
 *
 * Y la protección misma deja de ser nuestra: `BottomSheetDefaults.windowInsets`
 * de M3 1.3.0 es literalmente `WindowInsets.safeDrawing.only(Bottom)` —leído en
 * `SheetDefaults.kt` del jar de fuentes cacheado por Gradle— y M3 lo aplica con
 * `.windowInsetsPadding(...)` ANTES de pintar el contenido que le pasamos. Por
 * eso ya no hace falta un `navigationBarsPadding()` propio, y por eso la
 * protección contra ESTE defecto es hoy la misma que ya tiene `HojaDeLaFicha` —
 * que nunca tuvo un `navigationBarsPadding()` a mano ni una prueba de geometría
 * como ésta, porque M3 ya se lo daba gratis. Las dos redes que la versión vieja
 * de este archivo traía además de la de "Continuar" —que el inset lo aplicara la
 * hoja y no el contenedor de afuera, y que el inset de arriba no metiera una
 * franja muerta— se retiran con la misma razón: son responsabilidad de M3, no de
 * nuestra cadena de modificadores, y M3 las cumple por construcción
 * (`WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)` es bottom-only,
 * nunca top).
 *
 * ## El control positivo de esta versión
 *
 * En vez de una sonda `SideEffect` dentro de la composición (no hay dónde
 * ponerla: el `content` de `ModalBottomSheet` es un detalle de M3, no algo que
 * este módulo escriba), el control es **diferencial**: se mide dónde cae
 * "Continuar" ANTES de despachar el inset y otra vez DESPUÉS, contra la MISMA
 * composición. Si el inset nunca llegara a Compose, las dos medidas
 * coincidirían y la aserción de abajo fallaría — que es justo lo que hacía el
 * defecto original.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaHojaDelAbonoNoQuedaBajoLaBarraTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `los tres testTag y el scrim de M3 llegan a semantics`() {
        val dialogo = montarYEncontrarDialogo()

        assertEquals(
            "el scrim de M3 no llegó a semantics",
            1,
            composeTestRule.onAllNodesWithContentDescription(DESCRIPCION_DEL_SCRIM)
                .fetchSemanticsNodes().size
        )
        assertEquals("la hoja no llegó a semantics", 1, cuantos(HOJA_DE_ABONO_TAG))
        assertEquals("el botón no llegó a semantics", 1, cuantos(CONTINUAR_CON_LA_CUENTA_TAG))
        assertEquals(
            "la hoja no pintó una opción por cuenta cobrable",
            cuentas().size,
            cuantos(OPCION_DE_CUENTA_TAG)
        )
        assertTrue("el Dialog de M3 no quedó mostrado", dialogo.isShowing)
    }

    @Test
    fun `el boton continuar sube cuando llega el inset de la barra de navegacion`() {
        val dialogo = montarYEncontrarDialogo()

        val antes = fondoDelBoton()
        despacharInsetDeNavegacion(dialogo)
        val despues = fondoDelBoton()

        // Control positivo: si el inset nunca llegara a Compose, `antes` y
        // `despues` serían el mismo número y esta resta sería cero — que es
        // exactamente lo que medía el defecto original (`navigationBarsPadding()`
        // sin inset real detrás no mueve nada).
        val subioAlMenosElInset = antes - despues
        assertTrue(
            "\"Continuar\" no subió al despachar el inset (antes=$antes, despues=$despues): " +
                "la medición no prueba nada, el inset no está llegando a la hoja",
            subioAlMenosElInset >= enPx(ALTO_DE_LA_BARRA_DE_NAVEGACION) - TOLERANCIA_EN_PX
        )
        // Y con el inset puesto, el botón termina ARRIBA del borde de la ventana
        // de navegación: la condición necesaria (no que el dedo llegue —eso no
        // se puede probar en Compose— sino que el botón no caiga bajo la línea).
        val bordeDeLaBarra = dialogo.window!!.decorView.height - enPx(ALTO_DE_LA_BARRA_DE_NAVEGACION)
        assertTrue(
            "\"Continuar\" termina en y=$despues y la barra de navegación arranca en " +
                "y=$bordeDeLaBarra: el botón queda debajo de la ventana del sistema",
            despues <= bordeDeLaBarra + TOLERANCIA_EN_PX
        )
    }

    // -----------------------------------------------------------------------

    private fun montarYEncontrarDialogo(): Dialog {
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
                }
            }
        }
        composeTestRule.waitForIdle()
        return requireNotNull(ShadowDialog.getLatestDialog()) {
            "no se encontró el Dialog del ModalBottomSheet: la prueba no probaría nada"
        }
    }

    /** El borde inferior de "Continuar", en la ventana del `Dialog` del sheet. */
    private fun fondoDelBoton(): Float {
        composeTestRule.waitForIdle()
        return composeTestRule.onNodeWithTag(CONTINUAR_CON_LA_CUENTA_TAG)
            .fetchSemanticsNode()
            .boundsInWindow
            .bottom
    }

    /**
     * El inset va sobre el `AndroidComposeView` DENTRO del `Dialog` del sheet, no
     * sobre el de la composición de afuera: M3 monta el `ModalBottomSheet` en su
     * propia `Window` (`ModalBottomSheetDialogWrapper`), así que es ahí donde
     * Compose instala el listener que lo recibe.
     */
    private fun despacharInsetDeNavegacion(dialogo: Dialog) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                Insets.of(0, 0, 0, enPx(ALTO_DE_LA_BARRA_DE_NAVEGACION))
            )
            .build()
        val decor = requireNotNull(dialogo.window?.decorView) {
            "el Dialog del sheet no tiene decorView: la prueba no probaría nada"
        }
        composeTestRule.runOnUiThread {
            vistasDeCompose(decor).forEach { vista ->
                ViewCompat.dispatchApplyWindowInsets(vista, insets)
            }
        }
        composeTestRule.waitForIdle()
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
         * modo de tres botones del SM-A256E, donde el dueño midió el defecto.
         */
        val ALTO_DE_LA_BARRA_DE_NAVEGACION = 48.dp

        /** Medio píxel: los bordes se acumulan en `Float` y se comparan en px enteros. */
        const val TOLERANCIA_EN_PX = 0.5f

        /**
         * `Strings.CloseSheet` de M3 1.3.0 — "Close sheet" en inglés. Ver el
         * KDoc de `DESCRIPCION_DEL_SCRIM` en `LaHojaDelAbonoDejaElegirCuentaTest`
         * para de dónde sale exactamente y por qué no es el que parecía obvio
         * leyendo sólo el recurso de `material3-android`.
         */
        const val DESCRIPCION_DEL_SCRIM = "Close sheet"
    }
}
