package com.example.msp_app.core.sync.cobranza

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.toEntity
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.models.sale.EstadoCobranza
import com.example.msp_app.`test-fixtures`.RoomTestBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CobranzaSyncManagerTest : RoomTestBase() {

    @After
    fun resetByIdsFlag() {
        ByIdsChunker.byIdsAvailable.set(true)
    }

    private class FakeConnectivity(private val online: Boolean) :
        ConnectivityMonitor(ApplicationProvider.getApplicationContext()) {
        override fun isNetworkAvailable(): Boolean = online
        override val isConnected: Flow<Boolean> = flowOf(online)
    }

    private fun newConnectivity(connected: Boolean): ConnectivityMonitor = FakeConnectivity(
        connected
    )

    private fun newManager(
        api: V2CobranzaApi,
        online: Boolean = true,
        zona: Int? = 21,
        fechaCargaInicial: java.time.Instant? = null
    ): CobranzaSyncManager = CobranzaSyncManager(
        api = api,
        db = db,
        saleDao = db.saleDao(),
        paymentDao = db.paymentDao(),
        syncStateDao = db.cobranzaSyncStateDao(),
        connectivity = newConnectivity(online),
        userContextFlow = MutableStateFlow(
            zona?.let { UserContext(zona = it, fechaCargaInicial = fechaCargaInicial) }
        ).asStateFlow(),
        // Mutex fresco por test para evitar dependencias entre tests al usar el singleton de proceso.
        cobranzaWriteMutex = CobranzaWriteMutex()
    )

    private fun ventaDto(
        doctoCcId: Int,
        zonaId: Int = 21,
        cancelado: Boolean = false,
        importeTotal: String = "1000.00",
        numPagos: Int = 4,
        updatedAt: String = "2026-05-30T18:25:13.456789Z"
    ) = VentaDto(
        docto_cc_id = doctoCcId,
        docto_pv_id = null,
        cliente_id = 99,
        zona_cliente_id = zonaId,
        folio = "cv$doctoCcId",
        fecha_cargo = "2026-02-15T00:00:00Z",
        fecha_venta = null,
        precio_total = "5000.00",
        total_importe = importeTotal,
        impte_rest = "0.00",
        saldo = "4000.00",
        num_pagos = numPagos,
        fecha_ult_pago = null,
        cargo_cancelado = cancelado,
        updated_at = updatedAt,
        cliente_nombre = "JUAN PEREZ",
        limite_credito = null,
        cliente_notas = "",
        cobrador_id = null,
        nombre_cobrador = "",
        zona_nombre = "R/21",
        calle = "AV INDEPENDENCIA 123",
        ciudad = "TEHUACAN",
        estado = "PUEBLA",
        telefono = "",
        parcialidad = 200,
        enganche = "500.00",
        tiempo_corto_plazo_meses = null,
        monto_corto_plazo = null,
        precio_de_contado = null,
        aval_o_responsable = "",
        vendedor_1 = "",
        vendedor_2 = "",
        vendedor_3 = "",
        frec_pago = "SEMANAL"
    )

    private fun pagoDto(impteId: Int, doctoCcId: Int) = PagoDto(
        impte_docto_cc_id = impteId,
        docto_cc_id = doctoCcId + 1,
        docto_cc_acr_id = doctoCcId,
        cliente_id = 99,
        zona_cliente_id = 21,
        folio = "abono",
        concepto_cc_id = 87327,
        fecha = "2026-05-20T14:30:00Z",
        importe = "200.00",
        impuesto = "0.00",
        lat = null,
        lon = null,
        cancelado = false,
        aplicado = true,
        updated_at = "2026-05-30T18:25:13.456789Z",
        cobrador = "",
        cobrador_id = null,
        nombre_cliente = "",
        forma_cobro_id = null
    )

    @Test
    fun initialSyncWritesEntitiesAndPersistsCursor() = runTest {
        val api = fakeApi(
            ventas = listOf(
                page(items = listOf(ventaDto(101), ventaDto(102)), hasMore = false)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val mgr = newManager(api)

        val outcome = mgr.syncNow()
        assertTrue(outcome is SyncOutcome.Ok)
        assertEquals(2, (outcome as SyncOutcome.Ok).ventasApplied)
        assertEquals(101, db.saleDao().findByDoctoCcId(101)!!.DOCTO_CC_ID)
        assertEquals(102, db.saleDao().findByDoctoCcId(102)!!.DOCTO_CC_ID)
        val state = db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)
        assertNotNull(state)
        assertEquals("2026-05-30T18:25:13.456789Z", state!!.CURSOR)
    }

    @Test
    fun mergePreservesEstadoCobranzaAndDiaTemporal() = runTest {
        val seeded = ventaDto(201).toEntity().copy(
            ESTADO_COBRANZA = EstadoCobranza.PAGADO.name,
            DIA_TEMPORAL_COBRANZA = "VIERNES"
        )
        db.saleDao().insertAll(listOf(seeded))

        val api = fakeApi(
            ventas = listOf(
                page(
                    items = listOf(ventaDto(201, importeTotal = "2000.00", numPagos = 5)),
                    hasMore = false
                )
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val mgr = newManager(api)
        mgr.syncNow()

        val refreshed = db.saleDao().findByDoctoCcId(201)!!
        assertEquals(EstadoCobranza.PAGADO.name, refreshed.ESTADO_COBRANZA)
        assertEquals("VIERNES", refreshed.DIA_TEMPORAL_COBRANZA)
        assertEquals(2000.0, refreshed.TOTAL_IMPORTE, 0.001)
    }

    /**
     * Contrato estructural del merge frente a un re-backfill masivo (típico
     * tras aplicar una migración en producción que reescribe
     * MSP_SALDOS_VENTAS.UPDATED_AT para todos los cargos activos).
     *
     * Simula: cobrador tiene la venta 250 en local con estado de visita y
     * día temporal ajustados. La oficina aplica una migración (e.g. 000015)
     * que dispara un re-recompute por cargo. Al siguiente sync, el backend
     * remanda la fila con SALDO/IMPORTE/NUM_PAGOS recalculados y
     * UPDATED_AT=now. El cliente DEBE:
     *   - Conservar el estado local del cobrador (ESTADO_COBRANZA,
     *     DIA_TEMPORAL_COBRANZA).
     *   - Sobrescribir TODO lo demás con los valores nuevos del servidor.
     *
     * Esta prueba codifica el contrato como `actual == dtoFresco.toEntity()
     * con sentinelas locales superpuestas`. Si en el futuro agregas una
     * columna con estado local del cobrador:
     *   1. Súmala al `.copy(...)` de `mergeVentas` (CobranzaSyncManager.kt).
     *   2. Súmala al `.copy(...)` del `expectedAfterMerge` de abajo, con
     *      un sentinela.
     *   3. Súmala al `seeded.copy(...)` con el mismo sentinela.
     * Si te olvidas del paso 1 (lo más fácil de olvidar), este test falla
     * porque el sentinela local desaparece en el merge.
     */
    @Test
    fun reBackfillPreservaEstadoLocalYReescribeColumnasDelServidor() = runTest {
        // Sentinelas locales — valores que jamás vendrían del servidor en
        // un toEntity(): VISITADO (default es PENDIENTE) y un día con
        // sufijo único que no produce computeDiaCobranza.
        val sentinelEstado = EstadoCobranza.VISITADO.name
        val sentinelDiaTemporal = "JUEVES_TEMP_LOCAL"

        // Seed: snapshot "viejo" del servidor + sentinelas locales.
        val seededDtoSnapshot = ventaDto(
            doctoCcId = 250,
            importeTotal = "500.00",
            numPagos = 2,
            updatedAt = "2026-04-01T10:00:00Z"
        )
        val seeded = seededDtoSnapshot.toEntity().copy(
            ESTADO_COBRANZA = sentinelEstado,
            DIA_TEMPORAL_COBRANZA = sentinelDiaTemporal
        )
        db.saleDao().insertAll(listOf(seeded))

        // Re-backfill: mismo docto_cc_id, valores actualizados del servidor.
        val refreshedDto = ventaDto(
            doctoCcId = 250,
            importeTotal = "3200.00",
            numPagos = 9,
            updatedAt = "2026-05-30T18:25:13.456789Z"
        )
        val api = fakeApi(
            ventas = listOf(page(items = listOf(refreshedDto), hasMore = false)),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        val actual = db.saleDao().findByDoctoCcId(250)!!
        val expectedAfterMerge = refreshedDto.toEntity().copy(
            // ─── Columnas que mergeVentas DEBE preservar ────────────────
            // (mantener sincronizado con CobranzaSyncManager.mergeVentas)
            ESTADO_COBRANZA = sentinelEstado,
            DIA_TEMPORAL_COBRANZA = sentinelDiaTemporal
        )

        assertEquals(
            "El merge tras un re-backfill no respetó el contrato. " +
                "Probable causa: agregaste una columna con estado local del " +
                "cobrador sin listarla en CobranzaSyncManager.mergeVentas " +
                "(o sin sumarla al .copy() de este test).",
            expectedAfterMerge,
            actual
        )

        // Doble check explícito sobre los campos críticos por si el equals
        // del data class oculta el campo divergente en el mensaje de error.
        assertEquals(sentinelEstado, actual.ESTADO_COBRANZA)
        assertEquals(sentinelDiaTemporal, actual.DIA_TEMPORAL_COBRANZA)
        assertEquals(3200.0, actual.TOTAL_IMPORTE, 0.001)
        assertEquals(9, actual.NUM_IMPORTES)
    }

    @Test
    fun tombstoneRemovesSaleAndPayments() = runTest {
        val seed = ventaDto(301).toEntity()
        db.saleDao().insertAll(listOf(seed))
        db.paymentDao().saveAll(listOf(samplePayment(301)))

        val api = fakeApi(
            ventas = listOf(page(items = listOf(ventaDto(301, cancelado = true)), hasMore = false)),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        assertNull(db.saleDao().findByDoctoCcId(301))
        assertTrue(db.paymentDao().getPaymentsBySaleId(301).isEmpty())
    }

    @Test
    fun offlineSkipsApiCall() = runTest {
        val api = failingApi()
        val mgr = newManager(api, online = false)
        val outcome = mgr.syncNow()
        assertTrue(outcome is SyncOutcome.SkippedOffline)
    }

    @Test
    fun noZoneSkipsApiCall() = runTest {
        val api = failingApi()
        val mgr = newManager(api, zona = null)
        val outcome = mgr.syncNow()
        assertTrue(outcome is SyncOutcome.SkippedNoZone)
    }

    @Test
    fun apiErrorRecordsLastErrorWithoutCrashing() = runTest {
        val api = object : V2CobranzaApi {
            override suspend fun syncVentas(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ) = throw RuntimeException("network down")
            override suspend fun syncPagos(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ) = throw RuntimeException("network down")
            override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse =
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse =
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
                error("not used in sync tests")
            override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
                error("not used in sync tests")
            override suspend fun pagosByIds(zonaId: Int, ids: String) =
                error("not used in sync tests")
            override suspend fun saldosByIds(zonaId: Int, ids: String) =
                error("not used in sync tests")
        }
        val outcome = newManager(api).syncNow()
        assertTrue(outcome is SyncOutcome.Error)
        // Records error even when the row was never created — these calls are
        // best-effort. We don't assert state existence because the first
        // failed page may not have written a row yet.
    }

    // ─── Plan ?desde= / FECHA_CARGA_INICIAL ─────────────────────────────────

    @Test
    fun mergeSaldadaConPagoEnVentanaSeConserva() = runTest {
        // Pago dentro de la ventana del cobrador.
        val pagoEnVentana = samplePayment(401).copy(
            FECHA_HORA_PAGO = "2026-05-20T10:00:00Z"
        )
        db.paymentDao().saveAll(listOf(pagoEnVentana))
        val ventana = java.time.Instant.parse("2026-05-15T00:00:00Z")

        // Backend manda la venta saldada (saldo = 0) dentro de la ventana.
        val api = fakeApi(
            ventas = listOf(
                page(items = listOf(ventaDtoSaldada(401)), hasMore = false)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api, fechaCargaInicial = ventana).syncNow()

        // Como tiene un pago dentro de la ventana, la venta se conserva.
        assertNotNull(db.saleDao().findByDoctoCcId(401))
    }

    @Test
    fun mergeSaldadaSinPagoEnVentanaSeBorraYConservaPagos() = runTest {
        // Pago FUERA de la ventana (mucho más viejo).
        val pagoViejo = samplePayment(402).copy(
            FECHA_HORA_PAGO = "2025-12-01T10:00:00Z"
        )
        db.paymentDao().saveAll(listOf(pagoViejo))
        val ventana = java.time.Instant.parse("2026-05-15T00:00:00Z")

        // Backend manda la venta saldada (saldo = 0) sin pagos en ventana.
        val api = fakeApi(
            ventas = listOf(
                page(items = listOf(ventaDtoSaldada(402)), hasMore = false)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api, fechaCargaInicial = ventana).syncNow()

        // La venta se borra (fuera de ventana), los pagos se conservan
        // (auditoría SAT, reportes históricos).
        assertNull(db.saleDao().findByDoctoCcId(402))
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(402).size)
    }

    @Test
    fun pruneSaldadasFueraDeVentanaBorraSoloVentasSinTocarPayments() = runTest {
        // Seed: venta saldada con pago fuera de ventana.
        val ventaFueraDeVentana = ventaDtoSaldada(403).toEntity()
        db.saleDao().insertAll(listOf(ventaFueraDeVentana))
        db.paymentDao().saveAll(
            listOf(
                samplePayment(403).copy(
                    FECHA_HORA_PAGO = "2025-11-01T10:00:00Z"
                )
            )
        )
        // Seed: venta saldada con pago dentro de ventana (debe sobrevivir).
        val ventaEnVentana = ventaDtoSaldada(404).toEntity()
        db.saleDao().insertAll(listOf(ventaEnVentana))
        db.paymentDao().saveAll(
            listOf(
                samplePayment(404).copy(
                    FECHA_HORA_PAGO = "2026-05-22T10:00:00Z"
                )
            )
        )

        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val mgr = newManager(
            api,
            fechaCargaInicial = java.time.Instant.parse("2026-05-15T00:00:00Z")
        )

        val pruned = mgr.pruneSaldadasFueraDeVentana("2026-05-15T00:00:00Z")
        assertEquals(1, pruned)

        // 403: borrada (sus pagos viven).
        assertNull(db.saleDao().findByDoctoCcId(403))
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(403).size)
        // 404: sobrevive (tiene pago en ventana).
        assertNotNull(db.saleDao().findByDoctoCcId(404))
    }

    @Test
    fun syncEnviaDesdeEnTodasLasPaginas() = runTest {
        val ventana = java.time.Instant.parse("2026-05-15T00:00:00Z")
        val api = RecordingFakeApi(
            ventas = listOf(
                page(
                    items = listOf(ventaDto(501, updatedAt = "2026-05-30T18:00:00Z")),
                    hasMore = true
                ),
                page(
                    items = listOf(ventaDto(502, updatedAt = "2026-05-30T18:10:00Z")),
                    hasMore = false
                )
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val mgr = CobranzaSyncManager(
            api = api,
            db = db,
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            connectivity = newConnectivity(true),
            userContextFlow = MutableStateFlow(
                UserContext(zona = 21, fechaCargaInicial = ventana)
            ).asStateFlow(),
            cobranzaWriteMutex = CobranzaWriteMutex()
        )
        mgr.syncNow()

        // Las 2 páginas deben llevar el mismo `desde`; la app debe enviarlo
        // siempre, no solo cuando cursor == null (la regresión que arregló
        // el commit 2e16195 del backend).
        assertEquals(2, api.ventasDesdeCalls.size)
        assertTrue(api.ventasDesdeCalls.all { it == ventana.toString() })
        assertEquals(1, api.pagosDesdeCalls.size)
        assertEquals(ventana.toString(), api.pagosDesdeCalls.single())
    }

    @Test
    fun cambioDeZonaLimpiaLocalYReseteaCursores() = runTest {
        // Sync inicial en zona 21 — escribe ventas, pagos y cursor.
        val apiZona21 = fakeApi(
            ventas = listOf(
                page(items = listOf(ventaDto(601, zonaId = 21)), hasMore = false)
            ),
            pagos = listOf(pagoPage(items = listOf(pagoDto(701, 601)), hasMore = false))
        )
        newManager(apiZona21, zona = 21).syncNow()
        assertNotNull(db.saleDao().findByDoctoCcId(601))
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(601).size)
        assertEquals(
            21,
            db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!.ZONA_CLIENTE_ID
        )

        // El cobrador cambia a zona 42 — el manager debe detectar y limpiar.
        val apiZona42 = fakeApi(
            ventas = listOf(
                page(items = listOf(ventaDto(602, zonaId = 42)), hasMore = false)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(apiZona42, zona = 42).syncNow()

        // La venta y pago de zona 21 ya no están — el cache reflejó solo
        // la zona nueva. El cursor también arrancó desde cero (sin estado
        // residual de la zona vieja).
        assertNull(db.saleDao().findByDoctoCcId(601))
        assertEquals(0, db.paymentDao().getPaymentsBySaleId(601).size)
        assertNotNull(db.saleDao().findByDoctoCcId(602))
        assertEquals(
            42,
            db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!.ZONA_CLIENTE_ID
        )
    }

    @Test
    fun residuosDeOtraZonaActivanCleanupAunqueElStateYaCoincida() = runTest {
        // Caso edge: el state ya apunta a la zona actual (42), pero quedaron
        // rows huérfanos de la zona 21 — situación que solo aparece tras
        // una transición pasada mal hecha. El cleanup debe atraparlos por
        // el conteo de residuos, no solo por la zona del state.
        db.saleDao().insertAll(listOf(ventaDto(801, zonaId = 21).toEntity()))
        db.cobranzaSyncStateDao().upsert(
            com.example.msp_app.data.local.entities.CobranzaSyncStateEntity(
                RESOURCE = CobranzaSyncManager.RESOURCE_VENTAS,
                ZONA_CLIENTE_ID = 42,
                CURSOR = "2026-05-30T00:00:00Z",
                LAST_SYNCED_AT = "2026-05-30T00:00:00Z",
                LAST_ERROR = null
            )
        )

        val api = fakeApi(
            ventas = listOf(
                page(items = listOf(ventaDto(802, zonaId = 42)), hasMore = false)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api, zona = 42).syncNow()

        // Residuo de zona 21 borrado, venta de zona 42 presente.
        assertNull(db.saleDao().findByDoctoCcId(801))
        assertNotNull(db.saleDao().findByDoctoCcId(802))
    }

    @Test
    fun saldadaConPagoEnElMismoSyncSeConserva() = runTest {
        // Regresión: si el sync de pagos NO corre antes que el de ventas,
        // mergeVentas evalúa la saldada con countPagosDesde=0 y la borra.
        // Cuando el orden es correcto (pagos primero), el conteo da > 0 y
        // la venta sobrevive.
        val ventana = java.time.Instant.parse("2026-05-15T00:00:00Z")
        val pagoEnVentanaDto = pagoDto(impteId = 901, doctoCcId = 902).copy(
            fecha = "2026-05-20T10:00:00Z",
            docto_cc_acr_id = 902
        )
        val ventaSaldada = ventaDtoSaldada(902)

        val api = fakeApi(
            ventas = listOf(page(items = listOf(ventaSaldada), hasMore = false)),
            pagos = listOf(pagoPage(items = listOf(pagoEnVentanaDto), hasMore = false))
        )
        newManager(api, fechaCargaInicial = ventana).syncNow()

        // La venta saldada se conserva porque el sync de pagos corrió
        // antes y dejó el pago en local para que countPagosDesde lo viera.
        assertNotNull(db.saleDao().findByDoctoCcId(902))
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(902).size)
    }

    @Test
    fun pagesUntilHasMoreFalse() = runTest {
        val api = fakeApi(
            ventas = listOf(
                page(
                    items = listOf(ventaDto(401, updatedAt = "2026-05-30T18:00:00Z")),
                    hasMore = true
                ),
                page(
                    items = listOf(ventaDto(402, updatedAt = "2026-05-30T18:10:00Z")),
                    hasMore = false
                )
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val outcome = newManager(api).syncNow()
        assertTrue(outcome is SyncOutcome.Ok)
        assertEquals(2, (outcome as SyncOutcome.Ok).ventasApplied)
        val state = db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!
        assertEquals("2026-05-30T18:10:00Z", state.CURSOR)
    }

    // ─── Tombstones de pagos individuales (mig 20 server-side) ──────────────

    /**
     * Contrato: cuando el backend manda un pago con `cancelado=true`, el
     * cliente debe borrarlo de Room. Sin este branch, el UPSERT pegaba un
     * fantasma de $0 (IMPORTE viene en cero porque el server convierte la
     * cancelación en tombstone). Cubre tanto cancelaciones lógicas (flag
     * `CANCELADO='S'` en IMPORTES_DOCTOS_CC) como DELETE físicos (mig 20
     * los convierte en UPDATE tombstone con CANCELADO='S').
     */
    @Test
    fun tombstoneDePagoBorraPaymentLocal() = runTest {
        val impteId = 555
        val seed = samplePayment(701).copy(ID = impteId.toString())
        db.paymentDao().saveAll(listOf(seed))

        val canceladoDto = pagoDto(impteId = impteId, doctoCcId = 701).copy(cancelado = true)
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(canceladoDto), hasMore = false))
        )
        newManager(api).syncNow()

        assertTrue(
            "tombstone con cancelado=true debe borrar el pago local",
            db.paymentDao().getPaymentsBySaleId(701).isEmpty()
        )
    }

    /**
     * Idempotencia: si la primera vez que vemos un pago ya viene tombstoneado
     * (e.g. la app se instaló durante una ventana de inactividad), el DELETE
     * no debe fallar — SQLite trata el DELETE WHERE PK=x sin match como
     * cero rows affected.
     */
    @Test
    fun tombstoneDePagoQueNoEstabaEnLocalNoFalla() = runTest {
        val canceladoDto = pagoDto(impteId = 556, doctoCcId = 702).copy(cancelado = true)
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(canceladoDto), hasMore = false))
        )
        val outcome = newManager(api).syncNow()
        assertTrue(outcome is SyncOutcome.Ok)
        assertTrue(db.paymentDao().getPaymentsBySaleId(702).isEmpty())
    }

    /**
     * Mezcla en la misma página: el merge debe particionar tombstones
     * (DELETE) y vivos (UPSERT) sin perder ninguno de los dos lados. Una
     * regresión común sería iterar y solo aplicar la rama tombstone.
     */
    @Test
    fun mergePagosParticionaTombstonesYUpserts() = runTest {
        val seed = samplePayment(801).copy(ID = "1001")
        db.paymentDao().saveAll(listOf(seed))

        val tombstone = pagoDto(impteId = 1001, doctoCcId = 801).copy(cancelado = true)
        val vivo = pagoDto(impteId = 1002, doctoCcId = 801)
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(tombstone, vivo), hasMore = false))
        )
        newManager(api).syncNow()

        val pagos = db.paymentDao().getPaymentsBySaleId(801)
        assertEquals("solo el pago vivo queda en local", 1, pagos.size)
        assertEquals("1002", pagos.first().ID)
    }

    /**
     * El cursor debe avanzar aunque la página solo traiga tombstones — sin
     * esto, una racha de cancelaciones haría el sync inicial replayar el
     * mismo cursor en cada tick.
     */
    @Test
    fun tombstoneAvanzaCursorAunqueLaPaginaSoloTraigaTombstones() = runTest {
        val seed = samplePayment(901).copy(ID = "2001")
        db.paymentDao().saveAll(listOf(seed))

        val tombstone = pagoDto(impteId = 2001, doctoCcId = 901)
            .copy(cancelado = true, updated_at = "2026-06-02T20:00:00Z")
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(tombstone), hasMore = false))
        )
        newManager(api).syncNow()

        val state = db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_PAGOS)
        assertEquals("2026-06-02T20:00:00Z", state?.CURSOR)
    }

    // ─── applyByIds ─────────────────────────────────────────────────────────

    /**
     * applyByIds(PAGOS) llama a pagosByIds y mergea los resultados en Room.
     */
    @Test
    fun applyByIds_pagos_mergesResultsIntoRoom() = runTest {
        var byIdsCsvCalled: String? = null
        val api = object : V2CobranzaApi {
            override suspend fun syncVentas(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ) = error("not used")

            override suspend fun syncPagos(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ) = error("not used")

            override suspend fun pagosDigest(zonaId: Int, desde: String?) = error("not used")

            override suspend fun saldosDigest(zonaId: Int, desde: String?) = error("not used")

            override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
                error("not used")

            override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
                error("not used")

            override suspend fun pagosByIds(zonaId: Int, ids: String): List<PagoDto> {
                byIdsCsvCalled = ids
                return listOf(pagoDto(impteId = 1001, doctoCcId = 501))
            }

            override suspend fun saldosByIds(zonaId: Int, ids: String) = error("not used")
        }

        ByIdsChunker.byIdsAvailable.set(true)
        val mgr = newManager(api)
        mgr.applyByIds(SseKind.PAGOS, listOf(1001))

        assertEquals("1001", byIdsCsvCalled)
        // pagoDto(impteId=1001, doctoCcId=501) → docto_cc_acr_id=501
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(501).size)
    }

    /**
     * applyByIds(SALDOS) llama a saldosByIds y mergea en Room.
     */
    @Test
    fun applyByIds_saldos_mergesResultsIntoRoom() = runTest {
        var byIdsCsvCalled: String? = null
        val api = object : V2CobranzaApi {
            override suspend fun syncVentas(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ) = error("not used")

            override suspend fun syncPagos(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ) = error("not used")

            override suspend fun pagosDigest(zonaId: Int, desde: String?) = error("not used")

            override suspend fun saldosDigest(zonaId: Int, desde: String?) = error("not used")

            override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
                error("not used")

            override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
                error("not used")

            override suspend fun pagosByIds(zonaId: Int, ids: String) = error("not used")

            override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> {
                byIdsCsvCalled = ids
                return listOf(ventaDto(3001))
            }
        }

        ByIdsChunker.byIdsAvailable.set(true)
        val mgr = newManager(api)
        mgr.applyByIds(SseKind.SALDOS, listOf(3001))

        assertEquals("3001", byIdsCsvCalled)
        assertNotNull(db.saleDao().findByDoctoCcId(3001))
    }

    /**
     * applyByIds con ids vacío no llama a la API.
     */
    @Test
    fun applyByIds_emptyIds_doesNotCallApi() = runTest {
        val api = failingApi()
        val mgr = newManager(api)
        mgr.applyByIds(SseKind.PAGOS, emptyList())
        // Si llegamos aquí sin excepción, el API no fue llamado.
    }

    // ─── fixtures ───────────────────────────────────────────────────────────

    private data class VentaPage(val items: List<VentaDto>, val hasMore: Boolean)
    private data class PagoPage(val items: List<PagoDto>, val hasMore: Boolean)

    private fun page(items: List<VentaDto>, hasMore: Boolean) = VentaPage(items, hasMore)
    private fun pagoPage(items: List<PagoDto>, hasMore: Boolean) = PagoPage(items, hasMore)
    private fun pagoPageEmpty() = page(emptyList(), hasMore = false)

    /**
     * Fixture de venta saldada (`saldo = 0`). Reutiliza `ventaDto` y
     * sobrescribe el campo `saldo` — el resto coincide con el catálogo
     * estándar de las pruebas.
     */
    private fun ventaDtoSaldada(doctoCcId: Int) = ventaDto(doctoCcId).copy(saldo = "0.00")

    /**
     * Fake API que registra el último `desde` recibido por cada endpoint —
     * útil para verificar que el manager lo propaga en TODAS las páginas
     * y no solo en la primera.
     */
    private class RecordingFakeApi(
        private val ventas: List<VentaPage>,
        private val pagos: List<PagoPage>
    ) : V2CobranzaApi {
        private var ventasIdx = 0
        private var pagosIdx = 0
        val ventasDesdeCalls = mutableListOf<String?>()
        val pagosDesdeCalls = mutableListOf<String?>()

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse {
            ventasDesdeCalls.add(desde)
            val p = ventas.getOrNull(ventasIdx) ?: error("syncVentas called too many times")
            ventasIdx++
            return SyncVentasResponse(
                items = p.items,
                max_updated_at = p.items.lastOrNull()?.updated_at ?: cursor.orEmpty(),
                server_now = "2026-05-30T18:25:23Z",
                has_more = p.hasMore
            )
        }

        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse {
            pagosDesdeCalls.add(desde)
            val p = pagos.getOrNull(pagosIdx) ?: error("syncPagos called too many times")
            pagosIdx++
            return SyncPagosResponse(
                items = p.items,
                max_updated_at = p.items.lastOrNull()?.updated_at ?: cursor.orEmpty(),
                server_now = "2026-05-30T18:25:23Z",
                has_more = p.hasMore
            )
        }

        override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("not used in sync tests")

        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("not used in sync tests")

        override suspend fun pagosByIds(zonaId: Int, ids: String) = error("not used in sync tests")

        override suspend fun saldosByIds(zonaId: Int, ids: String) = error("not used in sync tests")
    }

    private fun fakeApi(ventas: List<VentaPage>, pagos: List<PagoPage>): V2CobranzaApi =
        RecordingFakeApi(ventas, pagos)

    private fun failingApi() = object : V2CobranzaApi {
        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse {
            fail("API should not be called when offline / unzoned")
            error("unreachable")
        }
        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse {
            fail("API should not be called when offline / unzoned")
            error("unreachable")
        }
        override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)
        override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)
        override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("not used in sync tests")
        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("not used in sync tests")
        override suspend fun pagosByIds(zonaId: Int, ids: String) = error("not used in sync tests")
        override suspend fun saldosByIds(zonaId: Int, ids: String) = error("not used in sync tests")
    }

    private fun samplePayment(doctoCcId: Int) = PaymentEntity(
        ID = "pmt-$doctoCcId",
        COBRADOR = "",
        DOCTO_CC_ACR_ID = doctoCcId,
        DOCTO_CC_ID = doctoCcId + 1,
        FECHA_HORA_PAGO = "2026-05-01T00:00:00Z",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 200.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 99,
        COBRADOR_ID = 0,
        FORMA_COBRO_ID = 1,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = ""
    )
}
