package com.example.msp_app.data.local.dao.visit

import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.`test-fixtures`.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the data-loss bug closed by [VisitDao.deleteUploadedVisits].
 *
 * `SalesViewModel.syncSales()` used to call [VisitDao.deleteAllVisits] as part
 * of the periodic sync sweep, which wiped every locally-stored visita —
 * including ones still pending upload (`GUARDADO_EN_MICROSIP = 0`) that had
 * not yet been confirmed by the server. A cobrador who registered a visita
 * offline and had not yet gotten connectivity to upload it would silently
 * lose that visita on the next sync sweep.
 *
 * This test runs against a real, in-memory Room [com.example.msp_app.core.database.AppDatabase]
 * and the REAL [VisitDao] (no fakes) — it proves the fix directly at the SQL
 * level: [VisitDao.deleteUploadedVisits] deletes only confirmed rows and
 * leaves pending rows untouched.
 */
class VisitDaoDeleteUploadedRegressionTest : RoomTestBase() {

    private val dao get() = db.visitDao()

    private fun visit(id: String, guardado: Int) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Ramirez Ortiz, Fernando",
        COBRADOR_ID = 200,
        FECHA = "2026-06-01T09:30:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        NOTA = "Visita de prueba",
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21552,
        IMPTE_DOCTO_CC_ID = 5000,
        GUARDADO_EN_MICROSIP = guardado
    )

    @Test
    fun `deleteUploadedVisits removes only confirmed rows and preserves pending ones`() = runTest {
        // Mixed cohort: two visitas already confirmed by the server, two that
        // are still awaiting upload (the exact scenario the old
        // deleteAllVisits() call in SalesViewModel.syncSales() used to destroy).
        dao.insertVisit(visit(id = "visita-uploaded-1", guardado = 1))
        dao.insertVisit(visit(id = "visita-uploaded-2", guardado = 1))
        dao.insertVisit(visit(id = "visita-pending-1", guardado = 0))
        dao.insertVisit(visit(id = "visita-pending-2", guardado = 0))

        dao.deleteUploadedVisits()

        // The two confirmed visitas are gone.
        assertNoLongerExists(dao, "visita-uploaded-1")
        assertNoLongerExists(dao, "visita-uploaded-2")

        // The two pending visitas MUST survive — this is the data-loss fix.
        val pending1 = dao.getVisitById("visita-pending-1")
        val pending2 = dao.getVisitById("visita-pending-2")
        assertNotNull("pending visita must survive deleteUploadedVisits", pending1)
        assertNotNull("pending visita must survive deleteUploadedVisits", pending2)
        assertEquals(0, pending1.GUARDADO_EN_MICROSIP)
        assertEquals(0, pending2.GUARDADO_EN_MICROSIP)

        assertEquals(2, dao.getPendingVisits().size)
    }

    @Test
    fun `contrast - the old deleteAllVisits would have destroyed the pending rows too`() = runTest {
        // Documents the bug this fix closes: deleteAllVisits() (still exposed
        // for other legitimate uses) has no WHERE clause and wipes pending
        // visitas along with confirmed ones. This is why syncSales() was
        // switched to call deleteUploadedVisits() instead.
        dao.insertVisit(visit(id = "visita-uploaded-1", guardado = 1))
        dao.insertVisit(visit(id = "visita-pending-1", guardado = 0))

        dao.deleteAllVisits()

        assertNoLongerExists(dao, "visita-uploaded-1")
        assertNoLongerExists(dao, "visita-pending-1")
        assertTrue(
            "deleteAllVisits leaves nothing behind, pending or not",
            dao.getPendingVisits().isEmpty()
        )
    }

    private suspend fun assertNoLongerExists(dao: VisitDao, id: String) {
        val result = runCatching { dao.getVisitById(id) }.getOrNull()
        assertEquals(
            "row for $id must be gone after deletion",
            null,
            result
        )
    }
}
