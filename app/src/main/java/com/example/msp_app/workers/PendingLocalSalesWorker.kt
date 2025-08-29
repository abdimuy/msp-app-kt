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

        val sale = localSaleStore.getSaleById(saleId)
            ?: return Result.failure().also {
                Log.e("PendingLocalSalesWorker", "Venta local no encontrada: $saleId")
            }

        return try {
            Log.d("PendingLocalSalesWorker", "Enviando venta local: ${sale.LOCAL_SALE_ID}")

            val products = saleProductStore.getProductsForSale(saleId)

            val request = with(mappers) {
                sale.toServerRequest(products)
            }

            val response = api.saveLocalSale(request)

            if (response.success) {
                localSaleStore.changeSaleStatus(saleId, true)
                Log.d(
                    "PendingLocalSalesWorker",
                    "Venta local marcada como enviada: ${sale.LOCAL_SALE_ID}"
                )
                Result.success()
            } else {
                Log.e("PendingLocalSalesWorker", "Error del servidor: ${response.message}")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e("PendingLocalSalesWorker", "Error al enviar venta local ${sale.LOCAL_SALE_ID}", e)
            Result.retry()
        }
    }
}