package com.example.msp_app.features.dailyReport.domain.models

data class DailyReportData(
    val warehouseName: String,
    val reportDate: String,
    val vendedorName: String,
    val currentInventory: List<DailyReportInventoryItem>,
    val sales: List<DailyReportSale>,
    val transfers: List<DailyReportTransfer>
) {
    val totalInventoryProducts: Int get() = currentInventory.size
    val totalInventoryUnits: Int get() = currentInventory.sumOf { it.stock }
    val totalSalesCount: Int get() = sales.size
    val totalSalesAmount: Double get() = sales.sumOf { it.total }
    val totalTransfersCount: Int get() = transfers.size
}

data class DailyReportInventoryItem(
    val name: String,
    val line: String,
    val stock: Int
)

data class DailyReportSale(
    val clientName: String,
    val saleType: String,
    val products: List<DailyReportSaleProduct>,
    val total: Double
) {
    val totalProducts: Int get() = products.sumOf { it.quantity }
}

data class DailyReportSaleProduct(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double
)

data class DailyReportTransfer(
    val originWarehouse: String,
    val destinationWarehouse: String,
    val description: String?,
    val products: List<DailyReportTransferProduct>,
    val isInbound: Boolean
) {
    val totalUnits: Int get() = products.sumOf { it.quantity }
}

data class DailyReportTransferProduct(
    val name: String,
    val quantity: Int
)
