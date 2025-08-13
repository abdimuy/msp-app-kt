package com.example.msp_app.features.cart.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import com.example.msp_app.data.models.productInventory.ProductInventory

data class CartItem(
    val product: ProductInventory,
    val quantity: Int
)

class CartViewModel : ViewModel() {
    private val _cartItems = mutableStateListOf<CartItem>()
    val cartProducts: SnapshotStateList<CartItem> get() = _cartItems

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
    }


    fun removeProduct(product: ProductInventory) {
        _cartItems.removeAll { it.product.ARTICULO_ID == product.ARTICULO_ID }
    }

    fun increaseQuantity(product: ProductInventory) {
        val itemIndex = _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }
        if (itemIndex != -1) {
            val item = _cartItems[itemIndex]
            if (item.quantity < item.product.EXISTENCIAS) {
                _cartItems[itemIndex] = item.copy(quantity = item.quantity + 1)
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
        }
    }

    fun getQuantityForProduct(product: ProductInventory): Int {
        val item = _cartItems.find { it.product.ARTICULO_ID == product.ARTICULO_ID }
        return item?.quantity ?: 0
    }

    fun getTotalItems(): Int {
        return _cartItems.sumOf { it.quantity }
    }
}