package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The local half of the reconciler against real Room.
 *
 * `GUARDADO_EN_MICROSIP` is the only local marker of "the server has it", so
 * the property worth pinning is narrow and absolute: this adapter flips exactly
 * the ids it is given, and a visita it was not given comes back as still
 * pending.
 */
class RoomPendingVisitsStoreTest : RoomTestBase() {

    private fun store() = RoomPendingVisitsStore(db.visitDao())

    private fun visit(id: String, guardado: Int = 0) = VisitEntity(
        ID = id,
        CLIENTE_ID = 30144,
        COBRADOR = "Efrain Dominguez Reyes",
        COBRADOR_ID = 7,
        FECHA = "2026-09-01T18:30:00Z",
        FORMA_COBRO_ID = 157,
        LAT = 19.043415,
        LNG = -98.198234,
        NOTA = null,
        TIPO_VISITA = "COBRO",
        ZONA_CLIENTE_ID = 21,
        IMPTE_DOCTO_CC_ID = 0,
        GUARDADO_EN_MICROSIP = guardado
    )

    @Test
    fun `pendingVisitIds devuelve solo las que el servidor no confirmo`() = runTest {
        db.visitDao().insertVisit(visit("v-1"))
        db.visitDao().insertVisit(visit("v-2", guardado = 1))
        db.visitDao().insertVisit(visit("v-3"))

        assertEquals(listOf("v-1", "v-3"), store().pendingVisitIds().sorted())
    }

    @Test
    fun `sin visitas pendientes devuelve lista vacia`() = runTest {
        db.visitDao().insertVisit(visit("v-1", guardado = 1))

        assertEquals(emptyList<String>(), store().pendingVisitIds())
    }

    @Test
    fun `markSynced marca solo lo confirmado y el resto sigue pendiente`() = runTest {
        listOf("v-1", "v-2", "v-3").forEach { db.visitDao().insertVisit(visit(it)) }

        store().markSynced(listOf("v-2"))

        assertEquals(listOf("v-1", "v-3"), store().pendingVisitIds().sorted())
        assertEquals(1, db.visitDao().getVisitById("v-2").GUARDADO_EN_MICROSIP)
    }

    @Test
    fun `markSynced con lista vacia no marca nada`() = runTest {
        listOf("v-1", "v-2").forEach { db.visitDao().insertVisit(visit(it)) }

        store().markSynced(emptyList())

        assertEquals(listOf("v-1", "v-2"), store().pendingVisitIds().sorted())
    }

    @Test
    fun `un lote mayor al tope de parametros de SQLite se trocea y marca todo`() = runTest {
        // 1500 > SQLITE_MAX_VARIABLE_NUMBER (999): sin trocear, SQLite lanza
        // "too many SQL variables" — el error silencioso que CLAUDE.md registra.
        val many = List(1500) { "v-$it" }
        many.forEach { db.visitDao().insertVisit(visit(it)) }

        store().markSynced(many)

        assertEquals(emptyList<String>(), store().pendingVisitIds())
    }
}
