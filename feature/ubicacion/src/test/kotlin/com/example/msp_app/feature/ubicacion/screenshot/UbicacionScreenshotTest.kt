package com.example.msp_app.feature.ubicacion.screenshot

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
 * Base de los goldens de `:feature:ubicacion`. Mismo bring-up que
 * `VisitasScreenshotTest` — ese base vive en el sourceset `test` de otro módulo
 * y no cruza, así que se reusa el patrón, no el tipo.
 *
 * # ⚠ QUÉ **NO** PRUEBAN ESTOS GOLDENS
 *
 * **Ninguno fotografía el mapa.** `captureRoboImage` corre bajo Robolectric, sin
 * red y sin GL, y `GoogleMap` necesita las dos cosas: lo que saldría es un
 * rectángulo vacío. Lo que estos goldens retratan es **la hoja y la barra de
 * filtros sobre un fondo liso**, nada más.
 *
 * O sea que un verde aquí **no dice nada** sobre:
 *
 *  - si los marcadores caen donde deben (ésa es la decisión de los 21 dp, y se
 *    comprueba en el aparato, no aquí),
 *  - si el círculo de precisión se dibuja del tamaño correcto —o si se dibuja—,
 *  - si el encuadre inicial mete todos los lugares en pantalla,
 *  - si el degradado por antigüedad se ve,
 *  - si el caché de variantes rehace los bitmaps al cambiar de tema.
 *
 * **Y lo que estos goldens SÍ cazaron, que es la razón de mirarlos:** dos de
 * ellos salieron idénticos byte a byte y destaparon que la hoja no pintaba las
 * dos cuentas del rotulado aprobado — con quince pruebas de dominio en verde
 * encima. Ver **E-INF-011**.
 *
 * **Esto se escribe aquí y no sólo en un reporte a propósito.** Es la misma
 * familia del falso verde de `E-INF-009` (una prueba con buen nombre que no
 * podía fallar) y del de `E-INF-002` (`verify` comparando contra lo que `record`
 * acababa de escribir): el peligro no es que la prueba falle, es que alguien vea
 * "goldens verdes" y crea que la pantalla está cubierta.
 *
 * **Lo que sí guardan, y vale:** los cuatro rótulos palabra por palabra, que "la
 * puerta" no se regale cuando nadie se la ganó, que el aviso de mudanza salga
 * sin filtrar, y que el chip de filtros diga "Filtrar" mientras no recorte nada.
 * Todo eso es texto y disposición, que es justo lo que Robolectric sí mide.
 *
 * La aritmética —el radio de 30 m, el umbral de 5 clientes, el círculo que se
 * niega a dibujarse con menos de 5 mediciones— vive en `:core:geo` y se prueba
 * sin mapa ni pantalla. Ésa es la razón por la que ese módulo existe aparte.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
abstract class UbicacionScreenshotTest : RobolectricTestBase() {

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
}
