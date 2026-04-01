package com.example.msp_app.features.dailyReport.domain.usecases

import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.dailyReport.data.repository.DailyReportDataSource
import com.example.msp_app.features.dailyReport.domain.models.DailyReportSale
import com.example.msp_app.features.dailyReport.domain.models.DailyReportSaleProduct
import com.example.msp_app.features.dailyReport.domain.models.DailyReportTransfer
import com.example.msp_app.features.dailyReport.domain.models.DailyReportTransferProduct
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateDailyReportUseCaseTest {

    private fun createUseCase(
        salesResult: Result<List<DailyReportSale>> = Result.success(emptyList()),
        transfersResult: Result<List<DailyReportTransfer>> = Result.success(emptyList())
    ): GenerateDailyReportUseCase {
        val dataSource = FakeDailyReportDataSource(salesResult, transfersResult)
        return GenerateDailyReportUseCase(dataSource)
    }

    @Test
    fun `execute returns success with assembled report data`() = runBlocking {
        val sales = listOf(
            DailyReportSale(
                clientName = "Cliente 1",
                saleType = "CONTADO",
                products = listOf(
                    DailyReportSaleProduct("Colchon Queen", 1, 5000.0, 5000.0)
                ),
                total = 5000.0
            )
        )
        val transfers = listOf(
            DailyReportTransfer(
                originWarehouse = "Almacen General",
                destinationWarehouse = "Camioneta Norte",
                description = "Carga matutina",
                products = listOf(DailyReportTransferProduct("Colchon Queen", 3)),
                isInbound = true
            )
        )
        val products = listOf(
            ProductInventory(1, "Colchon Queen", 5, 1, "Colchones", null),
            ProductInventory(2, "Almohada", 10, 2, "Accesorios", null)
        )

        val useCase = createUseCase(
            salesResult = Result.success(sales),
            transfersResult = Result.success(transfers)
        )

        val result = useCase.execute(100, "Camioneta Norte", "Juan Perez", products)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Camioneta Norte", data.warehouseName)
        assertEquals("Juan Perez", data.vendedorName)
        assertEquals(2, data.currentInventory.size)
        assertEquals(1, data.sales.size)
        assertEquals(1, data.transfers.size)
        assertEquals(5000.0, data.totalSalesAmount, 0.01)
    }

    @Test
    fun `execute sorts inventory alphabetically`() = runBlocking {
        val products = listOf(
            ProductInventory(1, "Zapatilla", 2, 1, "Calzado", null),
            ProductInventory(2, "Almohada", 5, 2, "Accesorios", null),
            ProductInventory(3, "Colchon", 3, 3, "Colchones", null)
        )

        val useCase = createUseCase()
        val result = useCase.execute(1, "Test", "Test", products)
        val inventory = result.getOrThrow().currentInventory

        assertEquals("Almohada", inventory[0].name)
        assertEquals("Colchon", inventory[1].name)
        assertEquals("Zapatilla", inventory[2].name)
    }

    @Test
    fun `execute returns failure when sales fetch fails`() = runBlocking {
        val useCase = createUseCase(
            salesResult = Result.failure(Exception("Network error"))
        )

        val result = useCase.execute(1, "Test", "Test", emptyList())

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute returns failure when transfers fetch fails`() = runBlocking {
        val useCase = createUseCase(
            transfersResult = Result.failure(Exception("API error"))
        )

        val result = useCase.execute(1, "Test", "Test", emptyList())

        assertTrue(result.isFailure)
        assertEquals("API error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute with empty data returns report with zero totals`() = runBlocking {
        val useCase = createUseCase()

        val result = useCase.execute(1, "Camioneta", "Vendedor", emptyList())

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(0, data.totalInventoryProducts)
        assertEquals(0, data.totalSalesCount)
        assertEquals(0, data.totalTransfersCount)
        assertEquals(0.0, data.totalSalesAmount, 0.01)
    }

    @Test
    fun `execute maps inventory fields correctly`() = runBlocking {
        val products = listOf(
            ProductInventory(42, "Colchon King", 7, 10, "Premium", null)
        )

        val useCase = createUseCase()
        val result = useCase.execute(1, "Test", "Test", products)
        val item = result.getOrThrow().currentInventory.first()

        assertEquals("Colchon King", item.name)
        assertEquals("Premium", item.line)
        assertEquals(7, item.stock)
    }

    @Test
    fun `execute with multiple sales calculates correct total`() = runBlocking {
        val sales = listOf(
            DailyReportSale("A", "CONTADO", emptyList(), 1000.0),
            DailyReportSale("B", "CREDITO", emptyList(), 2500.0),
            DailyReportSale("C", "CONTADO", emptyList(), 750.0)
        )

        val useCase = createUseCase(salesResult = Result.success(sales))
        val result = useCase.execute(1, "Test", "Test", emptyList())

        assertEquals(4250.0, result.getOrThrow().totalSalesAmount, 0.01)
        assertEquals(3, result.getOrThrow().totalSalesCount)
    }

    @Test
    fun `execute report date is formatted correctly`() = runBlocking {
        val useCase = createUseCase()
        val result = useCase.execute(1, "Test", "Test", emptyList())

        val date = result.getOrThrow().reportDate
        assertTrue(
            "Date should match dd/MM/yyyy format",
            date.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))
        )
    }

    @Test
    fun `execute preserves transfer inbound flag`() = runBlocking {
        val transfers = listOf(
            DailyReportTransfer("A", "B", null, emptyList(), isInbound = true),
            DailyReportTransfer("B", "A", null, emptyList(), isInbound = false)
        )

        val useCase = createUseCase(transfersResult = Result.success(transfers))
        val result = useCase.execute(1, "Test", "Test", emptyList())

        val reportTransfers = result.getOrThrow().transfers
        assertTrue(reportTransfers[0].isInbound)
        assertTrue(!reportTransfers[1].isInbound)
    }
}

private class FakeDailyReportDataSource(
    private val salesResult: Result<List<DailyReportSale>>,
    private val transfersResult: Result<List<DailyReportTransfer>>
) : DailyReportDataSource {
    override suspend fun getTodaySales(): Result<List<DailyReportSale>> = salesResult
    override suspend fun getTodayTransfers(camionetaId: Int): Result<List<DailyReportTransfer>> =
        transfersResult
}
