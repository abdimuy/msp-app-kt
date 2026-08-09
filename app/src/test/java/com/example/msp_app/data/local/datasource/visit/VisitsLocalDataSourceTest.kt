package com.example.msp_app.data.local.datasource.visit

import androidx.test.core.app.ApplicationProvider
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

    @Before
    fun setUpStore() {
        store = VisitsLocalDataSource(db.visitDao(), db.saleDao())
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
