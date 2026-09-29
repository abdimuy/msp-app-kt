package com.example.msp_app.feature.pagos.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.example.msp_app.feature.pagos.ui.AbonoFixtures
import com.example.msp_app.feature.pagos.ui.RegistrarAbonoContent
import com.example.msp_app.feature.pagos.ui.RegistrarAbonoUiState
import com.example.msp_app.feature.pagos.ui.components.FOTO_TAG
import com.example.msp_app.feature.pagos.ui.components.NOMBRE_DEL_CLIENTE_TAG
import com.example.msp_app.feature.pagos.ui.components.PRODUCTO_TAG
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Los estados de "Registrar abono" que **sólo se alcanzan tocando**: el
 * desplegable del producto y la hoja "Fotos y archivos" (mock
 * `registrar-abono-fijo.html`). Su estado es de la pantalla —abierto o
 * cerrado—, no del ViewModel, así que no hay fixture que los pinte: se toca y
 * se captura, igual que lo haría el cobrador.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class AbonoConToquesScreenshotTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `producto desplegado light`() = tocaYCaptura(
        "producto_desplegado_light_1_0",
        AbonoFixtures.conProductoLargo(),
        PRODUCTO_TAG
    )

    @Test
    fun `producto desplegado dark muy grande`() = tocaYCaptura(
        "producto_desplegado_dark_2_0",
        AbonoFixtures.conProductoLargo(),
        PRODUCTO_TAG,
        dark = true,
        nivel = FontSizeLevel.MUY_GRANDE
    )

    @Test
    @Config(qualifiers = "w360dp-h696dp-xhdpi")
    fun `pantalla baja muy grande, el nombre despliega el producto`() = tocaYCaptura(
        "bajo_desplegado_light_2_0",
        AbonoFixtures.conProductoLargo(),
        NOMBRE_DEL_CLIENTE_TAG,
        nivel = FontSizeLevel.MUY_GRANDE
    )

    @Test
    fun `hoja de fotos light`() =
        tocaYCaptura("hoja_fotos_light", AbonoFixtures.enCapturaConComprobantes(), FOTO_TAG)

    @Test
    fun `hoja de fotos dark`() = tocaYCaptura(
        "hoja_fotos_dark",
        AbonoFixtures.enCapturaConComprobantes(),
        FOTO_TAG,
        dark = true
    )

    private fun tocaYCaptura(
        nombre: String,
        state: RegistrarAbonoUiState,
        tag: String,
        dark: Boolean = false,
        nivel: FontSizeLevel = FontSizeLevel.NORMAL
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = dark, animateColors = false) {
                    Box(modifier = Modifier.fillMaxSize().background(MspTheme.colors.background)) {
                        RegistrarAbonoContent(
                            state = state,
                            onAtras = {},
                            onDigito = {},
                            onPunto = {},
                            onBorrar = {},
                            onMetodo = {},
                            onSugerido = {},
                            onRegistrar = {},
                            onConfirmar = {},
                            onEditar = {},
                            onRevisar = {},
                            onAgregarFoto = {},
                            onOrigen = {},
                            onCerrarOrigenes = {},
                            onQuitarFoto = {}
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag(tag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/pagos_abono_$nombre.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = RoborazziConfig.CHANGE_THRESHOLD
                )
            )
        )
    }
}
