package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.mapas.domain.MapaDeLaRuta
import com.example.msp_app.core.mapas.domain.PuntoDelMapa
import com.example.msp_app.core.mapas.ui.SueloDeLaRuta
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.MapaDelCliente
import org.junit.Test

/**
 * **El bloque de mapa COMPLETO**: el suelo de `:core:mapas` con su atribución,
 * más el pin, la pastilla y "cómo llegar" de esta feature, todo junto.
 *
 * ## Por qué este golden vive acá y no en `:core:mapas`
 *
 * Porque las cuatro piezas que pueden encimarse viven en dos módulos: la
 * atribución la pinta el suelo y el pin, la pastilla y el botón los pinta
 * `MapaDelCliente`. Un golden en `:core:mapas` vería la atribución sola sobre un
 * rectángulo y no podría ver la colisión, que es exactamente lo que hay que
 * mirar en 130 dp con la fuente a 2.0.
 *
 * ## Sigue siendo determinista
 *
 * MapLibre no entra: el suelo recibe el lienzo por un slot y acá se le pasa un
 * color plano. Lo que se fotografía es geometría de Compose.
 */
class MapaConAtribucionScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `bloque con mapa light normal`() = bloque(false, FontSizeLevel.NORMAL)

    @Test
    fun `bloque con mapa light grande`() = bloque(false, FontSizeLevel.GRANDE)

    @Test
    fun `bloque con mapa light muy grande`() = bloque(false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `bloque con mapa dark normal`() = bloque(true, FontSizeLevel.NORMAL)

    @Test
    fun `bloque con mapa dark grande`() = bloque(true, FontSizeLevel.GRANDE)

    @Test
    fun `bloque con mapa dark muy grande`() = bloque(true, FontSizeLevel.MUY_GRANDE)

    /** El sufijo del tema, igual que en `DetalleMatrixScreenshotTest`. */
    private fun tema(dark: Boolean) = if (dark) "dark" else "light"

    private fun bloque(dark: Boolean, nivel: FontSizeLevel) {
        capture("pagos_mapa_con_atribucion_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            Box(modifier = Modifier.fillMaxWidth().padding(MspTheme.spacing.md)) {
                MapaDelCliente(
                    ubicacion = UbicacionDelCobro(lat = 18.4609, lng = -97.3926),
                    onComoLlegar = {},
                    // Con mapa, el pin lo pinta el suelo: es el unico que sabe
                    // donde quedo el objetivo de la camara.
                    elSueloPintaElPin = true,
                    suelo = {
                        SueloDeLaRuta(
                            mapa = MAPA,
                            punto = PuntoDelMapa(lat = 18.4609, lng = -97.3926),
                            // Color plano y saturado: el peor caso razonable para
                            // la legibilidad de la atribución.
                            lienzo = { _, _ ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF3F6B8C))
                                )
                            }
                        )
                    }
                )
            }
        }
    }

    private companion object {
        val MAPA = MapaDeLaRuta(
            ruta = "/datos/mapas/ruta.pmtiles",
            cabecera = com.example.msp_app.core.mapas.domain.CabeceraDePmtiles(
                version = 3,
                zoomMinimo = 0,
                zoomMaximo = 14,
                oeste = -98.2,
                sur = 17.9,
                este = -96.4,
                norte = 19.6,
                metadatosDesde = 172L,
                metadatosLargo = 1_179L,
                finDeLosDatos = 25_507_515L
            )
        )
    }
}
