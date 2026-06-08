package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.sync.ventas.VendedorResolver
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import com.example.msp_app.data.api.services.ventas.VentasApi
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.models.sale.localsale.LocalSaleMappers
import com.example.msp_app.features.camionetaAssignment.data.repository.CamionetaAssignmentRepository
import com.example.msp_app.features.sales.upload.data.RoomUploadFailureRepository
import com.example.msp_app.features.sales.upload.domain.UploadFailure
import com.example.msp_app.features.sales.upload.domain.UploadFailureClassification
import com.example.msp_app.features.sales.upload.domain.UploadFailureClassifier
import com.example.msp_app.features.sales.upload.domain.UploadFailureRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class PendingLocalSalesWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    /**
     * Resolves vendedores and the camioneta id for a given user e-mail.
     * Returns a [Pair] of (vendedores list, camionetaId). camionetaId may be
     * null when the user has no camioneta assigned.
     *
     * Defaults to the production Firestore + Go API flow.
     * Overridable in tests without touching WorkManager's factory.
     */
    @VisibleForTesting
    internal val resolveVendedoresForEmail:
    suspend (userEmail: String) -> Pair<List<VendedorDTO>, Int?> =
        run {
            val repo = CamionetaAssignmentRepository()
            val resolver = VendedorResolver(repo);
            { email ->
                val allUsers = repo.getAllUsers().getOrElse { throw it }
                val camionetaId = allUsers.firstOrNull { it.EMAIL == email }?.CAMIONETA_ASIGNADA
                Pair(resolver.resolve(camionetaId), camionetaId)
            }
        },
    /**
     * Retrofit service for `POST /v2/ventas`.
     * Overridable in tests to inject a MockWebServer-backed or fake implementation.
     */
    @VisibleForTesting
    internal val ventasApi: VentasApi = V2ApiProvider.create(VentasApi::class.java),
    /**
     * Persists/clears the per-sale upload-failure record and the rotating
     * Idempotency-Key. See [UploadFailureRepository] for the contract.
     * Default uses the Room DAO via the app database singleton.
     */
    @VisibleForTesting
    internal val uploadFailureRepository: UploadFailureRepository =
        RoomUploadFailureRepository(AppDatabase.getInstance(appContext).localSaleDao()),
    /**
     * Wall-clock provider for stamping LAST_UPLOAD_AT. Overridable so tests
     * can assert on a deterministic timestamp.
     */
    @VisibleForTesting
    internal val nowEpochMillis: () -> Long = System::currentTimeMillis
) : CoroutineWorker(appContext, workerParams) {

    private val localSaleStore = LocalSaleDataSource(appContext)
    private val saleProductStore = SaleProductLocalDataSource(appContext)
    private val comboDataSource = ComboLocalDataSource(appContext)
    private val mappers = LocalSaleMappers()
    private val logger: RemoteLogger by lazy { RemoteLogger.getInstance(appContext) }

    override suspend fun doWork(): Result {
        val saleId = inputData.getString("local_sale_id")
            ?: return Result.failure().also {
                Log.e("PendingLocalSalesWorker", "No se proporcionó local_sale_id")
                logger.error(
                    module = "SALES_WORKER",
                    action = "MISSING_SALE_ID",
                    message = "Worker iniciado sin local_sale_id"
                )
            }

        val userEmail = inputData.getString("user_email")
            ?: return Result.failure().also {
                Log.e("PendingLocalSalesWorker", "No se proporcionó user_email")
                logger.error(
                    module = "SALES_WORKER",
                    action = "MISSING_USER_EMAIL",
                    message = "Worker iniciado sin user_email",
                    data = mapOf("saleId" to saleId)
                )
            }

        val sale = localSaleStore.getSaleById(saleId)
            ?: return Result.failure().also {
                Log.e("PendingLocalSalesWorker", "Venta local no encontrada: $saleId")
                logger.error(
                    module = "SALES_WORKER",
                    action = "SALE_NOT_FOUND",
                    message = "Venta no encontrada en base de datos local",
                    data = mapOf("saleId" to saleId, "userEmail" to userEmail)
                )
            }

        Log.d(
            "PendingLocalSalesWorker",
            "DEBUG_SALE saleId=$saleId" +
                " COLONIA='${sale.COLONIA}'" +
                " POBLACION='${sale.POBLACION}'" +
                " CIUDAD='${sale.CIUDAD}'" +
                " TIEMPO_A_CORTO_PLAZOMESES=${sale.TIEMPO_A_CORTO_PLAZOMESES}" +
                " TIPO_VENTA='${sale.TIPO_VENTA}'" +
                " FREC_PAGO='${sale.FREC_PAGO}'" +
                " DIA_COBRANZA='${sale.DIA_COBRANZA}'"
        )
        return try {
            val images = localSaleStore.getImagesForSale(saleId)
            if (images.isEmpty()) {
                Log.e("PendingLocalSalesWorker", "Venta sin imágenes: $saleId")
                logger.error(
                    module = "SALES_WORKER",
                    action = "NO_IMAGES",
                    message = "La venta no tiene imágenes adjuntas",
                    data = mapOf("saleId" to saleId)
                )
                return Result.failure()
            }

            val (rawVendedores, camionetaId) = resolveVendedoresForEmail(userEmail)
            // Deterministic snapshot UUID per (saleId, usuario_id) so retries
            // produce the SAME body → no idempotency_key_mismatch.
            val vendedores = rawVendedores.map { v ->
                v.copy(
                    id = UUID.nameUUIDFromBytes(
                        "$saleId|${v.usuario_id}".toByteArray()
                    ).toString()
                )
            }
            if (vendedores.isEmpty()) {
                Log.e(
                    "PendingLocalSalesWorker",
                    "No se resolvieron vendedores para userEmail $userEmail"
                )
                logger.error(
                    module = "SALES_WORKER",
                    action = "NO_VENDEDORES",
                    message = "No se pudieron resolver los vendedores de la camioneta",
                    data = mapOf("saleId" to saleId, "userEmail" to userEmail)
                )
                return Result.failure()
            }

            val products = saleProductStore.getProductsForSale(saleId)
            val combos = comboDataSource.getCombosForSale(saleId)

            // Persist stable SERVER_UUIDs before building the body so retries
            // use the same UUIDs (idempotency).
            for (p in products) {
                if (p.SERVER_UUID == null) {
                    saleProductStore.updateServerUuid(
                        p.LOCAL_SALE_ID,
                        p.ARTICULO_ID,
                        UUID.randomUUID().toString()
                    )
                }
            }
            for (c in combos) {
                if (c.SERVER_UUID == null) {
                    comboDataSource.updateServerUuid(
                        c.COMBO_ID,
                        c.LOCAL_SALE_ID,
                        UUID.randomUUID().toString()
                    )
                }
            }
            for (img in images) {
                if (img.SERVER_UUID == null) {
                    localSaleStore.updateImageServerUuid(
                        img.LOCAL_SALE_IMAGE_ID,
                        UUID.randomUUID().toString()
                    )
                }
            }

            // Re-read after persisting so UUIDs are populated.
            val productsWithUuids = saleProductStore.getProductsForSale(saleId)
            val combosWithUuids = comboDataSource.getCombosForSale(saleId)

            val body = with(mappers) {
                sale.toV2VentaBody(
                    products = productsWithUuids,
                    combos = combosWithUuids,
                    vendedores = vendedores,
                    camionetaId = camionetaId
                )
            }

            val jsonData = Gson().toJson(body)
            val datosRequestBody = jsonData.toRequestBody("application/json".toMediaTypeOrNull())

            val imageParts = mutableListOf<MultipartBody.Part>()
            images.forEach { image ->
                val file = File(image.IMAGE_URI)
                if (file.exists()) {
                    val mimeType = when (file.extension.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "gif" -> "image/gif"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                    imageParts.add(
                        MultipartBody.Part.createFormData("imagen", file.name, requestFile)
                    )
                } else {
                    Log.w("PendingLocalSalesWorker", "Imagen no encontrada: ${image.IMAGE_URI}")
                }
            }

            // Idempotency-Key defaults to the saleId but can be rotated by
            // edit-and-retry so a corrected body avoids cache mismatch.
            val idempotencyKey = uploadFailureRepository.currentIdempotencyKey(
                saleId = saleId,
                defaultKey = saleId
            )

            val response = ventasApi.crearVenta(
                idempotencyKey = idempotencyKey,
                datos = datosRequestBody,
                imagen = imageParts
            )

            localSaleStore.changeSaleStatus(saleId, true)
            // Clear any prior upload-failure tracking so the UI doesn't keep
            // showing a stale error after a successful retry.
            uploadFailureRepository.clearFailure(saleId)

            logger.info(
                module = "SALES_WORKER",
                action = "UPLOAD_SUCCESS",
                message = "Venta enviada exitosamente al backend v2",
                data = mapOf(
                    "saleId" to saleId,
                    "serverVentaId" to response.id,
                    "situacion" to response.situacion,
                    "attemptCount" to runAttemptCount,
                    "imageCount" to imageParts.size,
                    "comboCount" to combos.size,
                    "productCount" to products.size
                )
            )

            Result.success()
        } catch (e: HttpException) {
            val errBody = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            val parsed = parseProblemDetails(errBody)
            Log.e(
                "PendingLocalSalesWorker",
                "Error HTTP ${e.code()} al enviar venta $saleId  body=$errBody",
                e
            )
            logger.error(
                module = "SALES_WORKER",
                action = "HTTP_ERROR",
                message = "Error HTTP ${e.code()} al enviar venta: $errBody",
                error = e,
                data = mapOf(
                    "saleId" to saleId,
                    "attemptCount" to runAttemptCount,
                    "responseBody" to (errBody ?: "")
                )
            )

            // Reconcile via GET: la venta puede haber sido creada server-side por
            // replay-with admin (failed_intents), por una corrida anterior cuyo 2xx
            // no nos llegó, o por algún otro flujo asíncrono. Antes de gastar otro
            // intento (o rendirnos en un 4xx permanente), verificamos.
            val existeEnServer: Boolean? = try {
                val existing = ventasApi.obtenerVenta(saleId)
                Log.i(
                    "PendingLocalSalesWorker",
                    "Venta $saleId encontrada en servidor (situacion=${existing.situacion})"
                )
                true
            } catch (verifyErr: HttpException) {
                if (verifyErr.code() == 404) false else null
            } catch (_: Exception) {
                null
            }

            if (existeEnServer == true) {
                localSaleStore.changeSaleStatus(saleId, true)
                uploadFailureRepository.clearFailure(saleId)
                logger.info(
                    module = "SALES_WORKER",
                    action = "RECONCILED_VIA_GET",
                    message = "Venta ya existía server-side; reconciliada sin reintentar",
                    data = mapOf(
                        "saleId" to saleId,
                        "originalHttpCode" to e.code(),
                        "attemptCount" to runAttemptCount
                    )
                )
                Result.success()
            } else {
                val classification = UploadFailureClassifier.classify(e.code())
                uploadFailureRepository.recordFailure(
                    saleId = saleId,
                    failure = UploadFailure(
                        httpCode = e.code(),
                        errorCode = parsed.code,
                        errorMessage = parsed.detail,
                        classification = classification,
                        atEpochMillis = nowEpochMillis()
                    )
                )

                if (classification == UploadFailureClassification.PERMANENT) {
                    logger.info(
                        module = "SALES_WORKER",
                        action = "PERMANENT_FAILURE",
                        message = "Venta rechazada con error permanente; no se reintentará",
                        data = mapOf(
                            "saleId" to saleId,
                            "httpCode" to e.code(),
                            "errorCode" to (parsed.code ?: ""),
                            "errorMessage" to (parsed.detail ?: "")
                        )
                    )
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: IOException) {
            Log.w(
                "PendingLocalSalesWorker",
                "Error de red al enviar venta $saleId, reintentando",
                e
            )
            logger.error(
                module = "SALES_WORKER",
                action = "NETWORK_ERROR",
                message = "Error de red al enviar venta: ${e.message}",
                error = e,
                data = mapOf("saleId" to saleId, "attemptCount" to runAttemptCount)
            )
            // Network failures are transient by definition. Persist for UI
            // visibility but keep retrying.
            uploadFailureRepository.recordFailure(
                saleId = saleId,
                failure = UploadFailure(
                    httpCode = 0,
                    errorCode = "network_error",
                    errorMessage = e.message ?: "error de red",
                    classification = UploadFailureClassification.TRANSIENT,
                    atEpochMillis = nowEpochMillis()
                )
            )
            Result.retry()
        } catch (e: Exception) {
            Log.e("PendingLocalSalesWorker", "Error al enviar venta local $saleId", e)
            logger.error(
                module = "SALES_WORKER",
                action = "UPLOAD_ERROR",
                message = "Error al enviar venta: ${e.message}",
                error = e,
                data = mapOf("saleId" to saleId, "attemptCount" to runAttemptCount)
            )
            Result.retry()
        }
    }

    /** Parsed Problem-Details fragment from an RFC 7807-style error response. */
    private data class ProblemDetails(val code: String?, val detail: String?)

    /**
     * Best-effort parse of an `application/problem+json` body. Returns
     * (null, null) if the body is missing, malformed, or doesn't carry
     * the expected fields. Never throws — diagnostic parsing must not
     * upstage the original HTTP error.
     */
    private fun parseProblemDetails(body: String?): ProblemDetails {
        if (body.isNullOrBlank()) return ProblemDetails(null, null)
        return try {
            val obj = JsonParser.parseString(body).asJsonObject
            ProblemDetails(
                code = obj["code"]?.takeUnless { it.isJsonNull }?.asString,
                detail = obj["detail"]?.takeUnless { it.isJsonNull }?.asString
                    ?: obj["message"]?.takeUnless { it.isJsonNull }?.asString
            )
        } catch (_: Exception) {
            ProblemDetails(null, null)
        }
    }
}
