package com.example.msp_app.core.geo

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Utilidades compartidas por las pruebas de `:core:geo`.
 *
 * Las coordenadas base son de la ruta real (Tehuacán y alrededores) porque la
 * latitud importa: el paso de la rejilla y el coseno de la longitud se calculan
 * con ella, y una prueba escrita sobre el ecuador no ejercitaría esa corrección.
 */
object LugaresFixtures {

    /** Un domicilio cualquiera de la ruta. */
    val PUERTA = Punto(18.46818, -97.37786)

    /** La celda con 381 clientes de 30 cobradores, medida el 2026-09-24. */
    val PUNTO_MUY_COMPARTIDO = Punto(18.42318, -97.34106)

    val AHORA: Instant = Instant.parse("2026-09-24T12:00:00Z")

    /** Un punto desplazado [metrosNorte] / [metrosEste] del [origen]. */
    fun desplazado(origen: Punto, metrosNorte: Double, metrosEste: Double = 0.0): Punto {
        val gradosPorMetroLat = 180.0 / (Punto.RADIO_DE_LA_TIERRA_M * Math.PI)
        val dLat = metrosNorte * gradosPorMetroLat
        val dLon = metrosEste * gradosPorMetroLat / Math.cos(Math.toRadians(origen.lat))
        return Punto(origen.lat + dLat, origen.lon + dLon)
    }

    @Suppress("LongParameterList")
    fun medicion(
        punto: Punto,
        diasAtras: Long = 0,
        id: String = "pago-$diasAtras-${punto.lat}",
        ventaId: Int = 1,
        cobrador: String = "Rocío Manzano",
        esTransferencia: Boolean = false
    ): MedicionDelCobro = MedicionDelCobro(
        pagoId = id,
        punto = punto,
        fecha = AHORA.minus(diasAtras, ChronoUnit.DAYS),
        ventaId = ventaId,
        cobrador = cobrador,
        esTransferencia = esTransferencia
    )

    /** Un índice donde nada está compartido: el caso del cliente común. */
    fun sinPuntosCompartidos(): IndiceDePuntosCompartidos =
        IndiceDePuntosCompartidos.de(emptyList())

    /** Un índice donde [punto] lo comparten [clientes] clientes de [cobradores]. */
    fun conPuntoCompartido(
        punto: Punto,
        clientes: Int = 40,
        cobradores: Int = 1
    ): IndiceDePuntosCompartidos = IndiceDePuntosCompartidos.de(
        (1..clientes).map { i ->
            PuntoDeLaRuta(
                clienteId = 1000 + i,
                cobrador = "cobrador-${i % cobradores}",
                punto = punto
            )
        }
    )
}
