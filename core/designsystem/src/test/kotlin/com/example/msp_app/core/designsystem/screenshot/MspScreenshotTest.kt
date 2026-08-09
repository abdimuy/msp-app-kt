package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base de screenshot tests del design system Msp — el bring-up de Roborazzi
 * (Task 5) que Tasks 6-10 reutilizan para la matriz Tier×escala×tema
 * completa (aquí solo se graba el primer golden).
 *
 * [GraphicsMode.Mode.NATIVE] activa Robolectric Native Graphics (RNG):
 * Roborazzi necesita render de píxeles reales, no el shadow/legacy graphics
 * que usan los tests de lógica (p.ej. [com.example.msp_app.core.designsystem.theme.MspThemeTest]).
 * `qualifiers` fija tamaño/densidad de dispositivo explícitos — sin esto el
 * default de Robolectric puede variar entre máquinas y el golden deja de
 * ser reproducible.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
abstract class MspScreenshotTest : RobolectricTestBase() {

    /**
     * Captura [content] envuelto en [MspTheme] sobre un fondo sólido, a
     * `src/test/screenshots/<name>.png`, con la tolerancia
     * [RoborazziConfig.CHANGE_THRESHOLD].
     *
     * `animateColors = false` — render estático determinista: sin esto el
     * crossfade de [MspTheme] podría capturar un frame intermedio y el
     * golden se vuelve no determinista.
     *
     * [fontScale] fija la densidad de fuente vía [LocalDensity] — el
     * bring-up que Tasks 6-10 reutilizan para la matriz de escalas.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    fun capture(
        name: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit
    ) {
        captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = RoborazziConfig.CHANGE_THRESHOLD
                )
            )
        ) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MspTheme(darkTheme = dark, animateColors = false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MspTheme.colors.background)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
