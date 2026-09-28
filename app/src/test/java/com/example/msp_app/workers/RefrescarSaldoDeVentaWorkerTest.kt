package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.sync.cobranza.CobranzaWriteMutex
import com.example.msp_app.core.sync.cobranza.RefrescoDelSaldoDeLaVenta
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.pagos.CondonacionFixtures
import com.example.msp_app.workmanager.enqueueRefrescoDeSaldo
import com.example.msp_app.workmanager.refrescoDeSaldoUniqueWorkName
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * **El refresco del saldo de una venta tras una condonación rechazada: cuándo
 * reintenta, cuándo se rinde y cómo se encola.**
 *
 * Tercera compuerta posterior (2026-09-27):
 * - **ZZ1**: con `KEEP`, un refresco que ya estaba corriendo —y ya había pasado
 *   su transacción— se tragaba el encolado de una SEGUNDA condonación rechazada
 *   de la misma venta, y el saldo se quedaba abajo sin corrección.
 * - **4xx**: el worker reintentaba ante cualquier excepción, también ante un
 *   4xx permanente, sin tope.
 */
class RefrescarSaldoDeVentaWorkerTest : RoomTestBase() {

    private lateinit var context: Context

    /** El servidor que ven los refrescos que corre WorkManager en estas pruebas. */
    private var servidorDeLaCadena: V2CobranzaApi = fallaCon(IOException("sin servidor"))

