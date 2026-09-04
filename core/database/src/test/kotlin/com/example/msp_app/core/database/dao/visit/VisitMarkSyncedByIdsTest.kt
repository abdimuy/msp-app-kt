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

    // ─── Ruling AR: un comprobante sin entregar retiene la visita ────────────

    /**
     * **La constraint literal, en el camino donde la letra es correcta.**
     *
     * `by-ids` confirma que el servidor tiene la visita, pero la foto pendiente
     * todavía no llegó. Marcarla la sacaría del conjunto pendiente para siempre
     * y tiraría una foto que el servidor **sí iba a aceptar**: al reintentar, la
     * ruta resuelve la colisión con `FindByID`, adjunta la imagen nueva y
     * contesta 201.
     */
    @Test
    fun `una visita con comprobante sin subir no se marca`() = runTest {
        val dao = database.visitDao()
        dao.insertVisit(visit("v-con-foto"))
        dao.insertVisit(visit("v-sin-foto"))
        sembrarImagen(id = "IMG-1", visitaId = "v-con-foto")

        val changed = dao.markSyncedByIds(listOf("v-con-foto", "v-sin-foto"))

        assertEquals("solo se marco la que no debe nada", 1, changed)
        assertEquals(0, dao.getVisitById("v-con-foto").GUARDADO_EN_MICROSIP)
        // Control positivo, en la MISMA llamada: sin la de al lado, un UPDATE
        // que no marcara nada pasaría igual.
        assertEquals(1, dao.getVisitById("v-sin-foto").GUARDADO_EN_MICROSIP)
    }

    /** Un comprobante **ya subido** no retiene: `SUBIDA_EN` es lo que distingue. */
    @Test
    fun `un comprobante ya subido no retiene la visita`() = runTest {
        val dao = database.visitDao()
        dao.insertVisit(visit("v-entregada"))
        sembrarImagen(id = "IMG-1", visitaId = "v-entregada", subidaEn = "2026-09-04T18:00:00Z")

        assertEquals(1, dao.markSyncedByIds(listOf("v-entregada")))
        assertEquals(1, dao.getVisitById("v-entregada").GUARDADO_EN_MICROSIP)
    }

    /**
     * El comprobante pendiente de OTRA visita no retiene a la de al lado. Con
     * una sola visita en la tabla, un `NOT IN` sin correlación por `VISITA_ID`
     * pasaría igual.
     */
    @Test
    fun `el comprobante pendiente de otra visita no retiene a la de al lado`() = runTest {
        val dao = database.visitDao()
        dao.insertVisit(visit("v-1"))
        dao.insertVisit(visit("v-2"))
        sembrarImagen(id = "IMG-1", visitaId = "v-2")

        assertEquals(1, dao.markSyncedByIds(listOf("v-1")))
        assertEquals(1, dao.getVisitById("v-1").GUARDADO_EN_MICROSIP)
    }

    private suspend fun sembrarImagen(id: String, visitaId: String, subidaEn: String? = null) =
        database.visitImageDao().insertAll(
            listOf(
                com.example.msp_app.core.database.entities.VisitImageEntity(
                    ID = id,
                    VISITA_ID = visitaId,
                    URI = "/files/comprobante_visita_$id.jpg",
                    MIME = "image/jpeg",
                    ORDEN = 0,
                    CREADA_EN = "2026-09-04T17:00:00Z",
                    SUBIDA_EN = subidaEn
                )
            )
        )
}
