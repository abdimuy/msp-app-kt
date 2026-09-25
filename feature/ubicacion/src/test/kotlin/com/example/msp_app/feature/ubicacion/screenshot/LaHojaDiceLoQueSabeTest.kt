package com.example.msp_app.feature.ubicacion.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.geo.IndiceDePuntosCompartidos
import com.example.msp_app.core.geo.LugaresDelCliente
import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.PUERTA
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.desplazado
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.indiceVacio
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.medicion
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.rutaCompartida
import com.example.msp_app.feature.ubicacion.domain.FiltroDeLugares
import com.example.msp_app.feature.ubicacion.domain.paraElMapa
import com.example.msp_app.feature.ubicacion.ui.UbicacionUiState
import com.example.msp_app.feature.ubicacion.ui.components.BarraDeFiltros
import com.example.msp_app.feature.ubicacion.ui.components.HojaDeLosLugares
import org.junit.Test

/**
 * Los goldens de la hoja y de la barra de filtros.
 *
 * **Lea primero el KDoc de [UbicacionScreenshotTest]**: estas imágenes NO
 * cubren el mapa, y un verde aquí no dice nada sobre marcadores, círculo de
 * precisión ni encuadre.
 *
 * Los cinco estados que sí retratan son los cinco que el cobrador puede
 * encontrarse, y cada uno existe por una decisión que costó discutirse:
 *
 *  1. **con puerta** — el caso común.
 *  2. **punto compartido** — el título NO se regala, y la hoja dice las dos
 *     cuentas sin nombrar el lugar.
 *  3. **puras transferencias** — cierto como hecho, falso como domicilio.
 *  4. **mudanza** — sale sin que nadie filtre.
 *  5. **sin ningún punto** — no se inventa nada.
 */
class LaHojaDiceLoQueSabeTest : UbicacionScreenshotTest() {

    @Test fun hoja_con_puerta_claro() = captura(
        "ubicacion_puerta_light",
        dark = false,
        estado = conPuerta()
    )

    @Test fun hoja_con_puerta_oscuro() = captura(
        "ubicacion_puerta_dark",
        dark = true,
        estado = conPuerta()
    )

    @Test fun hoja_compartido_claro() =
        captura("ubicacion_compartido_light", dark = false, estado = compartido())

    @Test fun hoja_compartido_oscuro() =
        captura("ubicacion_compartido_dark", dark = true, estado = compartido())

    @Test fun hoja_transferencias_claro() =
        captura("ubicacion_transferencias_light", dark = false, estado = soloTransferencias())

    @Test fun hoja_mudanza_claro() = captura(
        "ubicacion_mudanza_light",
        dark = false,
        estado = mudanza()
    )

    @Test fun hoja_mudanza_oscuro() = captura(
        "ubicacion_mudanza_dark",
        dark = true,
        estado = mudanza()
    )

    @Test fun hoja_sin_puntos_claro() =
        captura("ubicacion_sin_puntos_light", dark = false, estado = sinPuntos())

    /**
     * El chip de filtros **con el mismo padding que le pone la pantalla**.
     *
     * La primera versión de este golden lo montaba con `align(TopEnd)` a secas y
     * la imagen salió con el chip **cortado contra la orilla derecha**. No era un
     * defecto de la pantalla —`UbicacionScreen` sí aplica `padding(end = 16.dp)`—
     * sino del banco de pruebas: un golden que no reproduce el entorno real
     * retrata algo que nadie va a ver, y entonces no puede delatar nada.
     *
     * Se deja anotado porque el error es fácil de repetir: montar la pieza
     * "suelta" para fotografiarla es cómodo y silenciosamente infiel.
     */
    @Test fun filtros_limpios_claro() = capture("ubicacion_filtros_light") {
        Box(Modifier.fillMaxSize()) {
            BarraDeFiltros(
                filtro = FiltroDeLugares(),
                ventas = listOf(10, 20),
                cobradores = listOf("Elías Mota", "Rocío Manzano"),
                onFiltro = {},
                modifier = Modifier.align(Alignment.TopEnd).padding(ORILLA_DE_LA_PANTALLA)
            )
        }
    }

    @Test fun filtros_puestos_claro() = capture("ubicacion_filtros_puestos_light") {
        Box(Modifier.fillMaxSize()) {
            BarraDeFiltros(
                filtro = FiltroDeLugares(ventaId = 10),
                ventas = listOf(10, 20),
                cobradores = listOf("Elías Mota", "Rocío Manzano"),
                onFiltro = {},
                modifier = Modifier.align(Alignment.TopEnd).padding(ORILLA_DE_LA_PANTALLA)
            )
        }
    }

    private fun captura(nombre: String, dark: Boolean, estado: UbicacionUiState) =
        capture(nombre, dark = dark) {
            Box(Modifier.fillMaxSize()) {
                HojaDeLosLugares(
                    state = estado,
                    onComoLlegar = {},
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

    // ─── Los estados ──────────────────────────────────────────────────────────

    private fun estadoDe(
        mediciones: List<MedicionDelCobro>,
        indice: IndiceDePuntosCompartidos = indiceVacio()
    ): UbicacionUiState {
        val lugares = LugaresDelCliente.de(mediciones, indice)
        return UbicacionUiState(
            cargando = false,
            lugares = lugares.paraElMapa(null),
            direccion = "Av. 5 Poniente 1204, Col. Centro",
            sinPuertaMedida = lugares.sinPuertaMedida,
            pareceMudanza = lugares.pareceMudanza,
            masViejo = mediciones.minOfOrNull { it.fecha },
            masNuevo = mediciones.maxOfOrNull { it.fecha }
        )
    }

    private fun conPuerta() = estadoDe(
        (0 until 8).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong() * 7) }
    )

    private fun compartido(): UbicacionUiState {
        val punto = desplazado(PUERTA, 400.0)
        return estadoDe(
            (0 until 9).map { medicion(desplazado(punto, it * 0.5), diasAtras = it.toLong() * 7) },
            IndiceDePuntosCompartidos.de(rutaCompartida(punto, clientes = 381, cobradores = 30))
        )
    }

    private fun soloTransferencias() = estadoDe(
        (0 until 6).map {
            medicion(
                desplazado(PUERTA, it * 1.0),
                diasAtras = it.toLong() * 7,
                esTransferencia = true
            )
        }
    )

    private fun mudanza() = estadoDe(
        (0 until 5).map {
            medicion(desplazado(PUERTA, it * 2.0), diasAtras = 700L + it, id = "v$it")
        } +
            (0 until 5).map {
                medicion(
                    desplazado(desplazado(PUERTA, 900.0), it * 2.0),
                    diasAtras = 20L + it,
                    id = "n$it"
                )
            }
    )

    private fun sinPuntos() = UbicacionUiState(
        cargando = false,
        direccion = "Av. 5 Poniente 1204, Col. Centro"
    )

    private companion object {
        /** El mismo aire que `UbicacionScreen` deja entre el chip y la orilla. */
        val ORILLA_DE_LA_PANTALLA = 16.dp
    }
}
