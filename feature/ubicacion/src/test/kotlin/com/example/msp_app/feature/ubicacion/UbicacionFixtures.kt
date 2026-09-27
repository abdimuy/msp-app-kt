package com.example.msp_app.feature.ubicacion

import com.example.msp_app.core.geo.IndiceDePuntosCompartidos
import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.core.geo.Punto
import com.example.msp_app.core.geo.PuntoDeLaRuta
import com.example.msp_app.feature.ubicacion.domain.port.AbrirEnMapasPort
import com.example.msp_app.feature.ubicacion.domain.port.PuntosPort
import java.time.Instant
import java.time.temporal.ChronoUnit

object UbicacionFixtures {

    val PUERTA = Punto(18.46818, -97.37786)
    val AHORA: Instant = Instant.parse("2026-09-24T12:00:00Z")

    fun desplazado(origen: Punto, metrosNorte: Double, metrosEste: Double = 0.0): Punto {
        val gradosPorMetro = 180.0 / (Punto.RADIO_DE_LA_TIERRA_M * Math.PI)
        return Punto(
            origen.lat + metrosNorte * gradosPorMetro,
            origen.lon + metrosEste * gradosPorMetro / Math.cos(Math.toRadians(origen.lat))
        )
    }

    @Suppress("LongParameterList")
    fun medicion(
        punto: Punto,
        diasAtras: Long = 0,
        id: String = "pago-$diasAtras-${punto.lat}",
        ventaId: Int = 1,
        cobrador: String = "Rocío Manzano",
        esTransferencia: Boolean = false,
        importe: Int? = null,
        fecha: Instant = AHORA.minus(diasAtras, ChronoUnit.DAYS)
    ) = MedicionDelCobro(
        pagoId = id,
        punto = punto,
        fecha = fecha,
        ventaId = ventaId,
        cobrador = cobrador,
        esTransferencia = esTransferencia,
        importe = importe?.let { java.math.BigDecimal(it) },
        formaCobroId = if (esTransferencia) 52569 else 157
    )

    /** Un puerto de mentira: sin Room, sin MockK. */
    class PuntosFalsos(
        private val mediciones: List<MedicionDelCobro>,
        private val ruta: List<PuntoDeLaRuta> = emptyList(),
        private val visitas: List<com.example.msp_app.feature.ubicacion.domain.VisitaMedida> =
            emptyList(),
        private val ventas: Map<Int, String> = emptyMap()
    ) : PuntosPort {
        override suspend fun visitasDe(clienteId: Int) = visitas

        override suspend fun ventasDe(clienteId: Int) = ventas

        var vecesQueLeyoLaRuta = 0
            private set

        override suspend fun medicionesDe(clienteId: Int) = mediciones

        override suspend fun clienteDeVenta(ventaId: Int): Int? = ventaId.takeIf { it > 0 }

        override suspend fun puntosDeLaRuta(): List<PuntoDeLaRuta> {
            vecesQueLeyoLaRuta++
            return ruta
        }
    }

    /** Un punto que comparten [clientes] clientes de [cobradores] cobradores. */
    fun rutaCompartida(punto: Punto, clientes: Int, cobradores: Int) = (1..clientes).map {
        PuntoDeLaRuta(1000 + it, "cobrador-${it % cobradores}", punto)
    }

    fun indiceVacio(): IndiceDePuntosCompartidos = IndiceDePuntosCompartidos.de(emptyList())

    /**
     * Un puerto de mapas de mentira que **recuerda a dónde se mandó navegar**.
     *
     * Devuelve `Result.success` porque el caso interesante de esta pantalla no
     * es el fallo del intent —ése ya lo cubre el adaptador— sino **hacia qué
     * punto se sale**: tiene que ser el centro del grupo, no la última medición.
     */
    class MapasFalsos : AbrirEnMapasPort {
        var ultimoDestino: Triple<Double, Double, String>? = null
            private set

        override suspend fun comoLlegar(lat: Double, lon: Double, etiqueta: String): Result<Unit> {
            ultimoDestino = Triple(lat, lon, etiqueta)
            return Result.success(Unit)
        }
    }
}
