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
        val trackingApi = object : VentasApi {
            override suspend fun crearVenta(
                idempotencyKey: String,
                datos: RequestBody,
                imagen: List<MultipartBody.Part>
            ): VentaDTO {
                callCount++
                capturedKey = idempotencyKey
                return fakeVentaDTO
            }
        }

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
        val trackingApi = object : VentasApi {
            override suspend fun crearVenta(
                idempotencyKey: String,
                datos: RequestBody,
                imagen: List<MultipartBody.Part>
            ): VentaDTO {
                apiCalled = true
                return fakeVentaDTO
            }
        }

        val result = buildAndRunWorker(saleId = saleId, api = trackingApi)

        assertEquals(ListenableWorker.Result.failure(), result)
        assertFalse("crearVenta must not be called when there are no images", apiCalled)

        val sale = saleDataSource.getSaleById(saleId)
        assertFalse("ENVIADO must stay false on failure", sale!!.ENVIADO)
    }

    @Test
    fun upload_v2_io_exception_returns_retry() = runTest {
        val saleId = seedHappySale("sale-io-err")

        val ioApi = object : VentasApi {
            override suspend fun crearVenta(
                idempotencyKey: String,
                datos: RequestBody,
                imagen: List<MultipartBody.Part>
            ): VentaDTO = throw IOException("simulated network failure")
        }

        val result = buildAndRunWorker(saleId = saleId, api = ioApi)

        assertEquals(ListenableWorker.Result.retry(), result)

        val sale = saleDataSource.getSaleById(saleId)
        assertFalse("ENVIADO must stay false on network error", sale!!.ENVIADO)
    }

    @Test
    fun upload_v2_409_returns_retry() = runTest {
        val saleId = seedHappySale("sale-409")

        val conflictApi = object : VentasApi {
            override suspend fun crearVenta(
                idempotencyKey: String,
                datos: RequestBody,
                imagen: List<MultipartBody.Part>
            ): VentaDTO = throw HttpException(
                retrofit2.Response.error<VentaDTO>(
                    409,
                    "{}".toResponseBody("application/json".toMediaTypeOrNull())
                )
            )
        }

        val result = buildAndRunWorker(saleId = saleId, api = conflictApi)

        assertEquals(
            "409 (idempotency_key_mismatch) must trigger retry, not be silently accepted",
            ListenableWorker.Result.retry(),
            result
        )

        val sale = saleDataSource.getSaleById(saleId)
        assertFalse("ENVIADO must stay false on 409", sale!!.ENVIADO)
    }

    @Test
    fun upload_v2_no_camioneta_returns_failure() = runTest {
        val saleId = seedHappySale("sale-no-vend")

        var apiCalled = false
        val trackingApi = object : VentasApi {
            override suspend fun crearVenta(
                idempotencyKey: String,
                datos: RequestBody,
                imagen: List<MultipartBody.Part>
            ): VentaDTO {
                apiCalled = true
                return fakeVentaDTO
            }
        }

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
