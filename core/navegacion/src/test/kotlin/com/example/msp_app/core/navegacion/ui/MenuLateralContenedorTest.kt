package com.example.msp_app.core.navegacion.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.component.LocalThemeReveal
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MenuLateralContenedorTest : RobolectricTestBase() {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var abiertos = 0
    private var aperturaVista: AperturaDelMenu? = null

    private fun montar(reducido: Boolean = false, onAlternarTema: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reducido) {
                MenuLateralContenedor(
                    hoja = { cerrar, apertura ->
                        aperturaVista = apertura
                        Button(
                            onClick = cerrar,
                            modifier = Modifier.testTag("cerrar")
                        ) { Text("Cerrar") }
                    },
                    onAbierto = { abiertos += 1 },
                    onAlternarTema = onAlternarTema
                ) { abrir ->
                    Button(onClick = abrir, modifier = Modifier.testTag("abrir")) { Text("Abrir") }
                }
            }
        }
    }

    private fun abrir() {
        compose.onNodeWithTag("abrir").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `cerrado no se ve y abrir lo muestra y avisa una vez`() {
        montar()
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
        abrir()
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsDisplayed()
        assertEquals(1, abiertos)

        // Cobra el ancho REAL de la hoja, no sólo su `left`: que `MspThemeRevealHost`
        // (Ruling R2) aplique `fillMaxSize()` alrededor del `ModalDrawerSheet` no debe
        // estirarlo a lo ancho de la raíz.
        val limitesHoja = compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot()
        val limitesRaiz = compose.onRoot().getBoundsInRoot()
        val anchoHoja = (limitesHoja.right - limitesHoja.left).value
        val anchoRaiz = (limitesRaiz.right - limitesRaiz.left).value
        assertEquals(ANCHO_DEL_MENU.value, anchoHoja, 0.5f)
        assertTrue(
            "la hoja ($anchoHoja) debe ser angosta que la raíz ($anchoRaiz)",
            anchoHoja < anchoRaiz
        )
    }

    @Test
    fun `atras con el menu abierto lo cierra y no deja la pantalla`() {
        montar()
        abrir()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
        assertFalse(
            "atrás salió de la pantalla en vez de cerrar el menú",
            compose.activity.isFinishing
        )
    }

    @Test
    fun `cerrar el menu limpia la busqueda y las secciones tocadas`() {
        montar()
        abrir()
        compose.runOnUiThread {
            aperturaVista!!.consulta = "garantias"
            aperturaVista!!.tocar("COBRO")
        }

        compose.onNodeWithTag("cerrar").performClick()
        compose.waitForIdle()

        assertEquals("", aperturaVista!!.consulta)
        assertTrue(aperturaVista!!.tocadas.isEmpty())
    }

    @Test
    fun `con movimiento reducido abre sin animacion`() {
        montar(reducido = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("abrir").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        assertEquals(0f, compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun `control positivo, sin movimiento reducido la entrada se anima`() {
        montar(reducido = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("abrir").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        assertTrue(compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value < 0f)
    }

    /**
     * **Ruling R2, hallazgo del revisor: [MenuLateralContenedor] era el único eslabón de la
     * cadena con un default para `onAlternarTema`.** [MspThemeRevealHost] exige su
     * `onToggleTheme` sin default; con `onAlternarTema: () -> Unit = {}` un caller que lo
     * olvidara dejaba el botón de tema de la hoja mudo SIN error de compilación. Ese default se
     * quitó (parámetro obligatorio, ver la firma de [MenuLateralContenedor]); este par cobra la
     * otra mitad, la de la prueba roja: que [hoja] de verdad quede DENTRO de
     * [MspThemeRevealHost] y no de un `MspTheme` pelado — que es lo único que hace que, en
     * producción, un toque en el toggle de tema de la hoja llegue a `onAlternarTema`.
     *
     * **Por qué el par mide la instalación del host y no un tap real completando la reveal**:
     * se intentó (ver `task-4-report.md`) click real sobre un [MspThemeToggle] compuesto dentro
     * de [hoja], tanto por `testTag` como por descripción — AMBOS caminos midieron que el tap
     * nunca llega a ningún `onToggle`/`onToggleTheme` cuando la hoja vive dentro del
     * `ModalDrawerSheet`/`ModalNavigationDrawer` de este contenedor (0 invocaciones en los dos
     * casos, sin excepción — un límite nuevo, propio de esta anidación, no sólo el ya conocido
     * `toImageBitmap()` de `MspThemeRevealHostTest`). En vez de inventar una prueba que mida otra
     * cosa, este par usa la MISMA técnica —sin gestos— con la que `MspThemeRevealHostTest`
     * (`:core:designsystem`) prueba el mecanismo: leer [LocalThemeReveal] dentro del contenido
     * para saber si el host está instalado.
     */
    @Test
    fun `con movimiento reducido no instala la reveal dentro de la hoja (R2, rama de respaldo)`() {
        var revealInstalada: Boolean? = null
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MenuLateralContenedor(
                    hoja = { _, _ -> revealInstalada = LocalThemeReveal.current != null },
                    onAbierto = {},
                    onAlternarTema = {}
                ) { abrir ->
                    Button(onClick = abrir, modifier = Modifier.testTag("abrir")) { Text("Abrir") }
                }
            }
        }

        compose.onNodeWithTag("abrir").performClick()
        compose.waitForIdle()

        assertFalse(
            "con movimiento reducido no debe instalarse ningún ThemeRevealController " +
                "(garantía anti-cuelgue de MspThemeRevealHost)",
            revealInstalada == true
        )
    }

    /**
     * El contraste del par: sin movimiento reducido, [MenuLateralContenedor] SÍ envuelve [hoja]
     * en [MspThemeRevealHost] — [LocalThemeReveal] llega no nulo adentro. Si alguien revirtiera
     * la hoja a un `MspTheme` pelado (perdiendo R2), esto daría `null` y la prueba enrojecería.
     */
    @Test
    fun `sin movimiento reducido MenuLateralContenedor envuelve la hoja en MspThemeRevealHost (R2)`() {
        var revealInstalada: Boolean? = null
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides false) {
                MenuLateralContenedor(
                    hoja = { _, _ -> revealInstalada = LocalThemeReveal.current != null },
                    onAbierto = {},
                    onAlternarTema = {}
                ) { abrir ->
                    Button(onClick = abrir, modifier = Modifier.testTag("abrir")) { Text("Abrir") }
                }
            }
        }

        compose.onNodeWithTag("abrir").performClick()
        compose.waitForIdle()

        assertTrue(
            "sin host instalado, el toggle de tema de la hoja haría crossfade en vez de " +
                "reveal (R2) — MenuLateralContenedor dejó de envolver la hoja en MspThemeRevealHost",
            revealInstalada == true
        )
    }
}
