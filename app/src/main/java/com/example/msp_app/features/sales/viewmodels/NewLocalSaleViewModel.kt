package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.logging.logSaleError
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.utils.PriceParser
import com.example.msp_app.workmanager.enqueuePendingLocalSalesWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class NewLocalSaleViewModel(application: Application) : AndroidViewModel(application) {
    private val localSaleStore = LocalSaleDataSource(application.applicationContext)
    private val saleProduct = SaleProductLocalDataSource(application.applicationContext)
    private val logger: RemoteLogger by lazy { RemoteLogger.getInstance(application) }

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

    private val _pendingSales = MutableStateFlow<List<LocalSaleEntity>>(emptyList())
    val pendingSales: StateFlow<List<LocalSaleEntity>> = _pendingSales

    private val _defectName = MutableStateFlow(TextFieldValue(""))
    val defectName: StateFlow<TextFieldValue> = _defectName.asStateFlow()

    private val _imageUris = MutableStateFlow(emptyList<Uri>())
    val imageUris: StateFlow<List<Uri>> = _imageUris.asStateFlow()

    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address.asStateFlow()

    private val _location = MutableStateFlow("")
    val location: StateFlow<String> = _location.asStateFlow()

    private val _latitude = MutableStateFlow(0.0)
    val latitude: StateFlow<Double> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow(0.0)
    val longitude: StateFlow<Double> = _longitude.asStateFlow()

    private val _numero = MutableStateFlow(TextFieldValue(""))
    val numero: StateFlow<TextFieldValue> = _numero.asStateFlow()

    private val _colonia = MutableStateFlow(TextFieldValue(""))
    val colonia: StateFlow<TextFieldValue> = _colonia.asStateFlow()

    private val _poblacion = MutableStateFlow(TextFieldValue(""))
    val poblacion: StateFlow<TextFieldValue> = _poblacion.asStateFlow()

    private val _ciudad = MutableStateFlow(TextFieldValue(""))
    val ciudad: StateFlow<TextFieldValue> = _ciudad.asStateFlow()

    private val _tipoVenta = MutableStateFlow("CONTADO")
    val tipoVenta: StateFlow<String> = _tipoVenta.asStateFlow()

    private val _phone = MutableStateFlow(TextFieldValue(""))
    val phone: StateFlow<TextFieldValue> = _phone.asStateFlow()

    private val _downpayment = MutableStateFlow(TextFieldValue(""))
    val downpayment: StateFlow<TextFieldValue> = _downpayment.asStateFlow()

    private val _installment = MutableStateFlow(TextFieldValue(""))
    val installment: StateFlow<TextFieldValue> = _installment.asStateFlow()

    private val _guarantor = MutableStateFlow(TextFieldValue(""))
    val guarantor: StateFlow<TextFieldValue> = _guarantor.asStateFlow()

    private val _note = MutableStateFlow(TextFieldValue(""))
    val note: StateFlow<TextFieldValue> = _note.asStateFlow()

    private val _collectionday = MutableStateFlow("")
    val collectionday: StateFlow<String> = _collectionday.asStateFlow()

    private val _paymentfrequency = MutableStateFlow("")
    val paymentfrequency: StateFlow<String> = _paymentfrequency.asStateFlow()


    fun onDefectNameChange(newValue: TextFieldValue) {
        _defectName.value = newValue
    }

    fun onImageUrisChange(newValue: List<Uri>) {
        _imageUris.value = newValue
    }

    fun onAddImageUri(uri: Uri) {
        _imageUris.value = _imageUris.value + uri
    }

    fun onRemoveImageUri(uri: Uri) {
        _imageUris.value = _imageUris.value.filterNot { it == uri }
    }

    fun onAddressChange(newValue: String) {
        _address.value = newValue
    }

    fun onLocationChange(newValue: String) {
        _location.value = newValue
    }

    fun onLatLngChange(lat: Double, lon: Double) {
        _latitude.value = lat
        _longitude.value = lon
    }

    fun onNumeroChange(newValue: TextFieldValue) {
        _numero.value = newValue
    }

    fun onColoniaChange(newValue: TextFieldValue) {
        _colonia.value = newValue
    }

    fun onPoblacionChange(newValue: TextFieldValue) {
        _poblacion.value = newValue
    }

    fun onCiudadChange(newValue: TextFieldValue) {
        _ciudad.value = newValue
    }

    fun onTipoVentaChange(newValue: String) {
        _tipoVenta.value = newValue
    }

    fun onPhoneChange(newValue: TextFieldValue) {
        _phone.value = newValue
    }

    fun onDownpaymentChange(newValue: TextFieldValue) {
        _downpayment.value = newValue
    }

    fun onInstallmentChange(newValue: TextFieldValue) {
        _installment.value = newValue
    }

    fun onGuarantorChange(newValue: TextFieldValue) {
        _guarantor.value = newValue
    }

    fun onNoteChange(newValue: TextFieldValue) {
        _note.value = newValue
    }

    fun onCollectionDayChange(newValue: String) {
        _collectionday.value = newValue
    }

    fun onPaymentFrequencyChange(newValue: String) {
        _paymentfrequency.value = newValue
    }

    fun clearForm() {
        _defectName.value = TextFieldValue("")
        _imageUris.value = emptyList()
        _address.value = ""
        _location.value = ""
        _latitude.value = 0.0
        _longitude.value = 0.0
        _numero.value = TextFieldValue("")
        _colonia.value = TextFieldValue("")
        _poblacion.value = TextFieldValue("")
        _ciudad.value = TextFieldValue("")
        _tipoVenta.value = "CONTADO"
        _phone.value = TextFieldValue("")
        _downpayment.value = TextFieldValue("")
        _installment.value = TextFieldValue("")
        _guarantor.value = TextFieldValue("")
        _note.value = TextFieldValue("")
        _collectionday.value = ""
        _paymentfrequency.value = ""
    }

    fun loadAllSales() {
        viewModelScope.launch {
            val result = localSaleStore.getAllSales()
            _sales.value = result
        }
    }

    fun loadPendingSales() {
        viewModelScope.launch {
            val result = localSaleStore.getPendingSales()
            _pendingSales.value = result
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

    suspend fun saveSaleImages(
        context: Context,
        uris: List<Uri>,
        saleId: String,
        fechasubida: String
    ) {
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

        } catch (e: Exception) {
            Log.e("NewLocalSaleViewModel", "Error al guardar imágenes: ${e.message}", e)
            throw e
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
        numero: String? = null,
        colonia: String? = null,
        poblacion: String? = null,
        ciudad: String? = null,
        tipoVenta: String? = "CONTADO",
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
        enviado: Boolean,
        saleProducts: List<SaleItem>,
        context: Context,
        userEmail: String
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _saveResult.value = null

                // Log del intento de creación
                logger.info(
                    module = "SALES",
                    action = "CREATE_SALE_ATTEMPT",
                    message = "Iniciando creación de venta",
                    data = mapOf(
                        "saleId" to saleId,
                        "clientName" to clientName,
                        "productCount" to saleProducts.size,
                        "hasImages" to imageUris.isNotEmpty(),
                        "hasLocation" to (latitude != 0.0 && longitude != 0.0),
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
                    ENVIADO = enviado,
                    NUMERO = numero,
                    COLONIA = colonia,
                    POBLACION = poblacion,
                    CIUDAD = ciudad,
                    TIPO_VENTA = tipoVenta
                )

                logger.debug(
                    module = "SALES",
                    action = "INSERT_SALE",
                    message = "Insertando venta en base de datos local",
                    data = mapOf("saleId" to saleId, "clientName" to clientName)
                )
                localSaleStore.insertSale(saleEntity)

                val productEntities = saleProducts.map { saleItem ->
                    val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
                    LocalSaleProductEntity(
                        LOCAL_SALE_ID = saleId,
                        ARTICULO_ID = saleItem.product.ARTICULO_ID,
                        ARTICULO = saleItem.product.ARTICULO,
                        CANTIDAD = saleItem.quantity,
                        PRECIO_LISTA = parsedPrices.precioLista,
                        PRECIO_CORTO_PLAZO = parsedPrices.precioCortoplazo,
                        PRECIO_CONTADO = parsedPrices.precioContado
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

                // Log de éxito
                logger.info(
                    module = "SALES",
                    action = "CREATE_SALE_SUCCESS",
                    message = "Venta creada exitosamente",
                    data = mapOf(
                        "saleId" to saleId,
                        "clientName" to clientName,
                        "productCount" to saleProducts.size,
                        "imageCount" to imageUris.size
                    )
                )

            } catch (e: Exception) {
                Log.e("NewLocalSaleViewModel", "Error creating sale: ${e.message}", e)

                // Log del error en Firebase
                logger.error(
                    module = "SALES",
                    action = "CREATE_SALE_ERROR",
                    message = "Error al crear venta: ${e.message}",
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

    /**
     * Método de prueba para generar errores y probar el sistema de logging
     * ELIMINAR EN PRODUCCIÓN
     */
    fun testErrorLogging(clientName: String, productCount: Int) {
        viewModelScope.launch {
            try {
                val testSaleId = "TEST-${System.currentTimeMillis()}"

                // Log de prueba INFO
                logger.info(
                    module = "SALES_TEST",
                    action = "TEST_ERROR_SYSTEM",
                    message = "Iniciando prueba del sistema de errores",
                    data = mapOf(
                        "testSaleId" to testSaleId,
                        "clientName" to clientName,
                        "productCount" to productCount,
                        "testType" to "intentional_error"
                    )
                )

                // Log de prueba WARNING
                logger.warning(
                    module = "SALES_TEST",
                    action = "TEST_WARNING",
                    message = "Esta es una advertencia de prueba",
                    data = mapOf(
                        "warningType" to "test_warning",
                        "severity" to "medium"
                    )
                )

                // Simular diferentes tipos de errores
                val randomError = (1..4).random()

                when (randomError) {
                    1 -> {
                        // Error de validación
                        logger.logSaleError(
                            saleId = testSaleId,
                            clientName = clientName,
                            errorMessage = "Error de validación de prueba: Productos insuficientes",
                            validationErrors = listOf(
                                "No hay suficiente stock",
                                "El cliente no tiene crédito aprobado",
                                "Faltan imágenes requeridas"
                            ),
                            additionalData = mapOf(
                                "testScenario" to "validation_error",
                                "isTest" to true
                            )
                        )
                        _saveResult.value =
                            SaveResult.Error("❌ Error de prueba: Validación fallida - Revisa Firebase para ver los logs")
                    }

                    2 -> {
                        // Error de base de datos
                        val dbError = Exception("Database connection timeout - TEST ERROR")
                        logger.error(
                            module = "SALES_TEST",
                            action = "DATABASE_ERROR",
                            message = "Error de base de datos simulado",
                            error = dbError,
                            data = mapOf(
                                "errorType" to "database_timeout",
                                "retryCount" to 3,
                                "isTest" to true
                            )
                        )
                        _saveResult.value =
                            SaveResult.Error("❌ Error de prueba: Base de datos - Revisa Firebase para ver los logs")
                    }

                    3 -> {
                        // Error crítico con NullPointerException
                        val npe =
                            NullPointerException("Simulated NPE for testing - Something was null")
                        logger.critical(
                            module = "SALES_TEST",
                            action = "CRITICAL_NPE",
                            message = "Error crítico simulado: NullPointerException",
                            error = npe,
                            data = mapOf(
                                "crashType" to "null_pointer",
                                "affectedModule" to "sales_creation",
                                "isTest" to true
                            )
                        )
                        _saveResult.value =
                            SaveResult.Error("❌ Error crítico de prueba: NPE - Revisa Firebase para ver los logs")
                    }

                    4 -> {
                        // Error de red
                        logger.error(
                            module = "SALES_TEST",
                            action = "NETWORK_ERROR",
                            message = "Error de red simulado: No se pudo conectar al servidor",
                            data = mapOf(
                                "errorCode" to 503,
                                "endpoint" to "/api/sales/create",
                                "timeout" to 30000,
                                "isTest" to true
                            )
                        )
                        _saveResult.value =
                            SaveResult.Error("❌ Error de prueba: Red - Revisa Firebase para ver los logs")
                    }
                }

                // Log de evento de prueba
                logger.logEvent(
                    module = "SALES_TEST",
                    eventType = RemoteLogger.EventType.SYSTEM_EVENT,
                    eventName = "test_completed",
                    data = mapOf(
                        "testId" to testSaleId,
                        "errorType" to randomError,
                        "timestamp" to System.currentTimeMillis()
                    )
                )

            } catch (e: Exception) {
                logger.critical(
                    module = "SALES_TEST",
                    action = "TEST_SYSTEM_FAILURE",
                    message = "El sistema de prueba falló inesperadamente",
                    error = e
                )
                _saveResult.value = SaveResult.Error("Error en el sistema de prueba: ${e.message}")
            }
        }
    }
}

sealed class SaveResult {
    data class Success(val saleId: String) : SaveResult()
    data class Error(val message: String) : SaveResult()
}