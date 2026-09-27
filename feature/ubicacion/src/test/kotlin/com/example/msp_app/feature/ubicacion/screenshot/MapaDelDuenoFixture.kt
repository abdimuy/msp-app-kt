package com.example.msp_app.feature.ubicacion.screenshot

import com.example.msp_app.core.geo.IndiceDePuntosCompartidos
import com.example.msp_app.core.geo.LugaresDelCliente
import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.core.geo.Punto
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.PUERTA
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.desplazado
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.rutaCompartida
import com.example.msp_app.feature.ubicacion.domain.ClaseDeLugar
import com.example.msp_app.feature.ubicacion.domain.MapaDelCliente
import com.example.msp_app.feature.ubicacion.ui.UbicacionUiState
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * **El cliente del mock**, con los mismos números: 21 cobros donde más paga
 * (sáb 10–12, $220, Marisol Vega), 6 en otro lugar a 440 m (mié 14–16, $150,
 * Gabriel Roque), 4 antes (feb–may, a 390 m), 3 en un punto de 7 clientes,
 * 3 transferencias y 2 puntos sueltos.
 */
object MapaDelDuenoFixture {
    private val zona = ZoneId.of("America/Mexico_City")
    val AHORA: Instant = LocalDateTime.of(2026, 9, 25, 12, 0).atZone(zona).toInstant()

    private fun en(t: String): Instant = LocalDateTime.parse(t).atZone(zona).toInstant()

    private var n = 0
    private fun m(
        p: Punto,
        t: String,
        venta: Int,
        cobrador: String,
        importe: Int,
        transferencia: Boolean = false
    ): MedicionDelCobro = MedicionDelCobro(
        pagoId = "p${n++}",
        punto = desplazado(p, (n % 5) * 1.5, (n % 3) * 1.2),
        fecha = en(t),
        ventaId = venta,
        cobrador = cobrador,
        esTransferencia = transferencia,
        importe = BigDecimal(importe),
        formaCobroId = if (transferencia) 52569 else 157
    )

    private val OTRO = desplazado(PUERTA, -300.0, 320.0)
    private val ANTES = desplazado(PUERTA, -250.0, -300.0)
    private val COMPARTIDO = desplazado(PUERTA, 200.0, 311.0)
    private val TRANSFERENCIAS = desplazado(PUERTA, 480.0, 150.0)

    fun mediciones(): List<MedicionDelCobro> {
        n = 0
        val principal = listOf(
            "2026-06-06T10:20", "2026-06-13T10:40", "2026-06-20T11:05", "2026-06-27T10:15",
            "2026-07-04T10:30", "2026-07-11T11:20", "2026-07-18T10:10", "2026-07-25T10:50",
            "2026-08-01T11:30", "2026-08-08T10:25", "2026-08-15T10:45", "2026-08-22T11:10",
            "2026-08-29T10:05", "2026-09-05T10:35", "2026-09-12T11:15", "2026-06-09T17:20",
            "2026-07-14T18:05", "2026-08-11T16:40", "2026-09-01T19:10", "2026-09-19T15:30",
            "2026-09-20T11:00"
        ).map { m(PUERTA, it, 1, "MARISOL VEGA", 220) }
        val otro = listOf(
            "2026-06-17T14:30",
            "2026-07-15T14:50",
            "2026-08-26T14:08",
            "2026-09-02T14:21",
            "2026-09-16T14:40",
            "2026-09-24T14:12"
        ).map { m(OTRO, it, 2, "GABRIEL ROQUE", 150) }
        val antes = listOf(
            "2026-02-14T10:30",
            "2026-03-14T10:10",
            "2026-04-18T11:00",
            "2026-05-24T10:40"
        )
            .map { m(ANTES, it, 2, "MARISOL VEGA", 200) }
        val compartido = listOf("2026-07-02T12:00", "2026-08-06T12:30", "2026-09-10T13:00")
            .map { m(COMPARTIDO, it, 1, "MARISOL VEGA", 220) }
        val transferencias = listOf("2026-07-08T20:00", "2026-08-12T21:00", "2026-09-09T20:30")
            .map { m(TRANSFERENCIAS, it, 1, "GABRIEL ROQUE", 220, transferencia = true) }
        val sueltos = listOf(
            m(Punto(25.67, -100.31), "2026-07-21T09:00", 1, "GABRIEL ROQUE", 150),
            m(desplazado(PUERTA, 2_000.0, 900.0), "2026-08-18T09:30", 2, "MARISOL VEGA", 150)
        )
        return principal + otro + antes + compartido + transferencias + sueltos
    }

    fun estado(tocado: ClaseDeLugar? = null): UbicacionUiState {
        val todas = mediciones()
        val indice = IndiceDePuntosCompartidos.de(
            rutaCompartida(COMPARTIDO, clientes = 7, cobradores = 2)
        )
        val mapa = MapaDelCliente.de(LugaresDelCliente.de(todas, indice))
        return UbicacionUiState(
            cargando = false,
            mapa = mapa,
            direccion = "Calle 17 Sur 1204",
            nombresDeVenta = mapOf(1 to "Refrigerador Mabe 14'", 2 to "Sala 3 piezas"),
            cobrosPorVenta = mapOf(1 to 26, 2 to 13),
            cobradoresDisponibles = listOf("MARISOL VEGA", "GABRIEL ROQUE"),
            totalCobros = 34,
            totalVisitas = 5,
            totalPromesas = 2,
            lugarTocado = tocado?.let { c -> mapa.todos.first { it.clase == c } },
            ahora = AHORA
        )
    }
}
