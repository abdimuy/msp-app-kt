package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import com.example.msp_app.data.api.services.ventas.VentaDTO
import com.example.msp_app.data.api.services.ventas.VentasApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.features.sales.upload.data.RoomUploadFailureRepository
import com.example.msp_app.features.sales.upload.domain.UploadFailureRepository
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
        api: VentasApi = happyPathApi(),
        repository: UploadFailureRepository = RoomUploadFailureRepository(db.localSaleDao()),
        now: () -> Long = { 1_700_000_000_000L }
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
                        ventasApi = api,
                        uploadFailureRepository = repository,
                        nowEpochMillis = now
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
    fun upload_v2_no_products_returns_failure() = runTest {
        // Char-test OLD→NEW money-path. ANTES: sin productos el worker armaba el
        // body con la lista vacía y LLAMABA a crearVenta (venta sin renglones en
        // Microsip). AHORA el guard corta antes de subir.
        val saleId = "sale-no-prod"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(
                imageId = "img-np",
                saleId = saleId,
                imageUri = fakeImageFile.absolutePath
            )
        )
        // No products inserted.

        var apiCalled = false
        val trackingApi = fakeApi(
            crear = { _, _, _ ->
                apiCalled = true
                fakeVentaDTO
            }
        )

        val result = buildAndRunWorker(saleId = saleId, api = trackingApi)

        assertEquals(ListenableWorker.Result.failure(), result)
        assertFalse("crearVenta must not be called when there are no products", apiCalled)

        val sale = saleDataSource.getSaleById(saleId)
        assertFalse("ENVIADO must stay false on failure", sale!!.ENVIADO)
    }

    @Test
    fun upload_v2_combos_only_no_products_returns_failure() = runTest {
        // El contrato v2 exige >=1 producto AUNQUE haya combos (el e2e
        // combo→juego del backend siempre manda productos junto al combo). Una
        // venta solo-combos NO es un vacío legítimo para la subida: no se sube.
        val saleId = "sale-combos-only"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        comboDataSource.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = saleId)
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(
                imageId = "img-co",
                saleId = saleId,
                imageUri = fakeImageFile.absolutePath
            )
        )
        // No products inserted.

        var apiCalled = false
        val trackingApi = fakeApi(
            crear = { _, _, _ ->
                apiCalled = true
                fakeVentaDTO
            }
        )

        val result = buildAndRunWorker(saleId = saleId, api = trackingApi)

        assertEquals(ListenableWorker.Result.failure(), result)
        assertFalse("crearVenta must not be called for a combos-only sale", apiCalled)
    }

    @Test
    fun upload_v2_one_product_no_combos_uploads_ok() = runTest {
        // Vacío legítimo de combos: una venta con >=1 producto y CERO combos SÍ
        // se sube. El guard sólo castiga productos vacíos, no combos vacíos.
        val saleId = seedHappySale("sale-prod-no-combo")

        var callCount = 0
        val trackingApi = fakeApi(crear = { _, _, _ ->
            callCount++
            fakeVentaDTO
        })

        val result = buildAndRunWorker(saleId = saleId, api = trackingApi)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("una venta con productos y sin combos se sube normal", 1, callCount)
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
    fun upload_v2_422_validation_with_404_on_verify_returns_failure_permanent() = runTest {
        val saleId = seedHappySale("sale-422-404")

        val problemBody =
            """{"code":"plazo_invalido","detail":"el plazo en meses debe ser mayor a cero","title":"Unprocessable Entity"}"""
        val api = fakeApi(
            crear = { _, _, _ ->
                throw HttpException(
                    retrofit2.Response.error<VentaDTO>(
                        422,
                        problemBody.toResponseBody("application/problem+json".toMediaTypeOrNull())
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
            "422 validation + GET 404 means a permanent client error — worker must surrender",
            ListenableWorker.Result.failure(),
            result
        )

        val sale = saleDataSource.getSaleById(saleId)!!
        assertFalse("ENVIADO must stay false when venta is rejected permanently", sale.ENVIADO)
        assertEquals("LAST_UPLOAD_HTTP_CODE must be persisted", 422, sale.LAST_UPLOAD_HTTP_CODE)
        assertEquals("plazo_invalido", sale.LAST_UPLOAD_ERROR_CODE)
        assertEquals(
            "el plazo en meses debe ser mayor a cero",
            sale.LAST_UPLOAD_ERROR_MESSAGE
        )
        assertEquals(true, sale.LAST_UPLOAD_PERMANENT)
    }

    @Test
    fun upload_v2_500_transient_with_404_on_verify_returns_retry_and_persists_transient() =
        runTest {
            val saleId = seedHappySale("sale-500-404")

            val api = fakeApi(
                crear = { _, _, _ ->
                    throw HttpException(
                        retrofit2.Response.error<VentaDTO>(
                            500,
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
                "5xx is transient — worker must retry",
                ListenableWorker.Result.retry(),
                result
            )

            val sale = saleDataSource.getSaleById(saleId)!!
            assertFalse(sale.ENVIADO)
            assertEquals(500, sale.LAST_UPLOAD_HTTP_CODE)
            assertEquals(
                "500 must be classified as transient so the user sees it but the worker keeps retrying",
                false,
                sale.LAST_UPLOAD_PERMANENT
            )
        }

    @Test
    fun upload_v2_reconcile_via_get_clears_persisted_failure() = runTest {
        val saleId = seedHappySale("sale-reconcile-clears")

        // Pre-seed a failure from an earlier attempt.
        val repo = RoomUploadFailureRepository(db.localSaleDao())
        repo.recordFailure(
            saleId,
            com.example.msp_app.features.sales.upload.domain.UploadFailure(
                httpCode = 500,
                errorCode = "boom",
                errorMessage = "previously transient",
                classification =
                com.example.msp_app.features.sales.upload.domain.UploadFailureClassification.TRANSIENT,
                atEpochMillis = 1L
            )
        )

        // Now the POST fails again but the GET reconcile finds the venta.
        val api = fakeApi(
            crear = { _, _, _ ->
                throw HttpException(
                    retrofit2.Response.error<VentaDTO>(
                        500,
                        "{}".toResponseBody("application/json".toMediaTypeOrNull())
                    )
                )
            },
            obtener = { fakeVentaDTO }
        )

        val result = buildAndRunWorker(saleId = saleId, api = api, repository = repo)
        assertEquals(ListenableWorker.Result.success(), result)

        val sale = saleDataSource.getSaleById(saleId)!!
        assert(sale.ENVIADO) { "ENVIADO must flip to true after GET reconcile" }
        assertEquals(
            "Reconcile-via-GET must clear the stale failure so the UI doesn't keep showing it",
            null,
            sale.LAST_UPLOAD_HTTP_CODE
        )
        assertEquals(null, sale.LAST_UPLOAD_ERROR_CODE)
        assertEquals(null, sale.LAST_UPLOAD_ERROR_MESSAGE)
        assertEquals(null, sale.LAST_UPLOAD_PERMANENT)
    }

    @Test
    fun upload_v2_io_exception_persists_network_error_transient() = runTest {
        val saleId = seedHappySale("sale-io-persist")

        val ioApi = fakeApi(
            crear = { _, _, _ -> throw IOException("connection reset") }
        )

        val result = buildAndRunWorker(saleId = saleId, api = ioApi)

        assertEquals(ListenableWorker.Result.retry(), result)

        val sale = saleDataSource.getSaleById(saleId)!!
        assertEquals(0, sale.LAST_UPLOAD_HTTP_CODE)
        assertEquals("network_error", sale.LAST_UPLOAD_ERROR_CODE)
        assertEquals("connection reset", sale.LAST_UPLOAD_ERROR_MESSAGE)
        assertEquals(false, sale.LAST_UPLOAD_PERMANENT)
    }

    @Test
    fun upload_v2_happy_path_clears_prior_failure() = runTest {
        val saleId = seedHappySale("sale-cleared-on-success")

        val repo = RoomUploadFailureRepository(db.localSaleDao())
        repo.recordFailure(
            saleId,
            com.example.msp_app.features.sales.upload.domain.UploadFailure(
                httpCode = 422,
                errorCode = "plazo_invalido",
                errorMessage = "el plazo en meses debe ser mayor a cero",
                classification =
                com.example.msp_app.features.sales.upload.domain.UploadFailureClassification.PERMANENT,
                atEpochMillis = 1L
            )
        )

        val result = buildAndRunWorker(saleId = saleId, repository = repo)
        assertEquals(ListenableWorker.Result.success(), result)

        val sale = saleDataSource.getSaleById(saleId)!!
        assert(sale.ENVIADO)
        assertEquals(
            "Successful upload must clear any prior failure so the UI doesn't show a stale error",
            null,
            sale.LAST_UPLOAD_ERROR_MESSAGE
        )
    }

    @Test
    fun upload_v2_uses_rotated_idempotency_key_when_set() = runTest {
        val saleId = seedHappySale("sale-rotated-key")

        // Rotate the key beforehand — simulates edit-and-retry having minted
        // a fresh Idempotency-Key before this worker run.
        val repo = RoomUploadFailureRepository(db.localSaleDao())
        repo.rotateIdempotencyKey(saleId, "rotated-uuid-99")

        var capturedKey: String? = null
        val trackingApi = fakeApi(
            crear = { idempotencyKey, _, _ ->
                capturedKey = idempotencyKey
                fakeVentaDTO
            }
        )

        val result = buildAndRunWorker(saleId = saleId, api = trackingApi, repository = repo)
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            "Worker must send the rotated Idempotency-Key when one has been set",
            "rotated-uuid-99",
            capturedKey
        )
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
