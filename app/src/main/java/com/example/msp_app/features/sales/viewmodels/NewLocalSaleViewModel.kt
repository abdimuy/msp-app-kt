package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleImageEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.database.sinCancelar
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.logging.logSaleError
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.features.sales.data.FotosDeLaVentaLocal
import com.example.msp_app.features.sales.data.RegistroDeVentaLocal
import com.example.msp_app.features.sales.data.ResultadoDeLaVentaLocal
import com.example.msp_app.utils.PriceParser
import com.example.msp_app.workmanager.enqueuePendingLocalSalesWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NewLocalSaleViewModel @JvmOverloads constructor(
    application: Application,
    private val fotos: FotosDeLaVentaLocal = FotosDeLaVentaLocal(application.applicationContext),
    private val registro: RegistroDeVentaLocal =
        RegistroDeVentaLocal(AppDatabase.getInstance(application.applicationContext))
) : AndroidViewModel(application) {
    private val localSaleStore = LocalSaleDataSource(application.applicationContext)
    private val saleProduct = SaleProductLocalDataSource(application.applicationContext)
    private val comboDataSource = ComboLocalDataSource(application.applicationContext)
    private val logger: RemoteLogger by lazy { RemoteLogger.getInstance(application) }

    /**
     * Mutex para prevenir la creación concurrente de ventas.
     * Garantiza que solo una operación de guardado se ejecute a la vez,
     * evitando race conditions y ventas duplicadas.
     */
    private val saleMutex = Mutex()

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

    private val _saleCombos = MutableStateFlow<List<LocalSaleComboEntity>>(emptyList())
    val saleCombos: StateFlow<List<LocalSaleComboEntity>> = _saleCombos

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

    fun loadCombosBySaleId(saleId: String) {
        viewModelScope.launch {
            try {
                val combos = comboDataSource.getCombosForSale(saleId)
                _saleCombos.value = combos
            } catch (e: Exception) {
                // Camino de lectura/UI: los datasources ya no tragan errores del
                // DAO, así que un fallo aquí se maneja como estado vacío en la UI
                // (no crashea el ViewModel). El guard money-path vive en el sync.
                Log.e(
                    "NewLocalSaleViewModel",
                    "Error loading combos for sale $saleId: ${e.message}"
                )
                _saleCombos.value = emptyList()
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
        enviado: Boolean,
        saleProducts: List<SaleItem>,
        context: Context,
        userEmail: String,
        zonaClienteId: Int? = null,
        zonaClienteNombre: String? = null,
        combos: List<LocalSaleComboEntity> = emptyList(),
        clienteId: Int? = null
    ) {
        viewModelScope.launch {
            // Verificar si ya hay una operación en curso (protección adicional contra doble click)
            if (saleMutex.isLocked) {
                Log.w(
                    "NewLocalSaleViewModel",
                    "Operación de guardado ya en curso, ignorando llamada duplicada"
                )
                return@launch
            }

            saleMutex.withLock {
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
                        return@withLock
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
                        return@withLock
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
                        TIPO_VENTA = tipoVenta,
                        ZONA_CLIENTE_ID = zonaClienteId,
                        ZONA_CLIENTE = zonaClienteNombre,
                        CLIENTE_ID = clienteId
                    )

                    // E-APP-094: con el saleId estable del borrador, una venta que
                    // ya se escribió no se vuelve a escribir (antes: una segunda
                    // venta). Se mira antes de comprimir para no reescribir los
                    // archivos de una venta que quizá se está subiendo.
                    if (registro.yaSeGuardo(saleId)) {
                        _saveResult.value = SaveResult.YaGuardada(saleId)
                        return@withLock
                    }
                    // Marcador antes de la única espera cancelable: comprimir fotos.
                    registro.capturar(saleId, userEmail)

                    val productEntities = saleProducts.map { saleItem ->
                        val parsedPrices = PriceParser.parsePricesFromString(
                            saleItem.product.PRECIOS
                        )
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

                    // Una foto que falla detiene la venta: nada se escribe.
                    val imagenes = try {
                        fotos.preparar(saleId, imageUris, saleDate)
                    } catch (fallida: FotosDeLaVentaLocal.FotoNoProcesada) {
                        Log.e("NewLocalSaleViewModel", fallida.message, fallida)
                        _saveResult.value = SaveResult.Error(fallida.message.orEmpty())
                        return@withLock
                    }

                    logger.debug(
                        module = "SALES",
                        action = "INSERT_SALE",
                        message = "Insertando venta en base de datos local",
                        data = mapOf("saleId" to saleId, "clientName" to clientName)
                    )
                    when (registro.escribir(saleEntity, productEntities, combos, imagenes)) {
                        ResultadoDeLaVentaLocal.ESCRITA -> Unit
                        ResultadoDeLaVentaLocal.YA_ESTABA -> {
                            registro.cerrar(saleId)
                            _saveResult.value = SaveResult.YaGuardada(saleId)
                            return@withLock
                        }
                        ResultadoDeLaVentaLocal.LINEAS_HUERFANAS -> {
                            Log.e("NewLocalSaleViewModel", "Venta $saleId: líneas sin cabecera")
                            _saveResult.value = SaveResult.Error(
                                "Hay productos de esta venta guardados sin la venta"
                            )
                            return@withLock
                        }
                    }

                    // Después del commit: la salida de la pantalla no corta el encolado.
                    sinCancelar { enqueuePendingLocalSalesWorker(context, saleId, userEmail) }
                    registro.cerrar(saleId)

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
                } catch (cancelada: CancellationException) {
                    throw cancelada
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
                        userEmail
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
                        _saveResult.value = SaveResult.Error("❌ Error de prueba: Validación fallida - Revisa Firebase para ver los logs")
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
                        _saveResult.value = SaveResult.Error("❌ Error de prueba: Base de datos - Revisa Firebase para ver los logs")
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
                        _saveResult.value = SaveResult.Error("❌ Error crítico de prueba: NPE - Revisa Firebase para ver los logs")
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
                        _saveResult.value = SaveResult.Error("❌ Error de prueba: Red - Revisa Firebase para ver los logs")
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

    /** E-APP-094: la venta con ese id ya estaba guardada; no se escribió otra. */
    data class YaGuardada(val saleId: String) : SaveResult()
}
