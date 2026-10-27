package com.example.msp_app.core.sync.pendingwork.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.workers.VisitsReconcileWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * E-APP-072 (plan visitas P1, Tarea 12): las dos lecturas de producción que
 * alimentan el reencolado —el barrido de sesión
 * ([PendingWorkSyncFactory.visitasParaElBarrido]) y el reconciliador
 * ([VisitsReconcileWorker.pendientesPorOmision])— devuelven TODAS las
 * pendientes en `FECHA ASC, ID ASC`. Se ponen rojas con un `take(50)` o con
 * una lectura sin orden (`getPendingVisits()`).
 */
class VisitasParaElBarridoTest : RoomTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var sembradas: List<VisitEntity>
    private lateinit var esperado: List<String>

    @Before
    fun sembrar() = runBlocking {
        // 60 pendientes insertadas con FECHA descendente y FECHA repetida cada
        // 28: el orden de inserción NO es el esperado y el desempate es por ID.
        sembradas = (1..60).map { i ->
            visita("v%02d".format(i), fecha = "2026-10-%02dT10:00:00Z".format(29 - (i % 28)))
        }
        sembradas.forEach { db.visitDao().insertVisit(it) }
        db.visitDao().insertVisit(
            visita("v00-soltada", fecha = "2026-01-01T00:00:00Z", guardado = 1)
        )
        esperado = sembradas.sortedWith(compareBy({ it.FECHA }, { it.ID })).map { it.ID }
        // Control positivo: el orden esperado no coincide con el de inserción
        // ni con el que da hoy la lectura sin ORDER BY.
        assertNotEquals(sembradas.map { it.ID }, esperado)
        assertNotEquals(db.visitDao().getPendingVisits().map { it.ID }, esperado)
    }

    @Test
    fun `E-APP-072 el barrido de sesion lee las 60 pendientes en orden FECHA ID`() = runBlocking {
        val ids = PendingWorkSyncFactory.visitasParaElBarrido(VisitsLocalDataSource(context))
        assertEquals(60, ids.size)
        assertEquals(esperado, ids)
    }

    @Test
    fun `E-APP-072 el reconciliador lee por omision las 60 pendientes en orden FECHA ID`() =
        runBlocking {
            val ids = VisitsReconcileWorker.pendientesPorOmision(context)
            assertEquals(60, ids.size)
            assertEquals(esperado, ids)
        }

    private fun visita(id: String, fecha: String, guardado: Int = 0) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Ramírez Ortiz, Fernando",
        COBRADOR_ID = 200,
        FECHA = fecha,
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        NOTA = null,
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21552,
        IMPTE_DOCTO_CC_ID = 5000,
        GUARDADO_EN_MICROSIP = guardado
    )
}
