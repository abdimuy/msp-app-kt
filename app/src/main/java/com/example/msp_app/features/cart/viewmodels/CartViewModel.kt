package com.example.msp_app.features.cart.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import com.example.msp_app.data.models.productInventory.ProductInventory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    fun addProductToCart(product: ProductInventory, quantity: Int = 1) {
        val existingIndex =
            _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }

        if (existingIndex != -1) {
            val existingItem = _cartItems[existingIndex]
            _cartItems[existingIndex] =
                existingItem.copy(quantity = existingItem.quantity + quantity)
        } else {
            _cartItems.add(CartItem(product, quantity))
        }
        markAsModified()
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
        if (!isReallyInitial) {
            _cartItems.clear()
            _savedCartState.clear()
        }

        warehouseProducts.forEach { (product, quantity) ->
            if (quantity > 0) {
                _cartItems.add(CartItem(product, quantity))
            }
        }

        if (isReallyInitial) {
            markAsSaved()
        } else {
            markAsSaved()
        }
    }

    fun clearCartForNewWarehouse() {
        _cartItems.clear()
        _savedCartState.clear()
        forceUnsaved = false
        _hasUnsavedChanges.value = false
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
