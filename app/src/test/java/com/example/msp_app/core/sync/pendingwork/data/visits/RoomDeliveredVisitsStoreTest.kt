package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.workers.pendingVisit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El lado local de la reconciliación inversa (plan visitas P1, Tarea 13)
 * contra Room real: qué se lee como soltado y qué cambia cada decisión.
 */
class RoomDeliveredVisitsStoreTest : RoomTestBase() {

    private fun store() = RoomDeliveredVisitsStore(db.visitDao())

    @Test
    fun `lee la legada sin confirmar y la confirmada reciente, no la vieja ni la resguardada ni la pendiente`() =
        runTest {
            val dao = db.visitDao()
            dao.insertVisit(pendingVisit(id = "legada", guardado = 1))
            dao.insertVisit(pendingVisit(id = "reciente"))
            dao.confirmar(listOf("reciente"), "2026-10-10T00:00:00Z")
            dao.insertVisit(pendingVisit(id = "vieja"))
            dao.confirmar(listOf("vieja"), "2026-08-01T00:00:00Z")
            dao.insertVisit(pendingVisit(id = "resguardada"))
            dao.resguardar("resguardada", "2026-10-10T00:00:00Z")
            dao.insertVisit(pendingVisit(id = "pendiente"))

            assertEquals(
                setOf("legada", "reciente"),
                store().entregadasDesde("2026-09-17T15:00:00Z").toSet()
            )
        }

    @Test
    fun `confirmar estampa CONFIRMADA_EN`() = runTest {
        db.visitDao().insertVisit(pendingVisit(id = "legada", guardado = 1))

        assertEquals(1, store().confirmar(listOf("legada"), "2026-10-17T15:00:00Z"))

        val v = db.visitDao().findVisitById("legada")!!
        assertEquals("2026-10-17T15:00:00Z", v.CONFIRMADA_EN)
        assertEquals(1, v.GUARDADO_EN_MICROSIP)
    }

    @Test
    fun `devolver a pendiente deja la visita en la cola y sin marcas`() = runTest {
        db.visitDao().insertVisit(pendingVisit(id = "c"))
        db.visitDao().confirmar(listOf("c"), "2026-10-10T00:00:00Z")

        assertEquals(1, store().devolverAPendiente(listOf("c")))

        val v = db.visitDao().findVisitById("c")!!
        assertEquals(0, v.GUARDADO_EN_MICROSIP)
        assertNull(v.CONFIRMADA_EN)
        assertEquals(listOf("c"), db.visitDao().pendientesOrdenadas())
    }

    @Test
    fun `un lote mayor al tope de parametros de SQLite se trocea`() = runTest {
        val muchas = List(1500) { "v-$it" }
        muchas.forEach { db.visitDao().insertVisit(pendingVisit(id = it, guardado = 1)) }

        assertEquals(1500, store().confirmar(muchas, "2026-10-17T15:00:00Z"))
        assertEquals(1500, store().devolverAPendiente(muchas))
        assertNotNull(db.visitDao().findVisitById("v-1499"))
        assertEquals(1500, db.visitDao().pendientesOrdenadas().size)
    }
}
