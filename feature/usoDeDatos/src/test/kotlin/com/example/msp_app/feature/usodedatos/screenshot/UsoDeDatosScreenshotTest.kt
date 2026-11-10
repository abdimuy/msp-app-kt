package com.example.msp_app.feature.usodedatos.screenshot

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.example.msp_app.feature.usodedatos.UsoDeDatosEjemplos
import com.example.msp_app.feature.usodedatos.UsoDeDatosUiState
import com.example.msp_app.feature.usodedatos.ui.UsoDeDatosContenido
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Goldens de "Uso de datos": con datos, vacío y sin desglose, claro y oscuro; con datos también a escala 2.0. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class UsoDeDatosScreenshotTest : RobolectricTestBase() {

    @Before fun apagaAnimaciones() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    @Test fun `con datos light`() = golden(
        "con_datos",
        false,
        FontSizeLevel.NORMAL,
        UsoDeDatosEjemplos.conDatos
    )

    @Test fun `con datos dark`() = golden(
        "con_datos",
        true,
        FontSizeLevel.NORMAL,
        UsoDeDatosEjemplos.conDatos
    )

    @Test fun `con datos light muy grande`() =
        golden("con_datos", false, FontSizeLevel.MUY_GRANDE, UsoDeDatosEjemplos.conDatos)

    @Test fun `vacio light`() = golden(
        "vacio",
        false,
        FontSizeLevel.NORMAL,
        UsoDeDatosEjemplos.vacio
    )

    @Test fun `vacio dark`() = golden("vacio", true, FontSizeLevel.NORMAL, UsoDeDatosEjemplos.vacio)

    @Test fun `sin desglose light`() =
        golden("sin_desglose", false, FontSizeLevel.NORMAL, UsoDeDatosEjemplos.sinDesglose)

    @Test fun `sin desglose dark`() =
        golden("sin_desglose", true, FontSizeLevel.NORMAL, UsoDeDatosEjemplos.sinDesglose)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun golden(
        nombre: String,
        dark: Boolean,
        nivel: FontSizeLevel,
        estado: UsoDeDatosUiState
    ) {
        val tema = if (dark) "dark" else "light"
        val escala = nivel.nominalScale.toString().replace('.', '_')
        captureRoboImage(
            filePath = "src/test/screenshots/uso_de_datos_${nombre}_${tema}_$escala.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = RoborazziConfig.CHANGE_THRESHOLD
                )
            )
        ) {
            Escala(nivel) {
                MspTheme(
                    darkTheme = dark,
                    animateColors = false
                ) { UsoDeDatosContenido(estado, onBack = {}) }
            }
        }
    }

    @Composable
    private fun Escala(nivel: FontSizeLevel, contenido: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, nivel.nominalScale),
            LocalFontSizeLevel provides nivel,
            LocalReduceMotion provides true,
            content = contenido
        )
    }
}
