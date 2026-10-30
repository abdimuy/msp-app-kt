package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.common.location.captura.EsperaDeEnvio
import com.example.msp_app.core.common.location.captura.RelojMonotono
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.PlazoDeEnvioEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.payment.PagoRecibidoDTO
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker
import com.google.gson.JsonParser
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-147 (K2): carrera con segundo encolado. Con WorkManager REAL (TestDriver), mientras el
 * worker espera la ubicacion se encola el mismo pago varias veces (el barrido, el boton «Enviar
 * pendientes», el sincronizador): `KEEP` con nombre unico deja UN solo trabajo, el original, y sale
 * UN solo POST. Tiempo real (la espera dura milisegundos porque el fix llega enseguida).
 */
class PendingPaymentsWorkerKeepTest : RoomTestBase() {

    private lateinit var context: Context
    private val posts = CopyOnWriteArrayList<Pair<String?, String?>>()

    @Before
    fun montar() {
        context = ApplicationProvider.getApplicationContext()
        val pared = object : AppClock {
            override fun now(): Instant = Instant.now()
        }
        val monotono = RelojMonotono { System.nanoTime() }
        val api = object : V2PaymentsApi {
            override suspend fun crearPago(
                idempotencyKey: String,
                datos: RequestBody,
                imagenes: List<MultipartBody.Part>
            ): PagoRecibidoDTO {
                val json = JsonParser.parseString(
                    Buffer().also { datos.writeTo(it) }.readUtf8()
                ).asJsonObject
                posts += json.get("lat")?.asString to json.get("lon")?.asString
                return PagoRecibidoDTO(id = idempotencyKey)
            }

            override suspend fun obtenerPago(id: String): PagoRecibidoDTO = error("sin red")
        }
        val fabrica = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = PendingPaymentsWorker(
                appContext = appContext,
                workerParams = workerParameters,
                paymentsStore = PaymentsLocalDataSource(appContext),
                v2Api = api,
                legacyApi = object : PaymentsApi {
                    override suspend fun savePayment(request: PaymentRequest) =
                        throw AssertionError("legacy no se usa")
                },
                useV2 = true,
                imagenes = db.paymentImageDao(),
                clock = pared,
                plazos = db.plazoDeEnvioDao(),
                espera = EsperaDeEnvio(pared, monotono),
                tomaNoVaALlegar = { false }
            )
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setWorkerFactory(fabrica).build()
        )
    }

    @Test
    fun `E-APP-147 tres encolados mientras el worker espera dejan un solo trabajo y un solo POST`() {
        runBlocking {
            db.paymentDao().savePayment(pago())
            db.plazoDeEnvioDao().anotar(
                PlazoDeEnvioEntity(
                    PAGO,
                    UbicacionDeCapturaEntity.TIPO_PAGO,
                    System.currentTimeMillis(),
                    PlazoDeEnvioEntity.PLAZO_MS
                )
            )
        }
        val wm = WorkManager.getInstance(context)
        val nombre = "sync_pending_payments_$PAGO"

        enqueuePendingPaymentsWorker(context, PAGO)
        val original = wm.getWorkInfosForUniqueWork(nombre).get().single().id
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(original)
        esperarHasta("el worker original esta esperando") {
            wm.getWorkInfoById(original).get()?.state == WorkInfo.State.RUNNING
        }
        assertTrue("retenido: ningun POST todavia", posts.isEmpty())

        // La carrera: el barrido, el boton y el sincronizador lo encolan otra vez mientras espera.
        repeat(3) { enqueuePendingPaymentsWorker(context, PAGO) }

        val infos = wm.getWorkInfosForUniqueWork(nombre).get()
        assertEquals("sigue habiendo UN solo trabajo", 1, infos.size)
        assertEquals("y es el original, no uno nuevo", original, infos.single().id)
        assertEquals(WorkInfo.State.RUNNING, infos.single().state)

        // El tomador escribe la ubicacion: el worker la ve en su siguiente vuelta y manda.
        runBlocking { db.paymentDao().updateLocationIfEmpty(PAGO, LAT, LON) }
        esperarHasta("el worker termina") {
            wm.getWorkInfoById(original).get()?.state == WorkInfo.State.SUCCEEDED
        }

        assertEquals("UN solo POST", 1, posts.size)
        assertEquals(LAT.toString(), posts.single().first)
        assertEquals(LON.toString(), posts.single().second)
        assertEquals(
            "y sigue habiendo un solo trabajo",
            1,
            wm.getWorkInfosForUniqueWork(nombre).get().size
        )
    }

    private fun esperarHasta(que: String, condicion: () -> Boolean) {
        val limite = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < limite) {
            if (condicion()) return
            Thread.sleep(20)
        }
        throw AssertionError("nunca se cumplio: $que")
    }

    private fun pago() = PaymentEntity(
        ID = PAGO,
        COBRADOR = "Mendoza Torres, Ana",
        DOCTO_CC_ACR_ID = 5000,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "2026-10-30T18:00:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 220.0,
        LAT = 0.0,
        LNG = 0.0,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 77,
        FORMA_COBRO_ID = 87327,
        ZONA_CLIENTE_ID = 21552,
        NOMBRE_CLIENTE = "Lopez Garcia, Minerva"
    )

    private companion object {
        const val PAGO = "pago-uuid-keep"
        const val LAT = 20.5888
        const val LON = -100.3899
    }
}
