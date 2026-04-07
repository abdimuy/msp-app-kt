package com.example.msp_app.features.dailyReport.data.repository

import android.content.Context
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.features.dailyReport.domain.models.DailyReportSale
import com.example.msp_app.features.dailyReport.domain.models.DailyReportSaleProduct
import com.example.msp_app.features.dailyReport.domain.models.DailyReportTransfer
import com.example.msp_app.features.dailyReport.domain.models.DailyReportTransferProduct
import com.example.msp_app.features.transfers.data.api.TransfersApiService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DailyReportDataSource {
    suspend fun getTodaySales(): Result<List<DailyReportSale>>
    suspend fun getTodayTransfers(camionetaId: Int): Result<List<DailyReportTransfer>>
}

class DailyReportRepository(
    private val context: Context,
    private val transfersApi: TransfersApiService = ApiProvider.create(
        TransfersApiService::class.java
    )
) : DailyReportDataSource {

    private val localSaleDao = AppDatabase.getInstance(context).localSaleDao()
    private val localSaleProductDao = AppDatabase.getInstance(context).localSaleProduct()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override suspend fun getTodaySales(): Result<List<DailyReportSale>> =
        withContext(Dispatchers.IO) {
            try {
                val allSales = localSaleDao.getAllSales()
                val todayPrefix = LocalDate.now().format(dateFormatter)
                val todaySales = allSales.filter { it.FECHA_VENTA.startsWith(todayPrefix) }

                val reportSales = todaySales.map { sale ->
                    val products = localSaleProductDao.getProductsForSale(sale.LOCAL_SALE_ID)
                    sale.toDailyReportSale(products)
                }

                Result.success(reportSales)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getTodayTransfers(camionetaId: Int): Result<List<DailyReportTransfer>> =
        withContext(Dispatchers.IO) {
            try {
                val today = LocalDate.now()
                val todayStr = today.format(dateFormatter)

                val inboundResponse = transfersApi.getTransfers(
                    fechaInicio = todayStr,
                    fechaFin = todayStr,
                    almacenDestinoId = camionetaId
                )
                val outboundResponse = transfersApi.getTransfers(
                    fechaInicio = todayStr,
                    fechaFin = todayStr,
                    almacenOrigenId = camionetaId
                )

                if (!inboundResponse.isSuccessful || !outboundResponse.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(
                            "Error al obtener traspasos: ${inboundResponse.code()}/${outboundResponse.code()}"
                        )
                    )
                }

                val inboundList = inboundResponse.body()?.body ?: emptyList()
                val outboundList = outboundResponse.body()?.body ?: emptyList()

                val allTransfers = (inboundList.map { it to true } + outboundList.map { it to false })
                    .sortedBy { (transfer, _) -> transfer.fechaHoraCreacion ?: "" }

                val reportTransfers = allTransfers.map { (transfer, isInbound) ->
                    val products = transfer.productos ?: emptyList()
                    val relevantProducts = products.filter {
                        if (isInbound) it.tipoMovto == "E" else it.tipoMovto == "S"
                    }

                    val hora = transfer.fechaHoraCreacion?.let { fechaHora ->
                        try {
                            val parsed = java.time.OffsetDateTime.parse(fechaHora)
                            parsed.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
                        } catch (e: Exception) {
                            null
                        }
                    }

                    DailyReportTransfer(
                        originWarehouse = transfer.almacen ?: "Almacén ${transfer.almacenId}",
                        destinationWarehouse = transfer.almacenDestino
                            ?: "Almacén ${transfer.almacenDestinoId}",
                        description = transfer.descripcion,
                        products = relevantProducts.map { product ->
                            DailyReportTransferProduct(
                                name = product.articuloNombre
                                    ?: product.claveArticulo,
                                quantity = product.unidades
                            )
                        },
                        isInbound = isInbound,
                        hora = hora
                    )
                }

                Result.success(reportTransfers)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

private fun LocalSaleEntity.toDailyReportSale(
    products: List<LocalSaleProductEntity>
): DailyReportSale {
    val saleType = TIPO_VENTA ?: "CONTADO"
    val isContado = saleType == "CONTADO"
    return DailyReportSale(
        clientName = NOMBRE_CLIENTE,
        saleType = saleType,
        products = products.map { product ->
            val price = if (isContado) product.PRECIO_CONTADO else product.PRECIO_LISTA
            DailyReportSaleProduct(
                name = product.ARTICULO,
                quantity = product.CANTIDAD,
                unitPrice = price,
                totalPrice = price * product.CANTIDAD
            )
        },
        total = PRECIO_TOTAL
    )
}
