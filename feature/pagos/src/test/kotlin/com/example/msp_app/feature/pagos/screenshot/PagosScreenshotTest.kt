package com.example.msp_app.feature.pagos.screenshot

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base de los goldens de `:feature:pagos`. Réplica del bring-up de
 * `MspScreenshotTest` (`:core:designsystem`) — ese base vive en el sourceset
 * `test` de otro módulo y no cruza, así que se reusa el patrón, no el tipo.
 *
 * ## La escala de fuente: 1.0 / 1.5 / 2.0, no 1.3
 *
 * [capture] recibe un [FontSizeLevel] y lee su [FontSizeLevel.nominalScale] en
 * vez de un `Float` suelto. `CollectionReportMatrixScreenshotTest` —la
 * plantilla— usa `1.0 / 1.3 / 2.0`, y **el 1.3 no corresponde a ningún nivel
 * real**: el enum declara `NORMAL 1.0 / GRANDE 1.5 / MUY_GRANDE 2.0`, así que
 * ese golden intermedio prueba una escala que ningún usuario puede elegir y
 * NO prueba la que sí puede (1.5). Aquí se prueba contra el enum, que además
 * hace imposible que la matriz vuelva a despegarse si algún día cambian los
 * valores.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
abstract class PagosScreenshotTest : RobolectricTestBase() {

    /**
     * Reduce-motion para toda captura: sin esto, cualquier composable con
     * animación podría capturar un frame intermedio y el golden dejaría de ser
     * determinista.
     */
    @Before
    fun apagaAnimacionesParaGoldensDeterministas() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    fun capture(
        name: String,
        dark: Boolean = false,
        nivel: FontSizeLevel = FontSizeLevel.NORMAL,
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
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
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

    /** El sufijo de nombre de cada nivel: `1_0`, `1_5`, `2_0`. */
    fun sufijoDe(nivel: FontSizeLevel): String = nivel.nominalScale.toString().replace('.', '_')
}
