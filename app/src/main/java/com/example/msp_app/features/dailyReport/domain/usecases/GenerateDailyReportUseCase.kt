package com.example.msp_app.features.dailyReport.domain.usecases

import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.dailyReport.data.repository.DailyReportDataSource
import com.example.msp_app.features.dailyReport.domain.models.DailyReportData
import com.example.msp_app.features.dailyReport.domain.models.DailyReportInventoryItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class GenerateDailyReportUseCase(
    private val repository: DailyReportDataSource
) {
    private val displayDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "MX"))

    suspend fun execute(
        camionetaId: Int,
        warehouseName: String,
        vendedorName: String,
        currentProducts: List<ProductInventory>
    ): Result<DailyReportData> = coroutineScope {
        val salesDeferred = async { repository.getTodaySales() }
        val transfersDeferred = async { repository.getTodayTransfers(camionetaId) }

        val salesResult = salesDeferred.await()
        if (salesResult.isFailure) {
            return@coroutineScope Result.failure(
                salesResult.exceptionOrNull() ?: Exception("Error al obtener ventas")
            )
        }

        val transfersResult = transfersDeferred.await()
        if (transfersResult.isFailure) {
            return@coroutineScope Result.failure(
                transfersResult.exceptionOrNull() ?: Exception("Error al obtener traspasos")
            )
        }

        // Armar mapa de producto -> almacén origen usando traspasos de entrada de hoy
        val transfers = transfersResult.getOrThrow()
        val originMap = mutableMapOf<String, String>()
        transfers.filter { it.isInbound }.forEach { transfer ->
            transfer.products.forEach { product ->
                originMap[product.name] = transfer.originWarehouse
            }
        }

        val inventory = currentProducts
            .sortedBy { it.ARTICULO }
            .map { product ->
                DailyReportInventoryItem(
                    name = product.ARTICULO,
                    line = product.LINEA_ARTICULO,
                    stock = product.EXISTENCIAS,
                    originWarehouse = originMap[product.ARTICULO]
                )
            }

        Result.success(
            DailyReportData(
                warehouseName = warehouseName,
                reportDate = LocalDate.now().format(displayDateFormatter),
                vendedorName = vendedorName,
                currentInventory = inventory,
                sales = salesResult.getOrThrow(),
                transfers = transfers
            )
        )
    }
}
