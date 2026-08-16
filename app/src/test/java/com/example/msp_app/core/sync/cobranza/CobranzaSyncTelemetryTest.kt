package com.example.msp_app.core.sync.cobranza

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El `UPDATED_AT` que el backfill de migración le puso a 1,835,734 de las
 * 2,173,422 filas: todas empatadas en un mismo valor. Es la condición que hace
 * que la paginación nunca salga del grupo empatado — el escenario D1.
 */
private const val UPDATED_AT_BACKFILL = "2026-08-01T03:00:00.000000Z"

private const val ZONA = 21

/**
 * Pruebas de la telemetría del sync de cobranza (`CobranzaSyncTelemetry` visto
 * a través de `CobranzaSyncManager`).
 *
 * La prueba que justifica todo el trabajo es
 * [d1 el mismo lote una y otra vez queda registrado como cursor estancado]:
 * reproduce la forma exacta del defecto que costó una semana —una zona
 * re-descargando el mismo lote indefinidamente— y verifica que quede escrito
 * en la telemetría de forma reconocible, sin tener el teléfono en la mano.
 *
 * Fakes-only, sin MockK. Nada se persiste fuera de la DB en memoria de
 * [RoomTestBase].
 */
class CobranzaSyncTelemetryTest : RoomTestBase() {

    // ─────────────────────────────────────────────────────────────────────
    // D1: el bucle
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `d1 el mismo lote una y otra vez queda registrado como cursor estancado`() = runTest {
        seedMigrationMarkers()
        val telemetry = RecordingTelemetry()
        // Servidor atorado: devuelve SIEMPRE el mismo lote, con el mismo
        // `updated_at` empatado y sin `has_more`. Desde el teléfono es
        // indistinguible del incidente real: filas que bajan, posición que no
        // se mueve.
        val api = StuckServerApi(pagos = listOf(pagoBackfill(501), pagoBackfill(502)))
        val manager = newManager(api, telemetry)

        repeat(4) { manager.syncNow() }

        val pagos = telemetry.recorded
            .filter { it.name == CobranzaSyncTelemetry.EVENT_RESOURCE }
            .filter { it.props["resource"] == CobranzaSyncManager.RESOURCE_PAGOS }
        assertEquals("una emisión por recurso por corrida", 4, pagos.size)

        // Corrida 1: no hay contra qué comparar todavía → avance.
        assertEquals("true", pagos[0].props["advanced"])
        assertEquals("0", pagos[0].props["stall_runs"])
        // Corridas 2-4: misma posición de cierre, filas siguen entrando.
        assertEquals(listOf("false", "false", "false"), pagos.drop(1).map { it.props["advanced"] })
        assertEquals(listOf("1", "2", "3"), pagos.drop(1).map { it.props["stall_runs"] })
        assertEquals(
            "las filas siguen aplicándose: es un bucle, no un recurso al día",
            listOf("2", "2", "2", "2"),
            pagos.map { it.props["rows"] }
        )
        // La posición de cierre es literalmente la misma huella corrida tras
        // corrida — el "mismo lote" es un hecho leído, no una inferencia.
        assertEquals(1, pagos.map { it.props["pos"] }.toSet().size)

        val alarmas = telemetry.recorded.filter {
            it.name == CobranzaSyncTelemetry.EVENT_CURSOR_STALLED
        }
        assertEquals("la alarma dispara al cruzar el umbral, una sola vez", 1, alarmas.size)
        val alarma = alarmas.single()
        assertEquals(CobranzaSyncManager.RESOURCE_PAGOS, alarma.props["resource"])
        assertEquals(CobranzaSyncTelemetry.STALL_THRESHOLD.toString(), alarma.props["stall_runs"])
        assertEquals("2", alarma.props["rows"])
        assertEquals(ZONA.toString(), alarma.props["zona"])
    }

