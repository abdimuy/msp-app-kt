package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.ui.AperturaPorGesto
import com.example.msp_app.core.navegacion.ui.MENU_LATERAL_TAG
import com.example.msp_app.core.navegacion.ui.MenuLateralContenedor
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.EDITAR_FICHA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * **REFUTA la afirmación del reporte original de la Task 16** ("estas dos
 * pantallas no tienen ningún botón real dentro de la franja"). Hallazgo de
 * revisión, 2026-10-06, MEDIDO: `ControlesFlotantes` (ojo + tema, y la
 * pastilla de la ficha en el cliente) vive en `DetalleClienteScreen.kt:397` /
 * `DetalleVentaScreen.kt:305`, posicionada con `Alignment.TopEnd` de
 * `inset+8dp` (`AIRE_SOBRE_LOS_CONTROLES`) a `inset+56dp`
 * (`+ALTO_DE_LOS_CONTROLES`) — DENTRO de los `inset+72dp`
 * (`ALTO_DE_LA_FRANJA`) que [com.example.msp_app.core.navegacion.ui.ZonaQueAbreElMenu]
 * intercepta en `PointerEventPass.Initial`. La afirmación original se basó en
 * `grep "ArrowBack"`, que sólo cubre la flecha de volver — el encargo pedía
 * cualquier botón de la franja, y estos dos son justo eso.
 *
 * ## Por qué `*Content` y no `*Screen` vía el grafo real
 *
 * `destinosDeCobranza` con `MenuDeLaApp(gesto = DESDE_LA_BARRA)` es la
 * envoltura REAL de producción, pero montarla en Robolectric deja estas dos
 * pantallas en `Cargando()` para siempre: medido y documentado en el KDoc de
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` ("la base de Robolectric
 * no tiene el cliente 1 ni la venta 1"), así que `ControlesFlotantes` nunca
 * llega a componerse por esa vía sin sembrar datos en Room. En vez de eso,
 * esta prueba monta [MenuLateralContenedor] REAL —la misma pieza que
 * `MenuDeLaApp` envuelve, con el mismo `gesto`— alrededor de
 * [DetalleClienteContent]/[DetalleVentaContent] REALES, con `detalle != null`
 * (`PagosFixtures`), que es justo lo que el grafo no permite medir en este
 * entorno. `:feature:pagos` NO gana una dependencia de producción sobre
 * `:core:navegacion` por esto — ver el comentario en `build.gradle.kts`, es
 * `testImplementation` nada más.
 *
 * ## `LocalReduceMotion` para el tema
 *
 * Se provee `true` por el límite conocido de `MspThemeRevealHost`
 * (`toImageBitmap()` truena en Robolectric a mitad de la animación — Ruling
 * R16 de `constraints.md`). En la práctica, en este nivel ([DetalleClienteContent]
 * en vez de [DetalleClienteScreen]) ni siquiera hace falta: `*Content` no
 * instala ningún `MspThemeRevealHost`, así que [com.example.msp_app.core.designsystem.component.LocalThemeReveal]
 * ya es `null` y `MspThemeToggle` cae directo a `onToggle()` sin reveal. Se
 * deja puesto para dejar explícita la intención y por si un día `*Content`
 * gana su propio host.
 *
 * ## Control de rojo (ver Step 2 del commit, documentado también en el reporte)
 *
 * Con `ZonaQueAbreElMenu` forzado a consumir el toque de inmediato en el pase
 * `Initial` (en vez de esperar a superar el umbral de arrastre), los cuatro
 * casos de "alterna y no abre" FALLAN: ni el ojo ni el tema disparan su
 * callback. Revertido, los cuatro vuelven a verde.
 *
 * Los tags de `MspPrivacyEyeToggle`/`MspThemeToggle` son `internal` de
 * `:core:designsystem` — mismo motivo documentado ya en
 * `MenuLateralContenedorTest` para `"msp_theme_toggle"` — así que viajan como
 * literales.
 */
class LaFranjaDelMenuNoRobaElOjoNiElTemaTest : RobolectricTestBase() {

    @get:Rule
    val compose = createComposeRule()

    private var aperturas = 0

    private fun montarCliente(ficha: FichaParaLaFicha = FichaParaLaFicha.NORMAL) {
        aperturas = 0
        val detalle = PagosFixtures.detalleCliente().let {
            if (ficha == FichaParaLaFicha.CON_ADVERTENCIA) {
                it.copy(ficha = PagosFixtures.fichaConAdvertencia())
            } else {
                it
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MspTheme(darkTheme = false, animateColors = false) {
                    var montosOcultos by remember { mutableStateOf(false) }
                    var temaOscuro by remember { mutableStateOf(false) }
                    var fichaEditada by remember { mutableStateOf(false) }
                    rastreador = Rastreador(
                        montosOcultos = { montosOcultos },
                        temaOscuro = { temaOscuro },
                        fichaEditada = { fichaEditada }
                    )
                    MenuLateralContenedor(
                        hoja = { _, _ -> },
                        onAbierto = { aperturas += 1 },
                        onAlternarTema = {},
                        gesto = AperturaPorGesto.DESDE_LA_BARRA
                    ) {
                        DetalleClienteContent(
                            state = DetalleClienteUiState(
                                cargando = false,
                                detalle = detalle,
                                montosOcultos = montosOcultos,
                                temaOscuro = temaOscuro
                            ),
                            onAtras = {},
                            onAbrirVenta = {},
                            onRegistrarAbono = {},
                            onRegistrarVisita = {},
                            onAlternarTema = { temaOscuro = !temaOscuro },
                            onAlternarPrivacidad = { montosOcultos = !montosOcultos },
                            fichaDelCliente = AccionesDeLaFicha(onEditar = { fichaEditada = true })
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun montarVenta() {
        aperturas = 0
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MspTheme(darkTheme = false, animateColors = false) {
                    var montosOcultos by remember { mutableStateOf(false) }
                    var temaOscuro by remember { mutableStateOf(false) }
                    rastreador = Rastreador(
                        montosOcultos = { montosOcultos },
                        temaOscuro = { temaOscuro },
                        fichaEditada = { false }
                    )
                    MenuLateralContenedor(
                        hoja = { _, _ -> },
                        onAbierto = { aperturas += 1 },
                        onAlternarTema = {},
                        gesto = AperturaPorGesto.DESDE_LA_BARRA
                    ) {
                        DetalleVentaContent(
                            state = DetalleVentaUiState(
                                cargando = false,
                                detalle = PagosFixtures.detalleVenta(),
                                montosOcultos = montosOcultos,
                                temaOscuro = temaOscuro
                            ),
                            onAtras = {},
                            onRegistrarAbono = {},
                            onRegistrarVisita = {},
                            onUsarLiquidacion = {},
                            onVerAbonos = {},
                            onVerGarantia = {},
                            controles = ControlesDeLaVenta(
                                onAlternarTema = { temaOscuro = !temaOscuro },
                                onAlternarPrivacidad = { montosOcultos = !montosOcultos }
                            )
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `tocar el ojo del detalle de cliente alterna montos y no abre el menu`() {
        montarCliente()
        compose.onNodeWithTag(PRIVACY_EYE_TOGGLE_TAG).performClick()
        compose.waitForIdle()

        assertTrue("el ojo no alternó montosOcultos", rastreador.montosOcultos())
        assertEquals(0, aperturas)
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
    }

    @Test
    fun `tocar el tema del detalle de cliente alterna tema y no abre el menu`() {
        montarCliente()
        compose.onNodeWithTag(THEME_TOGGLE_TAG).performClick()
        compose.waitForIdle()

        assertTrue("el tema no alternó temaOscuro", rastreador.temaOscuro())
        assertEquals(0, aperturas)
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
    }

    @Test
    fun `tocar la pastilla de advertencia del detalle de cliente la activa y no abre el menu`() {
        montarCliente(ficha = FichaParaLaFicha.CON_ADVERTENCIA)
        compose.onNodeWithTag(EDITAR_FICHA_TAG).performClick()
        compose.waitForIdle()

        assertTrue("la pastilla de advertencia no llamó a onEditar", rastreador.fichaEditada())
        assertEquals(0, aperturas)
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
    }

    @Test
    fun `tocar el ojo del detalle de venta alterna montos y no abre el menu`() {
        montarVenta()
        compose.onNodeWithTag(PRIVACY_EYE_TOGGLE_TAG).performClick()
        compose.waitForIdle()

        assertTrue("el ojo no alternó montosOcultos", rastreador.montosOcultos())
        assertEquals(0, aperturas)
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
    }

    @Test
    fun `tocar el tema del detalle de venta alterna tema y no abre el menu`() {
        montarVenta()
        compose.onNodeWithTag(THEME_TOGGLE_TAG).performClick()
        compose.waitForIdle()

        assertTrue("el tema no alternó temaOscuro", rastreador.temaOscuro())
        assertEquals(0, aperturas)
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
    }

    /**
     * Control positivo: lejos de los toggles (`x = 80`, el lado izquierdo de
     * la franja, donde no hay ningún control), un deslizamiento a la derecha
     * SÍ sigue abriendo el menú — esta prueba no está midiendo una franja que
     * dejó de existir ni una `ZonaQueAbreElMenu` que ya no abre nada.
     */
    @Test
    fun `control positivo, deslizar fuera de los toggles en el cliente si abre el menu`() {
        montarCliente()
        compose.onRoot().performTouchInput {
            swipe(start = Offset(80f, 30f), end = Offset(320f, 30f), durationMillis = 250)
        }
        compose.waitForIdle()

        assertEquals(1, aperturas)
    }

    private lateinit var rastreador: Rastreador

    private data class Rastreador(
        val montosOcultos: () -> Boolean,
        val temaOscuro: () -> Boolean,
        val fichaEditada: () -> Boolean
    )

    private enum class FichaParaLaFicha { NORMAL, CON_ADVERTENCIA }

    private companion object {
        /** `PrivacyEyeToggle.PRIVACY_EYE_TOGGLE_TAG` — `internal` en `:core:designsystem`. */
        const val PRIVACY_EYE_TOGGLE_TAG = "msp_privacy_eye_toggle"

        /** `ThemeToggle.THEME_TOGGLE_TAG` — `internal` en `:core:designsystem`. */
        const val THEME_TOGGLE_TAG = "msp_theme_toggle"
    }
}
