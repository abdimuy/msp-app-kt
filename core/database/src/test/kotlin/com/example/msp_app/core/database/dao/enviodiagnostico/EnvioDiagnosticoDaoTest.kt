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
    fun `E-APP-063 registrarFallo reporta a lo mas una vez por fila y codigo, aunque alternen`() =
        runTest {
            val r = listOf(
                "network_error",
                "network_error",
                "timeout",
                "network_error",
                "timeout",
                "http_502"
            )
                .mapIndexed { i, codigo ->
                    dao.registrarFallo(
                        "pago",
                        "p1",
                        codigo,
                        "m",
                        0,
                        "2026-10-16T10:0$i:00Z"
                    )
                }
            assertEquals(listOf(true, false, true, false, false, true), r)

            val fila = dao.obtener("pago", "p1")!!
            assertEquals(6, fila.INTENTOS)
            assertEquals("http_502", fila.ULTIMO_ERROR_CODIGO)
            assertEquals("2026-10-16T10:05:00Z", fila.ULTIMO_INTENTO_AT)
            assertEquals("network_error,timeout,http_502", fila.REPORTADO_CODIGO)
        }

    @Test
    fun `el reporte tiene tope de codigos por fila`() = runTest {
        val r = (1..12).map {
            dao.registrarFallo(
                "pago",
                "p1",
                "c$it",
                "m",
                0,
                "2026-10-16T10:00:00Z"
            )
        }
        assertEquals(EnvioDiagnosticoDao.MAX_CODIGOS_REPORTADOS, r.count { it })
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
    fun `registrarExito limpia el error y conserva lo ya reportado`() = runTest {
        dao.registrarFallo("visita", "v1", "timeout", "Tiempo agotado", 0, "2026-10-16T10:00:00Z")
        dao.registrarExito("visita", "v1", "2026-10-16T10:05:00Z")
        val fila = dao.obtener("visita", "v1")!!
        assertNull(fila.ULTIMO_ERROR_CODIGO)
        assertNull(fila.ULTIMO_ERROR_MENSAJE)
        assertNull(fila.ULTIMO_ERROR_HTTP)
        assertEquals("2026-10-16T10:05:00Z", fila.ULTIMO_EXITO_AT)
        assertEquals(2, fila.INTENTOS)
        assertFalse(
            dao.registrarFallo(
                "visita",
                "v1",
                "timeout",
                "Tiempo agotado",
                0,
                "2026-10-16T11:00:00Z"
            )
        )
        assertTrue(
            dao.registrarFallo(
                "visita",
                "v1",
                "http_500",
                "Falla del servidor",
                500,
                "2026-10-16T12:00:00Z"
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
    fun `E-APP-063 la poda borra toda fila sin intentos en 30 dias, tambien las soltadas sin 2xx`() =
        runTest {
            // Soltada por un 4xx resguardado: nunca tuvo 2xx y ya no recibe intentos.
            dao.registrarFallo(
                "pago",
                "soltada-sin-2xx",
                "validation_error",
                "Datos rechazados",
                422,
                "2026-08-01T10:00:00Z"
            )
            dao.registrarFallo(
                "pago",
                "exito-viejo",
                "timeout",
                "Tiempo agotado",
                0,
                "2026-08-01T10:00:00Z"
            )
            dao.registrarExito("pago", "exito-viejo", "2026-08-01T10:05:00Z")
            // Sigue fallando: su último intento es reciente.
            dao.registrarFallo(
                "pago",
                "sigue-fallando",
                "timeout",
                "Tiempo agotado",
                0,
                "2026-08-01T10:00:00Z"
            )
            dao.registrarFallo(
                "pago",
                "sigue-fallando",
                "timeout",
                "Tiempo agotado",
                0,
                "2026-10-15T10:00:00Z"
            )

            assertEquals(2, dao.podarIntentosAnterioresA("2026-09-16T00:00:00Z"))

            assertNull(dao.obtener("pago", "soltada-sin-2xx"))
            assertNull(dao.obtener("pago", "exito-viejo"))
            assertEquals("sigue-fallando", dao.obtener("pago", "sigue-fallando")!!.FILA_ID)
        }

    /**
     * Plan visitas P1, Tarea 14 (H14): la telemetría cuenta los fallos
     * VIGENTES de un tipo por código, con el máximo de intentos —así se ve una
     * fila que se reintenta para siempre—. Un éxito saca a su fila de la
     * cuenta; el otro tipo no se mezcla.
     */
    @Test
    fun `H14 fallos vigentes por codigo cuentan filas e intentos maximos`() = runTest {
        repeat(5) {
            dao.registrarFallo(
                "visita",
                "v1",
                "visita_fecha_futura",
                "m",
                422,
                "2026-10-16T10:0$it:00Z"
            )
        }
        dao.registrarFallo("visita", "v2", "visita_fecha_futura", "m", 422, "2026-10-16T10:00:00Z")
        dao.registrarFallo("visita", "v3", "timeout", "m", 0, "2026-10-16T10:00:00Z")
        dao.registrarFallo("visita", "v4", "timeout", "m", 0, "2026-10-16T10:00:00Z")
        dao.registrarExito("visita", "v4", "2026-10-16T10:01:00Z")
        dao.registrarFallo("pago", "p1", "timeout", "m", 0, "2026-10-16T10:00:00Z")

        assertEquals(
            listOf(
                FallosPorCodigo(codigo = "timeout", filas = 1, intentosMax = 1),
                FallosPorCodigo(codigo = "visita_fecha_futura", filas = 2, intentosMax = 5)
            ),
            dao.fallosVigentesPorCodigo("visita")
        )
    }
}
