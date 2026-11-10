package com.example.msp_app.workers

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.common.location.captura.EsperaDeEnvio
import com.example.msp_app.core.common.location.captura.RelojMonotono
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.ubicacion.PlazoDeEnvioDao
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.PlazoDeEnvioEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.payment.PagoRecibidoDTO
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.google.gson.JsonParser
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-147: el envio del pago se RETIENE en el worker hasta tener ubicacion o 30 s desde que se
 * guardo (decision del dueno 2026-10-30; K2..K4 de la compuerta ANTES c2). Simulacion determinista:
 * reloj de pared y reloj monotono falsos y tiempo virtual de `runTest`; Room real con ejecutores
 * directos para que el tiempo virtual no se escape mientras se espera al disco. t = 0 es el guardado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingPaymentsWorkerRetencionTest : RoomTestBase() {

    private lateinit var context: Context

    /** Cuanto adelanta (o atrasa) la pared respecto del tiempo virtual: el reloj "mal puesto". */
    private var desfaseDeLaPared = 0L

    /** Lo que la toma dijo: permiso negado o GPS apagado. */
    private var tomaNoVaALlegar = false

    private class Envio(val ms: Long, val lat: String?, val lon: String?, val id: String)

    private val envios = mutableListOf<Envio>()

    @Before
    fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        db.close()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        AppDatabase.setInstanceForTesting(db)
    }

    // ─── dobles y montaje ─────────────────────────────────────────────────────

    private fun TestScope.pared() = object : AppClock {
        override fun now(): Instant = Instant.ofEpochMilli(T0 + currentTime + desfaseDeLaPared)
    }

    private fun TestScope.monotono() = RelojMonotono { BASE + currentTime * 1_000_000L }

    private fun TestScope.api() = object : V2PaymentsApi {
        override suspend fun crearPago(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): PagoRecibidoDTO {
            val json = JsonParser.parseString(
                Buffer().also { datos.writeTo(it) }.readUtf8()
            ).asJsonObject
            envios += Envio(
                ms = currentTime,
                lat = json.get("lat")?.takeIf { !it.isJsonNull }?.asString,
                lon = json.get("lon")?.takeIf { !it.isJsonNull }?.asString,
                id = idempotencyKey
            )
            return PagoRecibidoDTO(id = idempotencyKey)
        }

        override suspend fun obtenerPago(id: String): PagoRecibidoDTO = error("sin red")
    }

    private fun TestScope.worker(
        id: String = PAGO,
        plazos: PlazoDeEnvioDao = db.plazoDeEnvioDao()
    ): PendingPaymentsWorker {
        val pared = pared()
        val prueba = this
        return TestListenableWorkerBuilder<PendingPaymentsWorker>(
            context,
            Data.Builder().putString("payment_id", id).build()
        ).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = PendingPaymentsWorker(
                appContext = appContext,
                workerParams = workerParameters,
                paymentsStore = PaymentsLocalDataSource(appContext),
                v2Api = prueba.api(),
                legacyApi = object : PaymentsApi {
                    override suspend fun savePayment(request: PaymentRequest) =
                        throw AssertionError("legacy no se usa")
                },
                useV2 = true,
                imagenes = db.paymentImageDao(),
                clock = pared,
                plazos = plazos,
                espera = EsperaDeEnvio(pared, prueba.monotono()),
                tomaNoVaALlegar = { tomaNoVaALlegar }
            )
        }).build() as PendingPaymentsWorker
    }

    /** Un pago guardado SIN ubicacion con su plazo de 30 s, como lo deja el adaptador. */
    private suspend fun guardarSinUbicacion(
        conPlazo: Boolean = true,
        lat: Double = 0.0,
        lng: Double = 0.0
    ) {
        db.paymentDao().savePayment(pago(lat, lng))
        if (conPlazo) {
            db.plazoDeEnvioDao().anotar(
                PlazoDeEnvioEntity(
                    PAGO,
                    UbicacionDeCapturaEntity.TIPO_PAGO,
                    T0,
                    PlazoDeEnvioEntity.PLAZO_MS
                )
            )
        }
    }

    /** Arranca el worker en una corrutina del test; devuelve su trabajo y su resultado. */
    private fun TestScope.arrancar(
        w: PendingPaymentsWorker = worker()
    ): Pair<Job, () -> ListenableWorker.Result?> {
        var resultado: ListenableWorker.Result? = null
        val job = launch { resultado = w.doWork() }
        runCurrent()
        return job to { resultado }
    }

    /** Lo que hace el tomador al aceptar un fix: UN update de lat y lng, solo si seguian en 0,0. */
    private suspend fun llegaElFix(lat: Double = LAT, lng: Double = LON) =
        db.paymentDao().updateLocationIfEmpty(PAGO, lat, lng)

    // ─── los escenarios ───────────────────────────────────────────────────────

    @Test
    fun `E-APP-147 fix antes de guardar - el pago ya trae ubicacion y sale sin esperar`() =
        runTest {
            guardarSinUbicacion(lat = LAT, lng = LON)

            val (job, resultado) = arrancar()

            assertEquals(ListenableWorker.Result.success(), resultado())
            assertEquals(1, envios.size)
            assertEquals(0L, envios.single().ms)
            assertEquals(LAT.toString(), envios.single().lat)
            assertEquals(LON.toString(), envios.single().lon)
            job.cancel()
        }

    @Test
    fun `E-APP-147 fix durante la espera - sale de inmediato con la ubicacion, un solo POST`() =
        runTest {
            guardarSinUbicacion()
            val (job, resultado) = arrancar()
            advanceTimeBy(7_000)
            assertTrue("sigue retenido", envios.isEmpty())

            llegaElFix()
            advanceTimeBy(300)
            runCurrent()

            assertEquals(ListenableWorker.Result.success(), resultado())
            assertEquals(1, envios.size)
            assertEquals(
                "sale al leer la ubicacion, no a los 30 s",
                true,
                envios.single().ms in 7_000..7_300
            )
            assertEquals("lat y lng juntas", LAT.toString(), envios.single().lat)
            assertEquals(LON.toString(), envios.single().lon)
            assertTrue(db.paymentDao().getPaymentById(PAGO)!!.GUARDADO_EN_MICROSIP)
            job.cancel()
        }

    @Test
    fun `E-APP-147 nunca llega - sale a los 30 s sin ubicacion y no se pierde`() = runTest {
        guardarSinUbicacion()
        val (job, resultado) = arrancar()
        advanceTimeBy(29_900)
        assertTrue(envios.isEmpty())

        advanceTimeBy(200)
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(1, envios.size)
        assertEquals(30_000L, envios.single().ms)
        assertNull("sin ubicacion se manda sin ella", envios.single().lat)
        assertNull(envios.single().lon)
        assertTrue(db.paymentDao().getPaymentById(PAGO)!!.GUARDADO_EN_MICROSIP)
        job.cancel()
    }

    @Test
    fun `E-APP-147 fix tras el vencimiento - queda local, no viaja y no genera otro envio`() =
        runTest {
            guardarSinUbicacion()
            val (job, _) = arrancar()
            advanceTimeBy(30_100)
            runCurrent()
            assertEquals(1, envios.size)

            // Llega tarde: el tomador la escribe en Room (queda local)...
            assertEquals(1, llegaElFix())
            assertEquals(LAT, db.paymentDao().getPaymentById(PAGO)!!.LAT!!, 0.0)

            // ...y ningun reintento posterior la manda: la captura ya esta soltada.
            val (job2, resultado2) = arrancar(worker())
            advanceTimeBy(5_000)
            assertEquals(ListenableWorker.Result.success(), resultado2())
            assertEquals("sigue siendo UN solo POST", 1, envios.size)
            assertNull("y el unico no llevo la ubicacion tardia", envios.single().lat)
            job.cancel()
            job2.cancel()
        }

    /** K8: la condonacion es un `Payment` con FORMA_COBRO de condonacion y se retiene igual. */
    @Test
    fun `E-APP-147 una condonacion se retiene igual que un abono`() = runTest {
        db.paymentDao().savePayment(pago(0.0, 0.0).copy(FORMA_COBRO_ID = 137_026))
        db.plazoDeEnvioDao().anotar(
            PlazoDeEnvioEntity(
                PAGO,
                UbicacionDeCapturaEntity.TIPO_PAGO,
                T0,
                PlazoDeEnvioEntity.PLAZO_MS
            )
        )
        val (job, resultado) = arrancar()
        advanceTimeBy(4_000)
        assertTrue("retenida", envios.isEmpty())

        llegaElFix()
        advanceTimeBy(300)
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(1, envios.size)
        assertEquals(LAT.toString(), envios.single().lat)
        job.cancel()
    }

    @Test
    fun `E-APP-147 permiso negado o GPS apagado - el worker no espera nada`() = runTest {
        guardarSinUbicacion()
        tomaNoVaALlegar = true

        val (job, resultado) = arrancar()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(1, envios.size)
        assertEquals("ni un milisegundo", 0L, envios.single().ms)
        job.cancel()
    }

    @Test
    fun `E-APP-147 un pago sin fila de plazo no se retiene`() = runTest {
        guardarSinUbicacion(conPlazo = false)

        val (job, resultado) = arrancar()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(0L, envios.single().ms)
        job.cancel()
    }

    // ─── K4 · el reloj de pared ───────────────────────────────────────────────

    @Test
    fun `E-APP-147 reloj de pared atrasado 1 h - no retiene 1 h, sale de inmediato`() = runTest {
        guardarSinUbicacion()
        desfaseDeLaPared = -3_600_000

        val (job, resultado) = arrancar()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(0L, envios.single().ms)
        job.cancel()
    }

    @Test
    fun `E-APP-147 reloj de pared adelantado - tampoco retiene`() = runTest {
        guardarSinUbicacion()
        desfaseDeLaPared = 3_600_000

        val (job, resultado) = arrancar()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(0L, envios.single().ms)
        job.cancel()
    }

    // ─── K3 · parada, proceso muerto y cancelacion ────────────────────────────

    @Test
    fun `E-APP-147 parada o proceso muerto durante la espera - no manda nada y el reinicio manda UNA vez`() =
        runTest {
            guardarSinUbicacion()
            val (job, resultado) = arrancar()
            advanceTimeBy(10_000)

            // WorkManager para el trabajo (red perdida) o el proceso muere: la corrida se cancela.
            job.cancel()
            runCurrent()
            assertEquals("la corrida parada no devolvio resultado", null, resultado())
            assertTrue("y no mando nada", envios.isEmpty())
            assertFalse(db.paymentDao().getPaymentById(PAGO)!!.GUARDADO_EN_MICROSIP)

            // El reinicio empieza de cero: espera solo lo que queda (20 s) y manda una vez.
            val (job2, resultado2) = arrancar(worker())
            advanceTimeBy(19_900)
            assertTrue(envios.isEmpty())
            advanceTimeBy(200)
            runCurrent()

            assertEquals(ListenableWorker.Result.success(), resultado2())
            assertEquals(1, envios.size)
            assertEquals(30_000L, envios.single().ms)
            job2.cancel()
        }

    @Test
    fun `E-APP-147 reinicio con el plazo ya vencido manda de inmediato y una sola vez`() = runTest {
        guardarSinUbicacion()
        advanceTimeBy(45_000) // el telefono estuvo apagado

        val (job, resultado) = arrancar()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(1, envios.size)
        assertEquals(45_000L, envios.single().ms)
        job.cancel()
    }

    @Test
    fun `E-APP-147 la cancelacion se propaga - no se vuelve retry ni se manda`() = runTest {
        guardarSinUbicacion()
        val (job, resultado) = arrancar()
        advanceTimeBy(3_000)

        job.cancel()
        runCurrent()

        assertTrue(job.isCancelled)
        assertEquals(null, resultado())
        assertTrue(envios.isEmpty())
        advanceTimeBy(60_000)
        assertTrue("una corrida cancelada no sigue viva y manda despues", envios.isEmpty())
    }

    /**
     * E-APP-151 (H2 de la compuerta DESPUES; mata D09): si mientras el worker espera el pago queda
     * soltado (otro camino lo subio y lo marco), tras la espera NO se vuelve a mandar. Es la guarda
     * contra el cobro doble: un segundo POST de un pago ya entregado.
     */
    @Test
    fun `E-APP-151 el pago soltado durante la espera sin llegar la ubicacion no se manda otra vez`() =
        runTest {
            guardarSinUbicacion()
            val (job, resultado) = arrancar()
            advanceTimeBy(5_000)
            assertTrue("retenido", envios.isEmpty())

            db.paymentDao().updateEstado(PAGO, 1) // otro camino lo entrego
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(ListenableWorker.Result.success(), resultado())
            assertTrue("cero POST: ya estaba soltado", envios.isEmpty())
            job.cancel()
        }

    @Test
    fun `E-APP-151 el pago soltado y con ubicacion durante la espera tampoco se manda otra vez`() =
        runTest {
            guardarSinUbicacion()
            val (job, resultado) = arrancar()
            advanceTimeBy(5_000)

            llegaElFix()
            db.paymentDao().updateEstado(PAGO, 1)
            advanceTimeBy(300)
            runCurrent()

            assertEquals(ListenableWorker.Result.success(), resultado())
            assertTrue("cero POST: ya estaba soltado", envios.isEmpty())
            job.cancel()
        }

    @Test
    fun `E-APP-147 mientras espera el pago sigue pendiente y no se toca nada`() = runTest {
        guardarSinUbicacion()
        val (job, _) = arrancar()
        advanceTimeBy(15_000)

        val pago = db.paymentDao().getPaymentById(PAGO)!!
        assertFalse(pago.GUARDADO_EN_MICROSIP)
        assertEquals(0, pago.DOCTO_CC_ID)
        assertEquals(0.0, pago.LAT!!, 0.0)
        assertTrue(db.paymentImageDao().getByPagoId(PAGO).isEmpty())
        job.cancel()
    }

    // ─── E-APP-155 (compuerta 2026-11-06, H-1): leer el plazo falla ───────────────────────────────────────────────

    private fun plazosQueFallan(error: Throwable) = object : PlazoDeEnvioDao {
        override suspend fun anotar(fila: PlazoDeEnvioEntity): Long = error("no se escribe aqui")
        override suspend fun obtener(id: String): PlazoDeEnvioEntity? = throw error
    }

    @Test
    fun `E-APP-155 si leer el plazo falla - sale exactamente un POST sin retener y el pago no se pierde`() =
        runTest {
            guardarSinUbicacion(conPlazo = false)
            val (job, resultado) = arrancar(
                worker(plazos = plazosQueFallan(IllegalStateException("room roto")))
            )

            assertEquals(ListenableWorker.Result.success(), resultado())
            assertEquals(1, envios.size)
            assertEquals("sin retencion", 0L, envios.single().ms)
            assertEquals(0L, currentTime)
            val pago = db.paymentDao().getPaymentById(PAGO)!!
            assertEquals(PAGO, pago.ID)
            assertTrue("quedo entregado, no perdido", pago.GUARDADO_EN_MICROSIP)
            job.cancel()
        }

    @Test
    fun `E-APP-155 si leer el plazo se cancela - la cancelacion se propaga y no se manda nada`() =
        runTest {
            guardarSinUbicacion(conPlazo = false)
            val w =
                worker(plazos = plazosQueFallan(kotlinx.coroutines.CancellationException("parada")))
            var resultado: ListenableWorker.Result? = null
            val job = launch { resultado = w.doWork() }
            runCurrent()

            assertTrue("la corrutina termino cancelada", job.isCancelled)
            assertNull(resultado)
            assertTrue("una cancelacion al leer el plazo no manda", envios.isEmpty())
        }

    private fun pago(lat: Double, lng: Double) = PaymentEntity(
        ID = PAGO,
        COBRADOR = "Mendoza Torres, Ana",
        DOCTO_CC_ACR_ID = 5000,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "2026-10-30T18:00:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 220.0,
        LAT = lat,
        LNG = lng,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 77,
        FORMA_COBRO_ID = 87327,
        ZONA_CLIENTE_ID = 21552,
        NOMBRE_CLIENTE = "Lopez Garcia, Minerva"
    )

    private companion object {
        const val PAGO = "pago-uuid-retencion"
        const val T0 = 1_793_383_200_000L
        const val BASE = 9_000_000_000_000L
        const val LAT = 20.5888
        const val LON = -100.3899
    }
}
