package com.example.msp_app.data.local.datasource.visit

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [VisitsLocalDataSource] construido por el **constructor
 * de DAOs inyectados** (la forma Hilt) con DAOs de la DB in-memory de
 * [RoomTestBase]. Cubre las visitas (con y sin pago) y la transaccion que
 * cambia el estado de cobranza de la venta, y prueba que la forma inyectada es
 * EQUIVALENTE al puente `context` que usan `PendingVisitsWorkerV2Test` y los
 * ViewModels no-Hilt (ambos resuelven a la misma DB via
 * [com.example.msp_app.core.database.AppDatabase.getInstance]).
 */
class VisitsLocalDataSourceTest : RoomTestBase() {

    private lateinit var store: VisitsLocalDataSource
    private lateinit var enqueuer: RecordingVisitsWorkEnqueuer

    @Before
    fun setUpStore() {
        enqueuer = RecordingVisitsWorkEnqueuer()
        store = VisitsLocalDataSource(db.visitDao(), db.saleDao(), enqueuer)
    }

    /** Fake a mano (sin MockK): estado publico + lista publica de llamadas. */
    private class RecordingVisitsWorkEnqueuer : VisitsWorkEnqueuer {
        data class Call(val visitId: String, val replace: Boolean)

        val calls: MutableList<Call> = mutableListOf()

        override fun enqueue(visitId: String, replace: Boolean) {
            calls += Call(visitId, replace)
        }
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private fun visit(
        id: String,
        guardado: Int = 0,
        fecha: String = "2026-06-02T15:00:00Z",
        saleCargoId: Int = 5000,
        lat: Double = 0.0,
        lng: Double = 0.0
    ) = VisitEntity(
        ID = id,
        CLIENTE_ID = 4821,
        COBRADOR = "Ramirez Ortiz, Fernando",
        COBRADOR_ID = 7,
        FECHA = fecha,
        FORMA_COBRO_ID = 0,
        LAT = lat,
        LNG = lng,
        NOTA = "El cliente pidio pasar la proxima semana",
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21,
        IMPTE_DOCTO_CC_ID = saleCargoId,
        GUARDADO_EN_MICROSIP = guardado
    )

    private fun sale(saleId: Int, saldoRest: Double = 1000.0, estado: String = "PENDIENTE") =
        SaleEntity(
            DOCTO_CC_ACR_ID = saleId,
            DOCTO_CC_ID = saleId + 1,
            FOLIO = "A-$saleId",
            CLIENTE_ID = 4821,
            APLICADO = "S",
            COBRADOR_ID = 7,
            CLIENTE = "Guadalupe Hernandez Soto",
            ZONA_CLIENTE_ID = 21,
            LIMITE_CREDITO = 0.0,
            NOTAS = "",
            ZONA_NOMBRE = "Centro",
            IMPORTE_PAGO_PROMEDIO = 350.0,
            TOTAL_IMPORTE = 3500.0,
            NUM_IMPORTES = 10,
            FECHA = "2026-01-01T00:00:00Z",
            PARCIALIDAD = 350,
            ENGANCHE = 500.0,
            TIEMPO_A_CORTO_PLAZOMESES = 0,
            MONTO_A_CORTO_PLAZO = 0.0,
            VENDEDOR_1 = "",
            VENDEDOR_2 = "",
            VENDEDOR_3 = "",
            PRECIO_TOTAL = 3500.0,
            IMPTE_REST = saldoRest,
            SALDO_REST = saldoRest,
            FECHA_ULT_PAGO = null,
            CALLE = "Av. Reforma 100",
            CIUDAD = "Tehuacan",
            ESTADO = "Puebla",
            TELEFONO = "2381234567",
            NOMBRE_COBRADOR = "Ramirez Ortiz, Fernando",
            ESTADO_COBRANZA = estado,
            DIA_COBRANZA = "LUNES",
            DIA_TEMPORAL_COBRANZA = "",
            PRECIO_DE_CONTADO = 3000.0,
            AVAL_O_RESPONSABLE = "",
            FREC_PAGO = "SEMANAL"
        )

    // ─── saveVisit / getPendingVisits ────────────────────────────────────────

    @Test
    fun saveVisit_roundTrips() = runTest {
        val v = visit(id = "v-1")
        store.saveVisit(v)
        assertEquals(v, store.getVisitById("v-1"))
    }

    @Test
    fun getPendingVisits_returnsOnlyUnsynced() = runTest {
        store.saveVisit(visit(id = "pend-1", guardado = 0))
        store.saveVisit(visit(id = "sync-1", guardado = 1))
        store.saveVisit(visit(id = "pend-2", guardado = 0))

        assertEquals(
            listOf("pend-1", "pend-2").sorted(),
            store.getPendingVisits().map { it.ID }.sorted()
        )
    }

    @Test
    fun getPendingVisits_emptyWhenAllSynced() = runTest {
        store.saveVisit(visit(id = "sync-1", guardado = 1))
        assertTrue(store.getPendingVisits().isEmpty())
    }

