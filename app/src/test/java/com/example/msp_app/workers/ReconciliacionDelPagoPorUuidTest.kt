package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.payment.PagoRecibidoDTO
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * E-APP-088 (decisión 4 del dueño 2026-10-20): ante un error HTTP se busca el
 * pago por UUID. EXISTE → es nuestro y llegó, aunque la oficina lo haya editado;
 * el único seguro extra es el CLIENTE. NO EXISTE → nunca se da por llegado:
 * queda pendiente con aviso y sin reenvío.
 */
class ReconciliacionDelPagoPorUuidTest : RoomTestBase() {

    private lateinit var context: Context
    private lateinit var pagos: PaymentsLocalDataSource
    private val clock = FakeClock(Instant.parse("2026-10-20T18:00:00Z"))
    private val avisos = mutableListOf<Map<String, Any?>>()
    private var posts = 0

    @Before
    fun preparar() = runTest {
        context = ApplicationProvider.getApplicationContext()
        pagos = PaymentsLocalDataSource(context)
        pagos.savePayment(pendiente())
    }

    @Test
    fun `E-APP-088 409 y GET con otro cliente queda pendiente, con discrepancia y sin POST siguiente`() =
        runTest {
            val api =
                api(obtener = { PagoRecibidoDTO(id = ID, cliente_id = 9999) }) { throw http(409) }

            assertEquals(ListenableWorker.Result.success(), correr(api))
            assertFalse("no se da por llegado", pagos.getPaymentById(ID)!!.GUARDADO_EN_MICROSIP)
            assertEquals("contenido_no_coincide", db.capturaEnCursoDao().obtener(ID)!!.DISCREPANCIA)
            assertEquals(1, avisos.size)
            assertEquals("contenido_no_coincide", avisos.single()["motivo"])

            // Siguiente corrida: ni un POST más.
            assertEquals(ListenableWorker.Result.success(), correr(api))
            assertEquals("sin reenvío", 1, posts)
        }

    @Test
    fun `E-APP-088 409 y GET existente aunque editado por la oficina se da por llegado`() =
        runTest {
            // La oficina cambió el importe: ya NO se compara (sólo el cliente).
            val api = api(obtener = {
                PagoRecibidoDTO(id = ID, cliente_id = CLIENTE, sincronizacion = "aplicada")
            }) { throw http(409) }

            assertEquals(ListenableWorker.Result.success(), correr(api))
            assertTrue(pagos.getPaymentById(ID)!!.GUARDADO_EN_MICROSIP)
            assertNull(db.capturaEnCursoDao().obtener(ID))
        }

    @Test
    fun `E-APP-088 422 resguardado y GET 404 no se da por enviado y no se reenvia`() = runTest {
        val api = api(obtener = { throw http(404) }) { throw http(422, capturada = true) }

        assertEquals(ListenableWorker.Result.success(), correr(api))
        assertFalse(
            "antes: X-Intent-Captured soltaba la captura sin prueba",
            pagos.getPaymentById(ID)!!.GUARDADO_EN_MICROSIP
        )
        assertEquals("no_existe_en_servidor", db.capturaEnCursoDao().obtener(ID)!!.DISCREPANCIA)
        correr(api)
        assertEquals(1, posts)
    }

    @Test
    fun `E-APP-088 422 resguardado y GET indeterminado se reintenta sin soltar`() = runTest {
        val api = api(obtener = { throw http(500) }) { throw http(422, capturada = true) }

        assertEquals(ListenableWorker.Result.retry(), correr(api))
        assertFalse(pagos.getPaymentById(ID)!!.GUARDADO_EN_MICROSIP)
        assertNull("un GET caído no es un hallazgo", db.capturaEnCursoDao().obtener(ID))
    }

    @Test
    fun `E-APP-088 control positivo, un GET sin cliente no se lee como discrepancia`() = runTest {
        val api = api(obtener = { PagoRecibidoDTO(id = ID) }) { throw http(409) }

        assertEquals(ListenableWorker.Result.success(), correr(api))
        assertTrue(pagos.getPaymentById(ID)!!.GUARDADO_EN_MICROSIP)
    }

