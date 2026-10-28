package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.api.services.payment.PagoRecibidoDTO
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.pagos.CapturasSinTerminarAdapter
import com.example.msp_app.feature.pagos.domain.port.CapturasSinTerminar
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
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * E-APP-118 (decisión 4 del dueño 2026-10-20): "Un envío no coincide con la
 * oficina" es igual para pagos y condonaciones que para ventas. Camino real:
 * el worker anota la discrepancia y el adaptador del aviso de la lista la
 * entrega con su venta. Room real.
 */
class EnvioQueNoCoincideEnElAvisoTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-10-20T18:00:00Z"))

    @Test
    fun `E-APP-118 una condonacion con otro cliente en la oficina llega al aviso con su venta`() =
        runTest {
            val aviso = correrConOtroCliente(
                pago(id = CONDONACION, formaCobro = Constants.CONDONACION_ID, venta = 77021)
            )

            assertEquals(CapturasSinTerminar(cuantas = 1, ventaId = 77021, noCoinciden = 1), aviso)
        }

    @Test
    fun `E-APP-118 un abono con otro cliente en la oficina llega al aviso con su venta`() =
        runTest {
            val aviso = correrConOtroCliente(pago(id = ABONO, formaCobro = 157, venta = 77188))

            assertEquals(CapturasSinTerminar(cuantas = 1, ventaId = 77188, noCoinciden = 1), aviso)
        }

    @Test
    fun `E-APP-118 control positivo, sin discrepancia el aviso no dice nada`() = runTest {
        assertEquals(
            CapturasSinTerminar.NINGUNA,
            CapturasSinTerminarAdapter(db.capturaEnCursoDao()) { USUARIO }.observar().first()
        )
    }

    private suspend fun correrConOtroCliente(pago: PaymentEntity): CapturasSinTerminar {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PaymentsLocalDataSource(context).savePayment(pago)
        val worker = TestListenableWorkerBuilder<PendingPaymentsWorker>(
            context,
            Data.Builder().putString("payment_id", pago.ID).build()
        ).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = PendingPaymentsWorker(
                appContext = appContext,
                workerParams = workerParameters,
                paymentsStore = PaymentsLocalDataSource(appContext),
                v2Api = conflictoConOtroCliente(pago.ID),
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
                avisarDiscrepancia = { }
            )
        }).build()
        assertEquals(
            ListenableWorker.Result.success(),
            runBlocking { (worker as PendingPaymentsWorker).doWork() }
        )
        return CapturasSinTerminarAdapter(db.capturaEnCursoDao()) { USUARIO }.observar().first()
    }

    private fun conflictoConOtroCliente(id: String): V2PaymentsApi = object : V2PaymentsApi {
        override suspend fun crearPago(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): PagoRecibidoDTO = throw HttpException(
            Response.error<PagoRecibidoDTO>(
                """{"code":"pago_id_en_uso"}"""
                    .toResponseBody("application/problem+json".toMediaTypeOrNull()),
                okhttp3.Response.Builder()
                    .code(409)
                    .message("test")
                    .protocol(Protocol.HTTP_1_1)
                    .request(Request.Builder().url("http://localhost/").build())
                    .build()
            )
        )

        override suspend fun obtenerPago(id: String): PagoRecibidoDTO =
            PagoRecibidoDTO(id = id, cliente_id = 9999)
    }

    private fun pago(id: String, formaCobro: Int, venta: Int) = PaymentEntity(
        ID = id,
        COBRADOR = "Rosa Elena Martinez Vazquez",
        DOCTO_CC_ACR_ID = venta,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "2026-10-20T15:04:05Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 220.0,
        LAT = 0.0,
        LNG = 0.0,
        CLIENTE_ID = 5021,
        COBRADOR_ID = 77,
        FORMA_COBRO_ID = formaCobro,
        ZONA_CLIENTE_ID = 25,
        NOMBRE_CLIENTE = "Victoria Flores Olmedo"
    )

    private companion object {
        const val USUARIO = "rosa@example.com"
        const val ABONO = "6c2e9b1d-4f3a-4b7e-8d21-0a9c5e7f1b33"
        const val CONDONACION = "9d4a7c2e-1b5f-4e8a-b3c6-2f0d8e5a7c19"
    }
}
