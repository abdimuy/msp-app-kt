package com.example.msp_app.feature.ubicacion.screenshot

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.theme.LocalAppDarkTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.example.msp_app.feature.ubicacion.domain.ClaseDeLugar
import com.example.msp_app.feature.ubicacion.ui.ModoDeLaHoja
import com.example.msp_app.feature.ubicacion.ui.UbicacionScreen
import com.example.msp_app.feature.ubicacion.ui.UbicacionUiState
import com.example.msp_app.feature.ubicacion.ui.components.AlturaDelDetalle
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Los cuatro teléfonos del mock**, a 360 × 744 dp (720 × 1488 px).
 *
 * # ⚠ QUÉ NO PRUEBAN
 *
 * **No fotografían el mapa** (Robolectric no tiene Play Services ni GL): el
 * fondo es el color del mapa del mock. Los marcadores se comparan aparte, en
 * `LosMarcadoresDelMockTest`. Lo que sí retratan: la hoja, las cards, los
 * controles de arriba, "Mi ubicación", el aviso de puntos lejos y el panel de
 * filtros — y cómo se ven al lado del mock (`scratchpad/mapa/`).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class MapaDeLugaresScreenshotTest : RobolectricTestBase() {

    @Before
    fun sinAnimaciones() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) =
        captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = RoborazziConfig.CHANGE_THRESHOLD
                )
            )
        ) {
            CompositionLocalProvider(LocalAppDarkTheme provides dark) { content() }
        }

    private fun pantalla(
        estado: UbicacionUiState,
        modo: ModoDeLaHoja = ModoDeLaHoja.REPOSO,
        filtros: Boolean = false,
        detalle: AlturaDelDetalle = AlturaDelDetalle.MEDIA
    ): @Composable () -> Unit = {
        UbicacionScreen(
            state = estado,
            onAtras = {},
            onComoLlegar = {},
            onFiltro = {},
            hojaInicial = modo,
            filtrosAbiertos = filtros,
            detalleInicial = detalle,
            pintarMapa = false
        )
    }

    @Test fun al_entrar_claro() = capture(
        "mapa_al_entrar_light",
        false,
        pantalla(MapaDelDuenoFixture.estado())
    )

    @Test fun al_entrar_oscuro() = capture(
        "mapa_al_entrar_dark",
        true,
        pantalla(MapaDelDuenoFixture.estado())
    )

    @Test fun hoja_arrastrada_claro() = capture(
        "mapa_hoja_arrastrada_light",
        false,
        pantalla(MapaDelDuenoFixture.estado(), ModoDeLaHoja.EXPANDIDA)
    )

    @Test fun lugar_tocado_claro() = capture(
        "mapa_lugar_tocado_light",
        false,
        pantalla(MapaDelDuenoFixture.estado(ClaseDeLugar.OTRO_LUGAR))
    )

    @Test fun filtros_claro() =
        capture("mapa_filtros_light", false, pantalla(MapaDelDuenoFixture.estado(), filtros = true))

    @Test fun lugar_tocado_oscuro() = capture(
        "mapa_lugar_tocado_dark",
        true,
        pantalla(MapaDelDuenoFixture.estado(ClaseDeLugar.OTRO_LUGAR))
    )

    @Test fun lugar_tocado_minima_claro() = capture(
        "mapa_lugar_tocado_minima_light",
        false,
        pantalla(
            MapaDelDuenoFixture.estado(ClaseDeLugar.OTRO_LUGAR),
            detalle = AlturaDelDetalle.MINIMA
        )
    )

    @Test fun lugar_tocado_completa_claro() = capture(
        "mapa_lugar_tocado_completa_light",
        false,
        pantalla(
            MapaDelDuenoFixture.estado(ClaseDeLugar.OTRO_LUGAR),
            detalle = AlturaDelDetalle.COMPLETA
        )
    )
}
