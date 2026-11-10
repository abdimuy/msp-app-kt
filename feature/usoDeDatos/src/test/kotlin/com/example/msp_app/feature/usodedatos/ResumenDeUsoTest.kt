package com.example.msp_app.feature.usodedatos

import com.example.msp_app.core.datos.UsoDeEndpoint
import com.example.msp_app.core.datos.UsoDelDia
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-175: lo que ve el cobrador. */
class ResumenDeUsoTest {

    private val hoy = LocalDate.of(2026, 11, 10)

    private fun dia(
        fecha: String,
        rx: Long = 0,
        movil: Long = 0,
        wifi: Long = 0,
        abierta: Long = 0,
        e: Map<String, UsoDeEndpoint> = emptyMap()
    ) = UsoDelDia(fecha, rx, 0, movil, wifi, 0, abierta, 0, e)

    @Test fun `megas con un decimal y unidades de mil`() {
        assertEquals("0.0 MB", FormatoDeMegas.mb(0))
        assertEquals("1.5 MB", FormatoDeMegas.mb(1_500_000))
        assertEquals("12.3 MB", FormatoDeMegas.mb(12_340_000))
        assertEquals("1,234.6 MB", FormatoDeMegas.mb(1_234_567_000))
        assertEquals("0.0 MB", FormatoDeMegas.mb(-5))
    }

    @Test fun `sin datos es vacio`() {
        val e = ConstructorDeResumen.construir(emptyList(), hoy)
        assertTrue(e.vacio)
        assertNull(e.hoy)
    }

    @Test fun `hoy es solo hoy y la semana son hoy y los 6 anteriores`() {
        val dias = listOf(
            dia("2026-11-10", rx = 1_000_000, movil = 1_000_000, abierta = 1_000_000),
            dia("2026-11-04", rx = 2_000_000, wifi = 2_000_000).copy(segundoPlano = 2_000_000),
            // día -6 arriba: entra; día -7 abajo: no
            dia("2026-11-03", rx = 9_000_000, wifi = 9_000_000).copy(segundoPlano = 9_000_000)
        )
        val e = ConstructorDeResumen.construir(dias, hoy)
        assertEquals(1_000_000L, e.hoy!!.total)
        assertEquals(1_000_000L, e.hoy!!.movil)
        assertEquals(1_000_000L, e.hoy!!.abierta)
        assertEquals(3_000_000L, e.semana!!.total)
        assertEquals(2_000_000L, e.semana!!.wifi)
        assertEquals(2_000_000L, e.semana!!.segundoPlano)
        assertFalse(e.vacio)
    }

    @Test fun `servicios agrupados ordenados con mapa firebase y otros`() {
        val e = ConstructorDeResumen.construir(
            listOf(
                dia(
                    "2026-11-10",
                    rx = 11_000_000,
                    e = mapOf(
                        "GET /clientes" to UsoDeEndpoint(6_000_000, 100_000, 1),
                        "GET /v2/visitas/{id}" to UsoDeEndpoint(100_000, 0, 5),
                        "GET /v2/visitas/sync/zona/{id}" to UsoDeEndpoint(300_000, 0, 2)
                    )
                )
            ),
            hoy
        )
        val s = e.hoy!!.servicios
        assertEquals(
            listOf("Lista de clientes", "Mapa, Firebase y otros", "Visitas"),
            s.map { it.nombre }
        )
        assertEquals(6_100_000L, s[0].bytes)
        assertEquals(11_000_000L - 6_500_000L, s[1].bytes)
        assertEquals(400_000L, s[2].bytes)
        assertFalse(e.hoy!!.sinDetalle)
    }

    @Test fun `sin desglose con total marca sin detalle y no inventa filas`() {
        val e = ConstructorDeResumen.construir(
            listOf(dia("2026-11-10", rx = 5_000_000, wifi = 5_000_000)),
            hoy
        )
        assertTrue(e.hoy!!.sinDetalle)
        assertTrue(e.hoy!!.servicios.isEmpty())
    }

    @Test fun `un dia sin trafico hoy pero con semana muestra hoy en ceros`() {
        val e = ConstructorDeResumen.construir(listOf(dia("2026-11-08", rx = 3_000_000)), hoy)
        assertEquals(0L, e.hoy!!.total)
        assertEquals(3_000_000L, e.semana!!.total)
        assertFalse(e.vacio)
    }

    @Test fun `no muestra servicios en cero`() {
        val e = ConstructorDeResumen.construir(
            listOf(dia("2026-11-10", rx = 100, e = mapOf("GET /ping" to UsoDeEndpoint(0, 0, 1)))),
            hoy
        )
        assertTrue(e.hoy!!.servicios.none { it.bytes == 0L })
    }
}
