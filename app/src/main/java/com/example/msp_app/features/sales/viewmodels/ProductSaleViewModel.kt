package com.example.msp_app.features.sales.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.utils.PriceParser
import java.util.UUID

data class SaleItem(
    val product: ProductInventory,
    val quantity: Int,
    val comboId: String? = null
) {
    val totalPrice: Double
        get() = (product.PRECIOS?.toDoubleOrNull() ?: 0.0) * quantity
}

data class ComboItem(
    val comboId: String = UUID.randomUUID().toString(),
    val nombreCombo: String,
    val precioLista: Double,
    val precioCortoPlazo: Double,
    val precioContado: Double
)

class SaleProductsViewModel : ViewModel() {
    private val _saleItems = mutableStateListOf<SaleItem>()
    val saleItems: SnapshotStateList<SaleItem> get() = _saleItems

    private val _combos = mutableStateMapOf<String, ComboItem>()
    val combos: SnapshotStateMap<String, ComboItem> get() = _combos

    private val _selectedForCombo = mutableStateListOf<Int>()
    val selectedForCombo: SnapshotStateList<Int> get() = _selectedForCombo

    private val _isCreatingCombo = MutableStateFlow(false)
    val isCreatingCombo: StateFlow<Boolean> = _isCreatingCombo.asStateFlow()

    fun addProductToSale(product: ProductInventory, quantity: Int) {
        if (quantity <= 0) return

        val maxStock = product.EXISTENCIAS
        val existingIndex = _saleItems.indexOfFirst {
            it.product.ARTICULO_ID == product.ARTICULO_ID
        }

        if (existingIndex != -1) {
            val existingItem = _saleItems[existingIndex]
            val newQuantity = (existingItem.quantity + quantity).coerceAtMost(maxStock)
            _saleItems[existingIndex] = existingItem.copy(
                quantity = newQuantity
            )
        } else {
            _saleItems.add(SaleItem(product, quantity.coerceAtMost(maxStock)))
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
        _combos.clear()
        _selectedForCombo.clear()
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

    fun updateProductPrices(
        product: ProductInventory,
        newListPrice: Double,
        newShortTermPrice: Double,
        newCashPrice: Double
    ) {
        val index = _saleItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }
        if (index != -1) {
            val oldItem = _saleItems[index]
            val updatedProduct = oldItem.product.copy(
                PRECIOS = PriceParser.pricesToJson(newListPrice, newShortTermPrice, newCashPrice)
            )
            _saleItems[index] = oldItem.copy(product = updatedProduct)
        }
    }

    // ==================== COMBO METHODS ====================

    fun toggleProductSelection(articleId: Int) {
        // Block product selection while creating a combo to prevent accidental deselection
        if (_isCreatingCombo.value) return
        
        if (_selectedForCombo.contains(articleId)) {
            _selectedForCombo.remove(articleId)
        } else {
            // Solo permitir seleccionar productos que no están en un combo
            val item = _saleItems.find { it.product.ARTICULO_ID == articleId }
            if (item != null && item.comboId == null) {
                _selectedForCombo.add(articleId)
            }
        }
    }

    fun clearSelection() {
        _selectedForCombo.clear()
    }

    fun isProductSelected(articleId: Int): Boolean {
        return _selectedForCombo.contains(articleId)
    }

    fun getSelectedProductsCount(): Int = _selectedForCombo.size

    fun getSelectedProductNames(): List<String> {
        return _saleItems
            .filter { _selectedForCombo.contains(it.product.ARTICULO_ID) }
            .map { it.product.ARTICULO }
    }

    /**
     * Sets the combo creation state to prevent accidental product deselection
     * during the transition to the combo creation dialog
     */
    fun setCreatingCombo(isCreating: Boolean) {
        _isCreatingCombo.value = isCreating
    }

    fun canCreateCombo(): Boolean = _selectedForCombo.size >= 2

    fun getSelectedItemsSuggestedPrices(): Triple<Double, Double, Double> {
        var totalLista = 0.0
        var totalCortoPlazo = 0.0
        var totalContado = 0.0

        _saleItems.filter { _selectedForCombo.contains(it.product.ARTICULO_ID) }
            .forEach { saleItem ->
                val prices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
                totalLista += prices.precioLista * saleItem.quantity
                totalCortoPlazo += prices.precioCortoplazo * saleItem.quantity
                totalContado += prices.precioContado * saleItem.quantity
            }

        return Triple(totalLista, totalCortoPlazo, totalContado)
    }

    fun createCombo(
        nombreCombo: String,
        precioLista: Double,
        precioCortoPlazo: Double,
        precioContado: Double
    ): String {
        val comboId = UUID.randomUUID().toString()
        return createComboWithId(comboId, nombreCombo, precioLista, precioCortoPlazo, precioContado)
    }

    /**
     * Create a combo with a specific ID (used for restoring from draft)
     */
    fun createComboWithId(
        comboId: String,
        nombreCombo: String,
        precioLista: Double,
        precioCortoPlazo: Double,
        precioContado: Double
    ): String {
        val combo = ComboItem(
            comboId = comboId,
            nombreCombo = nombreCombo,
            precioLista = precioLista,
            precioCortoPlazo = precioCortoPlazo,
            precioContado = precioContado
        )
        _combos[comboId] = combo

        // Asignar comboId a los productos seleccionados
        _selectedForCombo.forEach { articleId ->
            val index = _saleItems.indexOfFirst { it.product.ARTICULO_ID == articleId }
            if (index != -1) {
                _saleItems[index] = _saleItems[index].copy(comboId = comboId)
            }
        }

        _selectedForCombo.clear()
        return comboId
    }

    fun deleteCombo(comboId: String) {
        // Remover comboId de los productos
        _saleItems.forEachIndexed { index, item ->
            if (item.comboId == comboId) {
                _saleItems[index] = item.copy(comboId = null)
            }
        }
        _combos.remove(comboId)
    }

    fun getProductsInCombo(comboId: String): List<SaleItem> {
        return _saleItems.filter { it.comboId == comboId }
    }

    fun getIndividualProducts(): List<SaleItem> {
        return _saleItems.filter { it.comboId == null }
    }

    fun getCombosList(): List<ComboItem> = _combos.values.toList()

    fun hasAnyCombos(): Boolean = _combos.isNotEmpty()

    // Totales considerando combos (combo tiene su precio, no suma de productos)
    fun getTotalPrecioListaWithCombos(): Double {
        val individualTotal = _saleItems.filter { it.comboId == null }.sumOf { saleItem ->
            val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
            parsedPrices.precioLista * saleItem.quantity
        }
        val combosTotal = _combos.values.sumOf { it.precioLista }
        return individualTotal + combosTotal
    }

    fun getTotalMontoCortoPlazoWithCombos(): Double {
        val individualTotal = _saleItems.filter { it.comboId == null }.sumOf { saleItem ->
            val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
            parsedPrices.precioCortoplazo * saleItem.quantity
        }
        val combosTotal = _combos.values.sumOf { it.precioCortoPlazo }
        return individualTotal + combosTotal
    }

    fun getTotalMontoContadoWithCombos(): Double {
        val individualTotal = _saleItems.filter { it.comboId == null }.sumOf { saleItem ->
            val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
            parsedPrices.precioContado * saleItem.quantity
        }
        val combosTotal = _combos.values.sumOf { it.precioContado }
        return individualTotal + combosTotal
    }

    fun clearAll() {
        _saleItems.clear()
        _combos.clear()
        _selectedForCombo.clear()
    }
}