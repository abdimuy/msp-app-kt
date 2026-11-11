package com.example.msp_app.feature.compartirproductos.screenshot

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.ventas.precios.TipoDePrecio
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.example.msp_app.feature.compartirproductos.domain.ArmadorDeTarjeta
import com.example.msp_app.feature.compartirproductos.domain.ArticuloParaCompartir
import com.example.msp_app.feature.compartirproductos.domain.PrecioDeCatalogo
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import com.example.msp_app.feature.compartirproductos.render.DibujanteDeTarjeta
import com.example.msp_app.feature.compartirproductos.ui.BarraDeSeleccion
import com.example.msp_app.feature.compartirproductos.ui.ContenidoDeLaHoja
import com.example.msp_app.feature.compartirproductos.ui.EstadoDeCompartir
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.math.BigDecimal
import java.time.Instant
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens claro y oscuro de la hoja «Compartir» y de la barra de selección.
 * Mismo aviso que `TarjetasDeReferenciaTest`: `test` a secas NO compara imágenes; las pruebas de
 * `HojaDeCompartirTest` son las que protegen siempre. Estos son para mirar y para `verifyRoborazziDebug`.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class HojaGoldenTest : RobolectricTestBase() {

    private fun art(id: Int) = ArticuloParaCompartir(
        id,
        "SALA $id",
        "SALAS",
        listOf(PrecioDeCatalogo(TipoDePrecio.CONTADO, BigDecimal("100"))),
        null
    )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capturar(nombre: String, oscuro: Boolean, contenido: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/$nombre.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = RoborazziConfig.CHANGE_THRESHOLD
                )
            )
        ) {
            MaterialTheme(colorScheme = if (oscuro) darkColorScheme() else lightColorScheme()) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) { contenido() }
            }
        }
    }

    /** La vista previa real de una tarjeta, a 405×720 como la entrega el `ViewModel`. */
    private val vista by lazy {
        val d = DibujanteDeTarjeta(ApplicationProvider.getApplicationContext())
        val t = ArmadorDeTarjeta.armar(
            art(1),
            SeleccionDePrecios(),
            Instant.parse("2026-11-10T18:00:00Z")
        )
        Bitmap.createScaledBitmap(d.dibujar(t, null), 405, 720, true)
    }
    private val uno get() = EstadoDeCompartir(
        visible = true,
        articulos = listOf(art(1)),
        avisoPreciosViejos = true,
        vistaPrevia = vista
    )
    private val varios get() = EstadoDeCompartir(
        visible = true,
        articulos = List(12) { art(it) },
        seleccion = SeleccionDePrecios(true, false, true),
        generando = true,
        hechas = 5,
        vistaPrevia = vista
    )

    @Test fun hojaUnoClaro() = capturar("hoja_uno_claro", false) {
        ContenidoDeLaHoja(uno, { _, _ -> }, {})
    }

    @Test fun hojaUnoOscuro() = capturar("hoja_uno_oscuro", true) {
        ContenidoDeLaHoja(uno, { _, _ -> }, {})
    }

    @Test fun hojaVariosGenerandoClaro() = capturar("hoja_varios_generando_claro", false) {
        ContenidoDeLaHoja(varios, { _, _ -> }, {})
    }

    @Test fun hojaVariosGenerandoOscuro() = capturar("hoja_varios_generando_oscuro", true) {
        ContenidoDeLaHoja(varios, { _, _ -> }, {})
    }

    @Test fun barraClaro() = capturar(
        "barra_seleccion_claro",
        false
    ) { BarraDeSeleccion(7, {}, {}) }

    @Test fun barraOscuro() = capturar(
        "barra_seleccion_oscuro",
        true
    ) { BarraDeSeleccion(7, {}, {}) }
}