    @Before
    fun setUpWorkManager() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker = RefrescarSaldoDeVentaWorker(
                        appContext = appContext,
                        workerParams = workerParameters,
                        refresco = RefrescoDelSaldoDeLaVenta(
                            api = servidorDeLaCadena,
                            db = db,
                            cobranzaWriteMutex = CobranzaWriteMutex()
                        )
                    )
                })
                .build()
        )
    }

    /**
     * **ZZ1, simulado con `work-testing`.** El refresco anterior sigue vivo (aquí
     * `ENQUEUED`, porque la red de prueba nunca se abre; `work-testing` no deja
     * fijar `RUNNING` a mitad de un `doWork`). Un segundo encolado —otra
     * condonación rechazada de la misma venta— NO puede descartarse: tiene que
     * quedar encadenado detrás, para que corra un refresco POSTERIOR a su marca.
     * Con `KEEP` se descartaba; en `RUNNING` es exactamente el caso medido.
     */
    @Test
    fun `ZZ1 - un segundo encolado de la misma venta no se descarta`() {
        val nombre = refrescoDeSaldoUniqueWorkName(CondonacionFixtures.VENTA)

        enqueueRefrescoDeSaldo(context, ZONA, CondonacionFixtures.VENTA)
        val primero = vivos(nombre).single()
        enqueueRefrescoDeSaldo(context, ZONA, CondonacionFixtures.VENTA)

        val despues = vivos(nombre)
        assertEquals("el segundo refresco se descartó: $despues", 2, despues.size)
        assertTrue(
            "el primero se canceló en vez de quedarse delante",
            despues.any { it.id == primero.id }
        )
    }

    /**
     * **Un 4xx permanente no se reintenta, pero tampoco mata la cadena.** Con
     * `APPEND_OR_REPLACE` un `failure()` deja FALLIDOS a los refrescos
     * encadenados detrás (quinta compuerta, GE4: saldo 100 contra 300, fósil).
     * Rendirse es `success()` sin tocar el saldo: el eslabón siguiente —de un
     * pago posterior— siempre puede correr.
     */
    @Test
    fun `un 4xx permanente no se reintenta ni toca el saldo`() = runBlocking {
        db.saleDao().insertAll(listOf(CondonacionFixtures.venta(saldo = 100.0)))

        assertEquals(ListenableWorker.Result.success(), correr(fallaCon(httpError(403))))
        assertEquals(ListenableWorker.Result.success(), correr(fallaCon(httpError(400))))
        assertEquals(100.0, CondonacionFixtures.saldoDe(db), 1e-9)
    }

    /**
     * Lo transitorio SÍ se reintenta. El **404** entra aquí, igual que en la
     * subida (`PendingPaymentsWorkerV2Test.v2_404_del_tunel_se_reintenta_y_nunca_suelta`):
     * puede venir de un túnel o proxy que nunca llegó al API.
     */
    @Test
    fun `sin red, 5xx, 404 y 4xx de reintento se reintentan`() {
        assertEquals(ListenableWorker.Result.retry(), correr(fallaCon(IOException("sin señal"))))
        assertEquals(ListenableWorker.Result.retry(), correr(fallaCon(httpError(503))))
        assertEquals(ListenableWorker.Result.retry(), correr(fallaCon(httpError(404))))
        assertEquals(ListenableWorker.Result.retry(), correr(fallaCon(httpError(401))))
        assertEquals(ListenableWorker.Result.retry(), correr(fallaCon(httpError(429))))
    }

    /** Con tope, y al llegar al tope se rinde SIN matar la cadena. */
    @Test
    fun `tras el tope de intentos se rinde sin matar la cadena`() {
        assertEquals(
            ListenableWorker.Result.success(),
            correr(
                fallaCon(IOException("sin señal")),
                intento = RefrescarSaldoDeVentaWorker.MAX_INTENTOS
            )
        )
        assertEquals(
            "control: un intento antes del tope todavía reintenta",
            ListenableWorker.Result.retry(),
            correr(
                fallaCon(IOException("sin señal")),
                intento = RefrescarSaldoDeVentaWorker.MAX_INTENTOS - 1
            )
        )
    }

    /**
     * **La cadena, con WorkManager de prueba.** Dos refrescos encadenados de la
     * misma venta: el primero se topa con un 4xx permanente y el segundo, que
     * trae el saldo del servidor, TIENE que correr.
     */
    @Test
    fun `tras un 4xx permanente el siguiente refresco encadenado corre`() = runBlocking {
        db.saleDao().insertAll(listOf(CondonacionFixtures.venta(saldo = 100.0)))
        var llamadas = 0
        val base = fallaCon(IOException("no se usa"))
        servidorDeLaCadena = object : V2CobranzaApi by base {
            override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> {
                llamadas += 1
                if (llamadas == 1) throw httpError(403)
                return listOf(ventaDelServidor(saldo = "300.00"))
            }
        }
        val nombre = refrescoDeSaldoUniqueWorkName(CondonacionFixtures.VENTA)
        val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))

        enqueueRefrescoDeSaldo(context, ZONA, CondonacionFixtures.VENTA)
        val primero = vivos(nombre).single().id
        enqueueRefrescoDeSaldo(context, ZONA, CondonacionFixtures.VENTA)
        val segundo = vivos(nombre).single { it.id != primero }.id

        driver.setAllConstraintsMet(primero)
        esperarTerminado(primero)
        driver.setAllConstraintsMet(segundo)
        esperarTerminado(segundo)

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(nombre).get()
        assertEquals(
            "el segundo refresco no corrió: ${infos.map { it.state }}",
            WorkInfo.State.SUCCEEDED,
            infos.single { it.id == segundo }.state
        )
        assertEquals(300.0, CondonacionFixtures.saldoDe(db), 1e-9)
    }

    /**
     * Un `CoroutineWorker` corre su `doWork` en su propio dispatcher aunque el
     * executor de prueba sea síncrono: se espera, con reloj real, a que termine.
     */
    private fun esperarTerminado(id: java.util.UUID) = runBlocking {
        withTimeout(ESPERA_MS) {
            while (!WorkManager.getInstance(context).getWorkInfoById(id).get()!!.state.isFinished) {
                delay(PASO_MS)
            }
        }
    }

    private fun vivos(nombre: String): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(nombre)
        .get()
        .filterNot { it.state == WorkInfo.State.CANCELLED }

    private fun correr(api: V2CobranzaApi, intento: Int = 0): ListenableWorker.Result {
        val worker = TestListenableWorkerBuilder<RefrescarSaldoDeVentaWorker>(
            context,
            Data.Builder()
                .putInt(RefrescarSaldoDeVentaWorker.KEY_ZONA, ZONA)
                .putInt(RefrescarSaldoDeVentaWorker.KEY_CARGO, CondonacionFixtures.VENTA)
                .build()
        )
            .setRunAttemptCount(intento)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = RefrescarSaldoDeVentaWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    refresco = RefrescoDelSaldoDeLaVenta(
                        api = api,
                        db = db,
                        cobranzaWriteMutex = CobranzaWriteMutex()
                    )
                )
            })
            .build()
        return runBlocking { (worker as RefrescarSaldoDeVentaWorker).doWork() }
    }

    private fun httpError(code: Int) = HttpException(
        Response.error<List<VentaDto>>(code, "{}".toResponseBody(null))
    )

    /** Un servidor cuyo `saldos/by-ids` falla con [fallo]. Nada más se usa. */
    private fun fallaCon(fallo: Exception): V2CobranzaApi = object : V2CobranzaApi {
        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> = throw fallo
        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse = error("no se usa")
        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse = error("no se usa")
        override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse =
            error("no se usa")
        override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse =
            error("no se usa")
        override suspend fun listPagoIds(
            zonaId: Int,
            after: Int,
            limit: Int,
            desde: String?
        ): IdsResponse = error("no se usa")
        override suspend fun listSaldoIds(
            zonaId: Int,
            after: Int,
            limit: Int,
            desde: String?
        ): IdsResponse = error("no se usa")
        override suspend fun pagosByIds(zonaId: Int, ids: String): List<PagoDto> =
            error("no se usa")
    }

    private fun ventaDelServidor(saldo: String) = VentaDto(
        docto_cc_id = CondonacionFixtures.VENTA,
        docto_pv_id = null,
        cliente_id = 4821,
        zona_cliente_id = ZONA,
        folio = "Y00013662",
        fecha_cargo = "2026-01-01T00:00:00Z",
        fecha_venta = null,
        precio_total = "3500.00",
        total_importe = "3000.00",
        impte_rest = saldo,
        saldo = saldo,
        num_pagos = 10,
        fecha_ult_pago = null,
        cargo_cancelado = false,
        updated_at = "2026-09-26T21:27:30.000000Z",
        cliente_nombre = "Guadalupe Hernandez Soto",
        limite_credito = null,
        cliente_notas = "",
        cobrador_id = null,
        nombre_cobrador = "Rosa Elena Martinez Vazquez",
        zona_nombre = "Centro",
        calle = "Av. Reforma 100",
        ciudad = "Tehuacan",
        estado = "Puebla",
        telefono = "",
        parcialidad = 350,
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

    private companion object {
        const val ZONA = 21
        const val ESPERA_MS = 5_000L
        const val PASO_MS = 20L
    }
}
