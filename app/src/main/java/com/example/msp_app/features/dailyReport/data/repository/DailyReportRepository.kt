package com.example.msp_app.features.dailyReport.data.repository

import android.content.Context
import com.example.msp_app.core.time.AppClock
import com.example.msp_app.core.time.AppTime
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.features.dailyReport.domain.models.DailyReportSale
import com.example.msp_app.features.dailyReport.domain.models.DailyReportSaleProduct
import com.example.msp_app.features.dailyReport.domain.models.DailyReportTransfer
import com.example.msp_app.features.dailyReport.domain.models.DailyReportTransferProduct
import com.example.msp_app.features.dailyReport.domain.onBusinessDate
import com.example.msp_app.features.transfers.data.api.TransfersApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DailyReportDataSource {
    suspend fun getTodaySales(): Result<List<DailyReportSale>>
    suspend fun getTodayTransfers(camionetaId: Int): Result<List<DailyReportTransfer>>
}

class DailyReportRepository(
    private val context: Context,
    private val clock: AppClock = AppClock.System,
    private val transfersApi: TransfersApiService = ApiProvider.create(
        TransfersApiService::class.java
    )
) : DailyReportDataSource {

    private val localSaleDao = AppDatabase.getInstance(context).localSaleDao()
    private val localSaleProductDao = AppDatabase.getInstance(context).localSaleProduct()

    override suspend fun getTodaySales(): Result<List<DailyReportSale>> =
        withContext(Dispatchers.IO) {
            try {
                val allSales = localSaleDao.getAllSales()
                val today = AppTime.todayInBusinessZone(clock)
                val todaySales = allSales.onBusinessDate(today)

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
                val today = AppTime.todayInBusinessZone(clock)
                // Widen the server window by one day on each side to cover TZ drift:
                // a transfer created at 18:00 CDMX has a UTC timestamp on the next day,
                // so narrow server-side filtering would drop it. We fetch the bracket
                // and filter by business date client-side below.
                val rangeStart = AppTime.toWireDate(today.minusDays(1))
                val rangeEnd = AppTime.toWireDate(today.plusDays(1))

                val inboundResponse = transfersApi.getTransfers(
                    fechaInicio = rangeStart,
                    fechaFin = rangeEnd,
                    almacenDestinoId = camionetaId
                )
                val outboundResponse = transfersApi.getTransfers(
                    fechaInicio = rangeStart,
                    fechaFin = rangeEnd,
                    almacenOrigenId = camionetaId
                )

                if (!inboundResponse.isSuccessful || !outboundResponse.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(
                            "Error al obtener traspasos: ${inboundResponse.code()}/${outboundResponse.code()}"
                        )
                    )
                }

                // Client-side filter to business zone — independent of whatever TZ the
                // backend uses for the range parameters.
                val inboundList = inboundResponse.body()?.body.orEmpty().onBusinessDate(today)
                val outboundList = outboundResponse.body()?.body.orEmpty().onBusinessDate(today)

                val allTransfers = (inboundList.map { it to true } + outboundList.map { it to false })
                    .sortedBy { (transfer, _) -> transfer.fechaHoraCreacion ?: "" }

                val reportTransfers = allTransfers.map { (transfer, isInbound) ->
                    val products = transfer.productos ?: emptyList()
                    val relevantProducts = products.filter {
                        if (isInbound) it.tipoMovto == "E" else it.tipoMovto == "S"
                    }

                    val hora = transfer.fechaHoraCreacion?.let { iso ->
                        AppTime.parseWireFormatOrNull(iso)?.let { instant ->
                            AppTime.formatForDisplay(instant, AppTime.Formats.TIME_12H)
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
