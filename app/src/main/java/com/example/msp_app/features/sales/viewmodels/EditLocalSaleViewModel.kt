package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.local.entities.LocalSaleComboEntity
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.utils.PriceParser
import com.example.msp_app.features.sales.sync.enqueueLocalSaleUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.logging.logSaleError
import com.example.msp_app.core.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class EditLocalSaleViewModel(application: Application) : AndroidViewModel(application) {
    private val localSaleStore = LocalSaleDataSource(application.applicationContext)
    private val saleProduct = SaleProductLocalDataSource(application.applicationContext)
    private val comboDataSource = ComboLocalDataSource(application.applicationContext)
    private val logger: RemoteLogger by lazy { RemoteLogger.getInstance(application) }

    private val _selectedSale = MutableStateFlow<LocalSaleEntity?>(null)
    val selectedSale: StateFlow<LocalSaleEntity?> = _selectedSale

    private val _saleImages = MutableStateFlow<List<LocalSaleImageEntity>>(emptyList())
    val saleImages: StateFlow<List<LocalSaleImageEntity>> = _saleImages

    private val _saleProducts = MutableStateFlow<List<LocalSaleProductEntity>>(emptyList())
    val saleProducts: StateFlow<List<LocalSaleProductEntity>> = _saleProducts

    private val _saleCombos = MutableStateFlow<List<LocalSaleComboEntity>>(emptyList())
    val saleCombos: StateFlow<List<LocalSaleComboEntity>> = _saleCombos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult

    // Track images to delete on the server
    private val _imagesToDelete = MutableStateFlow<List<String>>(emptyList())
    val imagesToDelete: StateFlow<List<String>> = _imagesToDelete

    fun loadSaleById(saleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val sale = localSaleStore.getSaleById(saleId)
                _selectedSale.value = sale

                if (sale != null) {
                    loadImagesBySaleId(saleId)
                    loadProductsBySaleId(saleId)
                    loadCombosBySaleId(saleId)
                }
            } catch (e: Exception) {
                Log.e("EditLocalSaleViewModel", "Error loading sale: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadImagesBySaleId(saleId: String) {
        val images = localSaleStore.getImagesForSale(saleId)
        _saleImages.value = images
    }

    private suspend fun loadProductsBySaleId(saleId: String) {
        try {
            val products = saleProduct.getProductsForSale(saleId)
            _saleProducts.value = products
        } catch (e: Exception) {
            Log.e("EditLocalSaleViewModel", "Error loading products: ${e.message}")
            _saleProducts.value = emptyList()
        }
    }

    private suspend fun loadCombosBySaleId(saleId: String) {
        try {
            val combos = comboDataSource.getCombosForSale(saleId)
            _saleCombos.value = combos
        } catch (e: Exception) {
            Log.e("EditLocalSaleViewModel", "Error loading combos: ${e.message}")
            _saleCombos.value = emptyList()
        }
    }

    fun markImageForDeletion(imageId: String) {
        _imagesToDelete.value = _imagesToDelete.value + imageId
    }

    fun unmarkImageForDeletion(imageId: String) {
        _imagesToDelete.value = _imagesToDelete.value - imageId
    }

    fun clearImagesToDelete() {
        _imagesToDelete.value = emptyList()
    }

    suspend fun saveImagesLocally(
        context: Context,
        uris: List<Uri>,
        saleId: String
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val savedPaths = mutableListOf<Pair<String, String>>()

        uris.forEachIndexed { index, uri ->
            try {
                if (!ImageCompressor.isValidImage(context, uri)) {
                    Log.w("EditLocalSaleViewModel", "URI no válido o no es una imagen: $uri")
                    return@forEachIndexed
                }

                val fileName = "sale_${saleId}_edit_${System.currentTimeMillis()}_$index.jpg"

                val result = ImageCompressor.compressImage(
                    context = context,
                    uri = uri,
                    outputFileName = fileName
                )

                savedPaths.add(result.outputFile.absolutePath to "image/jpeg")

            } catch (e: Exception) {
                Log.e("EditLocalSaleViewModel", "Error al comprimir imagen $index: ${e.message}", e)
                logger.error(
                    module = "SALES",
                    action = "IMAGE_COMPRESSION_ERROR",
                    message = "Error al comprimir imagen en edición",
                    error = e,
                    data = mapOf(
                        "saleId" to saleId,
                        "imageIndex" to index,
                        "uri" to uri.toString()
                    )
                )
            }
        }

        return@withContext savedPaths
    }

    /**
     * Guarda las imágenes y retorna los IDs de las nuevas imágenes creadas.
     */
    suspend fun saveSaleImages(
        context: Context,
        uris: List<Uri>,
        saleId: String,
        fechasubida: String
    ): List<String> {
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

            images.forEach { image ->
                localSaleStore.insertSaleImage(image)
            }

            return images.map { it.LOCAL_SALE_IMAGE_ID }

        } catch (e: Exception) {
            Log.e("EditLocalSaleViewModel", "Error al guardar imágenes: ${e.message}", e)
            throw e
        }
    }

    fun updateSaleWithImages(
        saleId: String,
        clientName: String,
        saleDate: String,
        newImageUris: List<Uri>,
        latitude: Double,
        longitude: Double,
        address: String,
        numero: String? = null,
        colonia: String? = null,
        poblacion: String? = null,
        ciudad: String? = null,
        tipoVenta: String? = "CREDITO",
        installment: Double,
        downpayment: Double,
        phone: String,
        paymentfrequency: String,
        avaloresponsable: String,
        note: String,
        collectionday: String,
        totalprice: Double,
        shorttermtime: Int,
        shorttermamount: Double,
        cashamount: Double,
        saleProducts: List<SaleItem>,
        saleCombos: List<LocalSaleComboEntity> = emptyList(),
        context: Context,
        userEmail: String,
        zonaClienteId: Int? = null,
        zonaClienteNombre: String? = null,
        almacenOrigenId: Int? = null,
        almacenDestinoId: Int? = null
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _saveResult.value = null

                logger.info(
                    module = "SALES",
                    action = "UPDATE_SALE_ATTEMPT",
                    message = "Iniciando actualización de venta",
                    data = mapOf(
                        "saleId" to saleId,
                        "clientName" to clientName,
                        "productCount" to saleProducts.size,
                        "newImagesCount" to newImageUris.size,
                        "imagesToDeleteCount" to _imagesToDelete.value.size,
                        "userEmail" to userEmail
                    )
                )

                if (saleProducts.isEmpty()) {
                    val errorMsg = "La venta debe tener al menos un producto"
                    logger.logSaleError(
                        saleId = saleId,
                        clientName = clientName,
                        errorMessage = errorMsg,
                        validationErrors = listOf("No hay productos en la venta")
                    )
                    _saveResult.value = SaveResult.Error(errorMsg)
                    return@launch
                }

                if (clientName.isBlank()) {
                    val errorMsg = "El nombre del cliente es requerido"
                    logger.logSaleError(
                        saleId = saleId,
                        clientName = "(vacío)",
                        errorMessage = errorMsg,
                        validationErrors = listOf("Nombre del cliente vacío")
                    )
                    _saveResult.value = SaveResult.Error(errorMsg)
                    return@launch
                }

                // Update the sale entity
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
                    MONTO_DE_CONTADO = cashamount,
                    ENVIADO = false,  // Mark as not sent since we're updating
                    NUMERO = numero,
                    COLONIA = colonia,
                    POBLACION = poblacion,
                    CIUDAD = ciudad,
                    TIPO_VENTA = tipoVenta,
                    ZONA_CLIENTE_ID = zonaClienteId,
                    ZONA_CLIENTE = zonaClienteNombre
                )

                logger.debug(
                    module = "SALES",
                    action = "UPDATE_SALE",
                    message = "Actualizando venta en base de datos local",
                    data = mapOf("saleId" to saleId, "clientName" to clientName)
                )
                localSaleStore.updateSale(saleEntity)

                // Delete old products and insert new ones
                saleProduct.deleteProductsForSale(saleId)

                val productEntities = saleProducts.map { saleItem ->
                    val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
                    LocalSaleProductEntity(
                        LOCAL_SALE_ID = saleId,
                        ARTICULO_ID = saleItem.product.ARTICULO_ID,
                        ARTICULO = saleItem.product.ARTICULO,
                        CANTIDAD = saleItem.quantity,
                        PRECIO_LISTA = parsedPrices.precioLista,
                        PRECIO_CORTO_PLAZO = parsedPrices.precioCortoplazo,
                        PRECIO_CONTADO = parsedPrices.precioContado,
                        COMBO_ID = saleItem.comboId
                    )
                }

                if (productEntities.isNotEmpty()) {
                    saleProduct.insertSaleProducts(productEntities)
                }

                // Delete old combos and insert new ones in a transaction
                comboDataSource.replaceCombosForSale(saleId, saleCombos)

                // Get images to delete IDs - NO borramos los archivos físicos aquí
                // Los archivos se borrarán después de la sincronización exitosa
                val imagesToDeleteIds = _imagesToDelete.value

                // Solo borramos de la base de datos local las imágenes marcadas para eliminar
                // Los archivos físicos se mantienen hasta que la sincronización sea exitosa
                if (imagesToDeleteIds.isNotEmpty()) {
                    localSaleStore.deleteImagesByIds(imagesToDeleteIds)
                }

                // Save new images and get their IDs
                val newImageIds = if (newImageUris.isNotEmpty()) {
                    saveSaleImages(context, newImageUris, saleId, java.time.Instant.now().toString())
                } else {
                    emptyList()
                }

                // Enqueue sync with UPDATE operation
                // Solo enviamos las imágenes NUEVAS, no las que ya están en el servidor
                enqueueLocalSaleUpdate(
                    context = context,
                    localSaleId = saleId,
                    userEmail = userEmail,
                    imagenesAEliminar = imagesToDeleteIds,
                    imagenesNuevas = newImageIds,
                    almacenOrigenId = almacenOrigenId,
                    almacenDestinoId = almacenDestinoId
                )

                _saveResult.value = SaveResult.Success(saleId)
                clearImagesToDelete()

                logger.info(
                    module = "SALES",
                    action = "UPDATE_SALE_SUCCESS",
                    message = "Venta actualizada exitosamente",
                    data = mapOf(
                        "saleId" to saleId,
                        "clientName" to clientName,
                        "productCount" to saleProducts.size,
                        "newImageCount" to newImageUris.size,
                        "deletedImageCount" to imagesToDeleteIds.size
                    )
                )

            } catch (e: Exception) {
                Log.e("EditLocalSaleViewModel", "Error updating sale: ${e.message}", e)

                logger.error(
                    module = "SALES",
                    action = "UPDATE_SALE_ERROR",
                    message = "Error al actualizar venta: ${e.message}",
                    error = e,
                    data = mapOf(
                        "saleId" to saleId,
                        "clientName" to clientName,
                        "productCount" to saleProducts.size,
                        "errorType" to e.javaClass.simpleName
                    )
                )

                _saveResult.value = SaveResult.Error(e.message ?: "Error desconocido")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }
}
