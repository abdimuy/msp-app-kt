package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.workmanager.enqueuePendingLocalSalesWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class NewLocalSaleViewModel(application: Application) : AndroidViewModel(application) {
    private val localSaleStore = LocalSaleDataSource(application.applicationContext)
    private val saleProduct = SaleProductLocalDataSource(application.applicationContext)

    private val _sales = MutableStateFlow<List<LocalSaleEntity>>(emptyList())
    val sales: StateFlow<List<LocalSaleEntity>> = _sales

    private val _selectedSale = MutableStateFlow<LocalSaleEntity?>(null)
    val selectedSale: StateFlow<LocalSaleEntity?> = _selectedSale

    private val _saleImages = MutableStateFlow<List<LocalSaleImageEntity>>(emptyList())
    val saleImages: StateFlow<List<LocalSaleImageEntity>> = _saleImages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult

    private val _saleProducts = MutableStateFlow<List<LocalSaleProductEntity>>(emptyList())
    val saleProducts: StateFlow<List<LocalSaleProductEntity>> = _saleProducts

    private val _pendingSalesState =
        MutableStateFlow<ResultState<List<LocalSaleEntity>>>(ResultState.Loading)
    val pendingSalesState: StateFlow<ResultState<List<LocalSaleEntity>>> = _pendingSalesState

    fun loadAllSales() {
        viewModelScope.launch {
            val result = localSaleStore.getAllSales()
            _sales.value = result
        }
    }

    fun getSaleById(saleId: String) {
        viewModelScope.launch {
            val sale = localSaleStore.getSaleById(saleId)
            _selectedSale.value = sale
        }
    }

    fun loadImagesBySaleId(saleId: String) {
        viewModelScope.launch {
            val images = localSaleStore.getImagesForSale(saleId)
            _saleImages.value = images
        }
    }

    fun saveImagesLocally(
        context: Context,
        uris: List<Uri>,
        saleId: String
    ): List<Pair<String, String>> {
        val savedPaths = mutableListOf<Pair<String, String>>()

        uris.forEach { uri ->
            val inputStream = context.contentResolver.openInputStream(uri)
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = if (mime == "image/png") ".png" else ".jpg"

            val fileName = "sale_${saleId}_${System.currentTimeMillis()}$extension"
            val file = File(context.filesDir, fileName)

            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            savedPaths.add(file.absolutePath to mime)
        }
        return savedPaths
    }

    fun saveSaleImages(
        context: Context,
        uris: List<Uri>,
        saleId: String,
        fechasubida: String
    ) {
        viewModelScope.launch {
            try {
                val savedPaths = saveImagesLocally(context, uris, saleId)
                val images = savedPaths.map { (path, _) ->
                    LocalSaleImageEntity(
                        LOCAL_SALE_IMAGE_ID = UUID.randomUUID().toString(),
                        LOCAL_SALE_ID = saleId,
                        IMAGE_URI = path,
                        FECHA_SUBIDA = fechasubida
                    )
                }

                localSaleStore.insertSaleImage(images.first())
                images.drop(1).forEach { image ->
                    localSaleStore.insertSaleImage(image)
                }
                loadImagesBySaleId(saleId)

            } catch (e: Exception) {
                Log.e("Error", "${e.message}")
            }
        }
    }

    fun createSaleWithImages(
        saleId: String,
        clientName: String,
        saleDate: String,
        imageUris: List<Uri>,
        latitude: Double,
        longitude: Double,
        address: String,
        installment: Double,
        downpayment: Double,
        phone: String,
        paymentfrequency: String,
        avaloresponsable: String,
        note: String,
        collectionday: String,
        totalprice: Double = 0.0,
        shorttermtime: Int = 0,
        shorttermamount: Double = 0.0,
        enviado: Boolean,
        saleProducts: List<SaleItem>,
        context: Context,
        userEmail: String
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _saveResult.value = null

                if (saleProducts.isEmpty()) {
                    _saveResult.value = SaveResult.Error("La venta debe tener al menos un producto")
                    return@launch
                }

                if (clientName.isBlank()) {
                    _saveResult.value = SaveResult.Error("El nombre del cliente es requerido")
                    return@launch
                }

                val saleEntity = LocalSaleEntity(
                    LOCAL_SALE_ID = saleId,
                    NOMBRE_CLIENTE = clientName,
                    FECHA_VENTA = saleDate,
                    LATITUD = latitude,
                    LONGITUD = longitude,
                    DIRECCION = address,
                    PARCIALIDAD = installment,
                    ENGANCHE = downpayment,
                    TELEFONO = phone,
                    FREC_PAGO = paymentfrequency,
                    AVAL_O_RESPONSABLE = avaloresponsable,
                    NOTA = note,
                    DIA_COBRANZA = collectionday,
                    PRECIO_TOTAL = totalprice,
                    TIEMPO_A_CORTO_PLAZOMESES = shorttermtime,
                    MONTO_A_CORTO_PLAZO = shorttermamount,
                    ENVIADO = enviado
                )

                localSaleStore.insertSale(saleEntity)

                val productEntities = saleProducts.map { saleItem ->
                    LocalSaleProductEntity(
                        LOCAL_SALE_ID = saleId,
                        ARTICULO_ID = saleItem.product.ARTICULO_ID,
                        ARTICULO = saleItem.product.ARTICULO,
                        CANTIDAD = saleItem.quantity,
                        PRECIO_LISTA = 0.0,
                        PRECIO_CORTO_PLAZO = 0.0,
                        PRECIO_CONTADO = 0.0
                    )
                }

                if (productEntities.isNotEmpty()) {
                    saleProduct.insertSaleProducts(productEntities)
                }

                if (imageUris.isNotEmpty()) {
                    saveSaleImages(context, imageUris, saleId, saleDate)
                }

                enqueuePendingLocalSalesWorker(context, saleId, userEmail)

                _saveResult.value = SaveResult.Success(saleId)
                loadAllSales()

            } catch (e: Exception) {
                Log.e("NewLocalSaleViewModel", "Error creating sale: ${e.message}", e)
                _saveResult.value = SaveResult.Error(e.message ?: "Error desconocido")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteSale(saleId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                saleProduct.deleteProductsForSale(saleId)
                localSaleStore.deleteImagesForSale(saleId)

                loadAllSales()
                _saveResult.value = SaveResult.Success("Venta eliminada correctamente")

            } catch (e: Exception) {
                Log.e("NewLocalSaleViewModel", "Error deleting sale: ${e.message}", e)
                _saveResult.value = SaveResult.Error("Error al eliminar la venta")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun loadProductsBySaleId(saleId: String) {
        viewModelScope.launch {
            try {
                val products = saleProduct.getProductsForSale(saleId)
                _saleProducts.value = products
            } catch (e: Exception) {
                Log.e(
                    "NewLocalSaleViewModel",
                    "Error loading products for sale $saleId: ${e.message}"
                )
                _saleProducts.value = emptyList()
            }
        }
    }

    fun retryPendingSales(userEmail: String) {
        viewModelScope.launch {
            try {
                val pendingSales = localSaleStore.getPendingSales()
                pendingSales.forEach { sale ->
                    enqueuePendingLocalSalesWorker(
                        getApplication<Application>().applicationContext,
                        sale.LOCAL_SALE_ID,
                        userEmail,
                        replace = true
                    )
                }
                Log.d(
                    "NewLocalSaleViewModel",
                    "Reintentando envío de ${pendingSales.size} ventas pendientes"
                )
            } catch (e: Exception) {
                Log.e("NewLocalSaleViewModel", "Error al reintentar ventas pendientes", e)
            }
        }
    }

    fun getPendingSalesCount() {
        viewModelScope.launch {
            try {
                val pendingSales = localSaleStore.getPendingSales()
                Log.d("NewLocalSaleViewModel", "Ventas pendientes por enviar: ${pendingSales.size}")
            } catch (e: Exception) {
                Log.e("NewLocalSaleViewModel", "Error al obtener ventas pendientes", e)
            }
        }
    }

    fun getPendingSales() {
        viewModelScope.launch {
            try {
                _pendingSalesState.value = ResultState.Loading
                val pendingSales = localSaleStore.getPendingSales()
                _pendingSalesState.value = ResultState.Success(pendingSales)
            } catch (e: Exception) {
                _pendingSalesState.value =
                    ResultState.Error(e.message ?: "Error al obtener ventas pendientes")
            }
        }
    }
}

sealed class SaveResult {
    data class Success(val saleId: String) : SaveResult()
    data class Error(val message: String) : SaveResult()
}