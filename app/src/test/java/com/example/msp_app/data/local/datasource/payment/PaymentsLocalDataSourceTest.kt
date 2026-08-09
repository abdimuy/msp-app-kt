package com.example.msp_app.data.local.datasource.payment

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva del camino de DINERO en [PaymentsLocalDataSource].
 *
 * Construye el datasource por el **constructor de DAOs inyectados** (la forma
 * que un `@HiltViewModel`/`@HiltWorker` futuro recibirá) con DAOs de una DB
 * in-memory de [RoomTestBase], y afirma valores reales sobre las operaciones
 * de captura y consulta. Prueba además que la forma inyectable es EQUIVALENTE
 * a la forma-`context` (el puente legacy que usan los workers V2 y los
 * ViewModels no-Hilt): ambas resuelven a la MISMA instancia de DB vía
 * [com.example.msp_app.core.database.AppDatabase.getInstance], porque
 * [RoomTestBase] instala la DB in-memory con `setInstanceForTesting`.
 *
 * Los tests que construyen por `context` (`PendingPaymentsWorkerV2Test`,
 * `PaymentDaoCollapseTest`, e2e) prueban ese puente y permanecen verdes sin
 * cambios: esta suite añade la cobertura por el constructor inyectado sin
 * tocar el comportamiento.
 */
class PaymentsLocalDataSourceTest : RoomTestBase() {

    private lateinit var store: PaymentsLocalDataSource

