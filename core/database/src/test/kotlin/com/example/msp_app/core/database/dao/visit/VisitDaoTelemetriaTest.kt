package com.example.msp_app.core.database.dao.visit

import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan visitas P1, Tarea 14 (H14): las dos lecturas de la telemetría y de la
 * pantalla de pendientes. Solo LEEN `Visit`; ninguna participa en decidir un
 * envío.
 */
class VisitDaoTelemetriaTest : RoomTestBase() {

    private val dao get() = db.visitDao()

    @Test
    fun `H14 conteos de envio con la tabla vacia son cero y sin mas vieja`() = runTest {
        assertEquals(ConteosDeEnvio(0, null, 0, 0), dao.conteosDeEnvio(DESDE_24H))
    }

    @Test
    fun `H14 conteos de envio separan pendientes, resguardadas y confirmadas en 24 h`() = runTest {
        dao.insertVisit(visita("p-vieja", fecha = "2026-10-15T09:00:00Z"))
        dao.insertVisit(visita("p-nueva", fecha = "2026-10-16T11:00:00Z"))
        dao.insertVisit(visita("r"))
        dao.resguardar("r", "2026-10-16T12:00:00Z")
        dao.insertVisit(visita("c-reciente"))
        dao.confirmar(listOf("c-reciente"), "2026-10-16T13:00:00Z")
        dao.insertVisit(visita("c-vieja"))
        dao.confirmar(listOf("c-vieja"), "2026-10-14T13:00:00Z")
        // Legada v34: soltada sin marcas. No es pendiente, ni resguardada, ni confirmada.
        dao.insertVisit(visita("legada", guardado = 1))

        assertEquals(
            ConteosDeEnvio(
                pendientes = 2,
                masVieja = "2026-10-15T09:00:00Z",
                resguardadas = 1,
                confirmadas24h = 1
            ),
            dao.conteosDeEnvio(DESDE_24H)
        )
    }

    @Test
    fun `H14 la pantalla de envio trae pendientes y resguardadas, en orden, y nada mas`() =
        runTest {
            dao.insertVisit(visita("p2", fecha = "2026-10-16T11:00:00Z"))
            dao.insertVisit(visita("p1", fecha = "2026-10-15T09:00:00Z"))
            dao.insertVisit(visita("r", fecha = "2026-10-15T10:00:00Z"))
            dao.resguardar("r", "2026-10-16T12:00:00Z")
            dao.insertVisit(visita("c"))
            dao.confirmar(listOf("c"), "2026-10-16T13:00:00Z")
            dao.insertVisit(visita("legada", guardado = 1))

            assertEquals(listOf("p1", "r", "p2"), dao.paraPantallaDeEnvio().map { it.ID })
        }

    private fun visita(id: String, fecha: String = "2026-10-16T10:00:00Z", guardado: Int = 0) =
        VisitEntity(
            ID = id,
            CLIENTE_ID = 30144,
            COBRADOR = "Domínguez Reyes, Efraín",
            COBRADOR_ID = 7,
            FECHA = fecha,
            FORMA_COBRO_ID = 157,
            LAT = 19.043415,
            LNG = -98.198234,
            NOTA = "No estaba, vuelvo el jueves",
            TIPO_VISITA = "SIN_PAGO",
            ZONA_CLIENTE_ID = 21,
            IMPTE_DOCTO_CC_ID = 512345,
            GUARDADO_EN_MICROSIP = guardado
        )

    private companion object {
        const val DESDE_24H = "2026-10-15T14:00:00Z"
    }
}
