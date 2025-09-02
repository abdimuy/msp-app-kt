package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.localSales.LocalSalesApi
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.models.sale.localsale.LocalSaleMappers
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
    private val api = ApiProvider.create(LocalSalesApi::class.java)
    private val mappers = LocalSaleMappers()

    override suspend fun doWork(): Result {
        val saleId = inputData.getString("local_sale_id")
            ?: return Result.failure().also {
                Log.e("PendingLocalSalesWorker", "No se proporcionó local_sale_id")
            }

        val userEmail = inputData.getString("user_email")
            ?: return Result.failure().also {
                Log.e("PendingLocalSalesWorker", "No se proporcionó user_email")
            }

        val sale = localSaleStore.getSaleById(saleId)
            ?: return Result.failure().also {
                Log.e("PendingLocalSalesWorker", "Venta local no encontrada: $saleId")
            }

        return try {
            Log.d(
                "PendingLocalSalesWorker",
                "Enviando venta local: ${sale.LOCAL_SALE_ID} para usuario: $userEmail"
            )

            val products = saleProductStore.getProductsForSale(saleId)
            val images = localSaleStore.getImagesForSale(saleId)

            val request = with(mappers) {
                sale.toServerRequest(products, userEmail)
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
                        else -> {
                            Log.w(
                                "PendingLocalSalesWorker",
                                "Formato no soportado: ${file.extension} para ${file.name}"
                            )
                            "image/jpeg" // fallback
                        }
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

            Log.d(
                "PendingLocalSalesWorker",
                "Respuesta exitosa del servidor - Success: ${response.success}, Message: ${response.message}"
            )

            localSaleStore.changeSaleStatus(saleId, true)
            Log.d(
                "PendingLocalSalesWorker",
                "Venta local marcada como enviada: ${sale.LOCAL_SALE_ID}"
            )

            if (!response.success && response.message != null) {
                Log.w(
                    "PendingLocalSalesWorker",
                    "Servidor procesó la venta pero reportó: ${response.message}"
                )
            }

            Result.success()

        } catch (e: Exception) {
            if (e is retrofit2.HttpException && e.code() == 409) {
                Log.w(
                    "PendingLocalSalesWorker",
                    "Venta ${sale.LOCAL_SALE_ID} ya existe en el servidor (409 Conflict), marcando como enviada"
                )
                localSaleStore.changeSaleStatus(saleId, true)
                return Result.success()
            }

            Log.e("PendingLocalSalesWorker", "Error al enviar venta local ${sale.LOCAL_SALE_ID}", e)
            Result.retry()
        }
    }
}