    @Test
    fun `d1 si la posicion no sobrevive entre corridas el campo resumed lo delata`() = runTest {
        seedMigrationMarkers()
        val telemetry = RecordingTelemetry()
        val api = StuckServerApi(pagos = listOf(pagoBackfill(501), pagoBackfill(502)))
        val manager = newManager(api, telemetry)

        manager.syncNow()
        // Simula EXACTAMENTE el defecto pre-fix: la mitad `after_id` del cursor
        // no sobrevive entre corridas. El sync arranca creyendo estar al inicio
        // del grupo empatado y vuelve a bajar el lote completo.
        wipeAfterId(CobranzaSyncManager.RESOURCE_PAGOS)
        manager.syncNow()

        val pagos = telemetry.recorded
            .filter { it.name == CobranzaSyncTelemetry.EVENT_RESOURCE }
            .filter { it.props["resource"] == CobranzaSyncManager.RESOURCE_PAGOS }
        assertEquals(2, pagos.size)
        assertEquals("la primera corrida no tiene antecesora", "true", pagos[0].props["resumed"])
        assertEquals(
            "arrancó en una posición distinta a donde cerró la anterior: el cursor no se persistió",
            "false",
            pagos[1].props["resumed"]
        )
        assertEquals(
            "y aun así cerró donde ya estaba: el bucle",
            "false",
            pagos[1].props["advanced"]
        )
    }

    @Test
    fun `sync sano el cursor avanza en cada corrida y nunca se emite la alarma`() = runTest {
        seedMigrationMarkers()
        val telemetry = RecordingTelemetry()
        // Servidor que sí avanza: cada corrida entrega un lote nuevo.
        val api = AdvancingServerApi(
            pagosPorCorrida = listOf(
                listOf(pagoDto(501, "2026-08-01T03:00:00.000000Z")),
                listOf(pagoDto(502, "2026-08-02T03:00:00.000000Z")),
                listOf(pagoDto(503, "2026-08-03T03:00:00.000000Z")),
                listOf(pagoDto(504, "2026-08-04T03:00:00.000000Z"))
            )
        )
        val manager = newManager(api, telemetry)

        repeat(4) { manager.syncNow() }

        val pagos = telemetry.recorded
            .filter { it.name == CobranzaSyncTelemetry.EVENT_RESOURCE }
            .filter { it.props["resource"] == CobranzaSyncManager.RESOURCE_PAGOS }
        assertEquals(4, pagos.size)
        assertTrue(
            "todas las corridas deben reportar avance",
            pagos.all { it.props["advanced"] == "true" }
        )
        assertTrue("sin estancamiento", pagos.all { it.props["stall_runs"] == "0" })
        assertEquals(
            "cuatro posiciones de cierre distintas",
            4,
            pagos.map { it.props["pos"] }.toSet().size
        )
        assertTrue(
            "no hay alarma en un sync sano",
            telemetry.recorded.none { it.name == CobranzaSyncTelemetry.EVENT_CURSOR_STALLED }
        )
    }

