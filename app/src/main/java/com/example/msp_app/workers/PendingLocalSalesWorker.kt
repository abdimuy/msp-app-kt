package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.sync.ventas.VendedorResolver
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.ventas.VentasApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.models.sale.localsale.LocalSaleMappers
import com.example.msp_app.features.camionetaAssignment.data.repository.CamionetaAssignmentRepository
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class PendingLocalSalesWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val localSaleStore = LocalSaleDataSource(appContext)
    private val saleProductStore = SaleProductLocalDataSource(appContext)
    private val comboDataSource = ComboLocalDataSource(appContext)
    private val mappers = LocalSaleMappers()
    private val logger: RemoteLogger by lazy { RemoteLogger.getInstance(appContext) }
    private val camionetaRepo = CamionetaAssignmentRepository()
    private val vendedorResolver = VendedorResolver(camionetaRepo)

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

            val allUsers = camionetaRepo.getAllUsers().getOrElse { throw it }
            val currentUser = allUsers.firstOrNull { it.EMAIL == userEmail }
            val camionetaId = currentUser?.CAMIONETA_ASIGNADA

            val vendedores = vendedorResolver.resolve(camionetaId)
            if (vendedores.isEmpty()) {
                Log.e(
                    "PendingLocalSalesWorker",
                    "No se resolvieron vendedores para camioneta $camionetaId"
                )
                logger.error(
                    module = "SALES_WORKER",
                    action = "NO_VENDEDORES",
                    message = "No se pudieron resolver los vendedores de la camioneta",
                    data = mapOf(
                        "saleId" to saleId,
                        "camionetaId" to (camionetaId?.toString() ?: "null")
                    )
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

            val response = V2ApiProvider.create(VentasApi::class.java).crearVenta(
                idempotencyKey = saleId,
                datos = datosRequestBody,
                imagen = imageParts
            )

            localSaleStore.changeSaleStatus(saleId, true)

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
}