    /**
     * E-APP-117 (medición M2 de la compuerta DESPUÉS 2026-10-26): en el flujo
     * normal la pantalla ya cerró el marcador (`terminarComoRegistrado`) cuando
     * el worker recibe el 409/422. La discrepancia se anota sobre ese marcador
     * CERRADA y tiene que contar en "N capturas sin terminar".
     */
    @Test
    fun `E-APP-117 409 con otro cliente sobre el marcador ya CERRADA cuenta en el aviso`() =
        runTest {
            cerradoPorLaPantalla()
            val api =
                api(obtener = { PagoRecibidoDTO(id = ID, cliente_id = 9999) }) { throw http(409) }

            correr(api)

            val marcador = db.capturaEnCursoDao().obtener(ID)!!
            assertEquals(CapturaEnCursoEntity.CERRADA, marcador.ESTADO)
            assertEquals("contenido_no_coincide", marcador.DISCREPANCIA)
            assertEquals(1, db.capturaEnCursoDao().observarSinTerminar(USUARIO).first())
        }

    @Test
    fun `E-APP-117 422 resguardado con GET 404 sobre el marcador ya CERRADA cuenta en el aviso`() =
        runTest {
            cerradoPorLaPantalla()
            val api = api(obtener = { throw http(404) }) { throw http(422, capturada = true) }

            correr(api)

            assertEquals("no_existe_en_servidor", db.capturaEnCursoDao().obtener(ID)!!.DISCREPANCIA)
            assertEquals(1, db.capturaEnCursoDao().observarSinTerminar(USUARIO).first())
        }

    @Test
    fun `E-APP-117 control positivo, el marcador CERRADA sin discrepancia no cuenta`() = runTest {
        cerradoPorLaPantalla()
        val api = api(obtener = {
            PagoRecibidoDTO(id = ID, cliente_id = CLIENTE)
        }) { throw http(409) }

        correr(api)

        assertNull(db.capturaEnCursoDao().obtener(ID)!!.DISCREPANCIA)
        assertEquals(0, db.capturaEnCursoDao().observarSinTerminar(USUARIO).first())
    }

    private suspend fun cerradoPorLaPantalla() {
        db.capturaEnCursoDao().capturar(
            CapturaEnCursoEntity(
                ID = ID,
                TIPO = CapturaEnCursoEntity.TIPO_ABONO,
                LLAVE = "77188",
                USUARIO = USUARIO,
                ESTADO = CapturaEnCursoEntity.CERRADA,
                RESUMEN = "{}",
                CREADA_EN = "2026-10-20T15:04:05Z",
                ACTUALIZADA_EN = "2026-10-20T15:04:09Z"
            )
        )
    }

    // --- plomería ---------------------------------------------------------------

    private fun correr(api: V2PaymentsApi): ListenableWorker.Result {
        val worker = TestListenableWorkerBuilder<PendingPaymentsWorker>(
            context,
            Data.Builder().putString("payment_id", ID).build()
        ).setWorkerFactory(object : WorkerFactory() {
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
                        throw AssertionError("legacy no")
                },
                useV2 = true,
                imagenes = db.paymentImageDao(),
                clock = clock,
                encolarRefrescoDeSaldo = { _, _ -> },
                capturas = db.capturaEnCursoDao(),
                emailEnSesion = { USUARIO },
                avisarDiscrepancia = { avisos += it }
            )
        }).build()
        return runBlocking { (worker as PendingPaymentsWorker).doWork() }
    }

    private fun api(
        obtener: suspend (String) -> PagoRecibidoDTO,
        crear: suspend () -> PagoRecibidoDTO
    ): V2PaymentsApi = object : V2PaymentsApi {
        override suspend fun crearPago(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): PagoRecibidoDTO {
            posts++
            return crear()
        }

        override suspend fun obtenerPago(id: String): PagoRecibidoDTO = obtener(id)
    }

    private fun http(code: Int, capturada: Boolean = false): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("test")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("Content-Type", "application/problem+json")
        if (capturada) raw.header("X-Intent-Captured", "5b0c3a55-1f6e-4f7e-9a0e-5c1f0a9f7a11")
        return HttpException(
            Response.error<PagoRecibidoDTO>(
                """{"code":"pago_cargo_no_encontrado"}"""
                    .toResponseBody("application/problem+json".toMediaTypeOrNull()),
                raw.build()
            )
        )
    }

    private fun pendiente() = PaymentEntity(
        ID = ID,
        COBRADOR = "Rosa Elena Martinez Vazquez",
        DOCTO_CC_ACR_ID = 77188,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "2026-10-20T15:04:05Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 220.0,
        LAT = 0.0,
        LNG = 0.0,
        CLIENTE_ID = CLIENTE,
        COBRADOR_ID = 77,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 25,
        NOMBRE_CLIENTE = "Victoria Flores Olmedo"
    )

    private companion object {
        const val ID = "3b1f5a0e-7d2c-4c8e-9a41-5e6f7a8b9c0d"
        const val CLIENTE = 5021
        const val USUARIO = "rosa@example.com"
    }
}
