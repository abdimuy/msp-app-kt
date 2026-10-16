package com.example.msp_app.core.database.dao.enviodiagnostico

import com.example.msp_app.core.database.entities.EnvioDiagnosticoEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-063: el DAO de diagnóstico anota, deduplica el reporte y se poda. */
class EnvioDiagnosticoDaoTest : RoomTestBase() {

    private val dao get() = db.envioDiagnosticoDao()

    @Test
    fun `registrarFallo crea la fila y reporta una sola vez por codigo distinto`() = runTest {
        assertTrue(
            dao.registrarFallo(
                "pago",
                "p1",
                "network_error",
                "Sin conexión",
                0,
                "2026-10-16T10:00:00Z"
            )
        )
        assertFalse(
            dao.registrarFallo(
                "pago",
                "p1",
                "network_error",
                "Sin conexión",
                0,
                "2026-10-16T10:01:00Z"
            )
        )
        assertTrue(
            dao.registrarFallo(
                "pago",
                "p1",
                "http_502",
                "Falla del servidor",
                502,
                "2026-10-16T10:02:00Z"
            )
        )
        assertTrue(
            dao.registrarFallo(
                "pago",
                "p1",
                "network_error",
                "Sin conexión",
                0,
                "2026-10-16T10:03:00Z"
            )
        )

        val fila = dao.obtener("pago", "p1")!!
        assertEquals(4, fila.INTENTOS)
        assertEquals("network_error", fila.ULTIMO_ERROR_CODIGO)
        assertEquals(0, fila.ULTIMO_ERROR_HTTP)
        assertEquals("2026-10-16T10:03:00Z", fila.ULTIMO_INTENTO_AT)
    }

    @Test
    fun `el mismo id de pago y de visita son filas distintas`() = runTest {
        dao.registrarFallo("pago", "x", "timeout", "Tiempo agotado", 0, "2026-10-16T10:00:00Z")
        dao.registrarFallo(
            "visita",
            "x",
            "http_422",
            "Datos rechazados",
            422,
            "2026-10-16T10:00:00Z"
        )
        assertEquals("timeout", dao.obtener("pago", "x")!!.ULTIMO_ERROR_CODIGO)
        assertEquals("http_422", dao.obtener("visita", "x")!!.ULTIMO_ERROR_CODIGO)
    }

    @Test
    fun `registrarExito limpia el error y vuelve a permitir reportar`() = runTest {
        dao.registrarFallo("visita", "v1", "timeout", "Tiempo agotado", 0, "2026-10-16T10:00:00Z")
        dao.registrarExito("visita", "v1", "2026-10-16T10:05:00Z")
        val fila = dao.obtener("visita", "v1")!!
        assertNull(fila.ULTIMO_ERROR_CODIGO)
        assertNull(fila.ULTIMO_ERROR_MENSAJE)
        assertNull(fila.ULTIMO_ERROR_HTTP)
        assertEquals("2026-10-16T10:05:00Z", fila.ULTIMO_EXITO_AT)
        assertEquals(2, fila.INTENTOS)
        assertTrue(
            dao.registrarFallo(
                "visita",
                "v1",
                "timeout",
                "Tiempo agotado",
                0,
                "2026-10-16T11:00:00Z"
            )
        )
    }

    @Test
    fun `registrarExito sin fallo previo no crea fila`() = runTest {
        dao.registrarExito("pago", "nuevo", "2026-10-16T10:00:00Z")
        assertNull(dao.obtener("pago", "nuevo"))
    }

    @Test
    fun `observarFallosVigentes trae solo fallos del tipo, el mas reciente primero`() = runTest {
        dao.registrarFallo("pago", "a", "timeout", "Tiempo agotado", 0, "2026-10-16T10:00:00Z")
        dao.registrarFallo(
            "pago",
            "b",
            "http_500",
            "Falla del servidor",
            500,
            "2026-10-16T11:00:00Z"
        )
        dao.registrarFallo("pago", "c", "timeout", "Tiempo agotado", 0, "2026-10-16T09:00:00Z")
        dao.registrarExito("pago", "c", "2026-10-16T12:00:00Z")
        dao.registrarFallo("visita", "d", "timeout", "Tiempo agotado", 0, "2026-10-16T13:00:00Z")

        val filas = dao.observarFallosVigentes(EnvioDiagnosticoEntity.TIPO_PAGO).first()
        assertEquals(listOf("b", "a"), filas.map { it.FILA_ID })
    }

    @Test
    fun `la poda borra solo exitos viejos sin error vigente`() = runTest {
        dao.registrarFallo("pago", "viejo", "timeout", "Tiempo agotado", 0, "2026-08-01T10:00:00Z")
        dao.registrarExito("pago", "viejo", "2026-08-01T10:05:00Z")
        dao.registrarFallo(
            "pago",
            "reciente",
            "timeout",
            "Tiempo agotado",
            0,
            "2026-10-15T10:00:00Z"
        )
        dao.registrarExito("pago", "reciente", "2026-10-15T10:05:00Z")
        dao.registrarFallo(
            "pago",
            "fallando",
            "timeout",
            "Tiempo agotado",
            0,
            "2026-08-01T10:00:00Z"
        )

        dao.podarExitosAnterioresA("2026-09-16T00:00:00Z")

        assertNull(dao.obtener("pago", "viejo"))
        assertEquals("reciente", dao.obtener("pago", "reciente")!!.FILA_ID)
        assertEquals("fallando", dao.obtener("pago", "fallando")!!.FILA_ID)
    }
}
