package com.example.msp_app.features.cart.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    
    private val _loadingOperations = MutableStateFlow<Set<String>>(emptySet())
    val loadingOperations: StateFlow<Set<String>> = _loadingOperations
    
    private fun setOperationLoading(operationId: String, loading: Boolean) {
        val current = _loadingOperations.value.toMutableSet()
        if (loading) {
            current.add(operationId)
        } else {
            current.remove(operationId)
        }
        _loadingOperations.value = current
    }
    
    fun isOperationLoading(operationId: String): Boolean {
        return _loadingOperations.value.contains(operationId)
    }

    fun addProductToCart(
        product: ProductInventory, 
        quantity: Int = 1,
        onTransfer: ((ProductInventory, Int, () -> Unit, (String) -> Unit) -> Unit)? = null
    ) {
        val operationId = "add_${product.ARTICULO_ID}"
        setOperationLoading(operationId, true)
        
        val existingIndex =
            _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }

        if (existingIndex != -1) {
            val existingItem = _cartItems[existingIndex]
            _cartItems[existingIndex] =
                existingItem.copy(quantity = existingItem.quantity + quantity)
        } else {
            _cartItems.add(CartItem(product, quantity))
        }
        
        onTransfer?.invoke(
            product, 
            quantity,
            { setOperationLoading(operationId, false) },
            { error -> 
                setOperationLoading(operationId, false)
                if (existingIndex != -1) {
                    val existingItem = _cartItems[existingIndex]
                    _cartItems[existingIndex] = existingItem.copy(quantity = existingItem.quantity - quantity)
                } else {
                    _cartItems.removeAll { it.product.ARTICULO_ID == product.ARTICULO_ID }
                }
            }
        ) ?: setOperationLoading(operationId, false)
        
        markAsModified()
    }

    fun removeProduct(
        product: ProductInventory,
        onTransfer: ((ProductInventory, Int, () -> Unit, (String) -> Unit) -> Unit)? = null
    ) {
        val operationId = "remove_${product.ARTICULO_ID}"
        val itemToRemove = _cartItems.find { it.product.ARTICULO_ID == product.ARTICULO_ID }
        
        if (itemToRemove != null) {
            setOperationLoading(operationId, true)
            val quantityToReturn = itemToRemove.quantity
            
            _cartItems.removeAll { it.product.ARTICULO_ID == product.ARTICULO_ID }
            
            onTransfer?.invoke(
                product,
                quantityToReturn,
                { 
                    setOperationLoading(operationId, false)
                    markAsModified()
                },
                { error ->
                    setOperationLoading(operationId, false)
                    _cartItems.add(itemToRemove)
                }
            ) ?: run {
                setOperationLoading(operationId, false)
                markAsModified()
            }
        }
    }

    private fun changeQuantity(
        product: ProductInventory,
        increase: Boolean,
        stockLimit: Int,
        onTransfer: ((ProductInventory, Int, Boolean, () -> Unit, (String) -> Unit) -> Unit)? = null
    ) {
        val operationId = "${if (increase) "inc" else "dec"}_${product.ARTICULO_ID}"
        val itemIndex = _cartItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }
        
        if (itemIndex != -1) {
            val item = _cartItems[itemIndex]
            val newQuantity = if (increase) (item.quantity + 1).coerceAtMost(stockLimit)
            else item.quantity - 1

            if (newQuantity != item.quantity) {
                setOperationLoading(operationId, true)
                val oldQuantity = item.quantity
                
                when {
                    newQuantity < 1 -> _cartItems.removeAt(itemIndex)
                    else -> _cartItems[itemIndex] = item.copy(quantity = newQuantity)
                }
                
                        onTransfer?.let { transferFn ->
                    val quantityChange = if (increase) 1 else -1
                    transferFn(
                        product, 
                        quantityChange, 
                        increase,
                        { setOperationLoading(operationId, false) },
                        { error ->
                            setOperationLoading(operationId, false)
                                        if (newQuantity < 1) {
                                _cartItems.add(itemIndex, item)
                            } else {
                                _cartItems[itemIndex] = item.copy(quantity = oldQuantity)
                            }
                        }
                    )
                } ?: setOperationLoading(operationId, false)
                
                _hasUnsavedChanges.value = checkForUnsavedChanges()
            }
        }
    }

    fun increaseQuantity(
        product: ProductInventory, 
        stockLimit: Int,
        onTransfer: ((ProductInventory, Int, Boolean, () -> Unit, (String) -> Unit) -> Unit)? = null
    ) = changeQuantity(product, true, stockLimit, onTransfer)

    fun decreaseQuantity(
        product: ProductInventory,
        onTransfer: ((ProductInventory, Int, Boolean, () -> Unit, (String) -> Unit) -> Unit)? = null
    ) = changeQuantity(product, false, Int.MAX_VALUE, onTransfer)


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