    @Test
    fun `un recurso al dia no dispara la alarma aunque la posicion no se mueva`() = runTest {
        seedMigrationMarkers()
        val telemetry = RecordingTelemetry()
        // Cero filas en cada corrida: el cursor no se mueve porque no hay nada
        // que traer. Eso es SALUD, no un bucle — y la telemetría no debe
        // confundirlos.
        val api = StuckServerApi(pagos = emptyList())
        val manager = newManager(api, telemetry)

        repeat(5) { manager.syncNow() }

        val pagos = telemetry.recorded
            .filter { it.name == CobranzaSyncTelemetry.EVENT_RESOURCE }
            .filter { it.props["resource"] == CobranzaSyncManager.RESOURCE_PAGOS }
        assertEquals(5, pagos.size)
        assertEquals("0", pagos.last().props["rows"])
        assertTrue(
            "sin filas aplicadas no hay bucle que reportar",
            telemetry.recorded.none { it.name == CobranzaSyncTelemetry.EVENT_CURSOR_STALLED }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Evento de corrida
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `el evento de corrida reporta resultado, paginas, filas y duracion`() = runTest {
        seedMigrationMarkers()
        val telemetry = RecordingTelemetry()
        val api = StuckServerApi(pagos = listOf(pagoBackfill(501), pagoBackfill(502)))
        // Reloj monótono falso: cada lectura avanza 40 ms, así que la corrida
        // mide exactamente 40 ms (dos lecturas: apertura y cierre).
        val nanos = FakeNanoClock(stepMillis = 40)
        val manager = newManager(api, telemetry, nanoTime = nanos)

        manager.syncNow()

        val run = telemetry.recorded.single { it.name == CobranzaSyncTelemetry.EVENT_RUN }
        assertEquals(TelemetryEventType.EVENT, run.type)
        assertEquals(SyncRunOutcome.OK.wireValue, run.props["outcome"])
        assertEquals(ZONA.toString(), run.props["zona"])
        assertEquals("una página de pagos + una de ventas", "2", run.props["pages"])
        assertEquals("2", run.props["rows"])
        assertEquals("40", run.props["duration_ms"])
        assertEquals(CobranzaSyncTelemetry.PROGRESS_ADVANCED, run.props["progress"])
    }

    @Test
    fun `una corrida atorada cierra su evento de corrida con progress stalled`() = runTest {
        seedMigrationMarkers()
        val telemetry = RecordingTelemetry()
        val api = StuckServerApi(pagos = listOf(pagoBackfill(501)))
        val manager = newManager(api, telemetry)

        repeat(2) { manager.syncNow() }

        val runs = telemetry.recorded.filter { it.name == CobranzaSyncTelemetry.EVENT_RUN }
        assertEquals(2, runs.size)
        assertEquals(CobranzaSyncTelemetry.PROGRESS_ADVANCED, runs[0].props["progress"])
        assertEquals(
            "ningún recurso movió su posición",
            CobranzaSyncTelemetry.PROGRESS_STALLED,
            runs[1].props["progress"]
        )
    }

    @Test
    fun `una corrida saltada por falta de red tambien deja evento`() = runTest {
        seedMigrationMarkers()
        val telemetry = RecordingTelemetry()
        val manager = newManager(NeverCalledApi(), telemetry, online = false)

        manager.syncNow()

        val run = telemetry.recorded.single { it.name == CobranzaSyncTelemetry.EVENT_RUN }
        assertEquals(SyncRunOutcome.SKIPPED_OFFLINE.wireValue, run.props["outcome"])
        assertEquals(ZONA.toString(), run.props["zona"])
        assertEquals("0", run.props["pages"])
        assertEquals(CobranzaSyncTelemetry.PROGRESS_NOT_APPLICABLE, run.props["progress"])
    }

    @Test
    fun `una corrida sin contexto de zona deja evento sin identificar zona`() = runTest {
        val telemetry = RecordingTelemetry()
        val manager = newManager(NeverCalledApi(), telemetry, zona = null)

        manager.syncNow()

        val run = telemetry.recorded.single { it.name == CobranzaSyncTelemetry.EVENT_RUN }
        assertEquals(SyncRunOutcome.SKIPPED_NO_ZONE.wireValue, run.props["outcome"])
        assertEquals(CobranzaSyncTelemetry.UNKNOWN_ZONE, run.props["zona"])
    }

    @Test
    fun `una corrida que revienta reporta el resultado de error`() = runTest {
        seedMigrationMarkers()
        val telemetry = RecordingTelemetry()
        val manager = newManager(ExplodingApi(), telemetry)

        manager.syncNow()

        val run = telemetry.recorded.single { it.name == CobranzaSyncTelemetry.EVENT_RUN }
        assertEquals(SyncRunOutcome.ERROR.wireValue, run.props["outcome"])
    }

    // ─────────────────────────────────────────────────────────────────────
    // Garantías duras: no degradar el sync, cero PII
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `si la telemetria revienta el sync sigue funcionando`() = runTest {
        seedMigrationMarkers()
        val api = StuckServerApi(pagos = listOf(pagoBackfill(501), pagoBackfill(502)))
        val manager = newManager(api, ExplodingTelemetry())

        val outcome = manager.syncNow()

        assertTrue("el sync no puede caerse por telemetría", outcome is SyncOutcome.Ok)
        assertEquals(
            "y los datos sí se aplicaron",
            2,
            db.paymentDao().getAllPayments().size
        )
    }

    @Test
    fun `ningun evento emite datos personales`() = runTest {
        seedMigrationMarkers()
        val telemetry = RecordingTelemetry()
        val api = StuckServerApi(
            pagos = listOf(pagoBackfill(501)),
            ventas = listOf(ventaDto(9001))
        )
        val manager = newManager(api, telemetry)

        repeat(4) { manager.syncNow() }

        assertTrue("la prueba necesita eventos que revisar", telemetry.recorded.isNotEmpty())

        // 1. Nada de lo que viaja puede contener un dato de negocio. Los
        //    fixtures traen a propósito nombre, dirección, teléfono, folio e
        //    importe: si alguno se filtrara a `props`, esto lo caza.
        val datosDeNegocio = listOf(
            "JUAN PEREZ", "AV INDEPENDENCIA 123", "TEHUACAN", "PUEBLA",
            "5550001111", "cv9001", "200.00", "5000.00", "abono"
        )
        telemetry.recorded.forEach { evento ->
            (evento.props.values + evento.name).forEach { valor ->
                datosDeNegocio.forEach { pii ->
                    assertFalse(
                        "el evento '${evento.name}' filtró '$pii' en '$valor'",
                        valor.contains(pii, ignoreCase = true)
                    )
                }
            }
        }

        // 2. Lista blanca cerrada de claves. Un campo nuevo con datos de
        //    persona no puede colarse sin que esta prueba lo obligue a pasar
        //    por aquí primero.
        val clavesPermitidas = setOf(
            "zona", "resource", "pages", "rows", "advanced", "resumed",
            "pos", "pos_before", "stall_runs", "epoch_replay",
            "outcome", "duration_ms", "progress"
        )
        telemetry.recorded.forEach { evento ->
            evento.props.keys.forEach { clave ->
                assertTrue(
                    "clave no autorizada '$clave' en el evento '${evento.name}'",
                    clave in clavesPermitidas
                )
            }
        }

        // 3. La huella de posición no puede ser el valor crudo: ni el cursor ni
        //    el PK del documento deben salir del teléfono.
        val huellas = telemetry.recorded
            .filter { it.name == CobranzaSyncTelemetry.EVENT_RESOURCE }
            .mapNotNull { it.props["pos"] }
        assertTrue("la prueba necesita huellas que revisar", huellas.isNotEmpty())
        huellas.forEach { huella ->
            assertFalse("la huella no puede traer el cursor", huella.contains(UPDATED_AT_BACKFILL))
            assertFalse("la huella no puede traer el PK", huella.contains("501"))
        }
    }

    @Test
    fun `la huella de posicion es estable y distingue posiciones distintas`() {
        val a = SyncCursorPosition(UPDATED_AT_BACKFILL, 501)
        val b = SyncCursorPosition(UPDATED_AT_BACKFILL, 502)
        val c = SyncCursorPosition(null, 0)

        assertEquals("misma posición, misma huella", a.fingerprint(), a.fingerprint())
        assertTrue("posiciones distintas, huellas distintas", a.fingerprint() != b.fingerprint())
        assertEquals("sin cursor no hay posición", SyncCursorPosition.NO_POSITION, c.fingerprint())
        assertEquals("huella corta y estable", 8, a.fingerprint().length)
    }

    @Test
    fun `sin telemetria cableada el manager sigue sincronizando`() = runTest {
        seedMigrationMarkers()
        val api = StuckServerApi(pagos = listOf(pagoBackfill(501)))
        // Sin pasar `telemetry`: el default es `NoOpTelemetry`.
        val manager = CobranzaSyncManager(
            api = api,
            db = db,
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            productDao = db.productDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            connectivity = FakeConnectivity(true),
            userContextFlow = MutableStateFlow(UserContext(ZONA, null)).asStateFlow(),
            cobranzaWriteMutex = CobranzaWriteMutex()
        )

        assertTrue(manager.syncNow() is SyncOutcome.Ok)
        assertEquals(1, db.paymentDao().getAllPayments().size)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Infraestructura de prueba (fakes)
    // ─────────────────────────────────────────────────────────────────────

    private fun newManager(
        api: V2CobranzaApi,
        telemetry: Telemetry,
        online: Boolean = true,
        zona: Int? = ZONA,
        nanoTime: FakeNanoClock = FakeNanoClock(stepMillis = 1)
    ) = CobranzaSyncManager(
        api = api,
        db = db,
        saleDao = db.saleDao(),
        paymentDao = db.paymentDao(),
        productDao = db.productDao(),
        syncStateDao = db.cobranzaSyncStateDao(),
        connectivity = FakeConnectivity(online),
        userContextFlow = MutableStateFlow(
            zona?.let { UserContext(zona = it, fechaCargaInicial = null) }
        ).asStateFlow(),
        // Mutex fresco por prueba: el singleton de proceso acoplaría pruebas.
        cobranzaWriteMutex = CobranzaWriteMutex(),
        telemetry = telemetry,
        nanoTime = nanoTime::read
    )

    private class FakeConnectivity(private val online: Boolean) :
        ConnectivityMonitor(ApplicationProvider.getApplicationContext()) {
        override fun isNetworkAvailable(): Boolean = online
        override val isConnected: Flow<Boolean> = flowOf(online)
    }

    /** Reloj monótono determinista: cada lectura avanza [stepMillis]. */
    private class FakeNanoClock(private val stepMillis: Long) {
        private var reads = 0L
        fun read(): Long = (reads++) * stepMillis * 1_000_000L
    }

    /** Telemetría que revienta en cada llamada, para probar el blindaje. */
    private class ExplodingTelemetry : Telemetry {
        override fun screenView(screen: String): Unit = error("telemetría rota")
        override fun tap(screen: String, element: String): Unit = error("telemetría rota")
        override fun event(name: String, props: Map<String, String>): Unit =
            error("telemetría rota")
        override fun error(code: String, message: String, props: Map<String, String>): Unit =
            error("telemetría rota")
    }

    /**
     * El servidor del incidente: responde SIEMPRE el mismo lote, con el mismo
     * `updated_at` empatado, ignorando `cursor`/`after_id`. Es la forma
     * observable de D1 desde el teléfono.
     */
    private class StuckServerApi(
        private val pagos: List<PagoDto>,
        private val ventas: List<VentaDto> = emptyList()
    ) : CobranzaApiFake() {
        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ) = SyncPagosResponse(
            items = pagos,
            max_updated_at = pagos.lastOrNull()?.updated_at ?: cursor.orEmpty(),
            server_now = "2026-08-01T04:00:00Z",
            has_more = false,
            sync_epoch = null
        )

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ) = SyncVentasResponse(
            items = ventas,
            max_updated_at = ventas.lastOrNull()?.updated_at ?: cursor.orEmpty(),
            server_now = "2026-08-01T04:00:00Z",
            has_more = false,
            sync_epoch = null
        )
    }

    /** Servidor sano: cada corrida entrega un lote nuevo, con cursor mayor. */
    private class AdvancingServerApi(
        private val pagosPorCorrida: List<List<PagoDto>>
    ) : CobranzaApiFake() {
        private var corrida = 0

        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse {
            val items = pagosPorCorrida.getOrNull(corrida).orEmpty()
            corrida++
            return SyncPagosResponse(
                items = items,
                max_updated_at = items.lastOrNull()?.updated_at ?: cursor.orEmpty(),
                server_now = "2026-08-05T04:00:00Z",
                has_more = false,
                sync_epoch = null
            )
        }

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ) = SyncVentasResponse(
            items = emptyList(),
            max_updated_at = cursor.orEmpty(),
            server_now = "2026-08-05T04:00:00Z",
            has_more = false,
            sync_epoch = null
        )
    }

