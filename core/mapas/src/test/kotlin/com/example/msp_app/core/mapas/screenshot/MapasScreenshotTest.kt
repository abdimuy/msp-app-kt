package com.example.msp_app.core.mapas.screenshot

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
 * Base de los goldens de `:core:mapas`. Mismo bring-up que
 * `SpeechScreenshotTest` — ese base vive en el sourceset `test` de otro módulo y
 * no cruza, así que se reusa el patrón, no el tipo.
 *
 * ## Por qué estos goldens SON deterministas con un mapa GL de por medio
 *
 * Porque MapLibre nunca entra a la captura. `SueloDeLaRuta` recibe el lienzo por
 * un slot y acá se le pasa uno de mentira: un rectángulo de un color fijo con la
 * pinta de una tesela. Lo que el golden fotografía es todo lo demás —la
 * atribución, su pastilla, dónde cae, y el suelo liso de cuando no hay
 * extracto—, que es Compose puro y se dibuja igual en cada corrida.
 *
 * Si el lienzo real entrara, el golden dependería de si la máquina tiene GL: o
 * el test no corre, o corre fotografiando un hueco. Un verde que no mira nada es
 * peor que un rojo.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
abstract class MapasScreenshotTest : RobolectricTestBase() {

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
