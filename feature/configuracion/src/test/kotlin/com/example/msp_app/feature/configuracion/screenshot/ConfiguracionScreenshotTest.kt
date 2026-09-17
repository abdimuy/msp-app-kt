package com.example.msp_app.feature.configuracion.screenshot

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
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
 * Base de los goldens de `:feature:configuracion`. Mismo bring-up que
 * `SpeechScreenshotTest` — ese base vive en el sourceset `test` de otro módulo
 * y no cruza, así que se reusa el patrón, no el tipo.
 *
 * Este módulo **no tenía un solo golden** hasta la sección "Descargas": sus dos
 * secciones anteriores se probaban solo con asserts, y un assert no ve un
 * renglón encimado a escala 2.0.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
abstract class ConfiguracionScreenshotTest : RobolectricTestBase() {

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
                LocalFontSizeLevel provides nivel,
                LocalReduceMotion provides true
            ) {
                MspTheme(darkTheme = dark, animateColors = false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MspTheme.colors.background)
                            // El mismo aire horizontal que le da la pantalla a
                            // sus secciones: un golden sin él mentiría sobre
                            // cuánto ancho le queda al texto de cada renglón.
                            .padding(horizontal = MspTheme.spacing.md)
                    ) {
                        content()
                    }
                }
            }
        }
    }

    /** El sufijo de nombre de cada nivel: `1_0`, `1_5`, `2_0`. */
    fun sufijoDe(nivel: FontSizeLevel): String = nivel.nominalScale.toString().replace('.', '_')

    /** El sufijo del tema. */
    fun tema(dark: Boolean): String = if (dark) "dark" else "light"
}
