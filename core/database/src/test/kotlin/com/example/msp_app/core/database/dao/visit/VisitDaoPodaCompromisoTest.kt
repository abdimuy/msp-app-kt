package com.example.msp_app.core.database.dao.visit

import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * E-APP-100 (plan visitas P2, Tarea 15b; decisión del coordinador): la poda no
 * borra una visita con compromiso local que el servidor todavía no probó ni
 * resguardó. Con contrato 3 el compromiso viaja al servidor, pero hasta que
 * `COMPROMISO_CONFIRMADO_EN` o `COMPROMISO_RESGUARDADO_EN` lo digan, esta fila
 * es la única copia: podarla es perder la promesa o la cita.
 *
 * Todos los casos están fuera de la ventana de retención por fecha del
 * compromiso: lo único que decide es la prueba del compromiso.
 */
class VisitDaoPodaCompromisoTest : RoomTestBase() {

    private val dao get() = db.visitDao()

    private fun visita(id: String, promesa: String? = null, cita: String? = null) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Ramírez Ortiz, Fernando",
        COBRADOR_ID = 200,
        FECHA = "2026-01-01T09:30:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        NOTA = null,
        TIPO_VISITA = "PROMESA",
        ZONA_CLIENTE_ID = 21552,
        IMPTE_DOCTO_CC_ID = 5000,
        PROMESA_VENTA_ID = promesa?.let { 512345 },
        PROMESA_FECHA = promesa,
        PROMESA_MONTO_CENTAVOS = promesa?.let { 15000L },
        CITA_FECHA = cita,
        CITA_HORA = cita?.let { "10:30" }
    )

    private suspend fun confirmada(v: VisitEntity) {
        dao.insertVisit(v)
        dao.confirmar(listOf(v.ID), VIEJA)
    }

    private suspend fun podar() = dao.deleteUploadedVisits(CORTE, CORTE, CORTE, CORTE_DIA)

    private suspend fun retenidas() = dao.compromisosRetenidosPorPoda(
        CORTE,
        CORTE,
        CORTE,
        CORTE_DIA
    )

    @Test
    fun `E-APP-100 confirmada con promesa o cita sin probar no se poda`() = runTest {
        confirmada(visita("promesa", promesa = "2026-01-05"))
        confirmada(visita("cita", cita = "2026-01-06"))

        podar()

        assertNotNull(dao.findVisitById("promesa"))
        assertNotNull(dao.findVisitById("cita"))
    }

    /** Control positivo: con los MISMOS cortes, la poda sigue podando. */
    @Test
    fun `E-APP-100 sin compromiso o con el compromiso probado o resguardado si se poda`() =
        runTest {
            confirmada(visita("sin-compromiso"))
            confirmada(visita("probada", promesa = "2026-01-05"))
            dao.confirmarCompromiso("probada", VIEJA)
            confirmada(visita("resguardado", cita = "2026-01-06"))
            dao.resguardarCompromiso("resguardado", VIEJA)

            podar()

            assertNull(dao.findVisitById("sin-compromiso"))
            assertNull(dao.findVisitById("probada"))
            assertNull(dao.findVisitById("resguardado"))
        }

    /** La regla vale también para la rama de las resguardadas (la visita entera en la bandeja). */
    @Test
    fun `E-APP-100 resguardada con compromiso sin probar no se poda`() = runTest {
        dao.insertVisit(visita("con-promesa", promesa = "2026-01-05"))
        dao.resguardar("con-promesa", VIEJA)
        dao.insertVisit(visita("sin-promesa"))
        dao.resguardar("sin-promesa", VIEJA)

        podar()

        assertNotNull(dao.findVisitById("con-promesa"))
        assertNull("control positivo", dao.findVisitById("sin-promesa"))
    }

    /**
     * La telemetría cuenta exactamente las que la poda habría borrado si no
     * fuera por el compromiso sin probar: no las vigentes por fecha, ni las
     * pendientes, ni las ya probadas.
     */
    @Test
    fun `E-APP-100 compromisosRetenidosPorPoda cuenta solo las retenidas por el compromiso`() =
        runTest {
            confirmada(visita("retenida-1", promesa = "2026-01-05"))
            dao.insertVisit(visita("retenida-2", cita = "2026-01-06"))
            dao.resguardar("retenida-2", VIEJA)
            confirmada(visita("vigente-por-fecha", promesa = "2099-01-01"))
            dao.insertVisit(visita("pendiente", promesa = "2026-01-05"))
            confirmada(visita("probada", promesa = "2026-01-05"))
            dao.confirmarCompromiso("probada", VIEJA)
            confirmada(visita("sin-compromiso"))

            assertEquals(2, retenidas())
            podar()
            assertEquals("la poda no cambia el conteo de las retenidas", 2, retenidas())
            assertNull(dao.findVisitById("probada"))
        }

    private companion object {
        const val VIEJA = "2026-01-10T00:00:00Z"
        const val CORTE = "2026-09-01T00:00:00Z"
        const val CORTE_DIA = "2026-06-01"
    }
}
