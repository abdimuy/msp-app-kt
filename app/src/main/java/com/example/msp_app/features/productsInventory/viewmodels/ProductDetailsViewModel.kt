package com.example.msp_app.features.productsInventory.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.productInventory.ProductInventoryApi
import com.example.msp_app.data.local.datasource.productInventory.ProductInventoryLocalDataSource
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.data.models.productInventory.toDomain
import com.example.msp_app.data.models.productInventory.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductDetailsViewModel @JvmOverloads constructor(
    application: Application,
    private val api: ProductInventoryApi =
        ApiProvider.create(ProductInventoryApi::class.java),
    private val localDataSource: ProductInventoryLocalDataSource =
        ProductInventoryLocalDataSource(application.applicationContext),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val _product = MutableStateFlow<ProductInventory?>(null)
    val product: StateFlow<ProductInventory?> = _product

    private val _productState = MutableStateFlow<ResultState<ProductInventory>>(ResultState.Idle)
    val productState: StateFlow<ResultState<ProductInventory>> = _productState

    /**
     * Carga el detalle de un producto priorizando internet.
     *
     * Orden: red primero (y se cachea todo el catálogo de paso), y solo si la red
     * falla se usa el cache local. Si ambos fallan se expone un estado de Error para
     * que la UI muestre "Reintentar" en vez de un loader infinito.
     */
    fun loadProductById(id: Int) {
        viewModelScope.launch {
            _productState.value = ResultState.Loading
            _product.value = null
            try {
                val list = withContext(ioDispatcher) {
                    api.getProductInventory().body
                }
                val remote = list.firstOrNull { it.ARTICULO_ID == id }
                if (remote != null) {
                    _product.value = remote
                    _productState.value = ResultState.Success(remote)
                    cacheCatalog(list)
                } else {
                    _productState.value = ResultState.Error(
                        "No se encontró el producto"
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "ProductDetailsViewModel",
                    "Error obteniendo producto remoto: ${e.message}"
                )
                loadFromLocal(id)
            }
        }
    }

    private suspend fun cacheCatalog(list: List<ProductInventory>) {
        withContext(ioDispatcher) {
            try {
                localDataSource.insertAll(list.map { it.toEntity() })
            } catch (e: Exception) {
                Log.e(
                    "ProductDetailsViewModel",
                    "Error cacheando catálogo: ${e.message}"
                )
            }
        }
    }

    private suspend fun loadFromLocal(id: Int) {
        try {
            val localEntity = withContext(ioDispatcher) {
                localDataSource.getProductInventoryById(id)
            }
            if (localEntity == null) {
                _product.value = null
                _productState.value = ResultState.Error("Producto no encontrado")
                return
            }
            val local = localEntity.toDomain()
            _product.value = local
            _productState.value = ResultState.Success(local)
        } catch (e: Exception) {
            _product.value = null
            _productState.value = ResultState.Error(
                "No se pudo cargar el producto. Revisa tu conexión."
            )
        }
    }
}
