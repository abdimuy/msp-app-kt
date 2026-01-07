package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.localSales.LocalSalesApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.models.sale.localsale.LocalSaleMappers
import com.example.msp_app.core.logging.RemoteLogger
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class PendingLocalSalesWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val localSaleStore = LocalSaleDataSource(appContext)
    private val saleProductStore = SaleProductLocalDataSource(appContext)
    private val comboDataSource = ComboLocalDataSource(appContext)
    private val api = ApiProvider.create(LocalSalesApi::class.java)
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

        return try {
            val products = saleProductStore.getProductsForSale(saleId)
            val images = localSaleStore.getImagesForSale(saleId)
            val combos = comboDataSource.getCombosForSale(saleId)

            val request = with(mappers) {
                sale.toServerRequest(products, userEmail, combos)
            }
            val jsonData = Gson().toJson(request)
            val datosRequestBody = jsonData.toRequestBody("application/json".toMediaTypeOrNull())

            val imageParts = mutableListOf<MultipartBody.Part>()
            images.forEach { image ->
                val file = File(image.IMAGE_URI)
                if (file.exists()) {
                    val mimeType = when (file.extension.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "gif" -> "image/gif"
                        else -> "image/jpeg"
                    }

                    val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                    val imagePart =
                        MultipartBody.Part.createFormData("imagenes", file.name, requestFile)
                    imageParts.add(imagePart)
                } else {
                    Log.w("PendingLocalSalesWorker", "Imagen no encontrada: ${image.IMAGE_URI}")
                }
            }

            val response = api.saveLocalSale(datosRequestBody, imageParts)
            localSaleStore.changeSaleStatus(saleId, true)

            logger.info(
                module = "SALES_WORKER",
                action = "UPLOAD_SUCCESS",
                message = "Venta enviada exitosamente",
                data = mapOf(
                    "saleId" to saleId,
                    "imageCount" to imageParts.size,
                    "comboCount" to combos.size,
                    "productCount" to products.size
                )
            )

            Result.success()

        } catch (e: Exception) {
            if (e is retrofit2.HttpException && e.code() == 409) {
                localSaleStore.changeSaleStatus(saleId, true)
                return Result.success()
            }

            Log.e("PendingLocalSalesWorker", "Error al enviar venta local ${sale.LOCAL_SALE_ID}", e)
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