package com.example.msp_app.core.designsystem.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.example.msp_app.core.designsystem.component.MspInsigniaNueva
import com.example.msp_app.core.designsystem.component.MspPuntoNuevo
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden con un frame INTERMEDIO del anillo/halo — a propósito el único de los
 * goldens de [MspInsigniaNueva]/[MspPuntoNuevo] que NO extiende
 * [MspScreenshotTest]: esa base fuerza `ANIMATOR_DURATION_SCALE = 0` en su
 * `@Before` precisamente para que [MspInsigniaNueva]/[MspPuntoNuevo] (vía
 * `rememberMspReducedMotion`) nunca compongan el `InfiniteTransition` durante un
 * golden — perfecto para el reposo, inservible para enseñar que el pulso "se ve
 * súper bien" (palabras del dueño), que es justo lo que pide esta prueba.
 *
 * Sin tocar `Settings.Global` el valor por defecto de Robolectric ya es
 * `ANIMATOR_DURATION_SCALE = 1f` (sin reduce-motion), así que el pulso se
 * compone solo. Para que el frame capturado sea determinista (y no "lo que haya
 * tardado en renderizar la JVM") se congela el reloj de Compose
 * (`mainClock.autoAdvance = false`) ANTES de montar el contenido y se avanza a
 * mano exactamente [FRAME_MS] — 600ms, 25% del periodo de 2.4s
 * (`MSP_PULSE_PERIOD_MS`), bien dentro de la ventana de crecimiento de 0-1,680ms
 * así que el anillo/halo está a medio camino: ancho/escala intermedios, alpha
 * intermedio, nunca en 0 ni en el máximo.
 *
 * `composeTestRule.onRoot().captureRoboImage(...)` (no el `captureRoboImage(filePath){content}`
 * de [MspScreenshotTest]) porque esta variante renderiza el árbol de Compose YA
 * montado por [createAndroidComposeRule] —el mismo patrón que
 * `AbonoConToquesScreenshotTest` en `:feature:pagos`— en vez de levantar su
 * propia composición aislada; es lo que permite que el reloj congelado de la
 * regla sea el reloj que ve la captura.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class MspPulseRingFrameScreenshotTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `insignia nueva a medio anillo`() {
        congelaYCaptura("msp_insignia_nueva_anillo_medio") { MspInsigniaNueva() }
    }

    @Test
    fun `punto nuevo a medio halo`() {
        congelaYCaptura("msp_punto_nuevo_halo_medio") { MspPuntoNuevo() }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun congelaYCaptura(name: String, content: @Composable () -> Unit) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MspTheme.colors.background)
                        .padding(MspTheme.spacing.lg)
                ) {
                    content()
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(FRAME_MS)
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = RoborazziConfig.CHANGE_THRESHOLD
                )
            )
        )
    }

    private companion object {
        /** 25% de 2,400ms: bien dentro de la ventana de crecimiento (0-1,680ms). */
        const val FRAME_MS = 600L
    }
}
