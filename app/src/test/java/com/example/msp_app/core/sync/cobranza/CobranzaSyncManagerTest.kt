package com.example.msp_app.core.sync.cobranza

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.toEntity
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

/** Cursor "ya avanzado" que simula un dispositivo con historial sincronizado. */
private const val CURSOR_VIEJO = "2020-01-01T00:00:00Z"
private const val CURSOR_PAGINA_1 = "2026-06-01T10:00:00Z"
private const val CURSOR_PAGINA_2 = "2026-06-01T11:00:00Z"

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
        productDao = db.productDao(),
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

    private fun pagoDto(impteId: Int, doctoCcId: Int, pagoRecibidoId: String? = null) = PagoDto(
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
        forma_cobro_id = null,
        pago_recibido_id = pagoRecibidoId
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
            productDao = db.productDao(),
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
    fun cambioDeZonaPreservaPagosPendientesNoSubidos() = runTest {
        // REGRESIÓN (pérdida de dinero): un cobrador registra pagos OFFLINE,
        // cambia de sesión/zona (o vuelve el internet en otra zona) y la
        // limpieza por cambio de zona NO debe borrar los pagos aún sin subir.
        // Antes, zonaChangeCleanupIfNeeded llamaba paymentDao.deleteAll() y
        // los eliminaba antes de que el worker los enviara.

        // Sync inicial en zona 21 — deja el state en zona 21.
        val apiZona21 = fakeApi(
            ventas = listOf(page(items = listOf(ventaDto(1101, zonaId = 21)), hasMore = false)),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(apiZona21, zona = 21).syncNow()

        // El cobrador registró un pago OFFLINE (pendiente, GUARDADO=0); además
        // hay un pago ya confirmado en cache (descargado, GUARDADO=1).
        val pendiente = samplePayment(1102).copy(
            ID = "offline-pendiente-1",
            GUARDADO_EN_MICROSIP = false
        )
        val yaSubido = samplePayment(1103).copy(
            ID = "ya-subido-1",
            GUARDADO_EN_MICROSIP = true
        )
        db.paymentDao().saveAll(listOf(pendiente, yaSubido))

        // Cambia a zona 42 → dispara la limpieza por cambio de zona.
        val apiZona42 = fakeApi(
            ventas = listOf(page(items = listOf(ventaDto(1104, zonaId = 42)), hasMore = false)),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(apiZona42, zona = 42).syncNow()

        // CRÍTICO: el pago pendiente sobrevive — jamás se pierde el trabajo
        // sin sincronizar del cobrador al cambiar de zona/usuario.
        val pendientes = db.paymentDao().getPendingPayments()
        assertEquals(1, pendientes.size)
        assertEquals("offline-pendiente-1", pendientes.first().ID)

        // El pago ya confirmado (GUARDADO=1) sí se descarta con el cache viejo.
        assertTrue(db.paymentDao().getPaymentsBySaleId(1103).isEmpty())
    }

    @Test
    fun cambioDeZonaPreservaPagosMultiCobrador() = runTest {
        // Extiende la regresión de pérdida de dinero a un escenario más
        // realista: varios cobradores comparten el mismo dispositivo/caché
        // local en distintos momentos (o hay pagos pendientes de más de un
        // cobrador acumulados por retrasos de red) y cada uno tiene su
        // propia atribución (COBRADOR_ID) y zona de origen. El cleanup por
        // cambio de zona NUNCA debe mezclar ni perder la atribución de
        // ninguno de ellos, sin importar cuántos cobradores distintos haya.

        // Sync inicial en zona 21 — deja el state en zona 21.
        val apiZona21 = fakeApi(
            ventas = listOf(page(items = listOf(ventaDto(1201, zonaId = 21)), hasMore = false)),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(apiZona21, zona = 21).syncNow()

        // Dos pagos pendientes de dos cobradores/zonas distintas, más uno
        // ya confirmado que sí debe descartarse con el cache viejo.
        val pendienteCobrador7 = samplePayment(1202).copy(
            ID = "pendiente-cobrador-7",
            GUARDADO_EN_MICROSIP = false,
            COBRADOR = "Ricardo Flores Mendoza",
            COBRADOR_ID = 7,
            ZONA_CLIENTE_ID = 21
        )
        val pendienteCobrador9 = samplePayment(1203).copy(
            ID = "pendiente-cobrador-9",
            GUARDADO_EN_MICROSIP = false,
            COBRADOR = "Sandra Patricia Gomez Ruiz",
            COBRADOR_ID = 9,
            ZONA_CLIENTE_ID = 30
        )
        val yaSubido = samplePayment(1204).copy(
            ID = "ya-subido-multi",
            GUARDADO_EN_MICROSIP = true,
            COBRADOR = "Otro Cobrador",
            COBRADOR_ID = 3,
            ZONA_CLIENTE_ID = 21
        )
        db.paymentDao().saveAll(listOf(pendienteCobrador7, pendienteCobrador9, yaSubido))

        // Cambia a zona 42 → dispara la limpieza por cambio de zona.
        val apiZona42 = fakeApi(
            ventas = listOf(page(items = listOf(ventaDto(1205, zonaId = 42)), hasMore = false)),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(apiZona42, zona = 42).syncNow()

        val pendientes = db.paymentDao().getPendingPayments()
        assertEquals(2, pendientes.size)
        val porId = pendientes.associateBy { it.ID }

        val cobrador7 = porId.getValue("pendiente-cobrador-7")
        assertEquals(7, cobrador7.COBRADOR_ID)
        assertEquals(21, cobrador7.ZONA_CLIENTE_ID)
        assertEquals("Ricardo Flores Mendoza", cobrador7.COBRADOR)

        val cobrador9 = porId.getValue("pendiente-cobrador-9")
        assertEquals(9, cobrador9.COBRADOR_ID)
        assertEquals(30, cobrador9.ZONA_CLIENTE_ID)
        assertEquals("Sandra Patricia Gomez Ruiz", cobrador9.COBRADOR)

        // El pago confirmado se descarta con el cache viejo, sin importar
        // que perteneciera a un tercer cobrador.
        assertTrue(porId["ya-subido-multi"] == null)
        assertTrue(db.paymentDao().getPaymentsBySaleId(1204).isEmpty())
    }

    @Test
    fun cambioDeZonaOfflineNoBorraPendientes() = runTest {
        // Si el cambio de zona se detecta mientras el dispositivo está
        // offline, el guard de conectividad debe cortar ANTES de llegar a
        // zonaChangeCleanupIfNeeded. Probamos que el cleanup jamás corrió
        // dejando los pendientes (de dos cobradores distintos) intactos.

        // Deja el state apuntando a zona 21 (sync previo, ya persistido).
        val apiZona21 = fakeApi(
            ventas = listOf(page(items = listOf(ventaDto(1301, zonaId = 21)), hasMore = false)),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(apiZona21, zona = 21).syncNow()

        val pendienteA = samplePayment(1302).copy(
            ID = "offline-pendiente-A",
            GUARDADO_EN_MICROSIP = false,
            COBRADOR = "Carlos Ivan Mendez Soto",
            COBRADOR_ID = 11,
            ZONA_CLIENTE_ID = 21
        )
        val pendienteB = samplePayment(1303).copy(
            ID = "offline-pendiente-B",
            GUARDADO_EN_MICROSIP = false,
            COBRADOR = "Laura Beatriz Cruz Jimenez",
            COBRADOR_ID = 15,
            ZONA_CLIENTE_ID = 30
        )
        db.paymentDao().saveAll(listOf(pendienteA, pendienteB))

        // Ahora el cobrador aparece en zona 42 pero SIN conectividad — el
        // manager debe salir por SkippedOffline antes del cleanup.
        val mgrOffline = newManager(failingApi(), online = false, zona = 42)
        val outcome = mgrOffline.syncNow()

        assertTrue(outcome is SyncOutcome.SkippedOffline)

        val pendientes = db.paymentDao().getPendingPayments()
        assertEquals(2, pendientes.size)
        val porId = pendientes.associateBy { it.ID }

        val a = porId.getValue("offline-pendiente-A")
        assertEquals(11, a.COBRADOR_ID)
        assertEquals(21, a.ZONA_CLIENTE_ID)
        assertEquals("Carlos Ivan Mendez Soto", a.COBRADOR)

        val b = porId.getValue("offline-pendiente-B")
        assertEquals(15, b.COBRADOR_ID)
        assertEquals(30, b.ZONA_CLIENTE_ID)
        assertEquals("Laura Beatriz Cruz Jimenez", b.COBRADOR)

        // El state de sync sigue apuntando a la zona vieja: el cleanup
        // nunca se disparó (no hubo limpieza ni de ventas ni de state).
        assertEquals(
            21,
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
            com.example.msp_app.core.database.entities.CobranzaSyncStateEntity(
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

    // ─── Colapso de gemelos UUID vía pago_recibido_id ───────────────────────

    /**
     * Bug: un pago cobrado offline se guarda con ID=UUID local. Al subirse a
     * Microsip queda también en el cursor de sync con su ID numérico real
     * (IMPTE_DOCTO_CC_ID). Sin colapso, ambas filas coexisten y "Historial de
     * pagos" muestra el mismo pago dos veces. Contrato nuevo: cuando el pago
     * numérico entrante trae `pago_recibido_id` == el UUID local, el merge
     * debe borrar la fila UUID y dejar solo la numérica.
     */
    @Test
    fun mergePagosColapsaGemeloUuidCuandoPagoRecibidoIdCoincide() = runTest {
        val uuidLocal = "b6e6b7b0-1c2d-4a3e-9f0a-2f7a2e6b9c11"
        val seedUuid = samplePayment(1401).copy(
            ID = uuidLocal,
            GUARDADO_EN_MICROSIP = true
        )
        db.paymentDao().saveAll(listOf(seedUuid))

        val numericoDto = pagoDto(impteId = 88001, doctoCcId = 1401, pagoRecibidoId = uuidLocal)
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(numericoDto), hasMore = false))
        )
        newManager(api).syncNow()

        val pagos = db.paymentDao().getPaymentsBySaleId(1401)
        assertEquals("solo debe quedar la fila numérica", 1, pagos.size)
        assertEquals("88001", pagos.first().ID)
    }

    /**
     * Bug del 2026-08-13: tras la migración al API Go el cobrador vio todos
     * sus números exactamente al doble.
     *
     * El histórico NO trae `pago_recibido_id` — el Node nunca escribió
     * `MSP_PAGOS_RECIBIDOS.IMPTE_DOCTO_CC_ID`, que es por donde el backend Go
     * resuelve ese campo. Así que el colapso por UUID no alcanza al histórico
     * y el gemelo hay que ubicarlo por `docto_cc_id`, el documento de pago,
     * que sí es el mismo en ambos canales.
     *
     * Esta prueba cubre el caso mayoritario: el pago se capturó desde la app
     * y el sync legacy lo guardó con el UUID de la captura.
     */
    @Test
    fun mergePagosColapsaGemeloLegacyUuidSinPagoRecibidoId() = runTest {
        val uuidLegacy = "a1a1a1a1-2222-3333-4444-555555555555"
        val seedUuid = samplePayment(1402).copy(
            ID = uuidLegacy,
            GUARDADO_EN_MICROSIP = true
        )
        db.paymentDao().saveAll(listOf(seedUuid))

        val numericoDto = pagoDto(impteId = 88002, doctoCcId = 1402, pagoRecibidoId = null)
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(numericoDto), hasMore = false))
        )
        newManager(api).syncNow()

        val pagos = db.paymentDao().getPaymentsBySaleId(1402)
        assertEquals("solo debe quedar la fila numérica", 1, pagos.size)
        assertEquals("88002", pagos.first().ID)
    }

    /**
     * El otro formato legacy: los pagos capturados en oficina no tienen fila
     * en `MSP_PAGOS_RECIBIDOS`, así que el Node los devolvía con el ID
     * compuesto `"<DOCTO_CC_ID>-<IMPTE_DOCTO_CC_ID>"`. Mismo colapso.
     */
    @Test
    fun mergePagosColapsaGemeloLegacyCompuesto() = runTest {
        // samplePayment(1405) y el DTO de abajo comparten docto_cc_id = 1406.
        val seedCompuesto = samplePayment(1405).copy(
            ID = "1406-88005",
            GUARDADO_EN_MICROSIP = true
        )
        db.paymentDao().saveAll(listOf(seedCompuesto))

        val numericoDto = pagoDto(impteId = 88005, doctoCcId = 1405, pagoRecibidoId = null)
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(numericoDto), hasMore = false))
        )
        newManager(api).syncNow()

        val pagos = db.paymentDao().getPaymentsBySaleId(1405)
        assertEquals("solo debe quedar la fila numérica", 1, pagos.size)
        assertEquals("88005", pagos.first().ID)
    }

    /**
     * El colapso es por documento de pago exacto, no por parecido: una fila
     * legacy de OTRO `docto_cc_id` no se toca aunque baje en el mismo lote.
     */
    @Test
    fun mergePagosNoBorraFilaLegacyDeOtroDocumento() = runTest {
        val uuidAjeno = "e5e5e5e5-6666-7777-8888-999999999999"
        val seedAjeno = samplePayment(1407).copy(
            ID = uuidAjeno,
            GUARDADO_EN_MICROSIP = true
        )
        db.paymentDao().saveAll(listOf(seedAjeno))

        val numericoDto = pagoDto(impteId = 88006, doctoCcId = 1408, pagoRecibidoId = null)
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(numericoDto), hasMore = false))
        )
        newManager(api).syncNow()

        val pagos = db.paymentDao().getPaymentsBySaleId(1407)
        assertEquals("la fila legacy de otro documento sobrevive", 1, pagos.size)
        assertEquals(uuidAjeno, pagos.first().ID)
    }

    /**
     * Defensa: un pago PENDIENTE (GUARDADO_EN_MICROSIP=0, aún sin subir)
     * jamás debe borrarse, aunque — de una forma que nunca debería pasar —
     * un pago entrante trajera su mismo ID como pago_recibido_id. El server
     * solo conoce el UUID de un pago que ya subió (GUARDADO=1); un
     * pendiente nunca pudo haber llegado al server. Se prueba de todos
     * modos como red de seguridad.
     */
    @Test
    fun mergePagosNuncaBorraFilaUuidPendienteAunSiVieneReferenciada() = runTest {
        val uuidPendiente = "c3c3c3c3-4444-5555-6666-777777777777"
        val seedPendiente = samplePayment(1403).copy(
            ID = uuidPendiente,
            GUARDADO_EN_MICROSIP = false
        )
        db.paymentDao().saveAll(listOf(seedPendiente))

        val numericoDto = pagoDto(impteId = 88003, doctoCcId = 1403, pagoRecibidoId = uuidPendiente)
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(numericoDto), hasMore = false))
        )
        newManager(api).syncNow()

        val pendientes = db.paymentDao().getPendingPayments()
        assertTrue(
            "la fila pendiente jamás se borra, sin importar pago_recibido_id",
            pendientes.any { it.ID == uuidPendiente }
        )
    }

    /**
     * Caso mínimo: un pago pendiente de OTRO pago (no referenciado por
     * ningún pago_recibido_id entrante) sobrevive un merge normal sin
     * cambios.
     */
    @Test
    fun mergePagosPendienteNoReferenciadoSobreviveMergeNormal() = runTest {
        val uuidPendiente = "d4d4d4d4-5555-6666-7777-888888888888"
        val seedPendiente = samplePayment(1404).copy(
            ID = uuidPendiente,
            GUARDADO_EN_MICROSIP = false
        )
        db.paymentDao().saveAll(listOf(seedPendiente))

        val otroDto = pagoDto(impteId = 88004, doctoCcId = 9999)
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(items = listOf(otroDto), hasMore = false))
        )
        newManager(api).syncNow()

        val pendientes = db.paymentDao().getPendingPayments()
        assertTrue(pendientes.any { it.ID == uuidPendiente })
    }

    // ─── Migración one-time: resync completo de pagos (pago_recibido_id) ───

    /**
     * Antes de esta migración, dispositivos con pagos ya sincronizados
     * tienen un cursor de pagos avanzado que jamás volvería a traer pagos
     * viejos (el server no los re-manda porque su UPDATED_AT no cambió).
     * Sin un resync completo, los gemelos UUID ya guardados en el
     * dispositivo NUNCA se colapsarían porque el pago numérico
     * correspondiente no se vuelve a bajar. La migración limpia el cursor
     * de pagos UNA SOLA VEZ (marcada por una fila en cobranza_sync_state)
     * para forzar un replay completo que traiga pago_recibido_id en cada
     * fila y dispare el colapso.
     */
    @Test
    fun migracionPagoRecibidoIdFuerzaResyncCompletoUnaSolaVez() = runTest {
        // Simula un dispositivo pre-migración: cursor de pagos ya avanzado.
        db.cobranzaSyncStateDao().upsert(
            com.example.msp_app.core.database.entities.CobranzaSyncStateEntity(
                RESOURCE = CobranzaSyncManager.RESOURCE_PAGOS,
                ZONA_CLIENTE_ID = 21,
                CURSOR = "2020-01-01T00:00:00Z",
                LAST_SYNCED_AT = "2020-01-01T00:00:00Z",
                LAST_ERROR = null
            )
        )

        val api = RecordingFakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        // El primer sync post-migración DEBE ignorar el cursor viejo (lo pasa
        // como null), forzando el replay completo desde el inicio.
        assertEquals(listOf(null), api.pagosCursorCalls)

        // La migración quedó marcada — no debe volver a limpiarse en runs futuros.
        assertNotNull(
            db.cobranzaSyncStateDao().get(CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID)
        )
    }

    @Test
    fun migracionPagoRecibidoIdNoSeRepiteEnSyncsPosteriores() = runTest {
        db.cobranzaSyncStateDao().upsert(
            com.example.msp_app.core.database.entities.CobranzaSyncStateEntity(
                RESOURCE = CobranzaSyncManager.RESOURCE_PAGOS,
                ZONA_CLIENTE_ID = 21,
                CURSOR = "2020-01-01T00:00:00Z",
                LAST_SYNCED_AT = "2020-01-01T00:00:00Z",
                LAST_ERROR = null
            )
        )

        val api = RecordingFakeApi(
            ventas = listOf(pagoPageEmpty(), pagoPageEmpty()),
            pagos = listOf(
                pagoPage(items = listOf(pagoDto(9101, 9100)), hasMore = false),
                pagoPage(emptyList(), hasMore = false)
            )
        )
        val mgr = newManager(api)
        mgr.syncNow()
        mgr.syncNow()

        // Primera llamada: cursor viejo descartado (null forzado por la
        // migración). Segunda llamada: cursor real ya persistido — la
        // migración NO se repite.
        assertEquals(
            listOf(null, "2026-05-30T18:25:13.456789Z"),
            api.pagosCursorCalls
        )
    }

    // ─── Segundo replay one-time: pago_recibido_id ya persistido ───────────

    /**
     * Dispositivo que ya corrió el build af750fe: el marcador viejo
     * ([CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID]) ya existe, pero ese
     * build nunca escribía `PAGO_RECIBIDO_ID` en la fila numérica (la
     * columna llegó recién en este fix). El marcador nuevo
     * ([MIGRATION_PAGO_RECIBIDO_ID_PERSIST]) está ausente, así que el
     * segundo replay debe correr — el cursor de pagos se limpia una vez más
     * aunque el viejo marcador ya estuviera puesto.
     */
    @Test
    fun migracionPagoRecibidoIdPersistFuerzaResyncCuandoMarcadorViejoYaConsumido() = runTest {
        db.cobranzaSyncStateDao().upsert(
            com.example.msp_app.core.database.entities.CobranzaSyncStateEntity(
                RESOURCE = CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID,
                ZONA_CLIENTE_ID = 0,
                CURSOR = null,
                LAST_SYNCED_AT = "2026-01-01T00:00:00Z",
                LAST_ERROR = null
            )
        )
        db.cobranzaSyncStateDao().upsert(
            com.example.msp_app.core.database.entities.CobranzaSyncStateEntity(
                RESOURCE = CobranzaSyncManager.RESOURCE_PAGOS,
                ZONA_CLIENTE_ID = 21,
                CURSOR = "2020-01-01T00:00:00Z",
                LAST_SYNCED_AT = "2020-01-01T00:00:00Z",
                LAST_ERROR = null
            )
        )

        val api = RecordingFakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        // El nuevo marcador fuerza el cursor a null aunque el viejo ya existiera.
        assertEquals(listOf(null), api.pagosCursorCalls)
        assertNotNull(
            db.cobranzaSyncStateDao().get(CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID_PERSIST)
        )
    }

    /**
     * Una vez que el segundo replay corrió (marcador nuevo puesto), los
     * syncs posteriores usan el cursor real persistido — no se vuelve a
     * forzar null.
     */
    @Test
    fun migracionPagoRecibidoIdPersistNoSeRepiteEnSyncsPosteriores() = runTest {
        db.cobranzaSyncStateDao().upsert(
            com.example.msp_app.core.database.entities.CobranzaSyncStateEntity(
                RESOURCE = CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID,
                ZONA_CLIENTE_ID = 0,
                CURSOR = null,
                LAST_SYNCED_AT = "2026-01-01T00:00:00Z",
                LAST_ERROR = null
            )
        )
        db.cobranzaSyncStateDao().upsert(
            com.example.msp_app.core.database.entities.CobranzaSyncStateEntity(
                RESOURCE = CobranzaSyncManager.RESOURCE_PAGOS,
                ZONA_CLIENTE_ID = 21,
                CURSOR = "2020-01-01T00:00:00Z",
                LAST_SYNCED_AT = "2020-01-01T00:00:00Z",
                LAST_ERROR = null
            )
        )

        val api = RecordingFakeApi(
            ventas = listOf(pagoPageEmpty(), pagoPageEmpty()),
            pagos = listOf(
                pagoPage(items = listOf(pagoDto(9201, 9200)), hasMore = false),
                pagoPage(emptyList(), hasMore = false)
            )
        )
        val mgr = newManager(api)
        mgr.syncNow()
        mgr.syncNow()

        // Primera llamada: cursor forzado a null por el segundo replay.
        // Segunda llamada: cursor real ya persistido — no se vuelve a forzar.
        assertEquals(
            listOf(null, "2026-05-30T18:25:13.456789Z"),
            api.pagosCursorCalls
        )
    }

    /**
     * Instalación nueva: ni el marcador viejo ni el nuevo existen. Ambas
     * migraciones one-time corren en el mismo primer `syncNow()` y quedan
     * marcadas — sin duplicar el costo real (solo el cursor se limpia,
     * ambas veces sobre un cursor ya null).
     */
    @Test
    fun freshInstallCorreAmbasMigracionesUnaVez() = runTest {
        val api = RecordingFakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        assertNotNull(db.cobranzaSyncStateDao().get(CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID))
        assertNotNull(
            db.cobranzaSyncStateDao().get(CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID_PERSIST)
        )
        assertEquals(listOf(null), api.pagosCursorCalls)
    }

    // ─── Purga one-time del histórico duplicado por el cutover ─────────────

    /**
     * El histórico que ya está en Room nunca vuelve a bajar por el sync
     * incremental (su `UPDATED_AT` no cambió), así que el colapso de
     * [mergePagos] no lo alcanza. La purga one-time lo limpia — pero solo
     * donde el gemelo numérico ya existe en local: un pago legacy sin gemelo
     * es un pago viejo real que el canal v2 no va a reponer, y borrarlo
     * desplomaría los totales históricos del cobrador.
     */
    @Test
    fun purgaBorraSoloElHistoricoLegacyQueYaTieneGemeloNumerico() = runTest {
        val duplicado = samplePayment(2001).copy(
            ID = "f6f6f6f6-1111-2222-3333-444444444444",
            GUARDADO_EN_MICROSIP = true
        )
        val gemeloNumerico = samplePayment(2001).copy(
            ID = "77001",
            GUARDADO_EN_MICROSIP = true
        )
        // Mismo formato legacy, pero sin gemelo: histórico fuera de ventana.
        val sinGemelo = samplePayment(2002).copy(
            ID = "2003-77002",
            GUARDADO_EN_MICROSIP = true
        )
        db.paymentDao().saveAll(listOf(duplicado, gemeloNumerico, sinGemelo))

        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        val conGemelo = db.paymentDao().getPaymentsBySaleId(2001)
        assertEquals("el duplicado se purga", 1, conGemelo.size)
        assertEquals("77001", conGemelo.first().ID)

        assertEquals(
            "el histórico sin gemelo sobrevive — no hay quién lo reponga",
            1,
            db.paymentDao().getPaymentsBySaleId(2002).size
        )
    }

    /**
     * La prueba que impide perder dinero del cobrador: una captura local aún
     * sin subir (`GUARDADO_EN_MICROSIP = 0`) jamás se toca, ni siquiera si
     * comparte documento de pago con una fila numérica.
     */
    @Test
    fun purgaNuncaBorraUnaCapturaPendienteDeSubir() = runTest {
        val pendiente = samplePayment(2010).copy(
            ID = "aa11bb22-3333-4444-5555-666677778888",
            GUARDADO_EN_MICROSIP = false
        )
        val numerico = samplePayment(2010).copy(
            ID = "77010",
            GUARDADO_EN_MICROSIP = true
        )
        db.paymentDao().saveAll(listOf(pendiente, numerico))

        val api = fakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        assertTrue(
            "la captura pendiente sobrevive a la purga",
            db.paymentDao().getPendingPayments().any { it.ID == pendiente.ID }
        )
    }

    /**
     * Corre una sola vez: al segundo arranque el marcador ya existe y una
     * fila legacy nueva (por ejemplo, restaurada de un respaldo) no se
     * vuelve a borrar sin pasar por el merge.
     */
    @Test
    fun purgaCorreUnaSolaVez() = runTest {
        val api = fakeApi(
            ventas = listOf(pagoPageEmpty(), pagoPageEmpty()),
            pagos = listOf(
                pagoPage(emptyList(), hasMore = false),
                pagoPage(emptyList(), hasMore = false)
            )
        )
        val mgr = newManager(api)
        mgr.syncNow()

        assertNotNull(
            db.cobranzaSyncStateDao().get(CobranzaSyncManager.MIGRATION_PURGE_LEGACY_PAGO_IDS)
        )

        // Llega tarde, después de que la purga ya corrió.
        val tardio = samplePayment(2020).copy(
            ID = "bb22cc33-4444-5555-6666-777788889999",
            GUARDADO_EN_MICROSIP = true
        )
        val numerico = samplePayment(2020).copy(ID = "77020", GUARDADO_EN_MICROSIP = true)
        db.paymentDao().saveAll(listOf(tardio, numerico))

        mgr.syncNow()

        assertEquals(
            "la purga no se repite en arranques posteriores",
            2,
            db.paymentDao().getPaymentsBySaleId(2020).size
        )
    }

    // ─── Resync por generación (sync_epoch) ────────────────────────────────

    /**
     * El caso que motiva todo el mecanismo: el servidor cambia lo que proyecta
     * (p.ej. las coordenadas de los pagos pasan a salir de otra tabla) y sube
     * su generación. Las filas ya guardadas no volverían a bajar nunca — su
     * `UPDATED_AT` no cambió y el cursor ya las dejó atrás. Al ver la
     * generación distinta, el cliente descarta el cursor y replica completo.
     */
    @Test
    fun epochNuevoLimpiaElCursorYReplicaDesdeElInicio() = runTest {
        seedSyncState(CobranzaSyncManager.RESOURCE_VENTAS, cursor = CURSOR_VIEJO, epoch = 7)

        val api = RecordingFakeApi(
            ventas = listOf(
                // Primera respuesta: llega con la generación nueva y dispara el
                // replay; esta página ni se aplica.
                VentaPage(items = listOf(ventaDto(3101)), hasMore = false, epoch = 8),
                VentaPage(items = listOf(ventaDto(3101)), hasMore = false, epoch = 8)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val outcome = newManager(api).syncNow()

        assertTrue(outcome is SyncOutcome.Ok)
        assertEquals(
            "la segunda llamada debe ir sin cursor: replay desde el inicio",
            listOf(CURSOR_VIEJO, null),
            api.ventasCursorCalls
        )
        val state = db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!
        assertEquals("terminado el replay, la generación queda aplicada", 8, state.EPOCH)
        assertEquals("2026-05-30T18:25:13.456789Z", state.CURSOR)
        assertNotNull(db.saleDao().findByDoctoCcId(3101))
    }

    /**
     * Misma generación = nada que rehacer. Si replicara, el fake se quedaría
     * sin páginas y el sync terminaría en Error — por eso se afirma el Ok.
     */
    @Test
    fun epochIgualNoReplica() = runTest {
        seedSyncState(CobranzaSyncManager.RESOURCE_VENTAS, cursor = CURSOR_VIEJO, epoch = 8)

        val api = RecordingFakeApi(
            ventas = listOf(
                VentaPage(items = listOf(ventaDto(3102)), hasMore = false, epoch = 8)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val outcome = newManager(api).syncNow()

        assertTrue(outcome is SyncOutcome.Ok)
        assertEquals(
            "una sola llamada, con el cursor incremental de siempre",
            listOf(CURSOR_VIEJO),
            api.ventasCursorCalls
        )
        assertEquals(8, db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!.EPOCH)
    }

    /**
     * EL invariante del diseño: la generación se persiste solo cuando el
     * replay TERMINÓ. Si el proceso muere a media descarga (aquí, la red se
     * cae en la segunda página), la generación guardada sigue siendo la vieja
     * y el próximo arranque vuelve a replicar desde cero. Equivocarse por este
     * lado cuesta ancho de banda; por el otro dejaría el replay a la mitad
     * para siempre — el defecto que los marcadores `MIGRATION_*` documentan de
     * sí mismos y que este mecanismo viene a cerrar.
     */
    @Test
    fun epochNoSePersisteHastaQueElReplayTermina() = runTest {
        seedSyncState(CobranzaSyncManager.RESOURCE_VENTAS, cursor = CURSOR_VIEJO, epoch = 7)

        val apiInterrumpido = RecordingFakeApi(
            ventas = listOf(
                VentaPage(items = emptyList(), hasMore = false, epoch = 8),
                VentaPage(
                    items = listOf(ventaDto(3103, updatedAt = CURSOR_PAGINA_1)),
                    hasMore = true,
                    epoch = 8
                ),
                VentaPage(items = emptyList(), hasMore = false, epoch = 8, fails = true)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val interrumpido = newManager(apiInterrumpido).syncNow()

        assertTrue(interrumpido is SyncOutcome.Error)
        val aMedias = db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!
        assertEquals("la generación nueva NO se persiste a medio replay", 7, aMedias.EPOCH)
        assertEquals(CURSOR_PAGINA_1, aMedias.CURSOR)

        // Siguiente arranque: las generaciones siguen difiriendo, así que
        // replica otra vez desde el inicio en lugar de quedarse a medias.
        val apiReintento = RecordingFakeApi(
            ventas = listOf(
                VentaPage(items = emptyList(), hasMore = false, epoch = 8),
                VentaPage(
                    items = listOf(ventaDto(3104, updatedAt = CURSOR_PAGINA_2)),
                    hasMore = false,
                    epoch = 8
                )
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(apiReintento).syncNow()

        assertEquals(
            "el reintento vuelve a arrancar sin cursor",
            listOf(CURSOR_PAGINA_1, null),
            apiReintento.ventasCursorCalls
        )
        val completo = db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!
        assertEquals("ahora sí, replay completo → generación aplicada", 8, completo.EPOCH)
        assertNotNull(db.saleDao().findByDoctoCcId(3104))
    }

    /**
     * Servidor viejo (sin el campo `sync_epoch`): la app se comporta
     * exactamente como antes — no replica, no truena, y sobre todo no pisa la
     * generación que ya tenía aplicada. Si la borrara, un rollback temporal
     * del servidor forzaría un replay extra al volver a subirlo.
     */
    @Test
    fun servidorSinEpochNoReplicaNiPierdeLaGeneracionAplicada() = runTest {
        seedSyncState(CobranzaSyncManager.RESOURCE_VENTAS, cursor = CURSOR_VIEJO, epoch = 8)

        val api = RecordingFakeApi(
            ventas = listOf(
                page(
                    items = listOf(ventaDto(3105, updatedAt = CURSOR_PAGINA_1)),
                    hasMore = false
                ),
                page(
                    items = listOf(ventaDto(3106, updatedAt = CURSOR_PAGINA_2)),
                    hasMore = false
                )
            ),
            pagos = listOf(
                pagoPage(emptyList(), hasMore = false),
                pagoPage(emptyList(), hasMore = false)
            )
        )
        val mgr = newManager(api)
        mgr.syncNow()
        mgr.syncNow()

        assertEquals(
            "dos syncs, dos llamadas incrementales: ni un replay en bucle",
            listOf(CURSOR_VIEJO, CURSOR_PAGINA_1),
            api.ventasCursorCalls
        )
        assertEquals(8, db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!.EPOCH)
    }

    /**
     * Un 0 no es una generación: es lo que produce cualquier ruta que caiga en
     * el default de un entero (Gson sobre un `Int` no nulo, un DTO intermedio,
     * un servidor a medio configurar). Tratarlo como generación válida metería
     * al cliente en un replay por tick, para siempre. Se ignora igual que un
     * campo ausente, y la generación aplicada se conserva.
     */
    @Test
    fun epochCeroSeIgnoraYNoEntraEnBucleDeReplays() = runTest {
        seedSyncState(CobranzaSyncManager.RESOURCE_VENTAS, cursor = CURSOR_VIEJO, epoch = 8)

        val api = RecordingFakeApi(
            ventas = listOf(
                VentaPage(
                    items = listOf(ventaDto(3107, updatedAt = CURSOR_PAGINA_1)),
                    hasMore = false,
                    epoch = 0
                ),
                VentaPage(
                    items = listOf(ventaDto(3108, updatedAt = CURSOR_PAGINA_2)),
                    hasMore = false,
                    epoch = 0
                )
            ),
            pagos = listOf(
                pagoPage(emptyList(), hasMore = false),
                pagoPage(emptyList(), hasMore = false)
            )
        )
        val mgr = newManager(api)
        mgr.syncNow()
        mgr.syncNow()

        assertEquals(
            "ninguna llamada arranca sin cursor: el 0 nunca dispara replay",
            listOf(CURSOR_VIEJO, CURSOR_PAGINA_1),
            api.ventasCursorCalls
        )
        assertEquals(8, db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!.EPOCH)
    }

    /**
     * La generación es identidad, no orden: si el servidor RETROCEDE (restore
     * de un respaldo, redeploy con el contador reiniciado) sigue siendo una
     * generación distinta a la aplicada, así que se replica UNA vez y se
     * guarda el valor nuevo. El segundo sync ya no replica — sin esto, un
     * epoch menor replicaría en cada tick indefinidamente.
     */
    @Test
    fun epochQueRetrocedeReplicaUnaSolaVez() = runTest {
        seedSyncState(CobranzaSyncManager.RESOURCE_VENTAS, cursor = CURSOR_VIEJO, epoch = 9)

        val api = RecordingFakeApi(
            ventas = listOf(
                VentaPage(items = emptyList(), hasMore = false, epoch = 3),
                VentaPage(
                    items = listOf(ventaDto(3109, updatedAt = CURSOR_PAGINA_1)),
                    hasMore = false,
                    epoch = 3
                ),
                VentaPage(
                    items = listOf(ventaDto(3110, updatedAt = CURSOR_PAGINA_2)),
                    hasMore = false,
                    epoch = 3
                )
            ),
            pagos = listOf(
                pagoPage(emptyList(), hasMore = false),
                pagoPage(emptyList(), hasMore = false)
            )
        )
        val mgr = newManager(api)
        mgr.syncNow()
        mgr.syncNow()

        assertEquals(
            "un solo replay: el segundo sync ya usa el cursor incremental",
            listOf(CURSOR_VIEJO, null, CURSOR_PAGINA_1),
            api.ventasCursorCalls
        )
        assertEquals(3, db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!.EPOCH)
    }

    /**
     * El servidor sube de generación MIENTRAS descargamos: lo aplicado es una
     * mezcla de dos generaciones, así que no se persiste ninguna y el próximo
     * arranque replica desde cero. Conservador a propósito: el costo es red,
     * la alternativa es un cache mezclado dado por bueno para siempre.
     */
    @Test
    fun epochQueCambiaAMediaPaginacionNoSePersiste() = runTest {
        val api = RecordingFakeApi(
            ventas = listOf(
                VentaPage(
                    items = listOf(ventaDto(3111, updatedAt = CURSOR_PAGINA_1)),
                    hasMore = true,
                    epoch = 5
                ),
                VentaPage(
                    items = listOf(ventaDto(3112, updatedAt = CURSOR_PAGINA_2)),
                    hasMore = false,
                    epoch = 6
                )
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        val state = db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!
        assertNull("generación inestable durante la corrida: no se aplica ninguna", state.EPOCH)
        // Los datos sí se guardaron; lo único que queda pendiente es la
        // confirmación de la generación.
        assertNotNull(db.saleDao().findByDoctoCcId(3111))
        assertNotNull(db.saleDao().findByDoctoCcId(3112))
    }

    /**
     * El mecanismo es por recurso: pagos tiene su propia generación y su
     * propio replay. Se siembran los marcadores one-time viejos para que no
     * sean ellos los que limpien el cursor de pagos y el test mida solo el
     * efecto de la generación.
     */
    @Test
    fun epochNuevoTambienReplicaElRecursoDePagos() = runTest {
        seedMigrationMarkers()
        seedSyncState(CobranzaSyncManager.RESOURCE_PAGOS, cursor = CURSOR_VIEJO, epoch = 7)

        val api = RecordingFakeApi(
            ventas = listOf(pagoPageEmpty()),
            pagos = listOf(
                PagoPage(items = emptyList(), hasMore = false, epoch = 8),
                PagoPage(items = listOf(pagoDto(9301, 9300)), hasMore = false, epoch = 8)
            )
        )
        newManager(api).syncNow()

        assertEquals(listOf(CURSOR_VIEJO, null), api.pagosCursorCalls)
        assertEquals(8, db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_PAGOS)!!.EPOCH)
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(9300).size)
    }

    /**
     * Un dispositivo que venía de la versión anterior tiene EPOCH en NULL (la
     * migración 27→28 no inventa valores). NULL es "nunca aplicó una
     * generación", distinto de cualquier generación real, así que hace UN
     * replay al actualizar y queda alineado — no uno por tick.
     */
    @Test
    fun dispositivoConEpochNuloReplicaUnaVezAlActualizar() = runTest {
        seedSyncState(CobranzaSyncManager.RESOURCE_VENTAS, cursor = CURSOR_VIEJO, epoch = null)

        val api = RecordingFakeApi(
            ventas = listOf(
                VentaPage(items = emptyList(), hasMore = false, epoch = 4),
                VentaPage(
                    items = listOf(ventaDto(3113, updatedAt = CURSOR_PAGINA_1)),
                    hasMore = false,
                    epoch = 4
                ),
                VentaPage(
                    items = listOf(ventaDto(3114, updatedAt = CURSOR_PAGINA_2)),
                    hasMore = false,
                    epoch = 4
                )
            ),
            pagos = listOf(
                pagoPage(emptyList(), hasMore = false),
                pagoPage(emptyList(), hasMore = false)
            )
        )
        val mgr = newManager(api)
        mgr.syncNow()
        mgr.syncNow()

        assertEquals(
            listOf(CURSOR_VIEJO, null, CURSOR_PAGINA_1),
            api.ventasCursorCalls
        )
        assertEquals(4, db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!.EPOCH)
    }

    // ─── Cargo cancelado: el pago del cobrador nunca se pierde ─────────────

    /**
     * LA prueba que impide perder dinero en la cancelación de un cargo: el
     * cobrador captura un pago en la calle y ese mismo día en oficina cancelan
     * el cargo. El merge borra la venta y el cache de pagos ya confirmados,
     * pero la captura pendiente se queda. Después fallará al subirse contra un
     * cargo cancelado y quedará en la captura de intentos fallidos del
     * servidor, que se resuelve desde el escritorio. Borrarla aquí la
     * desaparecería sin que nadie pudiera saber que existió.
     */
    @Test
    fun tombstoneDeCargoCanceladoPreservaLaCapturaPendiente() = runTest {
        db.saleDao().insertAll(listOf(ventaDto(3201).toEntity()))
        val pendiente = samplePayment(3201).copy(
            ID = "5c9a4e18-2b7d-4f36-9a10-8e4c2d71b053",
            GUARDADO_EN_MICROSIP = false,
            IMPORTE = 480.50,
            COBRADOR = "Ricardo Flores Mendoza",
            COBRADOR_ID = 7
        )
        val confirmado = samplePayment(3201).copy(ID = "77201", GUARDADO_EN_MICROSIP = true)
        db.paymentDao().saveAll(listOf(pendiente, confirmado))

        val api = fakeApi(
            ventas = listOf(
                page(items = listOf(ventaDto(3201, cancelado = true)), hasMore = false)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        assertNull("la venta cancelada sí se va", db.saleDao().findByDoctoCcId(3201))
        val restantes = db.paymentDao().getPaymentsBySaleId(3201)
        assertEquals("solo sobrevive la captura pendiente", 1, restantes.size)
        val superviviente = restantes.single()
        assertEquals(pendiente.ID, superviviente.ID)
        assertEquals(480.50, superviviente.IMPORTE, 0.0)
        assertEquals("Ricardo Flores Mendoza", superviviente.COBRADOR)
        assertTrue(
            "y sigue en la cola de pendientes por subir",
            db.paymentDao().getPendingPayments().any { it.ID == pendiente.ID }
        )
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

    /**
     * `epoch` es el `sync_epoch` que el servidor manda en esa página (null =
     * servidor viejo). `fails` simula que la descarga muere en esa página —
     * la única forma de probar que el epoch NO se persiste a medio replay.
     */
    private data class VentaPage(
        val items: List<VentaDto>,
        val hasMore: Boolean,
        val epoch: Int? = null,
        val fails: Boolean = false
    )

    private data class PagoPage(
        val items: List<PagoDto>,
        val hasMore: Boolean,
        val epoch: Int? = null,
        val fails: Boolean = false
    )

    /**
     * Deja una fila de `cobranza_sync_state` como la tendría un dispositivo en
     * campo: cursor ya avanzado y una generación aplicada (o NULL, que es lo
     * que hereda de la migración 27→28).
     */
    private suspend fun seedSyncState(resource: String, cursor: String?, epoch: Int?) {
        db.cobranzaSyncStateDao().upsert(
            CobranzaSyncStateEntity(
                RESOURCE = resource,
                ZONA_CLIENTE_ID = 21,
                CURSOR = cursor,
                LAST_SYNCED_AT = "2026-08-01T00:00:00Z",
                LAST_ERROR = null,
                EPOCH = epoch
            )
        )
    }

    /**
     * Marca como ya consumidas las tres migraciones one-time del mecanismo
     * viejo, para que no sean ellas las que limpien el cursor de pagos cuando
     * lo que se está midiendo es el resync por generación.
     */
    private suspend fun seedMigrationMarkers() {
        listOf(
            CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID,
            CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID_PERSIST,
            CobranzaSyncManager.MIGRATION_PURGE_LEGACY_PAGO_IDS
        ).forEach { marker -> seedSyncState(marker, cursor = null, epoch = null) }
    }

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
        val pagosCursorCalls = mutableListOf<String?>()
        val ventasCursorCalls = mutableListOf<String?>()

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse {
            ventasDesdeCalls.add(desde)
            ventasCursorCalls.add(cursor)
            val p = ventas.getOrNull(ventasIdx) ?: error("syncVentas called too many times")
            ventasIdx++
            if (p.fails) throw RuntimeException("network down a media paginación")
            return SyncVentasResponse(
                items = p.items,
                max_updated_at = p.items.lastOrNull()?.updated_at ?: cursor.orEmpty(),
                server_now = "2026-05-30T18:25:23Z",
                has_more = p.hasMore,
                sync_epoch = p.epoch
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
            pagosCursorCalls.add(cursor)
            val p = pagos.getOrNull(pagosIdx) ?: error("syncPagos called too many times")
            pagosIdx++
            if (p.fails) throw RuntimeException("network down a media paginación")
            return SyncPagosResponse(
                items = p.items,
                max_updated_at = p.items.lastOrNull()?.updated_at ?: cursor.orEmpty(),
                server_now = "2026-05-30T18:25:23Z",
                has_more = p.hasMore,
                sync_epoch = p.epoch
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
