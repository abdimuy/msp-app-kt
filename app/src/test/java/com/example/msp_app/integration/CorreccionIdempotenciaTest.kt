package com.example.msp_app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import com.example.msp_app.data.api.services.ventas.VentaDTO
import com.example.msp_app.data.api.services.ventas.VentasApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.feature.ventacorreccion.data.AppClockRelojPort
import com.example.msp_app.feature.ventacorreccion.data.RoomVentaLocalCorreccionAdapter
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ReclamarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ResultadoReclamo
import com.example.msp_app.features.sales.upload.data.RoomUploadFailureRepository
import com.example.msp_app.`test-fixtures`.TestDataFactory
import com.example.msp_app.workers.PendingLocalSalesWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

private const val SALE_ID = "sale-idempotencia-001"
private const val EMAIL = "cobrador.pruebas@muebleriamsp.mx"
private const val NOMBRE_ORIGINAL = "Teodoro Aviles Campos"
private const val NOMBRE_CORREGIDO = "Teodoro Aviles Campos de la Cruz"

/**
 * Task 6 del plan "Corregir una venta antes de que suba": corregir NO rota la
 * `Idempotency-Key` y NO crea una segunda venta — ni cuando el `POST` se reintenta tal cual, ni
 * cuando el 2xx original se pierde y el segundo intento tiene que reconciliarse por `GET`. La
 * carrera EDICIÓN-vs-SUBIDA ya está cubierta en `CorreccionCarreraTest` (Task 4); aquí el foco es
 * la LLAVE y el conteo de ventas, no el entrelazado de las dos corrutinas.
 */
class CorreccionIdempotenciaTest : RoomTestBase() {

    private lateinit var context: Context
    private lateinit var saleDataSource: LocalSaleDataSource
    private lateinit var productDataSource: SaleProductLocalDataSource
    private lateinit var comboDataSource: ComboLocalDataSource
    private lateinit var fakeImageFile: File

    private val clock = FakeClock.at("2026-09-21T12:00:00Z")

    private val vendedores = listOf(
        VendedorDTO(
            id = "v-uuid-1",
            usuario_id = "u-uuid-1",
            email = "vendedor@muebleriamsp.mx",
            nombre = "Carlos Vendedor"
        )
    )

    private val ventaDTO = VentaDTO(
        id = SALE_ID,
        situacion = "PENDIENTE",
        sincronizacion = "PENDIENTE",
        tipo_venta = "CREDITO",
        fecha_venta = "2026-09-21T12:00:00Z",
        created_at = "2026-09-21T12:00:01Z",
        updated_at = "2026-09-21T12:00:01Z"
    )

