package com.example.msp_app.core.database.dao.visit

import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec visitas §5.2.5: el relleno del compromiso. `COMPROMISO_CONFIRMADO_EN` y
 * `COMPROMISO_RESGUARDADO_EN` son la prueba del servidor para el bloque de
 * promesa/cita, independiente de la de la visita base (`CONFIRMADA_EN`).
 */
class VisitDaoCompromisoTest : RoomTestBase() {

    private val dao get() = db.visitDao()

    @Test
    fun `compromisosPorRellenar lista solo las que tienen compromiso sin prueba, por FECHA e ID`() =
        runTest {
            dao.insertVisit(visita("b-promesa", fecha = T_DIA2, promesa = "2026-10-20"))
            dao.insertVisit(visita("a-cita", fecha = T_DIA2, cita = "2026-10-21"))
            dao.insertVisit(visita("c-vieja", fecha = T_DIA1, promesa = "2026-10-19"))
            dao.insertVisit(visita("sin-compromiso", fecha = T_DIA1))
            dao.insertVisit(visita("confirmada", fecha = T_DIA1, promesa = "2026-10-19"))
            dao.confirmarCompromiso("confirmada", T1)
            dao.insertVisit(visita("resguardada", fecha = T_DIA1, cita = "2026-10-19"))
            dao.resguardarCompromiso("resguardada", T1)

            assertEquals(listOf("c-vieja", "a-cita", "b-promesa"), dao.compromisosPorRellenar())
        }

    /** El compromiso se rellena aunque la visita base ya esté entregada. */
    @Test
    fun `compromisosPorRellenar incluye visitas base ya confirmadas`() = runTest {
        dao.insertVisit(visita("a", promesa = "2026-10-20"))
        dao.confirmarTrasSubida("a", T1)

        assertEquals(listOf("a"), dao.compromisosPorRellenar())
    }

    @Test
    fun `confirmarCompromiso estampa la prueba, limpia el resguardo y no toca la visita base`() =
        runTest {
            dao.insertVisit(visita("a", promesa = "2026-10-20"))
            dao.confirmarTrasSubida("a", T1)
            val base = dao.findVisitById("a")!!
            dao.resguardarCompromiso("a", T1)

            assertEquals(1, dao.confirmarCompromiso("a", T2))

            val v = dao.findVisitById("a")!!
            assertEquals(T2, v.COMPROMISO_CONFIRMADO_EN)
            assertNull(v.COMPROMISO_RESGUARDADO_EN)
            assertEquals(base.copy(COMPROMISO_CONFIRMADO_EN = T2), v)
        }

    @Test
    fun `confirmarCompromiso conserva la primera prueba`() = runTest {
        dao.insertVisit(visita("a", promesa = "2026-10-20"))
        dao.confirmarCompromiso("a", T1)

        assertEquals(1, dao.confirmarCompromiso("a", T2))

        assertEquals(T1, dao.findVisitById("a")!!.COMPROMISO_CONFIRMADO_EN)
    }

    @Test
    fun `resguardarCompromiso no pisa un compromiso ya confirmado ni desconfirma la visita`() =
        runTest {
            dao.insertVisit(visita("a", promesa = "2026-10-20"))
            dao.confirmarTrasSubida("a", T1)
            dao.insertVisit(visita("b", promesa = "2026-10-20"))
            dao.confirmarTrasSubida("b", T1)
            dao.confirmarCompromiso("b", T1)

            assertEquals(1, dao.resguardarCompromiso("a", T2))
            assertEquals(0, dao.resguardarCompromiso("b", T2))

            val a = dao.findVisitById("a")!!
            assertEquals(T2, a.COMPROMISO_RESGUARDADO_EN)
            assertEquals(T1, a.CONFIRMADA_EN)
            assertEquals(1, a.GUARDADO_EN_MICROSIP)
            assertNull(dao.findVisitById("b")!!.COMPROMISO_RESGUARDADO_EN)
        }

    @Test
    fun `las lecturas de Visit devuelven las dos marcas del compromiso`() = runTest {
        dao.insertVisit(visita("a", promesa = "2026-10-20").copy(COMPROMISO_CONFIRMADO_EN = T1))
        dao.insertVisit(visita("b", cita = "2026-10-20").copy(COMPROMISO_RESGUARDADO_EN = T2))

        val porId = listOf(dao.getVisitById("a"), dao.getVisitById("b"))
        val pendientes = dao.getPendingVisits().sortedBy { it.ID }
        val porFecha = dao.getVisitsByDate(
            "2026-10-01T00:00:00Z",
            "2026-11-01T00:00:00Z"
        ).sortedBy {
            it.ID
        }
        val porCliente = dao.getVisitsByClienteId(11486).sortedBy { it.ID }

        listOf(porId, pendientes, porFecha, porCliente).forEach { lista ->
            assertEquals(listOf(T1, null), lista.map { it.COMPROMISO_CONFIRMADO_EN })
            assertEquals(listOf(null, T2), lista.map { it.COMPROMISO_RESGUARDADO_EN })
        }
    }

    private fun visita(
        id: String,
        fecha: String = T_DIA2,
        promesa: String? = null,
        cita: String? = null
    ) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Mendoza Torres, Ana",
        COBRADOR_ID = 2991009,
        FECHA = fecha,
        FORMA_COBRO_ID = 157,
        LAT = 19.4326077,
        LNG = -99.133208,
        NOTA = null,
        TIPO_VISITA = "PROMESA",
        ZONA_CLIENTE_ID = 21552,
        IMPTE_DOCTO_CC_ID = 512345,
        PROMESA_VENTA_ID = promesa?.let { 512345 },
        PROMESA_FECHA = promesa,
        PROMESA_MONTO_CENTAVOS = promesa?.let { 15000L },
        CITA_FECHA = cita,
        CITA_HORA = cita?.let { "10:30" }
    )

    private companion object {
        const val T_DIA1 = "2026-10-15T18:00:00Z"
        const val T_DIA2 = "2026-10-16T18:00:00Z"
        const val T1 = "2026-10-16T18:05:00Z"
        const val T2 = "2026-10-17T09:00:00Z"
    }
}
