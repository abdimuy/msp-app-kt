package com.example.msp_app.features.dailyReport.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyReportDataTest {

    @Test
    fun `totalInventoryProducts returns correct count`() {
        val data = createReportData(
            inventory = listOf(
                DailyReportInventoryItem("Producto A", "Linea 1", 10),
                DailyReportInventoryItem("Producto B", "Linea 2", 5)
            )
        )
        assertEquals(2, data.totalInventoryProducts)
    }

    @Test
    fun `totalInventoryUnits sums all stock`() {
        val data = createReportData(
            inventory = listOf(
                DailyReportInventoryItem("Producto A", "Linea 1", 10),
                DailyReportInventoryItem("Producto B", "Linea 2", 5),
                DailyReportInventoryItem("Producto C", "Linea 1", 3)
            )
        )
        assertEquals(18, data.totalInventoryUnits)
    }

    @Test
    fun `totalSalesCount returns correct count`() {
        val data = createReportData(
            sales = listOf(
                createSale("Cliente 1", 100.0),
                createSale("Cliente 2", 200.0),
                createSale("Cliente 3", 150.0)
            )
        )
        assertEquals(3, data.totalSalesCount)
    }

    @Test
    fun `totalSalesAmount sums all sale totals`() {
        val data = createReportData(
            sales = listOf(
                createSale("Cliente 1", 100.0),
                createSale("Cliente 2", 200.0),
                createSale("Cliente 3", 150.0)
            )
        )
        assertEquals(450.0, data.totalSalesAmount, 0.01)
    }

    @Test
    fun `totalTransfersCount returns correct count`() {
        val data = createReportData(
            transfers = listOf(
                createTransfer(isInbound = true),
                createTransfer(isInbound = false)
            )
        )
        assertEquals(2, data.totalTransfersCount)
    }

    @Test
    fun `empty report has zero totals`() {
        val data = createReportData()
        assertEquals(0, data.totalInventoryProducts)
        assertEquals(0, data.totalInventoryUnits)
        assertEquals(0, data.totalSalesCount)
        assertEquals(0.0, data.totalSalesAmount, 0.01)
        assertEquals(0, data.totalTransfersCount)
    }

    @Test
    fun `DailyReportSale totalProducts sums product quantities`() {
        val sale = DailyReportSale(
            clientName = "Test",
            saleType = "CONTADO",
            products = listOf(
                DailyReportSaleProduct("A", 3, 100.0, 300.0),
                DailyReportSaleProduct("B", 2, 50.0, 100.0)
            ),
            total = 400.0
        )
        assertEquals(5, sale.totalProducts)
    }

    @Test
    fun `DailyReportTransfer totalUnits sums product quantities`() {
        val transfer = DailyReportTransfer(
            originWarehouse = "Almacen 1",
            destinationWarehouse = "Camioneta",
            description = "Test",
            products = listOf(
                DailyReportTransferProduct("A", 5),
                DailyReportTransferProduct("B", 10)
            ),
            isInbound = true
        )
        assertEquals(15, transfer.totalUnits)
    }

    @Test
    fun `DailyReportTransfer empty products has zero totalUnits`() {
        val transfer = DailyReportTransfer(
            originWarehouse = "A",
            destinationWarehouse = "B",
            description = null,
            products = emptyList(),
            isInbound = false
        )
        assertEquals(0, transfer.totalUnits)
    }

    private fun createReportData(
        inventory: List<DailyReportInventoryItem> = emptyList(),
        sales: List<DailyReportSale> = emptyList(),
        transfers: List<DailyReportTransfer> = emptyList()
    ) = DailyReportData(
        warehouseName = "Camioneta Test",
        reportDate = "30/03/2026",
        vendedorName = "Test Vendor",
        currentInventory = inventory,
        sales = sales,
        transfers = transfers
    )

    private fun createSale(clientName: String, total: Double) = DailyReportSale(
        clientName = clientName,
        saleType = "CONTADO",
        products = listOf(
            DailyReportSaleProduct("Producto", 1, total, total)
        ),
        total = total
    )

    private fun createTransfer(isInbound: Boolean) = DailyReportTransfer(
        originWarehouse = "Almacen 1",
        destinationWarehouse = "Camioneta",
        description = "Test transfer",
        products = listOf(DailyReportTransferProduct("Producto", 5)),
        isInbound = isInbound
    )
}
