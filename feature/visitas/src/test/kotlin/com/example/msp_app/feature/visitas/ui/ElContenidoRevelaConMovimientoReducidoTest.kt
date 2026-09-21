package com.example.msp_app.feature.visitas.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Con movimiento reducido, lo que un desenlace revela está — no se anima,
 * pero tampoco desaparece.**
 *
 * `MspRevealedContent` (Task del 21-sep, "¿Qué pasó en la puerta?") anima la
 * captura que cada desenlace revela — la promesa, la cita, de cuáles
 * cuentas —, y esa animación no se puede probar con un golden: una captura
 * congela un solo frame, nunca demuestra que algo se MOVIÓ. Lo que sí es
 * falsificable, y lo que de verdad importa proteger, es que con movimiento
 * reducido puesto el contenido esté COMPLETO desde el primer frame. El riesgo
 * real de una transición mal cableada no es que se vea brusca: es que un
 * `EnterTransition`/`ExitTransition` que no consulta el interruptor dependa
 * de una animación para terminar de aparecer, y con movimiento reducido esa
 * animación no corre — la pantalla se queda con la captura de la promesa
 * invisible y el cobrador cree que no hay nada que llenar.
 *
 * [LocalReduceMotion] y no `Settings.Global.ANIMATOR_DURATION_SCALE`: es un
 * `CompositionLocal`, se controla desde el árbol del test sin tocar el
 * sistema — mismo criterio que `LaListaInstalaLaRevealDeTemaTest`.
 */
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class ElContenidoRevelaConMovimientoReducidoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * `PROMETIO` porque es el desenlace que MÁS revela: el fold de "cómo
     * estaba" no aplica (no lo pide `CatalogoDeResultados.pideEtiqueta`), pero
     * "¿Cuándo?" y el teclado de monto sí — son el contenido que
     * `MspRevealedContent` envuelve, y por lo tanto el que este test tiene
     * que ver COMPLETO con movimiento reducido puesto.
     */
    @Test
    fun `con movimiento reducido la captura de la promesa esta completa`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MspTheme(darkTheme = false, animateColors = false) {
                    RegistrarVisitaContent(
                        state = VisitaFixtures.prometio(),
                        acciones = AccionesDeLaVisita.NINGUNA
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("¿Cuándo?").assertIsDisplayed()
    }
}
