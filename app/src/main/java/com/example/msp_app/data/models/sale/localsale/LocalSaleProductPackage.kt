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
) {
    fun isValid(): Boolean {
        return precioLista > 0 && precioCortoplazo > 0 && precioContado > 0
    }

    fun getTotalQuantity(): Int = products.sumOf { it.quantity }

    companion object {
        fun generatePackageName(products: List<SaleItem>): String {
            return products.joinToString(", ") { saleItem ->
                val words = saleItem.product.ARTICULO.split(" ")
                words.take(2).joinToString(" ")
            }
        }
    }
}

sealed class SaleCartItem {
    data class SingleProduct(val saleItem: SaleItem) : SaleCartItem()
    data class Package(val productPackage: LocalSaleProductPackage) : SaleCartItem()
}