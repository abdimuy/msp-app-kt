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

    fun addProduct(product: ProductInventory, amount: Int = 1) {
        val existingItemIndex =
            _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }

        if (existingItemIndex != -1) {
            val existingItem = _cartItems[existingItemIndex]
            _cartItems[existingItemIndex] =
                existingItem.copy(quantity = existingItem.quantity + amount)
        } else {
            _cartItems.add(CartItem(product = product, quantity = amount))
        }
        markAsModified()
    }

    fun removeProduct(product: ProductInventory) {
        _cartItems.removeAll { it.product.ARTICULO_ID == product.ARTICULO_ID }
        markAsModified()
    }

    fun increaseQuantity(product: ProductInventory) {
        val itemIndex = _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }
        if (itemIndex != -1) {
            val item = _cartItems[itemIndex]
            if (item.quantity < item.product.EXISTENCIAS) {
                _cartItems[itemIndex] = item.copy(quantity = item.quantity + 1)
                markAsModified()
            }
        }
    }

    fun decreaseQuantity(product: ProductInventory) {
        val itemIndex = _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }
        if (itemIndex != -1) {
            val item = _cartItems[itemIndex]
            if (item.quantity > 1) {
                _cartItems[itemIndex] = item.copy(quantity = item.quantity - 1)
            } else {
                _cartItems.removeAt(itemIndex)
            }
            markAsModified()
        }
    }

    fun getQuantityForProduct(product: ProductInventory): Int {
        val item = _cartItems.find { it.product.ARTICULO_ID == product.ARTICULO_ID }
        return item?.quantity ?: 0
    }

    fun getTotalItems(): Int {
        return _cartItems.sumOf { it.quantity }
    }

    fun getCartItemsForWarehouse(): List<Pair<ProductInventory, Int>> {
        return _cartItems.map { cartItem ->
            cartItem.product to cartItem.quantity
        }
    }

    fun markAsSaved() {
        _savedCartState.clear()
        _cartItems.forEach { cartItem ->
            _savedCartState[cartItem.product.ARTICULO_ID] = cartItem.quantity
        }
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
                _cartItems.add(CartItem(product = product, quantity = quantity))
            }
        }
        if (isReallyInitial) {
            markAsSaved()
        } else {
            if (forceUnsaved || checkForUnsavedChanges()) {
                _hasUnsavedChanges.value = true
            }
        }
    }

    private fun markAsModified() {
        forceUnsaved = true
        _hasUnsavedChanges.value = true
    }

    private fun checkForUnsavedChanges(): Boolean {
        if (_savedCartState.isEmpty() && _cartItems.isNotEmpty()) {
            return true
        }
        if (_cartItems.size != _savedCartState.size) {
            return true
        }
        return _cartItems.any { cartItem ->
            val savedQuantity = _savedCartState[cartItem.product.ARTICULO_ID] ?: 0
            cartItem.quantity != savedQuantity
        }
    }
}