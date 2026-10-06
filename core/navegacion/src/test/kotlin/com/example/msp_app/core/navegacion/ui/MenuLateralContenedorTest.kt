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
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

/**
 * **Causa raíz real (re-revisión ronda 2, MEDIDO) de por qué un `performClick()` sobre un hijo
 * de [hoja] no llegaba a su propio `onClick`/`onToggle`.** No es "cualquier nodo dentro del
 * cajón cierra el drawer" (diagnóstico anterior, REFUTADO más abajo): es que `ModalDrawerSheet`
 * pinta con una forma ASIMÉTRICA (`DrawerDefaults.shape`, esquinas redondeadas sólo del lado
 * contrario a la bisagra) y el modo gráfico LEGACY de Robolectric (el default de
 * [RobolectricTestBase]) no resuelve bien la prueba de recorte (`Outline`/`clipPath`) contra esa
 * forma: el toque sintético de `performClick()` nunca se considera "dentro" del hijo recortado y
 * cae al scrim de atrás, que es justo lo que cierra el cajón sin pasar por el hijo. Con
 * [GraphicsMode.Mode.NATIVE] (mismo mecanismo que ya usa `MspScreenshotTest`,
 * `:core:designsystem`, para necesitar render de píxeles reales) el recorte se resuelve bien y el
 * toque SÍ llega.
 *
 * **Sin esta anotación, `cerrar el menu limpia la busqueda y las secciones tocadas` era un falso
 * verde**: pasaba aun cambiando su `Button(onClick = cerrar, ...)` por `Button(onClick = {},
 * ...)` — es decir, pasaba SIN que `cerrar()` se ejecutara nunca, porque el cierre que la prueba
 * observaba (`apertura.reiniciar()`) lo disparaba el scrim, no el botón. En NATIVE esa misma
 * sustitución pone la prueba roja (`expected:<[]> but was:<[garantias]>`), como debe.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
     * **Ruling R2, hallazgo del revisor: faltaba la prueba de que tocar el toggle de tema real
     * dentro de [hoja] llega al `onAlternarTema` conectado.** Antes se creía imposible de medir
     * en Robolectric; era un falso negativo del modo gráfico LEGACY (ver el KDoc de la clase) —
     * resuelto con [GraphicsMode.Mode.NATIVE], el tap SÍ llega.
     *
     * [alternar] se pasa IDÉNTICO como `onAlternarTema` del contenedor y como `onToggle` del
     * [MspThemeToggle] de la hoja: es exactamente cómo lo cablea el menú real (la Task 10 hace lo
     * mismo con `onAlternarTema` del ViewModel). Con movimiento reducido el host no instala la
     * reveal ([MspThemeRevealHost] cae directo a `onToggle`), así que el tap es síncrono y no
     * necesita completar ninguna animación.
     */
    @Test
    fun `tocar el tema de la hoja llama al onAlternarTema conectado (movimiento reducido)`() {
        var alternados = 0
        val alternar: () -> Unit = { alternados += 1 }
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MenuLateralContenedor(
                    hoja = { _, _ -> MspThemeToggle(darkTheme = false, onToggle = alternar) },
                    onAbierto = {},
                    onAlternarTema = alternar
                ) { abrir ->
                    Button(onClick = abrir, modifier = Modifier.testTag("abrir")) { Text("Abrir") }
                }
            }
        }
        abrir()
        compose.onNodeWithTag("msp_theme_toggle").performClick() // THEME_TOGGLE_TAG es internal
        compose.waitForIdle()

        assertEquals(1, alternados)
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
     * **Por qué este par, además de la prueba con tap real**: la prueba
     * `tocar el tema de la hoja llama al onAlternarTema conectado` (más abajo) ya cobra, con un
     * gesto real en [GraphicsMode.Mode.NATIVE], que el tap llega — pero sólo bajo movimiento
     * reducido (la rama sin reveal). Este par cubre la OTRA mitad sin depender de ningún gesto:
     * usa la misma técnica que `MspThemeRevealHostTest` (`:core:designsystem`) prueba el
     * mecanismo — leer [LocalThemeReveal] dentro del contenido para saber si el host está
     * instalado —, así confirma en las DOS ramas (con y sin movimiento reducido) que
     * [MspThemeRevealHost] envuelve [hoja] de verdad, sin necesitar que la rama CON reveal
     * complete su animación (eso sigue sin medirse con un tap real; ver KDoc de
     * [MspThemeRevealHostTest] sobre `toImageBitmap()` en Robolectric).
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
     * la hoja a un `MspTheme` pelado (perdiendo R2), esto daría `null` y la prueba enrojecería
     * (control de rojo medido: ver `task-4-report.md`).
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
        compose.waitForIdle()

        assertTrue(
            "sin host instalado, el toggle de tema de la hoja haría crossfade en vez de " +
                "reveal (R2) — MenuLateralContenedor dejó de envolver la hoja en MspThemeRevealHost",
            revealInstalada == true
        )
    }
}
