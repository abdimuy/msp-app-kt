package com.example.msp_app.feature.collectionreport.screenshot

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
 * Base de screenshot tests del piloto `:feature:collectionReport` — réplica del bring-up de
 * `MspScreenshotTest` (`:core:designsystem`, Task 5): ese base vive en el sourceset `test`
 * de OTRO módulo, así que no es importable aquí (Kotlin `internal`/sourceset de test no
 * cruza módulos); el patrón (reduce-motion forzado antes de cada captura + `MspTheme` +
 * `RoborazziConfig.CHANGE_THRESHOLD`) se reutiliza tal cual para que este módulo tenga la
 * misma garantía de determinismo/anti-cuelgue.
 *
 * [GraphicsMode.Mode.NATIVE] + `qualifiers` fijo — mismo motivo que en `:core:designsystem`:
 * Roborazzi necesita render de píxeles reales y un tamaño/densidad reproducibles entre
 * máquinas.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
abstract class CollectionReportScreenshotTest : RobolectricTestBase() {

    /**
     * Fuerza reduce-motion (`ANIMATOR_DURATION_SCALE = 0`) para TODA captura de golden — sin
     * esto, `StaggeredEntrance`/`TabTransition`/`Sparkline` compondrían su rama animada y el
     * golden podría capturar un frame intermedio no determinista (spec §5 + gotcha del
     * dispatch: "avoid the Roborazzi animation hang").
     */
    @Before
    fun disableAnimationsForDeterministicGoldens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    /**
     * Captura [content] envuelto en [MspTheme] sobre un fondo sólido, a
     * `src/test/screenshots/<name>.png`, con la tolerancia [RoborazziConfig.CHANGE_THRESHOLD].
     * `animateColors = false` — render estático determinista del crossfade de tema.
     *
     * [fontScale] fija la densidad de fuente vía [LocalDensity] (mismo bring-up que
     * `MspScreenshotTest` en `:core:designsystem`, Task 5) — la matriz Tier 2 @2.0 de Task 9
     * lo consume.
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
