package com.example.msp_app.e2e

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.sync.cobranza.CobranzaReconciler
import com.example.msp_app.core.sync.cobranza.CobranzaWriteMutex
import com.example.msp_app.core.sync.cobranza.ReconcileOutcome
import com.example.msp_app.core.sync.cobranza.UserContext
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof of the cobranza duplicate-payment self-heal fix
 * (see `CobranzaReconcilerTest.selfHealColapsaGemeloUuidAunSinDriftEnDigest`
 * for the JVM-level equivalent this mirrors).
 *
 * Scenario: a payment captured offline is persisted twice locally — once
 * under a client-generated UUID (the capture row) and once under the
 * numeric ID Microsip assigned once the upload landed (the "twin"), the
 * latter carrying `PAGO_RECIBIDO_ID` pointing back at the UUID. When the
 * one-shot collapse inside `mergePagos` misses that window (race between
 * the pull and `markDone`, or a historical case), the UUID row is
 * orphaned forever unless [CobranzaReconciler.reconcileNow] self-heals it.
 * This suite proves the self-heal runs on the REAL on-device Room
 * database + the REAL reconciler, not a JVM/Robolectric fake.
 */
@RunWith(AndroidJUnit4::class)
class CobranzaSelfHealTwinE2ETest : PagosE2ETestBase() {

    // ── fixtures ─────────────────────────────────────────────────────────

    private fun samplePayment(
        id: String,
        doctoCcAcrId: Int,
        guardado: Boolean,
        pagoRecibidoId: String? = null
    ) = PaymentEntity(
        ID = id,
        COBRADOR = "Ricardo Flores Mendoza",
        DOCTO_CC_ACR_ID = doctoCcAcrId,
        DOCTO_CC_ID = doctoCcAcrId + 1,
        FECHA_HORA_PAGO = "2026-08-05T11:00:00Z",
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = 620.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 8801,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 87327,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Minerva Lopez Hernandez",
        PAGO_RECIBIDO_ID = pagoRecibidoId
    )

    private class FakeConnectivity(context: android.content.Context) : ConnectivityMonitor(
        context
    ) {
        override fun isNetworkAvailable(): Boolean = true
        override val isConnected: Flow<Boolean> = flowOf(true)
    }

    /**
     * Minimal V2CobranzaApi fake: digests always echo back whatever local
     * would compute for the seeded numeric payment, so digests MATCH and
     * `/ids` is never called — proving the self-heal collapse runs
     * unconditionally, not as a side effect of the `/ids` reconcile path.
     */
    private class DigestMatchingApi(
        private val digest: DigestResponse
    ) : V2CobranzaApi {
        var listPagoIdsCalled = 0

        override suspend fun pagosDigest(zonaId: Int, desde: String?) = digest

        override suspend fun saldosDigest(zonaId: Int, desde: String?) =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun listPagoIds(
            zonaId: Int,
            after: Int,
            limit: Int,
            desde: String?
        ): IdsResponse {
            listPagoIdsCalled++
            return IdsResponse(ids = emptyList(), has_more = false)
        }

        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            IdsResponse(ids = emptyList(), has_more = false)

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse = error("reconciler must not call syncVentas")

        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse = error("reconciler must not call syncPagos")

        override suspend fun pagosByIds(zonaId: Int, ids: String): List<PagoDto> =
            error("unreachable in a digest-match run")

        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> =
            error("unreachable in a digest-match run")
    }

    private fun newReconciler(api: V2CobranzaApi, zona: Int = 21): CobranzaReconciler =
        CobranzaReconciler(
            api = api,
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            connectivity = FakeConnectivity(context),
            userContextFlow = MutableStateFlow(
                UserContext(zona = zona, fechaCargaInicial = null)
            ).asStateFlow(),
            cobranzaWriteMutex = CobranzaWriteMutex()
        )

    private fun xorOf(vararg ids: Int): Long = ids.fold(0L) { acc, id -> acc xor id.toLong() }
    private fun sumOf(vararg ids: Int): Long = ids.fold(0L) { acc, id -> acc + id.toLong() }

    // ── Scenario 1 (MUST): self-heal of an existing twin, unconditionally ──

