package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.network.AuthTokenProvider
import com.example.msp_app.core.network.NetworkConfig
import com.example.msp_app.core.network.RetrofitClientFactory
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.VisitsPendingSynchronizer
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.workmanager.enqueuePendingVisitsWorker
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mediciones de spec visitas §9.2 sobre el código de HOY (antes de P1).
 * Cada prueba nombra su identificador; su salida (líneas `MEDICION`) se pega en
 * `msp-api/docs/evidencia/`. No cambian código de producción: miden.
 */
class MedicionesEnvioVisitasTest : RoomTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val subidaEn: Instant = Instant.parse("2026-10-17T18:00:00Z")

    @Before
    fun initWm() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
    }

    private fun medicion(texto: String) = println("MEDICION $texto")

    // ─── E-VIS-001 ────────────────────────────────────────────────────────────

    private fun correr(api: V2VisitsApi, runAttemptCount: Int): ListenableWorker.Result =
        runPendingVisitsWorker(
            context = context,
            imagenes = db.visitImageDao(),
            clock = FakeClock(subidaEn),
            // E-VIS-001 mide el envío de hoy: el modo de reversa.
            modo = ModoEnvioVisitas.V2_ACTUAL,
            api = api,
            maxAttempts = 10,
            runAttemptCount = runAttemptCount
        )

    private suspend fun guardado(id: String): Int =
        VisitsLocalDataSource(context).getVisitById(id).GUARDADO_EN_MICROSIP

    /** E-VIS-001 (H1): en el intento 10 un 500 sin cabecera suelta la visita. */
    @Test
    fun `E-VIS-001 un 500 sin cabecera en el intento 10 marca la visita enviada`() = runBlocking {
        val store = VisitsLocalDataSource(context)
        val api500 = fakeV2Api { _, _ -> throw httpError(500) }

        // Control positivo 1: el mismo 500 en el primer intento NO marca.
        store.saveVisit(pendingVisit())
        val primero = correr(api500, runAttemptCount = 0)
        val trasPrimero = guardado("visita-001")
        medicion("E-VIS-001 500 intento 1 -> $primero GUARDADO=$trasPrimero")
        assertEquals(ListenableWorker.Result.retry(), primero)
        assertEquals(0, trasPrimero)

        // Medición: el 500 en el intento 10 (runAttemptCount = 9) marca.
        val decimo = correr(api500, runAttemptCount = 9)
        val trasDecimo = guardado("visita-001")
        medicion("E-VIS-001 500 intento 10 -> $decimo GUARDADO=$trasDecimo")
        assertEquals(ListenableWorker.Result.success(), decimo)
        assertEquals("E-VIS-001: hoy el intento 10 suelta la visita sin prueba", 1, trasDecimo)

        // Control positivo 2: con 2xx marca (la marca que se mide es la de éxito).
        store.saveVisit(pendingVisit(id = "visita-002"))
        val ok = runPendingVisitsWorker(
            context = context,
            imagenes = db.visitImageDao(),
            clock = FakeClock(subidaEn),
            // E-VIS-001 mide el envío de hoy: el modo de reversa.
            modo = ModoEnvioVisitas.V2_ACTUAL,
            visitId = "visita-002",
            api = fakeV2Api { _, _ -> VisitaDTO(id = "visita-002") }
        )
        medicion("E-VIS-001 control 2xx -> $ok GUARDADO=${guardado("visita-002")}")
        assertEquals(1, guardado("visita-002"))
    }

    // ─── E-APP-071 ────────────────────────────────────────────────────────────

    private fun workSpecDe(uniqueName: String): androidx.work.impl.model.WorkSpec {
        val info: WorkInfo = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(uniqueName).get().single()
        // WorkInfo no expone el backoff; se lee del WorkSpec persistido (API
        // interna de work-runtime, aceptable en una prueba de medición).
        @Suppress("RestrictedApi")
        return WorkManagerImpl.getInstance(context)
            .workDatabase.workSpecDao().getWorkSpec(info.id.toString())!!
    }

    /** E-APP-071 (spec E-APP-058, H1b): sin setBackoffCriteria rige el default de WorkManager. */
    @Test
    fun `E-APP-071 el worker de visitas usa backoff exponencial de 30 s por omision`() {
        // Control positivo: un request con backoff explícito se lee distinto,
        // así que la lectura del WorkSpec sí distingue.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "control-backoff",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PendingVisitsWorker>()
                .setBackoffCriteria(BackoffPolicy.LINEAR, 60, TimeUnit.SECONDS)
                .build()
        )
        val control = workSpecDe("control-backoff")
        medicion("E-APP-071 control -> ${control.backoffPolicy} ${control.backoffDelayDuration} ms")
        assertEquals(BackoffPolicy.LINEAR, control.backoffPolicy)
        assertEquals(60_000L, control.backoffDelayDuration)

        enqueuePendingVisitsWorker(context, "visita-medicion")
        val ws = workSpecDe("sync_pending_visit_visita-medicion")
        val acumulado = (0..8).sumOf { ws.backoffDelayDuration / 1000 shl it }
        medicion(
            "E-APP-071 enqueuePendingVisitsWorker -> ${ws.backoffPolicy} " +
                "${ws.backoffDelayDuration} ms; acumulado hasta el intento 10 = $acumulado s"
        )
        assertEquals(BackoffPolicy.EXPONENTIAL, ws.backoffPolicy)
        assertEquals(30_000L, ws.backoffDelayDuration)
        // Acumulado hasta el intento 10 = 30 s × (2^0+…+2^8) = 15,330 s ≈ 4.26 h (spec H1b).
        assertEquals(15_330L, acumulado)
    }

    // ─── E-APP-072 ────────────────────────────────────────────────────────────

    private class EncoladorQueAnota : VisitsWorkEnqueuer {
        val ids = mutableListOf<String>()
        override fun enqueue(visitId: String) {
            ids += visitId
        }
    }

    /** E-APP-072 (spec E-APP-059, H3): el reencolado de sesión toma 50 y no ordena. */
    @Test
    fun `E-APP-072 el reencolado de sesion toma 50 sin orden`() = runBlocking {
        val store = VisitsLocalDataSource(context)
        // 60 pendientes sembradas con FECHA descendente (la más nueva primero).
        val sembradas = (0 until 60).map { i ->
            pendingVisit(id = "visita-%02d".format(i)).copy(
                FECHA = Instant.parse("2026-10-01T00:00:00Z").minusSeconds(60L * i).toString()
            )
        }
        sembradas.forEach { store.saveVisit(it) }

        val encolador = EncoladorQueAnota()
        // El mismo fetchPending que cablea PendingWorkSyncFactory.kt:99-102.
        val sync = VisitsPendingSynchronizer(
            fetchPending = { VisitsLocalDataSource(context).getPendingVisits() },
            enqueuer = encolador
        )
        val resultado = sync.sync(SyncContext(userId = "u1", userEmail = "cobrador@msp.com"))

        val porFechaAsc = sembradas.sortedBy { it.FECHA }.map { it.ID }.take(50)
        val ordenSiembra = sembradas.map { it.ID }.take(50)
        medicion("E-APP-072 resultado=$resultado encolados=${encolador.ids.size}")
        medicion("E-APP-072 primeros 5 encolados=${encolador.ids.take(5)}")
        medicion(
            "E-APP-072 orden == FECHA ASC: ${encolador.ids == porFechaAsc}; " +
                "orden == orden de inserción: ${encolador.ids == ordenSiembra}"
        )
        assertEquals(SyncResult.Enqueued(itemCount = 50, workRequestCount = 50), resultado)
        assertEquals(50, encolador.ids.size)
    }

    // ─── E-APP-074 ────────────────────────────────────────────────────────────

    private fun venta(id: Int, saldo: Double) = SaleEntity(
        DOCTO_CC_ACR_ID = id, DOCTO_CC_ID = id + 100_000, FOLIO = "B-$id", CLIENTE_ID = 11486,
        APLICADO = "S", COBRADOR_ID = 200, CLIENTE = "Guadalupe Hernández Ruiz",
        ZONA_CLIENTE_ID = 21552, LIMITE_CREDITO = 0.0, NOTAS = "", ZONA_NOMBRE = "Zona 3",
        IMPORTE_PAGO_PROMEDIO = null, TOTAL_IMPORTE = 0.0, NUM_IMPORTES = 0,
        FECHA = "2026-06-01T09:30:00Z", PARCIALIDAD = 150, ENGANCHE = 0.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0, MONTO_A_CORTO_PLAZO = 0.0, VENDEDOR_1 = "",
        VENDEDOR_2 = "", VENDEDOR_3 = "", PRECIO_TOTAL = 5000.0, IMPTE_REST = 0.0,
        SALDO_REST = saldo, FECHA_ULT_PAGO = null, CALLE = "Av. Juárez 120",
        CIUDAD = "Puebla", ESTADO = "Puebla", TELEFONO = "", NOMBRE_COBRADOR = "Fernando",
        ESTADO_COBRANZA = "PENDIENTE", DIA_COBRANZA = "LUNES", DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 0.0, AVAL_O_RESPONSABLE = "", FREC_PAGO = "SEMANAL"
    )

    private fun saldoCrudo(id: Int): Double = db.openHelper.readableDatabase
        .query("SELECT SALDO_REST FROM sales WHERE DOCTO_CC_ACR_ID = ?", arrayOf<Any>(id))
        .use { c ->
            assertTrue(c.moveToFirst())
            c.getDouble(0)
        }

    /** E-APP-074 (spec E-APP-061, H17, §7.2): `SALDO_REST - 0.0` no cambia el saldo. */
    @Test
    fun `E-APP-074 SALDO_REST menos cero no cambia el saldo`() = runBlocking {
        // La columna es NOT NULL (SaleEntity.SALDO_REST: Double): no hay fila NULL que probar.
        val notNull = db.openHelper.readableDatabase.query("PRAGMA table_info(sales)").use { c ->
            var nn = -1
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == "SALDO_REST") {
                    nn = c.getInt(c.getColumnIndexOrThrow("notnull"))
                }
            }
            nn
        }
        medicion("E-APP-074 SALDO_REST notnull=$notNull")
        assertEquals(1, notNull)

        val saldos = listOf(0.0, -150.5, 1234.56, -0.0)
        db.saleDao().insertAll(saldos.mapIndexed { i, s -> venta(9000 + i, s) })

        saldos.forEachIndexed { i, original ->
            val id = 9000 + i
            val antes = saldoCrudo(id)
            db.saleDao().updateTotal(id, 0.0, EstadoCobranza.VISITADO)
            val despues = saldoCrudo(id)
            val bitsOriginal = java.lang.Double.doubleToRawLongBits(original)
            val bitsAntes = java.lang.Double.doubleToRawLongBits(antes)
            val bitsDespues = java.lang.Double.doubleToRawLongBits(despues)
            medicion(
                "E-APP-074 original=$original antes=$antes despues=$despues " +
                    "bits ${bitsOriginal.toULong().toString(16)} / " +
                    "${bitsAntes.toULong().toString(16)} / ${bitsDespues.toULong().toString(16)}"
            )
            assertEquals("el valor numérico no cambia", antes, despues, 0.0)
            if (original != 0.0 || bitsOriginal == 0L) {
                // Todo salvo -0.0: bit a bit idéntico antes y después.
                assertEquals(bitsAntes, bitsDespues)
            }
        }
    }

    // ─── E-APP-075 ────────────────────────────────────────────────────────────

    private object TokenFijo : AuthTokenProvider {
        override suspend fun token(forceRefresh: Boolean): String = "tok-medicion"
    }

    private fun cuerpo() = CrearVisitaBody(
        id = "visita-075", cliente_id = 11486, cobrador_id = 200,
        cobrador = "Ramirez Ortiz, Fernando", forma_cobro_id = 0, lat = 0.0, lng = 0.0,
        tipo_visita = "SIN_PAGO", zona_cliente_id = 21552, fecha = "2026-10-17T18:00:00Z"
    )

    /**
     * Corta la conexión tras recibir el POST y luego responde 201. Devuelve
     * cuántas peticiones vio el servidor y si el POST terminó en excepción.
     */
    private fun medirCorte(
        host: String = "127.0.0.1",
        bind: String = "127.0.0.1",
        ajustar: (OkHttpClient) -> OkHttpClient
    ): Pair<Int, String> {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"visita-075"}""")
        )
        server.start(java.net.InetAddress.getByName(bind), 0)
        try {
            val base = "http://$host:${server.port}/"
            val retrofit = RetrofitClientFactory(
                Provider { NetworkConfig(base, base, base, "2.20.0") },
                TokenFijo
            ).v2()
            val client = ajustar(retrofit.callFactory() as OkHttpClient)
            val api = retrofit.newBuilder().client(client).build().create(V2VisitsApi::class.java)
            val salida = try {
                runBlocking { "ok id=" + api.crearVisita("visita-075", cuerpo()).id }
            } catch (e: IOException) {
                "${e.javaClass.simpleName}: ${e.message}"
            }
            return server.requestCount to salida
        } finally {
            server.shutdown()
        }
    }

    /**
     * E-APP-075 (spec E-APP-062, H18): el cliente v2 deja `retryOnConnectionFailure`
     * en `true` (default de OkHttp) y OkHttp reenvía por su cuenta un POST cuya
     * conexión se cortó después de enviarlo, **cuando hay otra ruta** (el host
     * resuelve a más de una dirección). Con una sola ruta y conexión nueva no
     * reenvía. No mide el caso de una conexión del pool ya cerrada por el servidor.
     */
    @Test
    fun `E-APP-075 el cliente v2 reintenta el POST al cortarse la conexion`() {
        val porOmision = (
            RetrofitClientFactory(
                Provider { NetworkConfig("http://x/", "http://x/", "http://x/", "2.20.0") },
                TokenFijo
            ).v2().callFactory() as OkHttpClient
            ).retryOnConnectionFailure
        medicion("E-APP-075 cliente v2: retryOnConnectionFailure=$porOmision")
        assertTrue(porOmision)

        // Dos rutas (localhost -> 127.0.0.1 y ::1, servidor en ::).
        val (n, salida) = medirCorte(host = "localhost", bind = "::") { it }
        medicion("E-APP-075 cliente v2 tal cual, dos rutas -> requestCount=$n $salida")
        // Control positivo: lo mismo con retryOnConnectionFailure(false) ve UNA petición.
        val (control, controlSalida) = medirCorte(host = "localhost", bind = "::") {
            it.newBuilder().retryOnConnectionFailure(false).build()
        }
        medicion("E-APP-075 control retry=false, dos rutas -> requestCount=$control $controlSalida")
        assertEquals(1, control)
        assertEquals("E-APP-075: OkHttp reenvió el POST por su cuenta", 2, n)

        // Una sola ruta (127.0.0.1), conexión nueva: no reenvía.
        val (unaRuta, unaRutaSalida) = medirCorte { it }
        medicion("E-APP-075 cliente v2 tal cual, una ruta -> requestCount=$unaRuta $unaRutaSalida")
        assertEquals(1, unaRuta)
    }
}
