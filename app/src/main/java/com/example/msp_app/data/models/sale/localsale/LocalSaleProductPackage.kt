package com.example.msp_app.data.models.sale.localsale

import com.example.msp_app.features.sales.viewmodels.SaleItem
import java.util.UUID

data class LocalSaleProductPackage(
    val packageId: String = UUID.randomUUID().toString(),
    val packageName: String,
    val products: List<SaleItem>,
    val precioLista: Double,
    val precioCortoplazo: Double,
    val precioContado: Double,
    val isExpanded: Boolean = false
)