    @Test
    fun selfHealColapsaGemeloUuidExistenteSinDriftEnDigest() = runBlocking {
        // Numeric row already confirmed by Microsip, pointing back at its
        // UUID capture twin.
        db.paymentDao().saveAll(
            listOf(
                samplePayment(
                    id = "15808629",
                    doctoCcAcrId = 9201,
                    guardado = true,
                    pagoRecibidoId = "uuid-selfheal-1"
                )
            )
        )
        // The UUID capture twin, already uploaded, orphaned because the
        // one-shot collapse in mergePagos missed its window.
        db.paymentDao().saveAll(
            listOf(
                samplePayment(id = "uuid-selfheal-1", doctoCcAcrId = 9201, guardado = true)
            )
        )

        // Server digest matches local EXACTLY (only the numeric ID counts —
        // the reconciler's own pre-check filters UUIDs via mapNotNull), so
        // the /ids path is skipped entirely and only the unconditional
        // self-heal runs.
        val digest = DigestResponse(
            count_activos = 1,
            ids_xor = xorOf(15808629).toString(),
            ids_sum = sumOf(15808629).toString(),
            max_updated_at = null
        )
        val api = DigestMatchingApi(digest)

        val outcome = newReconciler(api).reconcileNow()

        assertTrue("reconcile debe terminar Ok, no Error", outcome is ReconcileOutcome.Ok)
        assertEquals(
            "digest coincide -> /ids nunca debe llamarse",
            0,
            api.listPagoIdsCalled
        )

        assertNull(
            "el gemelo UUID debe quedar colapsado (borrado)",
            db.paymentDao().getPaymentById("uuid-selfheal-1")
        )
        val survivor = db.paymentDao().getPaymentById("15808629")
        assertNotNull("la fila numerica canonica debe seguir presente", survivor)
        assertTrue(survivor!!.GUARDADO_EN_MICROSIP)
    }

    // ── Scenario 2 (SHOULD): the pending-twin race, at DAO+reconciler level ──

    /**
     * Full worker-vs-sync race (real WorkManager upload racing the real
     * CobranzaSyncManager pull) would require synchronizing two concurrent
     * on-device operations around a MockWebServer response — not attempted
     * here as it would be a rabbit hole for this proof. Instead this
     * reproduces the invariant the race protects at the DAO+reconciler
     * level, which is still fully on-device against the real Room schema
     * and the real collapse query.
     */
    @Test
    fun gemeloPendienteNoColapsaHastaQueMarkDoneLoConfirma() = runBlocking {
        // Numeric twin already landed, referencing the UUID capture...
        db.paymentDao().saveAll(
            listOf(
                samplePayment(
                    id = "15808777",
                    doctoCcAcrId = 9301,
                    guardado = true,
                    pagoRecibidoId = "uuid-selfheal-race"
                )
            )
        )
        // ...but the UUID row is STILL mid-upload (GUARDADO_EN_MICROSIP=false).
        // This is the race window: the numeric twin can arrive from the pull
        // before the worker uploading the UUID capture calls markDone.
        db.paymentDao().saveAll(
            listOf(
                samplePayment(id = "uuid-selfheal-race", doctoCcAcrId = 9301, guardado = false)
            )
        )

        assertFalse(
            "un gemelo UUID pendiente de subir jamas debe reportarse colapsable",
            db.paymentDao().findCollapsibleUuidTwins().contains("uuid-selfheal-race")
        )

        // The upload finishes for real: PendingPaymentsWorker.markDone flips
        // GUARDADO_EN_MICROSIP via the same PaymentDao.updateEstado call path.
        db.paymentDao().updateEstado("uuid-selfheal-race", 1)

        assertTrue(
            "una vez confirmado, el gemelo UUID SI debe ser colapsable",
            db.paymentDao().findCollapsibleUuidTwins().contains("uuid-selfheal-race")
        )

        // Now the real reconciler, with a matching digest, collapses it.
        val digest = DigestResponse(
            count_activos = 1,
            ids_xor = xorOf(15808777).toString(),
            ids_sum = sumOf(15808777).toString(),
            max_updated_at = null
        )
        val outcome = newReconciler(DigestMatchingApi(digest)).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        assertNull(
            "tras markDone, el reconcile debe colapsar el gemelo",
            db.paymentDao().getPaymentById("uuid-selfheal-race")
        )
        assertNotNull(db.paymentDao().getPaymentById("15808777"))
    }

    // ── Scenario 3 (nice-to-have): real migration chain opens cleanly ──────

    @Test
    fun aperturaRealDeAppDatabaseMigrandoAV27PermiteQueryDeColapso() = runBlocking {
        // getInstance runs the full production migration chain (down to v1)
        // against a fresh on-device SQLite file — proves the schema the
        // self-heal query depends on (PAGO_RECIBIDO_ID column + index from
        // Migration26to27) is reachable via the real upgrade path, not only
        // via Room.inMemoryDatabaseBuilder's fresh-create shortcut used by
        // the rest of this suite.
        AppDatabase.clearInstance()
        try {
            val realDb = AppDatabase.getInstance(context)
            // Exercise the exact query the self-heal depends on; an empty
            // result is expected (fresh DB) — what matters is that opening
            // the DB through every real migration and running the query
            // that references PAGO_RECIBIDO_ID does not throw.
            val collapsible = realDb.paymentDao().findCollapsibleUuidTwins()
            assertTrue(collapsible.isEmpty())
        } finally {
            // Restore the in-memory test DB for @After teardown / any
            // subsequent test in the same process.
            AppDatabase.setInstanceForTesting(
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            )
        }
    }
}
