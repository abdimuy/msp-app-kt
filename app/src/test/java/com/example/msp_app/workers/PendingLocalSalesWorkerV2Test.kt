package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import com.example.msp_app.data.api.services.ventas.VentaDTO
import com.example.msp_app.data.api.services.ventas.VentasApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Unit tests for [PendingLocalSalesWorker] v2 upload path.
 *
 * All network calls are replaced by inline fake lambdas/implementations.
 * Room is backed by the in-memory database from [RoomTestBase].
 * Workers are constructed directly (bypassing WorkManager) via
 * [TestListenableWorkerBuilder] with a custom [WorkerFactory].
 */
class PendingLocalSalesWorkerV2Test : RoomTestBase() {

    private lateinit var context: Context
    private lateinit var saleDataSource: LocalSaleDataSource
    private lateinit var productDataSource: SaleProductLocalDataSource
    private lateinit var comboDataSource: ComboLocalDataSource

    /** A real image file on the Robolectric filesystem so the worker finds it. */
    private lateinit var fakeImageFile: File

    private val fakeVendedores = listOf(
        VendedorDTO(
            id = "v-uuid-1",
            usuario_id = "u-uuid-1",
            email = "vendedor@muebleriamsp.mx",
            nombre = "Carlos Vendedor"
        )
    )

    private val fakeVentaDTO = VentaDTO(
        id = "sale-001",
        situacion = "PENDIENTE",
        sincronizacion = "PENDIENTE",
        tipo_venta = "CREDITO",
        fecha_venta = "2026-03-06T12:00:00Z",
        created_at = "2026-03-06T12:00:01Z",
        updated_at = "2026-03-06T12:00:01Z"
    )

