package com.example.msp_app.core.sync.cobranza

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.payment.PagoRecibidoDTO
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.pagos.CondonacionFixtures
import com.example.msp_app.data.pagos.CondonacionFixtures.EFECTIVO
import com.example.msp_app.data.pagos.CondonacionFixtures.condonacion
import com.example.msp_app.data.pagos.CondonacionFixtures.saldoDe
import com.example.msp_app.data.pagos.CondonacionFixtures.venta
import com.example.msp_app.workers.ReparacionDeCondonacionesWorker
import com.example.msp_app.workmanager.enqueueReparacionDeCondonacionesSiFalta
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * **La reparación única de condonaciones fantasma y saldos negativos.**
 *
 * El defecto de la condonación dejó en la flota filas con la firma
 * `GUARDADO_EN_MICROSIP = 1`, `DOCTO_CC_ID = 0` —las rechazadas antes de que
 * existiera la marca -1— y ventas con `SALDO_REST` negativo (E-APP-029:
 * cuatro condonaciones de $1,000, sólo la primera aplicada; E-APP-030:
 * `-3000.0`). El servidor nunca tuvo el error; sólo el teléfono. Esto lo repara
 * en cada teléfono, una vez, al actualizar.
 */
class ReparacionDeCondonacionesTest : RoomTestBase() {

    private lateinit var context: Context

    private val respuestasDelServidor = mutableMapOf<String, () -> PagoRecibidoDTO>()
    private val consultados = mutableListOf<String>()
    private val saldosDelServidor = mutableMapOf<Int, String>()
    private var sinRedEnByIds = false

    private val pagosApi = object : V2PaymentsApi {
        override suspend fun crearPago(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): PagoRecibidoDTO = error("la reparación no manda pagos")

        override suspend fun obtenerPago(id: String): PagoRecibidoDTO {
            consultados += id
            return checkNotNull(respuestasDelServidor[id]) { "sin respuesta para $id" }()
        }
    }