    /** El sync ni siquiera debería llamar a la red (offline / sin zona). */
    private class NeverCalledApi : CobranzaApiFake() {
        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse = error("la API no debería llamarse")

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse = error("la API no debería llamarse")
    }

    /** Red caída a media corrida. */
    private class ExplodingApi : CobranzaApiFake() {
        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse = throw RuntimeException("network down")

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse = throw RuntimeException("network down")
    }

    /** Base con los endpoints que estas pruebas no ejercitan. */
    private abstract class CobranzaApiFake : V2CobranzaApi {
        override suspend fun pagosDigest(zonaId: Int, desde: String?) =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun saldosDigest(zonaId: Int, desde: String?) =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("sin uso en estas pruebas")

        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("sin uso en estas pruebas")

        override suspend fun pagosByIds(zonaId: Int, ids: String) =
            error("sin uso en estas pruebas")

        override suspend fun saldosByIds(zonaId: Int, ids: String) =
            error("sin uso en estas pruebas")
    }

    // ── Fixtures ─────────────────────────────────────────────────────────

    private fun pagoBackfill(impteId: Int) = pagoDto(impteId, UPDATED_AT_BACKFILL)

    private fun pagoDto(impteId: Int, updatedAt: String) = PagoDto(
        impte_docto_cc_id = impteId,
        docto_cc_id = impteId + 1,
        docto_cc_acr_id = impteId,
        cliente_id = 99,
        zona_cliente_id = ZONA,
        folio = "abono",
        concepto_cc_id = 87327,
        fecha = "2026-05-20T14:30:00Z",
        importe = "200.00",
        impuesto = "0.00",
        lat = null,
        lon = null,
        cancelado = false,
        aplicado = true,
        updated_at = updatedAt,
        cobrador = "",
        cobrador_id = null,
        forma_cobro_id = 1,
        nombre_cliente = "JUAN PEREZ",
        pago_recibido_id = null
    )

