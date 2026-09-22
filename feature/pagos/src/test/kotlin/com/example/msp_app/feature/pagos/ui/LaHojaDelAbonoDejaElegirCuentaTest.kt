package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.CuentaDelAbono
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.ui.components.CONTINUAR_CON_LA_CUENTA_TAG
import com.example.msp_app.feature.pagos.ui.components.HojaDeAbono
import com.example.msp_app.feature.pagos.ui.components.OPCION_DE_CUENTA_TAG
import com.example.msp_app.feature.pagos.ui.components.VELO_DEL_ABONO_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **La hoja "¿A cuál cuenta?" responde a los dedos.**
 *
 * `LaHojaDelAbonoNoQuedaBajoLaBarraTest` mide dónde CAEN los controles de esta
 * hoja y cuenta sus nodos de semántica, pero **no le da un solo clic a ninguno**:
 * hasta este archivo, la función principal de la hoja —elegir a cuál cuenta
 * entra el abono— no tenía red. Eso importa porque las dos trampas de toque que
 * este módulo ya documenta viven exactamente en la cadena de modificadores que
 * esta hoja usa:
 *
 * 1. Un `clip` con esquinas DESIGUALES manda el hit-test por `Outline.Rounded`
 *    → `isInPath` → `Path.op`, que sin gráficos nativos deja los toques de los
 *    descendientes en cero (`HojaDeConfirmacion`, y medido otra vez al escribir
 *    [com.example.msp_app.feature.pagos.ui.components.HojaDelContacto]).
 * 2. Un gesto del PADRE le gana a los `clickable` de sus hijos, así que un
 *    `detectTapGestures {}` puesto para "comerse el toque" se come también el de
 *    los controles de adentro (`HojaDeConfirmacion`, medido con contadores).
 *
 * [HojaDeAbono] tiene **las dos construcciones**. Si el toque no llegara, el
 * cobrador con dos cuentas abiertas no podría elegir ninguna y el abono se
 * quedaría atorado en la puerta — sin un solo rastro en logcat, porque no habría
 * error: simplemente nada pasaría.
 *
 * El velo se afirma aparte a propósito: es hermano de la hoja, no su padre, así
 * que un velo que responda no prueba nada sobre los controles de adentro.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaHojaDelAbonoDejaElegirCuentaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var elegida: Int? = null
    private var continuaciones = 0
    private var cierres = 0

    @Test
    fun `tocar una opcion elige esa cuenta`() {
        montar()

        composeTestRule.onAllNodesWithTag(OPCION_DE_CUENTA_TAG)[LA_SEGUNDA].performClick()

        assertEquals(
            "el toque no llegó a la opción: el cobrador no puede elegir cuenta",
            cuentas()[LA_SEGUNDA].ventaId,
            elegida
        )
    }

    @Test
    fun `tocar continuar confirma la cuenta elegida`() {
        montar()

        composeTestRule.onNodeWithTag(CONTINUAR_CON_LA_CUENTA_TAG).performClick()

        assertEquals("el toque no llegó a \"Continuar\"", 1, continuaciones)
    }

    /**
     * El velo cancela. Es hermano de la hoja —no su padre— justo para que su
     * gesto no le gane a los controles de adentro, así que este caso y los dos
     * de arriba tienen que pasar **a la vez**: si sólo pasara éste, el gesto del
     * velo se estaría comiendo los otros.
     */
    @Test
    fun `tocar el velo cancela sin elegir nada`() {
        montar()

        composeTestRule.onNodeWithTag(VELO_DEL_ABONO_TAG).performClick()

        assertEquals("el velo no cerró", 1, cierres)
        assertEquals("cancelar no puede elegir cuenta", null, elegida)
        assertEquals("cancelar no puede continuar", 0, continuaciones)
    }

    private fun montar() {
        val cobrables = cuentas()
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HojaDeAbono(
                        cuentas = cobrables,
                        // Preseleccionada la primera: "Continuar" sólo se
                        // habilita con una cuenta elegida, y lo que este
                        // archivo mide es el TOQUE, no esa regla.
                        elegida = cobrables.first().ventaId,
                        onElegir = { elegida = it },
                        onContinuar = { continuaciones++ },
                        onCerrar = { cierres++ }
                    )
                }
            }
        }
    }

    /** Las dos cuentas cobrables del mock: con una sola, esta hoja ni aparece. */
    private fun cuentas(): List<VentaDelCliente> =
        CuentaDelAbono.cobrables(PagosFixtures.detalleCliente().ventas)

    private companion object {
        /**
         * La SEGUNDA opción, no la primera: la primera llega preseleccionada, y
         * un `onElegir` que nunca se disparara dejaría `elegida` con el valor
         * correcto por accidente.
         */
        const val LA_SEGUNDA = 1
    }
}
