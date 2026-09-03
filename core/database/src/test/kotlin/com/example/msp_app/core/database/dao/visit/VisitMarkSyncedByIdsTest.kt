package com.example.msp_app.core.database.dao.visit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Task 10 — the single write the visitas reconciler is allowed to make.
 *
 * `GUARDADO_EN_MICROSIP = 1` is the local proof that the server holds a visita.
 * These cases pin the two properties that keep it honest: it flips **only** the
 * ids handed to it, and it flips nothing when handed nothing.
 */
class VisitMarkSyncedByIdsTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun visit(id: String) = VisitEntity(
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
        GUARDADO_EN_MICROSIP = 0
    )

    @Test
    fun `marca solo los ids dados y deja pendientes los demas`() = runTest {
        val dao = database.visitDao()
        listOf("v-1", "v-2", "v-3").forEach { dao.insertVisit(visit(it)) }

        val changed = dao.markSyncedByIds(listOf("v-1", "v-3"))

        assertEquals(2, changed)
        assertEquals(listOf("v-2"), dao.getPendingVisits().map { it.ID })
        assertEquals(1, dao.getVisitById("v-1").GUARDADO_EN_MICROSIP)
        assertEquals(0, dao.getVisitById("v-2").GUARDADO_EN_MICROSIP)
        assertEquals(1, dao.getVisitById("v-3").GUARDADO_EN_MICROSIP)
    }

    @Test
    fun `una lista vacia no toca NINGUNA fila`() = runTest {
        val dao = database.visitDao()
        listOf("v-1", "v-2").forEach { dao.insertVisit(visit(it)) }

        val changed = dao.markSyncedByIds(emptyList())

        assertEquals(0, changed)
        assertEquals(listOf("v-1", "v-2"), dao.getPendingVisits().map { it.ID }.sorted())
    }

    @Test
    fun `un id que no existe localmente no crea nada ni afecta a los demas`() = runTest {
        val dao = database.visitDao()
        dao.insertVisit(visit("v-1"))

        val changed = dao.markSyncedByIds(listOf("v-1", "id-fantasma"))

        assertEquals(1, changed)
        assertEquals(emptyList<String>(), dao.getPendingVisits().map { it.ID })
    }

    @Test
    fun `marcar dos veces es idempotente`() = runTest {
        val dao = database.visitDao()
        dao.insertVisit(visit("v-1"))

        dao.markSyncedByIds(listOf("v-1"))
        val secondChanged = dao.markSyncedByIds(listOf("v-1"))

        // Room cuenta las filas que la sentencia toco, no las que cambiaron de
        // valor: repetir el UPDATE sigue devolviendo 1 y sigue dejando la fila
        // en 1. Lo que importa es que no vuelve a 0 ni duplica nada.
        assertEquals(1, secondChanged)
        assertEquals(1, dao.getVisitById("v-1").GUARDADO_EN_MICROSIP)
        assertEquals(emptyList<String>(), dao.getPendingVisits().map { it.ID })
    }
}
