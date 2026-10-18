package com.example.msp_app.core.diagnosticoenvio

import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** E-APP-063: la pantalla de pendientes ve el motivo del fallo más reciente entre SUS pendientes. */
class LectorDeEnvioDiagnosticoTest : RoomTestBase() {

    @Test
    fun `motivo del fallo mas reciente entre los pendientes`() = runTest {
        val dao = db.envioDiagnosticoDao()
        dao.registrarFallo(
            "pago",
            "ya-enviado",
            "http_500",
            "Falla del servidor",
            500,
            "2026-10-16T12:00:00Z"
        )
        dao.registrarFallo("pago", "p1", "timeout", "Tiempo agotado", 0, "2026-10-16T10:00:00Z")
        dao.registrarFallo("pago", "p2", "network_error", "Sin conexión", 0, "2026-10-16T11:00:00Z")
        val lector = LectorDeEnvioDiagnostico(dao)

        assertEquals(
            "Último motivo: Sin conexión (network_error)",
            lector.motivoMasReciente("pago", setOf("p1", "p2")).first()
        )
        assertNull(lector.motivoMasReciente("pago", setOf("sin-fallo")).first())
        assertNull(lector.motivoMasReciente("visita", setOf("p1")).first())
    }

    /**
     * Plan visitas P1, Tarea 14: la pantalla de pendientes pinta el motivo y
     * los intentos de CADA fila, para que la que se reintenta para siempre se
     * vea. El otro tipo no se mezcla; una fila con éxito no tiene motivo.
     */
    @Test
    fun `H14 motivos por fila con sus intentos`() = runTest {
        val dao = db.envioDiagnosticoDao()
        repeat(3) {
            dao.registrarFallo(
                "visita",
                "v1",
                "visita_fecha_futura",
                "Datos rechazados",
                422,
                "2026-10-16T10:0$it:00Z"
            )
        }
        dao.registrarFallo("visita", "v2", "timeout", "Tiempo agotado", 0, "2026-10-16T10:00:00Z")
        dao.registrarExito("visita", "v2", "2026-10-16T10:05:00Z")
        dao.registrarFallo("pago", "v3", "timeout", "Tiempo agotado", 0, "2026-10-16T10:00:00Z")

        assertEquals(
            mapOf("v1" to MotivoDeFila("Último motivo: Datos rechazados (visita_fecha_futura)", 3)),
            LectorDeEnvioDiagnostico(dao).motivosPorFila("visita").first()
        )
    }
}
