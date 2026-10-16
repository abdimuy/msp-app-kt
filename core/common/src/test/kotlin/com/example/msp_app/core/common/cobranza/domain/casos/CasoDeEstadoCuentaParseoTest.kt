package com.example.msp_app.core.common.cobranza.domain.casos

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CasoDeEstadoCuentaParseoTest {

    private val valido = """
        { "nombre": "cliente con dos ventas, no estaba marca ambas",
          "ventana": { "inicio": "2026-10-09T13:46:00Z", "fin": "2026-10-14T22:40:00Z" },
          "hoy": "2026-10-14",
          "cuentas": [ { "venta_id": 1, "cliente_id": 10, "parcialidad": "150.00" },
                       { "venta_id": 2, "cliente_id": 10, "parcialidad": "200.00" } ],
          "pagos":   [ { "venta_id": 1, "importe": "150.00", "forma_cobro_id": 157,
                         "fecha_hora": "2026-10-10T15:00:00Z", "rechazado": false } ],
          "visitas": [ { "cliente_id": 10, "venta_id": null, "tipo_visita": "No se encontraba",
                         "fecha_hora": "2026-10-11T16:00:00Z", "fecha_cita": null, "hora_cita": null,
                         "fecha_promesa": null } ],
          "esperado": [ { "venta_id": 1, "estado": "pago", "con_algo": true, "columna": "pagados" },
                        { "venta_id": 2, "estado": "no_estaba", "con_algo": true, "columna": "por_visitar" } ] }
    """.trimIndent()

    private fun rechaza(json: String, debeMencionar: String) {
        try {
            CasoDeEstadoCuenta.desdeJson("999-prueba.json", json)
            fail("debió rechazar: $debeMencionar")
        } catch (e: IllegalArgumentException) {
            assertTrue("mensaje: ${e.message}", e.message.orEmpty().contains(debeMencionar))
        }
    }

    @Test
    fun `el ejemplo del contrato se parsea`() {
        val caso = CasoDeEstadoCuenta.desdeJson("001-ejemplo.json", valido)
        assertEquals(2, caso.cuentas.size)
        assertEquals(BigDecimal("150.00"), caso.pagos.single().importe)
        assertEquals(null, caso.visitas.single().ventaId)
        assertEquals(EstadoCuenta.NO_ESTABA, caso.esperado[1].estado)
    }

    @Test
    fun `los ocho codigos del contrato van y vuelven`() {
        val codigos = listOf(
            "pago",
            "abono_parcial",
            "visite_vuelvo",
            "prometio_proxima",
            "se_nego",
            "cita_a_una_hora",
            "no_estaba",
            "sin_tocar"
        )
        assertEquals(EstadoCuenta.entries.toSet(), codigos.map(CodigoEstado::aEnum).toSet())
        codigos.forEach { assertEquals(it, CodigoEstado.aCodigo(CodigoEstado.aEnum(it))) }
    }

    @Test
    fun `hoy ausente se rechaza`() {
        rechaza(valido.replace("\"hoy\": \"2026-10-14\",", ""), "falta la clave [hoy]")
    }

    @Test
    fun `columna desconocida se rechaza`() {
        rechaza(valido.replace("\"por_visitar\"", "\"volver\""), "columna desconocida \"volver\"")
    }

    @Test
    fun `codigo de estado desconocido se rechaza`() {
        rechaza(valido.replace("\"no_estaba\"", "\"NO_ESTABA\""), "NO_ESTABA")
    }

    @Test
    fun `dinero como numero JSON se rechaza`() {
        rechaza(valido.replace("\"importe\": \"150.00\"", "\"importe\": 150.0"), "importe")
    }

    @Test
    fun `clave desconocida se rechaza`() {
        rechaza(valido.replace("\"fecha_cita\": null", "\"fecha_cit\": null"), "fecha_cit")
    }

    @Test
    fun `clave obligatoria ausente se rechaza`() {
        rechaza(valido.replace(", \"rechazado\": false", ""), "rechazado")
    }

    @Test
    fun `esperado incompleto se rechaza`() {
        val sinVentaDos = valido.replace(
            Regex(
                """,\s*\{ "venta_id": 2, "estado": "no_estaba", "con_algo": true, "columna": "por_visitar" \}"""
            ),
            ""
        )
        // Control: el reemplazo sí ocurrió (si no, la prueba pasaría por otra razón).
        assertTrue(sinVentaDos != valido)
        rechaza(sinVentaDos, "esperado")
    }
}