    @Before
    fun setUpPrereqs() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:stub")
                    .setApiKey("stub-api-key")
                    .setProjectId("stub-project")
                    .build()
            )
        }
        saleDataSource = LocalSaleDataSource(context)
        productDataSource = SaleProductLocalDataSource(context)
        comboDataSource = ComboLocalDataSource(context)
        fakeImageFile = File.createTempFile("recibo", ".jpg")
        fakeImageFile.writeBytes(ByteArray(16) { 0xFF.toByte() })
    }

    // ─── el editor real ───────────────────────────────────────────────────

    private object ReencolarNoOp : ReencolarSubidaPort {
        override fun cancelarTrabajoEncolado(saleId: String) = Unit
        override fun reencolar(saleId: String, userEmail: String) = Unit
    }

    private fun puerto() = RoomVentaLocalCorreccionAdapter(
        db,
        db.localSaleDao(),
        db.localSaleProduct(),
        db.localSaleComboDao()
    )

    private val reloj = AppClockRelojPort(clock)
    private fun reclamar() = ReclamarCorreccion(puerto(), reloj, ReencolarNoOp)
    private fun guardar() = GuardarCorreccion(puerto(), reloj, ReencolarNoOp)

    // ─── la venta ─────────────────────────────────────────────────────────

    private suspend fun sembrarVenta(saleId: String = SALE_ID) {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = saleId, clientName = NOMBRE_ORIGINAL)
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = saleId, articuloId = 100)
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(
                imageId = "img-001",
                saleId = saleId,
                imageUri = fakeImageFile.absolutePath
            )
        )
    }

    /** Cuántas ventas hay en la base, sin la ventana de 7 días de `getAllSales`. */
    private suspend fun cuantasVentas(): Int =
        db.localSaleDao().getSalesByStatus(true).size + db.localSaleDao().getSalesByStatus(false).size

    // ─── el worker real ───────────────────────────────────────────────────

    private suspend fun correrWorker(
        api: VentasApi,
        saleId: String = SALE_ID
    ): ListenableWorker.Result {
        val inputData = androidx.work.Data.Builder()
            .putString("local_sale_id", saleId)
            .putString("user_email", EMAIL)
            .build()

        val worker = TestListenableWorkerBuilder<PendingLocalSalesWorker>(context, inputData)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = PendingLocalSalesWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    resolveVendedoresForEmail = { Pair(vendedores, 3) },
                    ventasApi = api,
                    uploadFailureRepository = RoomUploadFailureRepository(db.localSaleDao()),
                    nowEpochMillis = { clock.now().toEpochMilli() },
                    // Sin latido: estas pruebas miden llave/conteo, no la carrera — ver
                    // `CorreccionCarreraTest` para el latido.
                    latidoDeSubidaMs = 0L
                )
            })
            .build()

        return (worker as PendingLocalSalesWorker).doWork()
    }

    private fun api(
        crear: suspend (String, RequestBody, List<MultipartBody.Part>) -> VentaDTO,
        obtener: suspend (String) -> VentaDTO = {
            throw AssertionError("obtenerVenta no debería llamarse en esta prueba")
        }
    ): VentasApi = object : VentasApi {
        override suspend fun crearVenta(
            idempotencyKey: String,
            datos: RequestBody,
            imagen: List<MultipartBody.Part>
        ): VentaDTO = crear(idempotencyKey, datos, imagen)

        override suspend fun obtenerVenta(id: String): VentaDTO = obtener(id)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // C. Idempotencia
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `la Idempotency-Key es igual al LOCAL_SALE_ID, no cambia entre intentos, y la columna sigue NULL`() =
        runTest {
            sembrarVenta()

            var llamadas = 0
            var idempotencyKeyCorrida1: String? = null
            var idempotencyKeyCorrida2: String? = null

            // Corrida 1: se pierde la señal.
            val corrida1 = correrWorker(
                api = api(crear = { key, _, _ ->
                    llamadas++
                    idempotencyKeyCorrida1 = key
                    throw IOException("sin señal")
                })
            )
            assertEquals(ListenableWorker.Result.retry(), corrida1)

            // Se corrige.
            val reclamo = reclamar()(SALE_ID)
            check(reclamo is ResultadoReclamo.Reclamada)
            guardar()(
                SALE_ID,
                reclamo.claimId,
                reclamo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
                reclamo.venta.productos,
                reclamo.venta.combos,
                EMAIL
            )

            // Corrida 2: la señal vuelve.
            val corrida2 = correrWorker(
                api = api(crear = { key, _, _ ->
                    llamadas++
                    idempotencyKeyCorrida2 = key
                    ventaDTO
                })
            )
            assertEquals(ListenableWorker.Result.success(), corrida2)

            assertEquals("crearVenta se llamó exactamente dos veces", 2, llamadas)
            assertEquals(
                "la llave NO cambia entre intentos",
                idempotencyKeyCorrida1,
                idempotencyKeyCorrida2
            )
            assertEquals(
                "la llave es el LOCAL_SALE_ID",
                SALE_ID,
                idempotencyKeyCorrida1
            )

            val filaFinal = saleDataSource.getSaleById(SALE_ID)!!
            assertNull(
                "IDEMPOTENCY_KEY sigue NULL: nunca se rotó, sólo se usó el default (LOCAL_SALE_ID)",
                filaFinal.IDEMPOTENCY_KEY
            )
            assertEquals(NOMBRE_CORREGIDO, filaFinal.NOMBRE_CLIENTE)
            assertTrue(filaFinal.ENVIADO)
            assertEquals("corregir no crea una segunda venta", 1, cuantasVentas())
        }

    // ═══════════════════════════════════════════════════════════════════════
    // D. El 2xx perdido
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `el 2xx perdido se reconcilia por GET en la segunda corrida, sin duplicar y sin bucle`() =
        runTest {
            sembrarVenta()

            var llamadasCrear = 0
            var llamadasObtener = 0

            // Corrida 1: el POST realmente llega al servidor (en un servidor real dejaría la
            // venta guardada), pero la respuesta se pierde antes de volver al teléfono.
            val corrida1 = correrWorker(
                api = api(crear = { _, _, _ ->
                    llamadasCrear++
                    throw IOException("la respuesta se perdió a medio camino")
                })
            )
            assertEquals(ListenableWorker.Result.retry(), corrida1)
            assertFalse(
                "un POST cuya respuesta se perdió no puede marcar enviada",
                saleDataSource.getSaleById(SALE_ID)!!.ENVIADO
            )

            // El dueño, sin saber que el servidor ya la tiene, corrige.
            val reclamo = reclamar()(SALE_ID)
            check(reclamo is ResultadoReclamo.Reclamada)
            guardar()(
                SALE_ID,
                reclamo.claimId,
                reclamo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
                reclamo.venta.productos,
                reclamo.venta.combos,
                EMAIL
            )
            assertEquals(1, saleDataSource.getSaleById(SALE_ID)!!.REVISION)

            // Corrida 2: la MISMA Idempotency-Key ya fue usada — el servidor rechaza como
            // repetida (409) y el `GET` de reconciliación la encuentra.
            val corrida2 = correrWorker(
                api = api(
                    crear = { _, _, _ ->
                        llamadasCrear++
                        throw HttpException(
                            retrofit2.Response.error<VentaDTO>(
                                409,
                                "{}".toResponseBody("application/json".toMediaTypeOrNull())
                            )
                        )
                    },
                    obtener = { id ->
                        llamadasObtener++
                        assertEquals(SALE_ID, id)
                        ventaDTO
                    }
                )
            )

            assertEquals(
                "reconciliada por GET: el resultado es éxito, no un reintento",
                ListenableWorker.Result.success(),
                corrida2
            )
            assertEquals("crearVenta: uno por corrida, dos en total", 2, llamadasCrear)
            assertEquals(
                "obtenerVenta se llamó una sola vez, en la reconciliación",
                1,
                llamadasObtener
            )

            val filaFinal = saleDataSource.getSaleById(SALE_ID)!!
            assertTrue("el GET probó que el servidor tiene la venta", filaFinal.ENVIADO)
            assertEquals("una sola venta: corregir no duplicó nada", 1, cuantasVentas())
            assertNull(
                "el registro de fallo quedó limpio tras reconciliar",
                filaFinal.LAST_UPLOAD_ERROR_CODE
            )
            assertNull(filaFinal.LAST_UPLOAD_HTTP_CODE)

            // EL HUECO QUE LA TASK 6B CIERRA — la aserción más importante de este archivo.
            //
            // El servidor se quedó con el cuerpo ORIGINAL de la corrida 1: la corrección nunca
            // viajó (el segundo POST fue rechazado con 409 antes de que su cuerpo importara) y
            // el GET sólo probó que LA VENTA existe, no QUÉ cuerpo tiene. Hasta la Task 6 esto
            // quedaba en silencio —la comparación se hacía contra el snapshot de la corrida 2,
            // que ya traía la corrección adentro, así que dentro de esa corrida nada divergía— y
            // esta misma prueba lo afirmaba al revés, con un `assertFalse`, documentando el hueco.
            //
            // Ahora la comparación se ancla a `REVISION_POSTEADA`: el PRIMER cuerpo emitido fue
            // el de REVISION=0 y la fila ya va en REVISION=1. Difieren, y la marca se pone. Si
            // alguien quita el ancla, esta aserción es la que se pone roja.
            assertTrue(
                "el servidor tiene el cuerpo original y el teléfono enseña la corrección: " +
                    "la divergencia tiene que quedar marcada, no en silencio",
                filaFinal.CORRECCION_NO_ENVIADA
            )
            assertEquals(
                "el ancla es la REVISION del PRIMER cuerpo posteado, no la de la corrida actual",
                0,
                filaFinal.REVISION_POSTEADA
            )
            assertEquals(
                "la corrección SÍ queda escrita localmente, aunque el servidor no la tenga",
                NOMBRE_CORREGIDO,
                filaFinal.NOMBRE_CLIENTE
            )
        }
}