    @Test
    fun getPendingVisits_emptyWhenNone() = runTest {
        assertTrue(store.getPendingVisits().isEmpty())
    }

    // ─── getVisitsByDate: ventana media-abierta + orden DESC ──────────────────

    @Test
    fun getVisitsByDate_windowHalfOpenOrderedDesc() = runTest {
        store.saveVisit(visit(id = "antes", fecha = "2026-05-31T15:00:00Z"))
        store.saveVisit(visit(id = "a", fecha = "2026-06-01T15:00:00Z"))
        store.saveVisit(visit(id = "b", fecha = "2026-06-03T15:00:00Z"))
        store.saveVisit(visit(id = "en-end", fecha = "2026-06-05T00:00:00Z"))

        val result = store.getVisitsByDate("2026-06-01T00:00:00Z", "2026-06-05T00:00:00Z")

        assertEquals(
            "solo dentro de [start, end); end exclusivo; orden FECHA DESC",
            listOf("b", "a"),
            result.map { it.ID }
        )
    }

    // ─── updateVisitState / changeVisitStatus / updateVisitLocation ───────────

    @Test
    fun updateVisitState_setsGuardadoFlag() = runTest {
        store.saveVisit(visit(id = "us-1", guardado = 0))

        store.updateVisitState("us-1", 1)
        assertEquals(1, store.getVisitById("us-1").GUARDADO_EN_MICROSIP)
    }

    @Test
    fun changeVisitStatus_mapsBooleanToFlag() = runTest {
        store.saveVisit(visit(id = "cs-1", guardado = 0))

        store.changeVisitStatus("cs-1", true)
        assertEquals(1, store.getVisitById("cs-1").GUARDADO_EN_MICROSIP)

        store.changeVisitStatus("cs-1", false)
        assertEquals(0, store.getVisitById("cs-1").GUARDADO_EN_MICROSIP)
    }

    @Test
    fun updateVisitLocation_persistsCoords() = runTest {
        store.saveVisit(visit(id = "loc-1", lat = 0.0, lng = 0.0))

        store.updateVisitLocation("loc-1", 18.4501, -97.3902)

        val got = store.getVisitById("loc-1")
        assertEquals(18.4501, got.LAT, 1e-9)
        assertEquals(-97.3902, got.LNG, 1e-9)
    }

    // ─── Task 5, robustez suprema: la ubicacion tardia nunca duplica la subida ─
    // updateVisitLocation solo toca LAT/LNG (VisitDao.updateLocation) y desde
    // Task 5 UpdateLocationHandler ya no encola nada en la rama de visita: es
    // estructuralmente imposible que esta actualizacion tardia encole. Estos
    // dos tests cubren los dos estados en los que puede encontrar a la visita.

    @Test
    fun updateVisitLocation_llegaTardeSobreVisitaYaSubida_noLaPisaNiReencola() = runTest {
        store.saveVisit(visit(id = "up-1", guardado = 1, lat = 0.0, lng = 0.0))

        store.updateVisitLocation("up-1", 18.99, -97.11)

        val got = store.getVisitById("up-1")
        assertEquals(18.99, got.LAT, 1e-9)
        assertEquals(-97.11, got.LNG, 1e-9)
        assertEquals(
            "sigue marcada como subida; la ubicacion tardia no pisa GUARDADO_EN_MICROSIP",
            1,
            got.GUARDADO_EN_MICROSIP
        )
        assertTrue(
            "updateVisitLocation no encola nada: no puede crear una segunda subida",
            enqueuer.calls.isEmpty()
        )
    }

    @Test
    fun updateVisitLocation_llegaTardeSobreVisitaPendiente_noEncola() = runTest {
        store.saveVisit(visit(id = "pend-loc-1", guardado = 0, lat = 0.0, lng = 0.0))

        store.updateVisitLocation("pend-loc-1", 18.99, -97.11)

        assertEquals(0, store.getVisitById("pend-loc-1").GUARDADO_EN_MICROSIP)
        assertTrue(
            "el encolado ya paso en el guardado inicial; la ubicacion tardia no vuelve a encolar",
            enqueuer.calls.isEmpty()
        )
    }

    // ─── insertVisitAndUpdateState: visita + estado de cobranza de la venta ───

    @Test
    fun insertVisitAndUpdateState_insertsVisitAndSetsEstadoWithoutTouchingSaldo() = runTest {
        db.saleDao().insertAll(
            listOf(sale(saleId = 5000, saldoRest = 1000.0, estado = "PENDIENTE"))
        )

        store.insertVisitAndUpdateState(
            saleId = 5000,
            visit = visit(id = "iv-1", saleCargoId = 5000),
            newState = EstadoCobranza.VISITADO
        )

        assertEquals("iv-1", store.getVisitById("iv-1").ID)
        val updated = db.saleDao().findByDoctoCcId(5001)!!
        assertEquals(
            "una visita NO abona: SALDO_REST intacto (updateTotal con 0.0)",
            1000.0,
            updated.SALDO_REST,
            1e-9
        )
        assertEquals("VISITADO", updated.ESTADO_COBRANZA)
    }

