package com.example.msp_app.features.cart.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import com.example.msp_app.data.models.productInventory.ProductInventory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CartItem(
    val product: ProductInventory,
    val quantity: Int
)

class CartViewModel : ViewModel() {
    private val _cartItems = mutableStateListOf<CartItem>()
    val cartProducts: SnapshotStateList<CartItem> get() = _cartItems

    private val _savedCartState = mutableMapOf<Int, Int>()
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges
    private var forceUnsaved = false

    suspend fun getValidatedQuantity(
        product: ProductInventory,
        api: WarehousesApi,
        warehouseId: Int
    ): Int {
        val warehouseProducts = api.getWarehouseProducts(warehouseId).body.ARTICULOS
        val serverProduct = warehouseProducts.find { it.ARTICULO_ID == product.ARTICULO_ID }
        return serverProduct?.EXISTENCIAS ?: 0
    }

    fun addProductValidated(
        product: ProductInventory,
        api: WarehousesApi,
        warehouseId: Int,
        amount: Int = 1
    ) {
        viewModelScope.launch {
            val validQuantity = getValidatedQuantity(product, api, warehouseId)
            val existingIndex =
                _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }

            if (existingIndex != -1) {
                val existingItem = _cartItems[existingIndex]
                val newQuantity = (existingItem.quantity + amount).coerceAtMost(validQuantity)
                _cartItems[existingIndex] = existingItem.copy(quantity = newQuantity)
            } else {
                val newQuantity = amount.coerceAtMost(validQuantity)
                if (newQuantity > 0) _cartItems.add(CartItem(product, newQuantity))
            }
            markAsModified()
        }
    }


    fun removeProduct(product: ProductInventory) {
        _cartItems.removeAll { it.product.ARTICULO_ID == product.ARTICULO_ID }
        markAsModified()
    }

    private fun changeQuantity(
        product: ProductInventory,
        increase: Boolean,
        stockLimit: Int
    ) {
        val itemIndex = _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }
        if (itemIndex != -1) {
            val item = _cartItems[itemIndex]
            val newQuantity = if (increase) (item.quantity + 1).coerceAtMost(stockLimit)
            else item.quantity - 1

            if (newQuantity != item.quantity) {
                when {
                    newQuantity < 1 -> _cartItems.removeAt(itemIndex)
                    else -> _cartItems[itemIndex] = item.copy(quantity = newQuantity)
                }
                _hasUnsavedChanges.value = checkForUnsavedChanges()
            }
        }
    }

    fun increaseQuantity(product: ProductInventory, stockLimit: Int) =
        changeQuantity(product, true, stockLimit)

    fun decreaseQuantity(product: ProductInventory) =
        changeQuantity(product, false, Int.MAX_VALUE)


    fun getQuantityForProduct(product: ProductInventory): Int {
        val item = _cartItems.find { it.product.ARTICULO_ID == product.ARTICULO_ID }
        return item?.quantity ?: 0
    }

    fun getTotalItems(): Int = _cartItems.sumOf { it.quantity }

    fun getCartItemsForWarehouse(): List<Pair<ProductInventory, Int>> =
        _cartItems.map { it.product to it.quantity }

    fun markAsSaved() {
        _savedCartState.clear()
        _cartItems.forEach { _savedCartState[it.product.ARTICULO_ID] = it.quantity }
        forceUnsaved = false
        _hasUnsavedChanges.value = false
    }

    fun mergeCartWithWarehouse(
        warehouseProducts: List<Pair<ProductInventory, Int>>,
        isInitialLoad: Boolean = false
    ) {
        val isReallyInitial = isInitialLoad && _cartItems.isEmpty() && !forceUnsaved
        warehouseProducts.forEach { (product, quantity) ->
            val existingIndex =
                _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }
            if (existingIndex != -1) {
                val existingItem = _cartItems[existingIndex]
                _cartItems[existingIndex] = existingItem.copy(quantity = quantity)
            } else {
                _cartItems.add(CartItem(product, quantity))
            }
        }
        if (isReallyInitial) markAsSaved()
        else if (forceUnsaved || checkForUnsavedChanges()) _hasUnsavedChanges.value = true
    }

    private fun markAsModified() {
        forceUnsaved = true
        _hasUnsavedChanges.value = true
    }

    private fun checkForUnsavedChanges(): Boolean {
        if (_savedCartState.isEmpty() && _cartItems.isNotEmpty()) return true
        if (_cartItems.size != _savedCartState.size) return true
        return _cartItems.any { cartItem ->
            val savedQuantity = _savedCartState[cartItem.product.ARTICULO_ID] ?: 0
            cartItem.quantity != savedQuantity
        }
    }
}
