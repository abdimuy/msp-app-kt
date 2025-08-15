package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class NewLocalSaleViewModel(application: Application) : AndroidViewModel(application) {
    private val localSaleStore = LocalSaleDataSource(application.applicationContext)

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
        direccion: String,
        context: Context
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val saleEntity = LocalSaleEntity(
                    LOCAL_SALE_ID = saleId,
                    NOMBRE_CLIENTE = clientName,
                    FECHA_VENTA = saleDate,
                    LATITUD = latitude,
                    LONGITUD = longitude,
                    DIRECCION = direccion
                )

                localSaleStore.insertSale(saleEntity)

                if (imageUris.isNotEmpty()) {
                    saveSaleImages(context, imageUris, saleId, saleDate)
                }
                _saveResult.value = SaveResult.Success(saleId)
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Error desconocido")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun deleteSale(saleId: String) {
        viewModelScope.launch {
            try {
                localSaleStore.deleteImagesForSale(saleId)
                loadAllSales()
            } catch (e: Exception) {

            }
        }
    }
}

sealed class SaveResult {
    data class Success(val saleId: String) : SaveResult()
    data class Error(val message: String) : SaveResult()
}