package com.example.msp_app.feature.pagos.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.CuentaDelAbono
import com.example.msp_app.feature.pagos.ui.components.AccionDeCondonar
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import com.example.msp_app.feature.pagos.ui.components.HOJA_DEL_CONTACTO_TAG
import com.example.msp_app.feature.pagos.ui.components.HOJA_DE_ABONO_TAG
import com.example.msp_app.feature.pagos.ui.components.HojaDeAbono
import com.example.msp_app.feature.pagos.ui.components.HojaDelContacto
import com.example.msp_app.feature.pagos.ui.components.MENU_CONDONAR_TAG
import com.example.msp_app.feature.pagos.ui.components.MENU_DEL_DOCK_TAG
import com.example.msp_app.feature.pagos.ui.components.MenuDelDock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * **El botón atrás de Android cierra la hoja abierta, no la pantalla.**
 *
 * El defecto, en palabras del dueño: *"cuando está abierto eso, si le doy el
 * botón de navegación de Android de regresar, no lo cierra el sheet sino que me
 * regresa a la pantalla anterior"*. Pasaba con las tres superficies de este
 * módulo que entonces NO eran `ModalBottomSheet` —la hoja "¿a cuál cuenta?"
 * (abono y condonación), la de "¿qué abrir?" de un contacto y el menú "⋯" del
 * dock—: vivían en el árbol de la pantalla, y nada las enganchaba al
 * despachador de atrás, así que el evento llegaba directo al `NavHost`.
 *
 * ## La hoja "¿a cuál cuenta?" cambió de mecanismo, no de comportamiento
 *
 * Task del 2026-09-26 (misma animación que [HojaDeLaFicha]): [HojaDeAbono] pasó
 * a ser un `ModalBottomSheet` de M3, y el `BackHandler` propio se retiró — ya no
 * hace falta, M3 cierra por atrás solo (`ModalBottomSheetProperties.
 * shouldDismissOnBackPress`, `true` por defecto). Pero el mecanismo de prueba de
 * los dos casos de abajo (`abono`, `condonar`) tuvo que cambiar de verdad, no
 * sólo de nombre: `ModalBottomSheet` se monta en su propio `ComponentDialog`
 * (`ModalBottomSheetDialogWrapper`, forkeado de `Dialog` de Compose), con su
 * PROPIO `onBackPressedDispatcher` — uno que NO es el mismo que
 * `LocalOnBackPressedDispatcherOwner.current` ve en la composición de AFUERA.
 * Medido en la fuente de M3 1.3.0 (`ModalBottomSheet.android.kt`): el diálogo
 * llama `window.decorView.setViewTreeOnBackPressedDispatcherOwner(this)` —su
 * propio dispatcher, no el heredado— y `ComponentDialog.onBackPressed()`
 * reenvía a ESE dispatcher. Así que llamar al dispatcher del "NavHost" de este
 * archivo (como hacían `HojaDelContacto` y el menú del dock, que siguen sin ser
 * `ModalBottomSheet`) no cruzaría al diálogo: hay que encontrar el `Dialog` real
 * que Robolectric registró (`ShadowDialog.getLatestDialog()`) y llamar a SU
 * `onBackPressed()`, que es exactamente lo que hace el sistema cuando entrega la
 * tecla atrás a la ventana que tiene el foco.
 *
 * ## Cómo se mide "no se navegó"
 *
 * El `NavHost` se imita con un [BackHandler] **montado antes** que la hoja: el
 * despachador atiende al último callback registrado, igual que en la app, donde
 * el del `NavHost` se registra al montar el grafo y el de la hoja al abrirla.
 * Cada prueba tiene su control positivo: tras cerrar la hoja, un segundo atrás
 * **sí** tiene que llegar al "NavHost". Sin ese control, `navegaciones == 0`
 * pasaría en verde con un "NavHost" que nunca recibe nada. Para `HojaDeAbono` el
 * control positivo usa el dispatcher de AFUERA —el único que existe una vez que
 * el diálogo del sheet ya se destruyó.
 *
 * ## El control de reversión de estas dos, medido
 *
 * Revertir `HojaDeAbono` para que pase
 * `properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)` y
 * correr sólo estas dos pruebas pone **las dos en rojo**: `dialogoDelSheet()`
 * sigue encontrando el diálogo (nunca se cierra) y `cierres` se queda en 0 —
 * verificado el 2026-09-26, restaurado de inmediato, sin dejar el parámetro
 * puesto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class AtrasCierraLasHojasTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var despachador: OnBackPressedDispatcher
    private var navegaciones = 0
    private var cierres = 0

    @Test
    fun `atras cierra la hoja de la cuenta sin salir de la pantalla`() {
        montar { cerrar ->
            HojaDeAbono(
                cuentas = CuentaDelAbono.cobrables(PagosFixtures.detalleCliente().ventas),
                elegida = null,
                onElegir = {},
                onContinuar = {},
                onCerrar = cerrar
            )
        }
        composeTestRule.onNodeWithTag(HOJA_DE_ABONO_TAG).assertIsDisplayed()

        atrasEnElDialogoDelSheet()

        assertEquals("atrás no cerró la hoja", 1, cierres)
        assertEquals("atrás salió de la pantalla con la hoja abierta", 0, navegaciones)
        composeTestRule.onNodeWithTag(HOJA_DE_ABONO_TAG).assertDoesNotExist()
        controlPositivo()
    }

    @Test
    fun `atras cierra la hoja de condonar sin salir de la pantalla`() {
        montar { cerrar ->
            HojaDeAbono(
                cuentas = CuentaDelAbono.cobrables(PagosFixtures.detalleCliente().ventas),
                elegida = null,
                onElegir = {},
                onContinuar = {},
                onCerrar = cerrar,
                destino = DestinoDeLaCuenta.CONDONACION
            )
        }
        composeTestRule.onNodeWithTag(HOJA_DE_ABONO_TAG).assertIsDisplayed()

        atrasEnElDialogoDelSheet()

        assertEquals("atrás no cerró la hoja de condonar", 1, cierres)
        assertEquals("atrás salió de la pantalla con la hoja abierta", 0, navegaciones)
        composeTestRule.onNodeWithTag(HOJA_DE_ABONO_TAG).assertDoesNotExist()
        controlPositivo()
    }

    @Test
    fun `atras cierra la hoja del contacto sin salir de la pantalla`() {
        montar { cerrar ->
            HojaDelContacto(onVerUbicacion = {}, onVerTicket = {}, onCerrar = cerrar)
        }
        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertIsDisplayed()

        atras()

        assertEquals("atrás no cerró la hoja del contacto", 1, cierres)
        assertEquals("atrás salió de la pantalla con la hoja abierta", 0, navegaciones)
        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertDoesNotExist()
        controlPositivo()
    }

    /**
     * El menú guarda su `abierto` adentro, así que no hay `onCerrar` que
     * contar: lo que se afirma es que el renglón se fue. Se abre con el mismo
     * toque que usa el cobrador.
     */
    @Test
    fun `atras cierra el menu del dock sin salir de la pantalla`() {
        montar { _ ->
            DockDeAcciones(
                textoPrimario = "Registrar abono",
                onPrimario = {},
                onVisita = {},
                menu = MenuDelDock(condonar = AccionDeCondonar(onAbrir = {}, cuentas = 2))
            )
        }
        composeTestRule.onNodeWithTag(MENU_DEL_DOCK_TAG).performClick()
        composeTestRule.onNodeWithTag(MENU_CONDONAR_TAG).assertIsDisplayed()

        atras()

        assertEquals("atrás salió de la pantalla con el menú abierto", 0, navegaciones)
        composeTestRule.onNodeWithTag(MENU_CONDONAR_TAG).assertDoesNotExist()
        controlPositivo()
    }

    /**
     * Monta el "NavHost" y, encima, la hoja mientras siga abierta. `cerrar`
     * cuenta el cierre y desmonta la hoja, que es lo que hace la pantalla real.
     */
    private fun montar(hoja: @Composable (cerrar: () -> Unit) -> Unit) {
        composeTestRule.setContent {
            despachador = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                .onBackPressedDispatcher
            BackHandler { navegaciones++ }
            var abierta by remember { mutableStateOf(true) }
            MspTheme(darkTheme = false, animateColors = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (abierta) {
                        hoja {
                            cierres++
                            abierta = false
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun atras() {
        composeTestRule.runOnIdle { despachador.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /**
     * El atrás de [HojaDeAbono]: no pasa por [despachador] —ver el KDoc de esta
     * clase—, pasa por el `Dialog` real que M3 registró para el
     * `ModalBottomSheet`. `ShadowDialog.getLatestDialog()` es el mismo mecanismo
     * con el que Robolectric deja inspeccionar cualquier diálogo mostrado; llamar
     * a `onBackPressed()` sobre él es lo que el sistema hace cuando entrega la
     * tecla atrás a la ventana con el foco.
     */
    private fun atrasEnElDialogoDelSheet() {
        composeTestRule.waitForIdle()
        val dialogo = requireNotNull(ShadowDialog.getLatestDialog()) {
            "no se encontró el Dialog del ModalBottomSheet: la prueba no probaría nada"
        }
        assertNotNull("el Dialog del sheet ya no está en pantalla", dialogo.window)
        composeTestRule.runOnIdle { dialogo.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /** Ya sin hoja, atrás SÍ tiene que llegar al "NavHost". */
    private fun controlPositivo() {
        atras()
        assertEquals("el control no llegó al NavHost: la prueba no mide nada", 1, navegaciones)
    }
}
