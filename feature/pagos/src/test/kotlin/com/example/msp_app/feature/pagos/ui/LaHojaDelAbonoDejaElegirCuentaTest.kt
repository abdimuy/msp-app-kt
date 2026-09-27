package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.CuentaDelAbono
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.ui.components.CONTINUAR_CON_LA_CUENTA_TAG
import com.example.msp_app.feature.pagos.ui.components.CuerpoDeAbono
import com.example.msp_app.feature.pagos.ui.components.HojaDeAbono
import com.example.msp_app.feature.pagos.ui.components.OPCION_DE_CUENTA_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **La hoja "¿A cuál cuenta?" responde a los dedos.**
 *
 * `LaHojaDelAbonoNoQuedaBajoLaBarraTest` mide dónde CAEN los controles de esta
 * hoja y cuenta sus nodos de semántica, pero **no le da un solo clic a ninguno**:
 * este archivo es la red de la función principal de la hoja —elegir a cuál
 * cuenta entra el abono.
 *
 * ## Por qué las dos primeras pruebas montan [CuerpoDeAbono] y no [HojaDeAbono]
 *
 * Desde la task del 2026-09-26 (misma animación que `HojaDeLaFicha`),
 * [HojaDeAbono] es un `ModalBottomSheet` de M3, y `performClick()` bajo
 * Robolectric **no cruza a la ventana del `Popup`/`Dialog`** donde M3 monta la
 * hoja — mismo hallazgo, ya medido en este repo, que documenta `PrintSheetTest`
 * de `:feature:collectionReport` para `PrintSheet`/`PrintSheetBody`. Elegir una
 * opción y tocar "Continuar" se prueban entonces contra [CuerpoDeAbono] directo,
 * sin el `ModalBottomSheet` alrededor — el mismo patrón que ya usa
 * `CuerpoDeLaFicha` para sus goldens.
 *
 * Antes de la extracción, esta hoja tenía las dos trampas de toque que este
 * módulo documenta —un `clip` con esquinas desiguales matando el hit-test bajo
 * Robolectric, y un gesto del padre comiéndose el de los hijos ([HojaDeConfirmacion][
 * com.example.msp_app.feature.pagos.ui.components.HojaDeConfirmacion])—; las dos
 * se fueron con el `Box` a mano que dibujaba el velo y el fondo. [CuerpoDeAbono]
 * ya no tiene ninguna: no dibuja velo ni fondo, M3 los pone.
 *
 * ## El "velo" ya no es nuestro: es el scrim de M3
 *
 * `tocar el velo cancela` prueba el `ModalBottomSheet` real (no [CuerpoDeAbono]):
 * el scrim de M3 expone su propia acción de semántica —`onClick` con
 * `contentDescription` "Close sheet"— para accesibilidad, y
 * `performClick()` sobre un nodo con esa acción explícita invoca el callback
 * directo sin pasar por un gesto de puntero, así que sí cruza al `Popup` (medido:
 * ver el resultado de la corrida al pie de este archivo). Si algún día ese
 * `contentDescription` deja de existir o cambia de texto en una versión nueva de
 * M3, esta prueba se rompe por "no encontrado" y no en silencio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaHojaDelAbonoDejaElegirCuentaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var elegida: Int? = null
    private var continuaciones = 0
    private var cierres = 0

    @Test
    fun `tocar una opcion elige esa cuenta`() {
        montarCuerpo()

        composeTestRule.onAllNodesWithTag(OPCION_DE_CUENTA_TAG)[LA_SEGUNDA].performClick()

        assertEquals(
            "el toque no llegó a la opción: el cobrador no puede elegir cuenta",
            cuentas()[LA_SEGUNDA].ventaId,
            elegida
        )
    }

    @Test
    fun `tocar continuar confirma la cuenta elegida`() {
        montarCuerpo()

        composeTestRule.onNodeWithTag(CONTINUAR_CON_LA_CUENTA_TAG).performClick()

        assertEquals("el toque no llegó a \"Continuar\"", 1, continuaciones)
    }

    /**
     * El scrim de M3 cancela: nada se registra. A diferencia de las dos pruebas
     * de arriba, ésta monta [HojaDeAbono] completa —el `ModalBottomSheet`— porque
     * lo que se prueba es SU scrim, no uno nuestro.
     */
    @Test
    fun `tocar el velo cancela sin elegir nada`() {
        montarHoja()

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_DEL_SCRIM).performClick()
        composeTestRule.waitForIdle()

        assertEquals("el velo no cerró", 1, cierres)
        assertEquals("cancelar no puede elegir cuenta", null, elegida)
        assertEquals("cancelar no puede continuar", 0, continuaciones)
    }

    private fun montarCuerpo() {
        val cobrables = cuentas()
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CuerpoDeAbono(
                        cuentas = cobrables,
                        // Preseleccionada la primera: "Continuar" sólo se
                        // habilita con una cuenta elegida, y lo que este
                        // archivo mide es el TOQUE, no esa regla.
                        elegida = cobrables.first().ventaId,
                        onElegir = { elegida = it },
                        onContinuar = { continuaciones++ }
                    )
                }
            }
        }
    }

    private fun montarHoja() {
        val cobrables = cuentas()
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HojaDeAbono(
                        cuentas = cobrables,
                        elegida = cobrables.first().ventaId,
                        onElegir = { elegida = it },
                        onContinuar = { continuaciones++ },
                        onCerrar = { cierres++ }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
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

        /**
         * `Strings.CloseSheet` de M3 1.3.0 (`ModalBottomSheet.kt:412`), resuelto
         * por `getString` a `androidx.compose.ui.R.string.close_sheet` —el `R`
         * de `:ui`, no el de `material3`, según el import de
         * `ModalBottomSheet.android.kt`—, "Close sheet" en inglés. Confirmado
         * MEDIDO bajo Robolectric (SDK 33, sin qualifier de locale): la primera
         * versión de esta prueba asumía, LEYENDO sólo `material3-release.aar`,
         * que era `m3c_bottom_sheet_dismiss_description` ("Dismiss bottom
         * sheet") — no lo es, ese recurso es de `BottomSheetScaffold`, no del
         * scrim del `ModalBottomSheet`.
         */
        const val DESCRIPCION_DEL_SCRIM = "Close sheet"
    }
}