    private val cobranzaApi = object : V2CobranzaApi {
        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> {
            if (sinRedEnByIds) throw IOException("sin señal")
            return ids.split(",").mapNotNull { id ->
                saldosDelServidor[id.toInt()]?.let { ventaDelServidor(id.toInt(), it) }
            }
        }
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

    private fun reparacion() = ReparacionDeCondonaciones(
        pagosApi = pagosApi,
        refresco = RefrescoDelSaldoDeLaVenta(
            api = cobranzaApi,
            db = db,
            cobranzaWriteMutex = CobranzaWriteMutex()
        ),
        db = db
    )

    @Before
    fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        ReparacionDeCondonacionesWorker.preferenciasDeLaReparacion(context).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build()
        )
    }

    // --- (a) el caso de campo ------------------------------------------------

    /**
     * Venta en -2530 con dos condonaciones que el servidor nunca aplicó (404 del
     * propio API) y una que sí (200 con documento): las dos primeras quedan
     * rechazadas (-1), la tercera con su documento, y el saldo, el del servidor.
     */
    @Test
    fun `a - fantasmas marcados, la aplicada con su documento y el saldo del servidor`() {
        sembrar(venta(saldo = -2530.0))
        guardar(condonacion(C1, 2300.0, guardado = true))
        guardar(condonacion(C2, 230.0, guardado = true))
        guardar(condonacion(C3, 2300.0, guardado = true))
        respuestasDelServidor[C1] = { throw noExiste() }
        respuestasDelServidor[C2] = { throw noExiste() }
        respuestasDelServidor[C3] = { PagoRecibidoDTO(id = C3, docto_cc_id = DOCUMENTO) }
        saldosDelServidor[CondonacionFixtures.VENTA] = "150.00"

        val resultado = runBlocking { reparacion().reparar() }

        assertEquals(ResultadoDeLaReparacion.COMPLETA, resultado)
        assertEquals(DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR, documentoDe(C1))
        assertEquals(DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR, documentoDe(C2))
        assertEquals(DOCUMENTO, documentoDe(C3))
        assertEquals("el saldo es el del servidor", 150.0, saldo(), 1e-9)
    }

    // --- (b) sin dato del servidor --------------------------------------------

    @Test
    fun `b - sin dato del servidor, un saldo negativo sube a cero`() {
        sembrar(venta(saldo = -500.0))

        assertEquals(ResultadoDeLaReparacion.COMPLETA, runBlocking { reparacion().reparar() })

        assertEquals(0.0, saldo(), 1e-9)
    }

    @Test
    fun `b - sin dato del servidor, un saldo positivo no se toca`() {
        sembrar(venta(saldo = 700.0))
        guardar(condonacion(C1, 300.0, guardado = true))
        respuestasDelServidor[C1] = { throw noExiste() }

        assertEquals(ResultadoDeLaReparacion.COMPLETA, runBlocking { reparacion().reparar() })

        assertEquals("sin dato del servidor no se afirma otro saldo", 700.0, saldo(), 1e-9)
        assertEquals(DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR, documentoDe(C1))
    }

    /** Un 200 todavía pendiente en el servidor: se queda como está. */
    @Test
    fun `una condonacion pendiente en el servidor no se toca`() {
        sembrar(venta(saldo = 700.0))
        guardar(condonacion(C1, 300.0, guardado = true))
        respuestasDelServidor[C1] = { PagoRecibidoDTO(id = C1, sincronizacion = "pendiente") }

        assertEquals(ResultadoDeLaReparacion.COMPLETA, runBlocking { reparacion().reparar() })

        assertEquals(0, documentoDe(C1))
        assertEquals(700.0, saldo(), 1e-9)
    }

    // --- (c) sin red ------------------------------------------------------------

    @Test
    fun `c - sin red reintenta y no marca la bandera`() {
        sembrar(venta(saldo = -2530.0))
        guardar(condonacion(C1, 2300.0, guardado = true))
        respuestasDelServidor[C1] = { throw IOException("sin señal") }
        sinRedEnByIds = true

        assertEquals(ListenableWorker.Result.retry(), correrElTrabajo())

        assertFalse(bandera())
        assertEquals(0, documentoDe(C1))
        assertEquals(-2530.0, saldo(), 1e-9)
    }

    /**
     * Un 404 que NO trae el `problem+json` del API —el de un túnel o proxy que
     * nunca llegó— no prueba que el pago no exista: se reintenta, no se marca.
     */
    @Test
    fun `c - un 404 que no es del API reintenta y no marca`() {
        sembrar(venta(saldo = -2530.0))
        guardar(condonacion(C1, 2300.0, guardado = true))
        respuestasDelServidor[C1] = { throw httpError(404, contentType = "text/html") }

        assertEquals(ResultadoDeLaReparacion.REINTENTAR, runBlocking { reparacion().reparar() })

        assertEquals(0, documentoDe(C1))
    }

    @Test
    fun `c - sin red al refrescar el saldo reintenta, y lo marcado se queda`() {
        sembrar(venta(saldo = -2530.0))
        guardar(condonacion(C1, 2300.0, guardado = true))
        respuestasDelServidor[C1] = { throw noExiste() }
        sinRedEnByIds = true

        assertEquals(ListenableWorker.Result.retry(), correrElTrabajo())

        assertFalse(bandera())
        assertEquals(DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR, documentoDe(C1))
        assertEquals("sin dato no se toca", -2530.0, saldo(), 1e-9)
    }

    /**
     * **Una fantasma que falla no frena a las demás.** Un id que contesta 5xx
     * para siempre no puede impedir marcar las otras ni nivelar los negativos:
     * se salta, se recuerda y la corrida pide reintento sólo por ella.
     */
    @Test
    fun `c - una fantasma con 5xx persistente no frena a las demas ni la nivelacion`() {
        sembrar(venta(saldo = -2530.0))
        guardar(condonacion(C1, 2300.0, guardado = true))
        guardar(condonacion(C2, 230.0, guardado = true))
        respuestasDelServidor[C1] = { throw httpError(503) }
        respuestasDelServidor[C2] = { throw noExiste() }

        val resultado = runBlocking { reparacion().reparar() }

        assertEquals(
            "quedó una pendiente: se reintenta",
            ResultadoDeLaReparacion.REINTENTAR,
            resultado
        )
        assertEquals("la que falló se queda como estaba", 0, documentoDe(C1))
        assertEquals("la otra sí se marcó", DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR, documentoDe(C2))
        assertEquals("el negativo sin dato del servidor se niveló", 0.0, saldo(), 1e-9)
    }

    /**
     * **Con tope.** Tras [ReparacionDeCondonacionesWorker.MAX_INTENTOS] la
     * reparación se da por terminada aunque quede algo sin resolver (queda en el
     * log): lo que no se pudo confirmar se deja como estaba, nunca se escribe mal.
     */
    @Test
    fun `c - tras el tope de intentos se marca la bandera`() {
        sembrar(venta(saldo = 700.0))
        guardar(condonacion(C1, 300.0, guardado = true))
        respuestasDelServidor[C1] = { throw httpError(503) }

        assertEquals(
            "control: antes del tope todavía reintenta",
            ListenableWorker.Result.retry(),
            correrElTrabajo(intento = ReparacionDeCondonacionesWorker.MAX_INTENTOS - 1)
        )
        assertFalse(bandera())

        assertEquals(
            ListenableWorker.Result.success(),
            correrElTrabajo(intento = ReparacionDeCondonacionesWorker.MAX_INTENTOS)
        )
        assertTrue(bandera())
        assertEquals("lo no resuelto no se toca", 0, documentoDe(C1))
        assertEquals(700.0, saldo(), 1e-9)
    }

    // --- (d) idempotente ---------------------------------------------------------

    @Test
    fun `d - una segunda corrida no cambia nada ni vuelve a preguntar`() {
        sembrar(venta(saldo = -2530.0))
        guardar(condonacion(C1, 2300.0, guardado = true))
        guardar(condonacion(C3, 2300.0, guardado = true))
        respuestasDelServidor[C1] = { throw noExiste() }
        respuestasDelServidor[C3] = { PagoRecibidoDTO(id = C3, docto_cc_id = DOCUMENTO) }
        saldosDelServidor[CondonacionFixtures.VENTA] = "150.00"
        runBlocking { reparacion().reparar() }
        consultados.clear()

        assertEquals(ResultadoDeLaReparacion.COMPLETA, runBlocking { reparacion().reparar() })

        assertTrue("volvió a preguntar: $consultados", consultados.isEmpty())
        assertEquals(DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR, documentoDe(C1))
        assertEquals(DOCUMENTO, documentoDe(C3))
        assertEquals(150.0, saldo(), 1e-9)
    }

    // --- (e) los abonos no se tocan -----------------------------------------------

    @Test
    fun `e - un abono soltado sin documento ni se consulta ni se toca`() {
        sembrar(venta(saldo = 700.0))
        guardar(condonacion(ABONO, 300.0, formaCobro = EFECTIVO, guardado = true))

        assertEquals(ResultadoDeLaReparacion.COMPLETA, runBlocking { reparacion().reparar() })

        assertTrue(consultados.isEmpty())
        assertEquals(0, documentoDe(ABONO))
        assertEquals(700.0, saldo(), 1e-9)
    }

    // --- (f) la bandera ----------------------------------------------------------

    @Test
    fun `f - al completar marca la bandera y ya no se reencola`() {
        sembrar(venta(saldo = -500.0))

        assertEquals(ListenableWorker.Result.success(), correrElTrabajo())
        assertTrue(bandera())

        enqueueReparacionDeCondonacionesSiFalta(context)
        assertTrue("con la bandera marcada no se encola nada", trabajos().isEmpty())
    }

    @Test
    fun `f - sin la bandera, arrancar encola la reparacion una sola vez`() {
        enqueueReparacionDeCondonacionesSiFalta(context)
        enqueueReparacionDeCondonacionesSiFalta(context)

        assertEquals(1, trabajos().filterNot { it.state == WorkInfo.State.CANCELLED }.size)
    }

    @Test
    fun `f - con la bandera marcada el trabajo no vuelve a reparar`() {
        ReparacionDeCondonacionesWorker.preferenciasDeLaReparacion(context).edit()
            .putBoolean(ReparacionDeCondonacionesWorker.BANDERA, true).commit()
        sembrar(venta(saldo = -500.0))

        assertEquals(ListenableWorker.Result.success(), correrElTrabajo())

        assertEquals("no tenía que reparar", -500.0, saldo(), 1e-9)
    }

    // --- apoyo --------------------------------------------------------------------

    private fun sembrar(venta: com.example.msp_app.core.database.entities.SaleEntity) =
        runBlocking { db.saleDao().insertAll(listOf(venta)) }

    private fun guardar(pago: com.example.msp_app.core.database.entities.PaymentEntity) =
        runBlocking { db.paymentDao().savePayment(pago) }

    private fun documentoDe(id: String): Int = runBlocking {
        db.paymentDao().getPaymentById(id)!!.DOCTO_CC_ID
    }

    private fun saldo(): Double = runBlocking { saldoDe(db) }

    private fun bandera(): Boolean = ReparacionDeCondonacionesWorker.preferenciasDeLaReparacion(
        context
    )
        .getBoolean(ReparacionDeCondonacionesWorker.BANDERA, false)

    private fun trabajos(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(ReparacionDeCondonacionesWorker.NOMBRE_UNICO)
        .get()

    private fun correrElTrabajo(intento: Int = 0): ListenableWorker.Result {
        val worker = TestListenableWorkerBuilder<ReparacionDeCondonacionesWorker>(context)
            .setRunAttemptCount(intento)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = ReparacionDeCondonacionesWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    reparacion = reparacion(),
                    prefs = ReparacionDeCondonacionesWorker.preferenciasDeLaReparacion(appContext)
                )
            })
            .build()
        return runBlocking { (worker as ReparacionDeCondonacionesWorker).doWork() }
    }

    /** El 404 del propio API: `ErrPagoNoEncontrado` en `problem+json`. */
    private fun noExiste() = httpError(404)

    private fun httpError(
        code: Int,
        contentType: String = "application/problem+json"
    ): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("test")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("Content-Type", contentType)
            .build()
        return HttpException(
            Response.error<PagoRecibidoDTO>(
                """{"code":"pago_no_encontrado"}""".toResponseBody(contentType.toMediaTypeOrNull()),
                raw
            )
        )
    }

    private fun ventaDelServidor(cargo: Int, saldo: String) = VentaDto(
        docto_cc_id = cargo,
        docto_pv_id = null,
        cliente_id = 4821,
        zona_cliente_id = 21,
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
        const val C1 = "0f6c1a2b-3d4e-4f50-8a61-b7c8d9e0f101"
        const val C2 = "0f6c1a2b-3d4e-4f50-8a61-b7c8d9e0f102"
        const val C3 = "0f6c1a2b-3d4e-4f50-8a61-b7c8d9e0f103"
        const val ABONO = "0f6c1a2b-3d4e-4f50-8a61-b7c8d9e0f1ab"
        const val DOCUMENTO = 15_080_902
    }
}
