package com.example.msp_app.feature.garantias.screenshot

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

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
// Plantilla para las capturas de cada contenido, igual que PagosScreenshotTest.
@Suppress("UnnecessaryAbstractClass")
abstract class GarantiasScreenshotTest : RobolectricTestBase() {

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

    fun sufijoDe(nivel: FontSizeLevel): String = nivel.nominalScale.toString().replace('.', '_')
}
