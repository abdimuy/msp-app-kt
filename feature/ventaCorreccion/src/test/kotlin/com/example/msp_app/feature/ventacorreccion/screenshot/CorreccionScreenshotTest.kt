package com.example.msp_app.feature.ventacorreccion.screenshot

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
import com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion
import com.example.msp_app.feature.ventacorreccion.ui.components.AvisoNoCorregible
import com.example.msp_app.feature.ventacorreccion.ui.components.BotonCorregir
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens de la Task 5 (plan "Corregir una venta antes de que suba"): [BotonCorregir] y
 * [AvisoNoCorregible] — con DOS textos representativos, el más corto (`YA_SE_ENVIO`) y el más
 * largo (`LA_REVISA_LA_OFICINA`) de las tres cadenas de aviso — en claro/oscuro × las tres
 * escalas de letra que YA usa el catálogo de `:core:designsystem`
 * (`CatalogScreenshotTest.SCALES`: `1.0`/`1.3`/`2.0`) — leídas de ahí, no inventadas.
 *
 * Réplica del bring-up `MspScreenshotTest` (`:core:designsystem`)/`CollectionReportScreenshotTest`
 * (`:feature:collectionReport`): ese base vive en el sourceset `test` de otro módulo, así que no
 * es importable aquí (Kotlin `internal`/sourceset de test no cruza módulos) — mismo motivo
 * documentado en ambos.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class CorreccionScreenshotTest : RobolectricTestBase() {

    @Before
    fun disableAnimationsForDeterministicGoldens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    @Test
    fun `boton corregir - matriz tema x escala`() {
        captureMatrix("boton_corregir") { BotonCorregir(onClick = {}) }
    }

    @Test
    fun `aviso no corregible ya se envio - matriz tema x escala`() {
        captureMatrix("aviso_no_corregible_ya_se_envio") {
            AvisoNoCorregible(mensaje = TextosCorreccion.YA_SE_ENVIO)
        }
    }

    @Test
    fun `aviso no corregible la revisa la oficina - matriz tema x escala`() {
        captureMatrix("aviso_no_corregible_la_revisa_la_oficina") {
            AvisoNoCorregible(mensaje = TextosCorreccion.LA_REVISA_LA_OFICINA)
        }
    }

    private fun captureMatrix(component: String, content: @Composable () -> Unit) {
        for (theme in THEMES) {
            for (scale in SCALES) {
                capture(
                    name = "${component}_${theme.name}_${scale.label}",
                    dark = theme.dark,
                    fontScale = scale.value,
                    content = content
                )
            }
        }
    }

    /**
     * Captura [content] envuelto en [MspTheme] sobre un fondo sólido, a
     * `src/test/screenshots/<name>.png`, con la tolerancia [RoborazziConfig.CHANGE_THRESHOLD].
     * `animateColors = false`: render estático determinista, sin el crossfade de tema.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        dark: Boolean,
        fontScale: Float,
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

private data class CorreccionGoldenTheme(val name: String, val dark: Boolean)
private data class CorreccionGoldenScale(val label: String, val value: Float)

private val THEMES = listOf(
    CorreccionGoldenTheme("light", dark = false),
    CorreccionGoldenTheme("dark", dark = true)
)

// Mismas tres escalas que `CatalogScreenshotTest.SCALES` en `:core:designsystem` — leídas de
// ahí, no inventadas (brief de Task 5).
private val SCALES = listOf(
    CorreccionGoldenScale("1_0", 1.0f),
    CorreccionGoldenScale("1_3", 1.3f),
    CorreccionGoldenScale("2_0", 2.0f)
)
