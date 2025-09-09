package com.example.msp_app.features.sales.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.utils.PriceParser

data class SaleItem(
    val product: ProductInventory,
    val quantity: Int
) {
    val totalPrice: Double
        get() = (product.PRECIOS?.toDoubleOrNull() ?: 0.0) * quantity
}

class SaleProductsViewModel : ViewModel() {
    private val _saleItems = mutableStateListOf<SaleItem>()
    val saleItems: SnapshotStateList<SaleItem> get() = _saleItems

    fun addProductToSale(product: ProductInventory, quantity: Int) {
        if (quantity <= 0) return

        val existingIndex = _saleItems.indexOfFirst {
            it.product.ARTICULO_ID == product.ARTICULO_ID
        }

        if (existingIndex != -1) {
            val existingItem = _saleItems[existingIndex]
            _saleItems[existingIndex] = existingItem.copy(
                quantity = existingItem.quantity + quantity
            )
        } else {
            _saleItems.add(SaleItem(product, quantity))
        }
    }

    fun removeProductFromSale(product: ProductInventory) {
        _saleItems.removeAll { it.product.ARTICULO_ID == product.ARTICULO_ID }
    }

    fun updateQuantity(product: ProductInventory, newQuantity: Int) {
        val index = _saleItems.indexOfFirst {
            it.product.ARTICULO_ID == product.ARTICULO_ID
        }

        if (index != -1) {
            if (newQuantity <= 0) {
                _saleItems.removeAt(index)
            } else {
                _saleItems[index] = _saleItems[index].copy(quantity = newQuantity)
            }
        }
    }

    fun getQuantityForProduct(product: ProductInventory): Int {
        return _saleItems.find {
            it.product.ARTICULO_ID == product.ARTICULO_ID
        }?.quantity ?: 0
    }

    fun getTotalItems(): Int = _saleItems.sumOf { it.quantity }

    fun getTotalPrice(): Double = _saleItems.sumOf { it.totalPrice }

    fun getSaleItemsForWarehouse(): List<Pair<ProductInventory, Int>> =
        _saleItems.map { it.product to it.quantity }

    fun getSaleItemsList(): List<SaleItem> = _saleItems.toList()

    fun clearSale() {
        _saleItems.clear()
    }

    fun hasItems(): Boolean = _saleItems.isNotEmpty()

    fun validatePrices(): Boolean {
        return _saleItems.all {
            it.product.PRECIOS?.toDoubleOrNull() != null &&
                    it.product.PRECIOS.toDouble() > 0
        }
    }

    fun getTotalPrecioLista(): Double = _saleItems.sumOf { saleItem ->
        val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
        parsedPrices.precioLista * saleItem.quantity
    }

    fun getTotalMontoCortoplazo(): Double = _saleItems.sumOf { saleItem ->
        val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
        parsedPrices.precioCortoplazo * saleItem.quantity
    }

    fun getTotalMontoContado(): Double = _saleItems.sumOf { saleItem ->
        val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
        parsedPrices.precioContado * saleItem.quantity
    }
}