    private fun ventaDto(doctoCcId: Int) = VentaDto(
        docto_cc_id = doctoCcId,
        docto_pv_id = null,
        cliente_id = 99,
        zona_cliente_id = ZONA,
        folio = "cv$doctoCcId",
        fecha_cargo = "2026-02-15T00:00:00Z",
        fecha_venta = null,
        precio_total = "5000.00",
        total_importe = "1000.00",
        impte_rest = "0.00",
        saldo = "4000.00",
        num_pagos = 4,
        fecha_ult_pago = null,
        cargo_cancelado = false,
        updated_at = UPDATED_AT_BACKFILL,
        cliente_nombre = "JUAN PEREZ",
        limite_credito = null,
        cliente_notas = "",
        cobrador_id = null,
        nombre_cobrador = "",
        zona_nombre = "R/$ZONA",
        calle = "AV INDEPENDENCIA 123",
        ciudad = "TEHUACAN",
        estado = "PUEBLA",
        telefono = "5550001111",
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

    /**
     * Marca como ya consumidas las migraciones one-time de resync para que no
     * sean ellas las que limpien el cursor de pagos entre corridas.
     */
    private suspend fun seedMigrationMarkers() {
        listOf(
            CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID,
            CobranzaSyncManager.MIGRATION_PAGO_RECIBIDO_ID_PERSIST,
            CobranzaSyncManager.MIGRATION_PURGE_LEGACY_PAGO_IDS
        ).forEach { marker ->
            db.cobranzaSyncStateDao().upsert(
                CobranzaSyncStateEntity(
                    RESOURCE = marker,
                    ZONA_CLIENTE_ID = ZONA,
                    CURSOR = null,
                    LAST_SYNCED_AT = "2026-08-01T00:00:00Z",
                    LAST_ERROR = null
                )
            )
        }
    }

    /** Reproduce el defecto pre-fix: la mitad `after_id` del cursor se pierde. */
    private suspend fun wipeAfterId(resource: String) {
        val actual = requireNotNull(db.cobranzaSyncStateDao().get(resource)) {
            "el recurso $resource debería tener estado tras la primera corrida"
        }
        assertNotNull("la corrida anterior debe haber dejado un after_id", actual.AFTER_ID)
        db.cobranzaSyncStateDao().upsert(actual.copy(AFTER_ID = 0))
    }
}