    @Before
    fun setUpStore() {
        // Forma INYECTADA (DAOs) — la que Hilt entregará.
        store = PaymentsLocalDataSource(db.paymentDao(), db.saleDao())
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private fun payment(
        id: String,
        guardado: Boolean = false,
        saleId: Int = 5000,
        fecha: String = "2026-06-01T15:00:00Z",
        importe: Double = 350.0,
        formaCobro: Int = 157,
        lat: Double? = null,
        lng: Double? = null
    ) = PaymentEntity(
        ID = id,
        COBRADOR = "Rosa Elena Martinez Vazquez",
        DOCTO_CC_ACR_ID = saleId,
        DOCTO_CC_ID = saleId + 1,
        FECHA_HORA_PAGO = fecha,
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = importe,
        LAT = lat,
        LNG = lng,
        CLIENTE_ID = 4821,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = formaCobro,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Guadalupe Hernandez Soto"
    )

    private fun sale(
        saleId: Int = 5000,
        saldoRest: Double = 1000.0,
        estadoCobranza: String = "PENDIENTE"
    ) = SaleEntity(
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
        NOMBRE_COBRADOR = "Rosa Elena Martinez Vazquez",
        ESTADO_COBRANZA = estadoCobranza,
        DIA_COBRANZA = "LUNES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 3000.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = "SEMANAL"
    )

    // ─── getPendingPayments: GUARDADO_EN_MICROSIP true/false ──────────────────

    @Test
    fun getPendingPayments_returnsOnlyUnsynced() = runTest {
        store.savePayment(payment(id = "pend-1", guardado = false))
        store.savePayment(payment(id = "sync-1", guardado = true))
        store.savePayment(payment(id = "pend-2", guardado = false))

        val pending = store.getPendingPayments()

        assertEquals(
            "solo los pagos con GUARDADO_EN_MICROSIP=false son pendientes",
            listOf("pend-1", "pend-2").sorted(),
            pending.map { it.ID }.sorted()
        )
        assertTrue(pending.all { !it.GUARDADO_EN_MICROSIP })
    }

    @Test
    fun getPendingPayments_emptyWhenAllSynced() = runTest {
        store.savePayment(payment(id = "sync-1", guardado = true))
        store.savePayment(payment(id = "sync-2", guardado = true))

        assertTrue(
            "si todo esta guardado en Microsip no hay pendientes",
            store.getPendingPayments().isEmpty()
        )
    }

    @Test
    fun getPendingPayments_emptyWhenNoPayments() = runTest {
        assertTrue(store.getPendingPayments().isEmpty())
    }

    @Test
    fun getPendingPayments_orderedByFechaAsc() = runTest {
        store.savePayment(payment(id = "b", fecha = "2026-06-03T15:00:00Z"))
        store.savePayment(payment(id = "a", fecha = "2026-06-01T15:00:00Z"))
        store.savePayment(payment(id = "c", fecha = "2026-06-02T15:00:00Z"))

        assertEquals(
            "getPendingPayments ordena por FECHA_HORA_PAGO ASC (mas viejo primero, orden de subida)",
            listOf("a", "c", "b"),
            store.getPendingPayments().map { it.ID }
        )
    }

    // ─── savePayment / round-trip ─────────────────────────────────────────────

    @Test
    fun savePayment_roundTripsAllFields() = runTest {
        val p = payment(id = "rt-1", importe = 1234.56, lat = 18.45, lng = -97.39)
        store.savePayment(p)

        val got = store.getPaymentById("rt-1")!!
        assertEquals(p, got)
    }

    @Test
    fun getPaymentById_nullWhenAbsent() = runTest {
        assertNull(store.getPaymentById("no-existe"))
    }

    @Test
    fun getPaymentsBySaleId_filtersByCargo() = runTest {
        store.savePayment(payment(id = "s5000-a", saleId = 5000))
        store.savePayment(payment(id = "s5000-b", saleId = 5000))
        store.savePayment(payment(id = "s9999", saleId = 9999))

        assertEquals(
            listOf("s5000-a", "s5000-b").sorted(),
            store.getPaymentsBySaleId(5000).map { it.ID }.sorted()
        )
    }

    // ─── getPaymentsByDate: filtro forma_cobro + ventana media-abierta ────────

    @Test
    fun getPaymentsByDate_filtersFormaCobroAndWindow() = runTest {
        // Dentro de ventana y formas de cobro validas (157/158/52569).
        store.savePayment(
            payment(id = "efectivo", fecha = "2026-06-02T15:00:00Z", formaCobro = 157)
        )
        store.savePayment(payment(id = "cheque", fecha = "2026-06-03T15:00:00Z", formaCobro = 158))
        store.savePayment(
            payment(id = "transfer", fecha = "2026-06-04T15:00:00Z", formaCobro = 52569)
        )
        // Forma de cobro excluida (p.ej. enganche 87327, condonacion 137026).
        store.savePayment(
            payment(id = "enganche", fecha = "2026-06-02T15:00:00Z", formaCobro = 87327)
        )
        store.savePayment(
            payment(id = "condonacion", fecha = "2026-06-02T15:00:00Z", formaCobro = 137026)
        )
        // Fuera de ventana.
        store.savePayment(payment(id = "antes", fecha = "2026-05-31T15:00:00Z", formaCobro = 157))
        store.savePayment(payment(id = "en-end", fecha = "2026-06-05T00:00:00Z", formaCobro = 157))

        val result = store.getPaymentsByDate("2026-06-01T00:00:00Z", "2026-06-05T00:00:00Z")

        assertEquals(
            "solo formas 157/158/52569 dentro de [start, end); end es exclusivo",
            listOf("transfer", "cheque", "efectivo"),
            result.map { it.ID }
        )
    }

    @Test
    fun getForgivenessByDate_onlyCondonacion() = runTest {
        store.savePayment(
            payment(id = "cond-1", fecha = "2026-06-02T15:00:00Z", formaCobro = 137026)
        )
        store.savePayment(payment(id = "pago-1", fecha = "2026-06-02T15:00:00Z", formaCobro = 157))

        val result = store.getForgivenessByDate("2026-06-01T00:00:00Z", "2026-06-05T00:00:00Z")

        assertEquals(listOf("cond-1"), result.map { it.ID })
    }

    @Test
    fun getAllPayments_orderedByFechaDesc() = runTest {
        store.savePayment(payment(id = "a", fecha = "2026-06-01T15:00:00Z"))
        store.savePayment(payment(id = "c", fecha = "2026-06-03T15:00:00Z"))
        store.savePayment(payment(id = "b", fecha = "2026-06-02T15:00:00Z"))

        assertEquals(listOf("c", "b", "a"), store.getAllPayments().map { it.ID })
    }

    // ─── changePaymentStatus / updatePaymentLocation ──────────────────────────

    @Test
    fun changePaymentStatus_flipsGuardadoFlag() = runTest {
        store.savePayment(payment(id = "cps-1", guardado = false))

        store.changePaymentStatus("cps-1", true)
        assertTrue(store.getPaymentById("cps-1")!!.GUARDADO_EN_MICROSIP)

        store.changePaymentStatus("cps-1", false)
        assertTrue(!store.getPaymentById("cps-1")!!.GUARDADO_EN_MICROSIP)
    }

    @Test
    fun updatePaymentLocation_persistsCoords() = runTest {
        store.savePayment(payment(id = "loc-1", lat = null, lng = null))

        store.updatePaymentLocation("loc-1", 18.4501, -97.3902)

        val got = store.getPaymentById("loc-1")!!
        assertEquals(18.4501, got.LAT!!, 1e-9)
        assertEquals(-97.3902, got.LNG!!, 1e-9)
    }

    @Test
    fun getSuggestedAmountsBySaleId_distinctIntDesc() = runTest {
        store.savePayment(payment(id = "sa-1", saleId = 7000, importe = 350.0))
        store.savePayment(payment(id = "sa-2", saleId = 7000, importe = 700.0))
        store.savePayment(payment(id = "sa-3", saleId = 7000, importe = 350.0))

        assertEquals(listOf(700, 350), store.getSuggestedAmountsBySaleId(7000))
    }

    @Test
    fun getLocationsGroupedBySaleId_excludesZeroAndNull() = runTest {
        store.savePayment(payment(id = "geo-1", saleId = 5000, lat = 18.4, lng = -97.3))
        store.savePayment(payment(id = "geo-2", saleId = 5000, lat = 18.5, lng = -97.2))
        store.savePayment(payment(id = "geo-3", saleId = 8000, lat = 19.0, lng = -98.0))
        store.savePayment(payment(id = "geo-null", saleId = 5000, lat = null, lng = null))
        store.savePayment(payment(id = "geo-zero", saleId = 5000, lat = 0.0, lng = 0.0))

        val groups = store.getLocationsGroupedBySaleId().associateBy { it.saleId }

        assertEquals(
            "cargo 5000 conserva solo las 2 coords no-nulas y no-cero",
            2,
            groups[5000]!!.locations.size
        )
        assertEquals(1, groups[8000]!!.locations.size)
    }

    // ─── camino de captura: insertPaymentAndUpdateSale / saveAndEnqueue ───────

    @Test
    fun saveAndEnqueue_insertsPaymentAndDecrementsSaldo() = runTest {
        db.saleDao().insertAll(listOf(sale(saleId = 5000, saldoRest = 1000.0)))

        store.saveAndEnqueue(
            payment = payment(id = "cap-1", saleId = 5000, importe = 350.0),
            saleId = 5000,
            newAmount = 350.0,
            newEstadoCobranza = EstadoCobranza.PAGADO
        )

        // El pago quedo persistido.
        assertEquals(350.0, store.getPaymentById("cap-1")!!.IMPORTE, 1e-9)
        // updateTotal RESTA el monto del SALDO_REST y fija ESTADO_COBRANZA.
        val updated = db.saleDao().findByDoctoCcId(5001)!!
        assertEquals("SALDO_REST = 1000 - 350", 650.0, updated.SALDO_REST, 1e-9)
        assertEquals("PAGADO", updated.ESTADO_COBRANZA)
    }

    @Test
    fun insertPaymentAndUpdateSale_isAtomicVisiblePair() = runTest {
        db.saleDao().insertAll(listOf(sale(saleId = 6000, saldoRest = 500.0)))

        store.insertPaymentAndUpdateSale(
            payment = payment(id = "cap-2", saleId = 6000, importe = 500.0),
            saleId = 6000,
            newAmount = 500.0,
            newEstadoCobranza = EstadoCobranza.PAGADO
        )

        assertEquals("cap-2", store.getPaymentById("cap-2")!!.ID)
        assertEquals(0.0, db.saleDao().findByDoctoCcId(6001)!!.SALDO_REST, 1e-9)
    }

    // ─── equivalencia inyectado ⇔ puente context ──────────────────────────────

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        // Sembramos por la forma INYECTADA...
        store.savePayment(payment(id = "eq-pend", guardado = false))
        store.savePayment(payment(id = "eq-sync", guardado = true))

        // ...y leemos por el PUENTE `context`. RoomTestBase instalo la MISMA DB
        // in-memory con setInstanceForTesting, asi que getInstance(context)
        // devuelve exactamente la instancia de `db`.
        val contextForm = PaymentsLocalDataSource(
            ApplicationProvider.getApplicationContext()
        )

        assertEquals(
            "ambos constructores resuelven a la misma DB: mismos pendientes",
            store.getPendingPayments().map { it.ID },
            contextForm.getPendingPayments().map { it.ID }
        )
        assertEquals(
            store.getPaymentById("eq-sync"),
            contextForm.getPaymentById("eq-sync")
        )
    }
}