    @Before
    fun setUpWorkerPrereqs() {
        context = ApplicationProvider.getApplicationContext()

        // Firebase must be initialized before RemoteLogger (lazy field in the worker)
        // is accessed. In Robolectric the default app is not auto-initialized, so we
        // bootstrap it with stub credentials — Firestore writes will simply fail
        // silently (fire-and-forget coroutines inside RemoteLogger.log).
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

        // Create a real temp file so File(path).exists() returns true in the worker.
        fakeImageFile = File.createTempFile("receipt", ".jpg")
        fakeImageFile.writeBytes(ByteArray(16) { 0xFF.toByte() })
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds the worker directly, injecting the test seams, and runs [doWork].
     */
    private fun buildAndRunWorker(
        saleId: String = "sale-001",
        userEmail: String = "test@muebleriamsp.mx",
        resolver: suspend (String) -> Pair<List<VendedorDTO>, Int?> = { Pair(fakeVendedores, 3) },
        api: VentasApi = happyPathApi()
    ): ListenableWorker.Result {
        val inputData = androidx.work.Data.Builder()
            .putString("local_sale_id", saleId)
            .putString("user_email", userEmail)
            .build()

        val worker = TestListenableWorkerBuilder<PendingLocalSalesWorker>(context, inputData)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): ListenableWorker {
                    return PendingLocalSalesWorker(
                        appContext = appContext,
                        workerParams = workerParameters,
                        resolveVendedoresForEmail = resolver,
                        ventasApi = api
                    )
                }
            })
            .build()

        var result: ListenableWorker.Result = ListenableWorker.Result.failure()
        kotlinx.coroutines.runBlocking {
            result = (worker as PendingLocalSalesWorker).doWork()
        }
        return result
    }

    /** Returns a [VentasApi] that always succeeds with [fakeVentaDTO]. */
    private fun happyPathApi(): VentasApi = object : VentasApi {
        override suspend fun crearVenta(
            idempotencyKey: String,
            datos: RequestBody,
            imagen: List<MultipartBody.Part>
        ): VentaDTO = fakeVentaDTO

        override suspend fun obtenerVenta(id: String): VentaDTO = fakeVentaDTO
    }

    /**
     * Builds a [VentasApi] fake from two suspend lambdas. Tests that don't
     * exercise [obtenerVenta] should pass a body that throws so an
     * unexpected call fails the test loudly.
     */
    private fun fakeApi(
        crear: suspend (String, RequestBody, List<MultipartBody.Part>) -> VentaDTO,
        obtener: suspend (String) -> VentaDTO =
            { throw AssertionError("obtenerVenta should not be called in this test") }
    ): VentasApi = object : VentasApi {
        override suspend fun crearVenta(
            idempotencyKey: String,
            datos: RequestBody,
            imagen: List<MultipartBody.Part>
        ): VentaDTO = crear(idempotencyKey, datos, imagen)

        override suspend fun obtenerVenta(id: String): VentaDTO = obtener(id)
    }

    // ─── scenario setup ───────────────────────────────────────────────────────

    /**
     * Inserts a sale + 1 product + 1 real image into the in-memory DB and returns the
     * canonical saleId.
     */
    private suspend fun seedHappySale(saleId: String = "sale-001"): String {
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
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
        return saleId
    }

    // ─── tests ────────────────────────────────────────────────────────────────

    @Test
    fun upload_v2_happy_path_marks_sent() = runTest {
        val saleId = seedHappySale()

        // Track crearVenta call count and capture the idempotency key.
        var callCount = 0
        var capturedKey: String? = null
        val trackingApi = fakeApi(
            crear = { idempotencyKey, _, _ ->
                callCount++
                capturedKey = idempotencyKey
                fakeVentaDTO
            }
        )

        val result = buildAndRunWorker(saleId = saleId, api = trackingApi)

        assertEquals(ListenableWorker.Result.success(), result)

        val updatedSale = saleDataSource.getSaleById(saleId)
        assertNotNull(updatedSale)
        assert(updatedSale!!.ENVIADO) { "ENVIADO should be true after successful upload" }

        assertEquals("crearVenta should be called exactly once", 1, callCount)
        assertEquals("Idempotency-Key must equal the saleId", saleId, capturedKey)

        val products = productDataSource.getProductsForSale(saleId)
        assertEquals(1, products.size)
        assertNotNull("SERVER_UUID must be populated after upload", products[0].SERVER_UUID)
    }

    @Test
    fun upload_v2_no_images_returns_failure() = runTest {
        val saleId = "sale-no-img"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = saleId, articuloId = 200)
        )
        // No images inserted.

        var apiCalled = false
        val trackingApi = fakeApi(
            crear = { _, _, _ ->
                apiCalled = true
                fakeVentaDTO
            }
        )

        val result = buildAndRunWorker(saleId = saleId, api = trackingApi)

        assertEquals(ListenableWorker.Result.failure(), result)
        assertFalse("crearVenta must not be called when there are no images", apiCalled)

        val sale = saleDataSource.getSaleById(saleId)
        assertFalse("ENVIADO must stay false on failure", sale!!.ENVIADO)
    }

    @Test
    fun upload_v2_io_exception_returns_retry() = runTest {
        val saleId = seedHappySale("sale-io-err")

        val ioApi = fakeApi(
            crear = { _, _, _ -> throw IOException("simulated network failure") }
        )

        val result = buildAndRunWorker(saleId = saleId, api = ioApi)

        assertEquals(ListenableWorker.Result.retry(), result)

        val sale = saleDataSource.getSaleById(saleId)
        assertFalse("ENVIADO must stay false on network error", sale!!.ENVIADO)
    }

    @Test
    fun upload_v2_409_with_existing_venta_reconciles_via_get() = runTest {
        val saleId = seedHappySale("sale-409")

        val conflictApi = fakeApi(
            crear = { _, _, _ ->
                throw HttpException(
                    retrofit2.Response.error<VentaDTO>(
                        409,
                        "{}".toResponseBody("application/json".toMediaTypeOrNull())
                    )
                )
            },
            obtener = { fakeVentaDTO }
        )

        val result = buildAndRunWorker(saleId = saleId, api = conflictApi)

        assertEquals(
            "409 with venta present server-side must be reconciled via GET",
            ListenableWorker.Result.success(),
            result
        )

        val sale = saleDataSource.getSaleById(saleId)
        assert(sale!!.ENVIADO) { "ENVIADO must flip to true after reconcile via GET" }
    }

    @Test
    fun upload_v2_4xx_with_existing_venta_reconciles_via_get() = runTest {
        val saleId = seedHappySale("sale-422")

        val api = fakeApi(
            crear = { _, _, _ ->
                throw HttpException(
                    retrofit2.Response.error<VentaDTO>(
                        422,
                        "{}".toResponseBody("application/json".toMediaTypeOrNull())
                    )
                )
            },
            obtener = { fakeVentaDTO }
        )

        val result = buildAndRunWorker(saleId = saleId, api = api)

        assertEquals(
            "Any 4xx with venta present server-side must reconcile via GET",
            ListenableWorker.Result.success(),
            result
        )

        val sale = saleDataSource.getSaleById(saleId)
        assert(sale!!.ENVIADO) { "ENVIADO must flip to true after reconcile via GET" }
    }

    @Test
    fun upload_v2_4xx_with_404_on_verify_returns_retry() = runTest {
        val saleId = seedHappySale("sale-422-404")

        val api = fakeApi(
            crear = { _, _, _ ->
                throw HttpException(
                    retrofit2.Response.error<VentaDTO>(
                        422,
                        "{}".toResponseBody("application/json".toMediaTypeOrNull())
                    )
                )
            },
            obtener = {
                throw HttpException(
                    retrofit2.Response.error<VentaDTO>(
                        404,
                        "{}".toResponseBody("application/json".toMediaTypeOrNull())
                    )
                )
            }
        )

        val result = buildAndRunWorker(saleId = saleId, api = api)

        assertEquals(
            "4xx + GET 404 means venta truly does not exist — retry",
            ListenableWorker.Result.retry(),
            result
        )

        val sale = saleDataSource.getSaleById(saleId)
        assertFalse("ENVIADO must stay false when venta is absent server-side", sale!!.ENVIADO)
    }

    @Test
    fun upload_v2_5xx_with_get_failure_returns_retry() = runTest {
        val saleId = seedHappySale("sale-500-io")

        val api = fakeApi(
            crear = { _, _, _ ->
                throw HttpException(
                    retrofit2.Response.error<VentaDTO>(
                        500,
                        "{}".toResponseBody("application/json".toMediaTypeOrNull())
                    )
                )
            },
            obtener = { throw IOException("verify endpoint also down") }
        )

        val result = buildAndRunWorker(saleId = saleId, api = api)

        assertEquals(
            "Inconclusive verify (transient error) must result in retry",
            ListenableWorker.Result.retry(),
            result
        )

        val sale = saleDataSource.getSaleById(saleId)
        assertFalse("ENVIADO must stay false on inconclusive verify", sale!!.ENVIADO)
    }

    @Test
    fun upload_v2_no_camioneta_returns_failure() = runTest {
        val saleId = seedHappySale("sale-no-vend")

        var apiCalled = false
        val trackingApi = fakeApi(
            crear = { _, _, _ ->
                apiCalled = true
                fakeVentaDTO
            }
        )

        // Resolver returns empty list — no camioneta assigned.
        val emptyResolver: suspend (String) -> Pair<List<VendedorDTO>, Int?> = {
            Pair(emptyList(), null)
        }

        val result = buildAndRunWorker(
            saleId = saleId,
            resolver = emptyResolver,
            api = trackingApi
        )

        assertEquals(ListenableWorker.Result.failure(), result)
        assertFalse("crearVenta must not be called when vendedores list is empty", apiCalled)

        val sale = saleDataSource.getSaleById(saleId)
        assertFalse("ENVIADO must stay false when no camioneta", sale!!.ENVIADO)
    }
}
