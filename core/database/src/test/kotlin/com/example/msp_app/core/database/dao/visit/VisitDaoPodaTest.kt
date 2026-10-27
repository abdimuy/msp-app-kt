package com.example.msp_app.core.database.dao.visit

import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Poda segura (spec visitas §4.2-8, cierra H2; Review Focus 2 del plan P1):
 * solo se borra lo que el servidor CONFIRMÓ hace más de 30 días o lo que
 * RESGUARDÓ hace más de 90. Una legada `GUARDADO_EN_MICROSIP = 1` con
 * `CONFIRMADA_EN` nulo (soltada sin prueba antes de v35) espera a que la
 * reconciliación inversa (Tarea 13) la confirme o la devuelva a pendiente.
 *
 * Los argumentos de [VisitDao.deleteUploadedVisits] van en orden:
 * confirmadasAntesDe, resguardadasAntesDe, comprobantesDesde, conservarCompromisosDesde.
 */
class VisitDaoPodaTest : RoomTestBase() {

    private val dao get() = db.visitDao()

    private fun visita(id: String, guardado: Int = 0) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Ramírez Ortiz, Fernando",
        COBRADOR_ID = 200,
        FECHA = "2026-06-01T09:30:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        NOTA = "pidió pasar el viernes",
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21552,
        IMPTE_DOCTO_CC_ID = 5000,
        GUARDADO_EN_MICROSIP = guardado
    )

    @Test fun podaNoBorraEntregadaLegadaSinConfirmar() = runTest {
        dao.insertVisit(visita("legada", guardado = 1)) // CONFIRMADA_EN nulo: anterior a v35
        // Control positivo: con los MISMOS cortes, una confirmada sí se va.
        dao.insertVisit(visita("confirmada"))
        dao.confirmar(listOf("confirmada"), "2026-01-01T00:00:00Z")

        dao.deleteUploadedVisits(
            "2099-01-01T00:00:00Z",
            "2099-01-01T00:00:00Z",
            "2099-01-01T00:00:00Z",
            "2099-01-01",
            emptyList()
        )

        assertNotNull(
            "la legada sin confirmar espera a la reconciliación inversa",
            dao.findVisitById("legada")
        )
        assertNull(dao.findVisitById("confirmada"))
    }

    @Test fun `poda borra la confirmada hace mas de 30 dias`() = runTest {
        dao.insertVisit(visita("vieja"))
        dao.confirmar(listOf("vieja"), "2026-09-01T00:00:00Z")
        dao.deleteUploadedVisits(
            "2026-09-16T00:00:00Z",
            "2026-07-18T00:00:00Z",
            "2026-10-01T00:00:00Z",
            "2026-07-18",
            emptyList()
        )
        assertNull(dao.findVisitById("vieja"))
    }

    @Test fun `poda no borra la confirmada reciente ni la pendiente`() = runTest {
        dao.insertVisit(visita("reciente"))
        dao.confirmar(listOf("reciente"), "2026-10-15T00:00:00Z")
        dao.insertVisit(visita("pendiente"))
        dao.deleteUploadedVisits(
            "2026-09-16T00:00:00Z",
            "2026-07-18T00:00:00Z",
            "2026-10-01T00:00:00Z",
            "2026-07-18",
            emptyList()
        )
        assertNotNull(dao.findVisitById("reciente"))
        assertNotNull(dao.findVisitById("pendiente"))
    }

    @Test fun `el instante exacto del corte de confirmadas sobrevive`() = runTest {
        dao.insertVisit(visita("borde"))
        dao.confirmar(listOf("borde"), "2026-09-16T00:00:00Z")
        dao.deleteUploadedVisits(
            "2026-09-16T00:00:00Z",
            "2026-07-18T00:00:00Z",
            "2026-10-01T00:00:00Z",
            "2026-07-18",
            emptyList()
        )
        assertNotNull("`<`, no `<=`", dao.findVisitById("borde"))
    }

    @Test fun `resguardada se poda solo tras 90 dias`() = runTest {
        dao.insertVisit(visita("r"))
        dao.resguardar("r", "2026-08-01T00:00:00Z")
        dao.deleteUploadedVisits(
            "2026-09-16T00:00:00Z",
            "2026-07-18T00:00:00Z",
            "2026-10-01T00:00:00Z",
            "2026-07-18",
            emptyList()
        )
        assertNotNull(dao.findVisitById("r"))
        assertEquals(1, dao.resguardadasPorPodar("2026-10-31T00:00:00Z"))
    }

    @Test fun `resguardada vieja si se poda y deja de contarse`() = runTest {
        dao.insertVisit(visita("r"))
        dao.resguardar("r", "2026-07-01T00:00:00Z")
        assertEquals(1, dao.resguardadasPorPodar("2026-07-18T00:00:00Z"))
        dao.deleteUploadedVisits(
            "2026-09-16T00:00:00Z",
            "2026-07-18T00:00:00Z",
            "2026-10-01T00:00:00Z",
            "2026-07-18",
            emptyList()
        )
        assertNull(dao.findVisitById("r"))
        assertEquals(0, dao.resguardadasPorPodar("2026-07-18T00:00:00Z"))
    }

    @Test fun `una resguardada nunca se poda por el corte de confirmadas`() = runTest {
        dao.insertVisit(visita("r"))
        dao.resguardar("r", "2026-08-01T00:00:00Z")
        // Corte de confirmadas en el futuro, corte de resguardadas antes de la marca.
        dao.deleteUploadedVisits(
            "2099-01-01T00:00:00Z",
            "2026-07-18T00:00:00Z",
            "2026-10-01T00:00:00Z",
            "2026-07-18",
            emptyList()
        )
        assertNotNull(dao.findVisitById("r"))
    }

    @Test fun `resguardadasPorPodar no cuenta confirmadas, pendientes ni legadas`() = runTest {
        dao.insertVisit(visita("c"))
        dao.confirmar(listOf("c"), "2026-01-01T00:00:00Z")
        dao.insertVisit(visita("p"))
        dao.insertVisit(visita("legada", guardado = 1))
        dao.insertVisit(visita("r"))
        dao.resguardar("r", "2026-01-01T00:00:00Z")
        assertEquals(1, dao.resguardadasPorPodar("2099-01-01T00:00:00Z"))
    }

    @Test fun `una confirmada vieja con promesa vigente sobrevive`() = runTest {
        dao.insertVisit(visita("promesa").copy(PROMESA_FECHA = "2026-10-20"))
        dao.confirmar(listOf("promesa"), "2026-01-01T00:00:00Z")
        dao.deleteUploadedVisits(
            "2026-09-16T00:00:00Z",
            "2026-07-18T00:00:00Z",
            "2026-10-01T00:00:00Z",
            "2026-07-18",
            emptyList()
        )
        assertNotNull(dao.findVisitById("promesa"))
    }
}