    // ─── Task 5: saveVisitAndEnqueue — el guardado encola la subida el mismo ──
    // ─── (headline test, task-5-brief.md) ──────────────────────────────────────

    /**
     * Headline test del plan (task-5-brief.md): "guardar una visita con el
     * servicio de ubicacion completamente ausente y comprobar que igual
     * queda encolada para subir". Ni [com.example.msp_app.services.UpdateLocationService]
     * ni [com.example.msp_app.services.UpdateLocationHandler] se referencian
     * en ningun punto de este test — el servicio de ubicacion no solo no
     * corre, no existe en este camino. Esto tambien cubre el caso de
     * robustez suprema "la ubicacion nunca llega": la visita queda subible
     * sin depender de que updateVisitLocation se llame jamas.
     *
     * **Control de reversion (verificado, ver task-5-report.md):** comentar
     * la linea `enqueuer.enqueue(...)` en
     * [VisitsLocalDataSource.saveVisitAndEnqueue] pone este test en ROJO —
     * `enqueuer.calls` queda vacio.
     */
    @Test
    fun saveVisitAndEnqueue_enqueuesUploadEvenWithoutLocationService() = runTest {
        db.saleDao().insertAll(
            listOf(sale(saleId = 5000, saldoRest = 1000.0, estado = "PENDIENTE"))
        )

        store.saveVisitAndEnqueue(
            saleId = 5000,
            visit = visit(id = "no-loc-1", saleCargoId = 5000),
            newState = EstadoCobranza.VISITADO
        )

        assertEquals(
            "la visita se escribio localmente",
            "no-loc-1",
            store.getVisitById("no-loc-1").ID
        )
        assertEquals(
            "el guardado local encola por si mismo, sin esperar la ubicacion",
            listOf(RecordingVisitsWorkEnqueuer.Call("no-loc-1", replace = false)),
            enqueuer.calls
        )
        assertEquals(
            "todavia pendiente de subir (nadie confirmo el servidor)",
            0,
            store.getVisitById("no-loc-1").GUARDADO_EN_MICROSIP
        )
    }

    @Test
    fun saveVisitAndEnqueue_setsEstadoDeCobranzaLikeInsertVisitAndUpdateState() = runTest {
        db.saleDao().insertAll(
            listOf(sale(saleId = 6000, saldoRest = 500.0, estado = "PENDIENTE"))
        )

        store.saveVisitAndEnqueue(
            saleId = 6000,
            visit = visit(id = "sve-1", saleCargoId = 6000),
            newState = EstadoCobranza.VISITADO
        )

        val updated = db.saleDao().findByDoctoCcId(6001)!!
        assertEquals(
            "conserva el comportamiento de insertVisitAndUpdateState: no abona",
            500.0,
            updated.SALDO_REST,
            1e-9
        )
        assertEquals("VISITADO", updated.ESTADO_COBRANZA)
    }

    // ─── updateTemporaryCollectionDate ────────────────────────────────────────

    @Test
    fun updateTemporaryCollectionDate_persistsOnSale() = runTest {
        db.saleDao().insertAll(listOf(sale(saleId = 5000)))

        store.updateTemporaryCollectionDate(5000, "2026-06-10")

        assertEquals("2026-06-10", db.saleDao().findByDoctoCcId(5001)!!.DIA_TEMPORAL_COBRANZA)
    }

    // ─── deleteAllVisits / deleteUploadedVisits ───────────────────────────────

    @Test
    fun deleteUploadedVisits_preservesPending() = runTest {
        store.saveVisit(visit(id = "pend-1", guardado = 0))
        store.saveVisit(visit(id = "sync-1", guardado = 1))

        store.deleteUploadedVisits()

        assertEquals(
            "solo se borran las confirmadas por el servidor; la pendiente sobrevive",
            listOf("pend-1"),
            store.getPendingVisits().map { it.ID }
        )
        // La confirmada ya no esta.
        assertTrue(
            store.getVisitsByDate("2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z").none {
                it.ID == "sync-1"
            }
        )
    }

    @Test
    fun deleteAllVisits_removesEverything() = runTest {
        store.saveVisit(visit(id = "pend-1", guardado = 0))
        store.saveVisit(visit(id = "sync-1", guardado = 1))

        store.deleteAllVisits()

        assertTrue(store.getPendingVisits().isEmpty())
        assertTrue(store.getVisitsByDate("2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z").isEmpty())
    }

    // ─── equivalencia inyectado ⇔ puente context ──────────────────────────────

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        store.saveVisit(visit(id = "eq-pend", guardado = 0))
        store.saveVisit(visit(id = "eq-sync", guardado = 1))

        val contextForm = VisitsLocalDataSource(ApplicationProvider.getApplicationContext())

        assertEquals(
            "ambos constructores resuelven a la misma DB in-memory",
            store.getPendingVisits().map { it.ID },
            contextForm.getPendingVisits().map { it.ID }
        )
        assertEquals(store.getVisitById("eq-sync"), contextForm.getVisitById("eq-sync"))
    }
}
