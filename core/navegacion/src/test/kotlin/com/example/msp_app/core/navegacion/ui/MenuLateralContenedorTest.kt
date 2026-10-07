package com.example.msp_app.core.navegacion.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
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

    /**
     * Ruling R7: corrige el plan original (umbral absoluto de 50 %, efectivo ~55 % y frágil).
     * En vez de un porcentaje fijo, compara el recorrido REAL del resorte (medido con un tap
     * real sobre el contenedor de producción, igual que el resto de esta clase) contra el de la
     * tween por defecto de `DrawerState.open()` — la que `abrir` usaba antes de esta tarea — EN
     * EL MISMO INSTANTE (64 ms / 4 cuadros). Es una comparación relativa: sólo cobra que el
     * resorte sea más rápido que la tween a ese instante, no una cifra concreta. Costo aceptado
     * (R7): prueba menos estricta que el 50 % absoluto del plan.
     *
     * **Por qué el lado de la tween se evalúa matemáticamente y no con un segundo cajón en
     * pantalla** (hallazgo de esta tarea, MEDIDO): montar un `ModalNavigationDrawer` de control
     * junto al contenedor real, en la MISMA composición, da resultados no deterministas — en una
     * corrida el resorte quedó congelado en su posición cerrada durante los cuatro cuadros
     * medidos (dando ~0 % en vez de los ~70 % reales), en otra la tween del cajón de control
     * saltó a 100 % de golpe; algo en tener dos `AnchoredDraggableState.anchoredDrag`
     * compitiendo por el mismo [compose.mainClock] rompe el orden de los frames, con o sin tocar
     * los dos cajones a la vez. En vez de arriesgar esa inestabilidad, la tween por defecto se
     * evalúa con la misma API que el propio `animate()` de Compose usa internamente
     * ([VectorizedAnimationSpec.getValueFromNanos], función pura, sin corrutina ni reloj) sobre
     * `TweenSpec<Float>(durationMillis = 256)` — medida en el fuente de material3 1.3.0
     * (`NavigationDrawer.kt:1160`, 2026-10-06; el mismo valor que ya documentaba el KDoc de
     * [abrir]) — normalizada a initialValue=0f/targetValue=1f, que para un `TweenSpec` da
     * exactamente la fracción de interpolación en ese instante, sin necesidad de cajón alguno.
     *
     * Antes de la implementación (Step 3), `abrir` del contenedor real también usa `open()` (la
     * misma tween que se evalúa aquí matemáticamente), así que `recorridoResorte` sale MENOR que
     * `recorridoTween` (la lectura por UI, con el desfase de 16 dp del borde cerrado real de
     * [MenuLateralContenedor] frente a [ANCHO_DEL_MENU], subestima la fracción verdadera) y
     * `recorridoResorte > recorridoTween` sale ROJO — ese es el rojo esperado del Step 2.
     */
    @Test
    fun `la entrada con resorte recorre mas que la tween por defecto en el mismo instante y no rebota`() {
        montar(reducido = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("abrir").performClick()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        val izquierdaA64Ms = compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value
        val recorridoResorte = 1f - (-izquierdaA64Ms / ANCHO_DEL_MENU.value)

        val posiciones = (1..60).map {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value
        }
        assertTrue("el menú rebotó más allá del borde: $posiciones", posiciones.all { it <= 0.5f })
        assertEquals(0f, posiciones.last(), 0.5f)

        val tweenVectorizado = tween<Float>(durationMillis = 256).vectorize(Float.VectorConverter)
        val recorridoTween = tweenVectorizado.getValueFromNanos(
            playTimeNanos = 64_000_000L,
            initialValue = AnimationVector1D(0f),
            targetValue = AnimationVector1D(1f),
            initialVelocity = AnimationVector1D(0f)
        ).value

        assertTrue(
            "el resorte (${recorridoResorte * 100}%) debe recorrer más que la tween por defecto " +
                "(${recorridoTween * 100}%) en el mismo instante (64 ms)",
            recorridoResorte > recorridoTween
        )
    }
}
