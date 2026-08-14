package com.example.msp_app.features.sales.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.msp_app.components.ModernSpinner
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.sales.components.combo.CreateComboDialog
import com.example.msp_app.features.sales.components.productselector.ProductSaleSummary
import com.example.msp_app.features.sales.components.productselector.ProductSelectionBottomSheet
import com.example.msp_app.features.sales.components.saleimagesviewer.ImageViewerDialog
import com.example.msp_app.features.sales.components.zoneselector.ZoneSelectorSimple
import com.example.msp_app.features.sales.viewmodels.EditLocalSaleViewModel
import com.example.msp_app.features.sales.viewmodels.NewSaleFormState
import com.example.msp_app.features.sales.viewmodels.NewSaleFormValidator
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.features.sales.viewmodels.SaveResult
import com.example.msp_app.features.warehouses.WarehouseViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSaleScreen(localSaleId: String, navController: NavController) {
    val viewModel: EditLocalSaleViewModel = viewModel()
    val warehouseViewModel: WarehouseViewModel = hiltViewModel()
    val authViewModel = LocalAuthViewModel.current
    val saleProductsViewModel: SaleProductsViewModel = viewModel()
    val context = LocalContext.current

    val selectedSale by viewModel.selectedSale.collectAsState()
    val existingImages by viewModel.saleImages.collectAsState()
    val existingProducts by viewModel.saleProducts.collectAsState()
    val existingCombos by viewModel.saleCombos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val imagesToDelete by viewModel.imagesToDelete.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    var showProductSheet by remember { mutableStateOf(false) }
    var showCreateComboDialog by remember { mutableStateOf(false) }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var newImageUris by remember { mutableStateOf(listOf<Uri>()) }
    var showImageViewer by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableStateOf(0) }
    var showImageSizeError by remember { mutableStateOf(false) }

    // Form state
    var defectName by remember { mutableStateOf(TextFieldValue("")) }
    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var location by remember { mutableStateOf("") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var numero by remember { mutableStateOf(TextFieldValue("")) }
    var colonia by remember { mutableStateOf(TextFieldValue("")) }
    var poblacion by remember { mutableStateOf(TextFieldValue("")) }
    var ciudad by remember { mutableStateOf(TextFieldValue("")) }
    var tipoVenta by remember { mutableStateOf("CREDITO") }
    var downpayment by remember { mutableStateOf(TextFieldValue("")) }
    var installment by remember { mutableStateOf(TextFieldValue("")) }
    var guarantor by remember { mutableStateOf(TextFieldValue("")) }
    var note by remember { mutableStateOf(TextFieldValue("")) }
    var collectionday by remember { mutableStateOf("") }
    var paymentfrequency by remember { mutableStateOf("") }
    var selectedZoneId by remember { mutableStateOf<Int?>(null) }
    var selectedZoneName by remember { mutableStateOf("") }
    var saleDate by remember { mutableStateOf("") }

    // Dropdowns
    var expandedfrequency by remember { mutableStateOf(false) }
    var expandedDia by remember { mutableStateOf(false) }
    var expandedTipoVenta by remember { mutableStateOf(false) }

    // Validation errors
    var defectNameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    // Colonia/Población/Ciudad: obligatorias en el API desde siempre, sin error
    // propio en esta pantalla hasta el incidente del 2026-08-13.
    var coloniaError by remember { mutableStateOf(false) }
    var poblacionError by remember { mutableStateOf(false) }
    var ciudadError by remember { mutableStateOf(false) }
    var installmentError by remember { mutableStateOf(false) }
    var paymentFrequencyError by remember { mutableStateOf(false) }
    var collectionDayError by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf(false) }
    var productsError by remember { mutableStateOf(false) }
    var downpaymentError by remember { mutableStateOf(false) }
    var zoneError by remember { mutableStateOf(false) }

    // Dialogs
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Flag to track if form was initialized
    var formInitialized by remember { mutableStateOf(false) }

    val frequencyOptions = listOf("Semanal", "Quincenal", "Mensual")
    val dayOptions =
        listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    val tipoVentaOptions = listOf("CONTADO", "CREDITO")

    val userData by authViewModel.userData.collectAsState()
    val warehouseState by warehouseViewModel.warehouseProducts.collectAsState()

    val camionetaId = when (val userState = userData) {
        is ResultState.Success -> userState.data?.CAMIONETA_ASIGNADA
        else -> null
    }

    val productosCamioneta = when (val s = warehouseState) {
        is ResultState.Success -> s.data.body.ARTICULOS
        else -> emptyList()
    }

    // Load sale data
    LaunchedEffect(localSaleId) {
        viewModel.loadSaleById(localSaleId)
    }

    // Load warehouse products
    LaunchedEffect(camionetaId) {
        if (camionetaId != null) {
            warehouseViewModel.selectWarehouse(camionetaId)
        }
    }

    // Initialize form with sale data
    LaunchedEffect(selectedSale, existingProducts, existingCombos, productosCamioneta) {
        if (!formInitialized && selectedSale != null && existingProducts.isNotEmpty() && productosCamioneta.isNotEmpty()) {
            val sale = selectedSale!!
            defectName = TextFieldValue(sale.NOMBRE_CLIENTE)
            phone = TextFieldValue(sale.TELEFONO)
            location = sale.DIRECCION
            latitude = sale.LATITUD
            longitude = sale.LONGITUD
            numero = TextFieldValue(sale.NUMERO ?: "")
            colonia = TextFieldValue(sale.COLONIA ?: "")
            poblacion = TextFieldValue(sale.POBLACION ?: "")
            ciudad = TextFieldValue(sale.CIUDAD ?: "")
            tipoVenta = sale.TIPO_VENTA ?: "CREDITO"
            saleProductsViewModel.setTipoVenta(tipoVenta)
            downpayment = TextFieldValue(sale.ENGANCHE?.toString() ?: "")
            installment = TextFieldValue(sale.PARCIALIDAD.toString())
            guarantor = TextFieldValue(sale.AVAL_O_RESPONSABLE ?: "")
            note = TextFieldValue(sale.NOTA ?: "")
            collectionday = sale.DIA_COBRANZA
            paymentfrequency = sale.FREC_PAGO
            selectedZoneId = sale.ZONA_CLIENTE_ID
            selectedZoneName = sale.ZONA_CLIENTE ?: ""
            saleDate = sale.FECHA_VENTA

            // Load products into SaleProductsViewModel with their comboId
            existingProducts.forEach { productEntity ->
                val product = productosCamioneta.find { it.ARTICULO_ID == productEntity.ARTICULO_ID }
                if (product != null) {
                    saleProductsViewModel.addProductToSaleWithCombo(
                        product,
                        productEntity.CANTIDAD,
                        productEntity.COMBO_ID
                    )
                }
            }

            // Restore combo metadata
            existingCombos.forEach { combo ->
                saleProductsViewModel.createComboWithId(
                    comboId = combo.COMBO_ID,
                    nombreCombo = combo.NOMBRE_COMBO,
                    precioLista = combo.PRECIO_LISTA,
                    precioCortoPlazo = combo.PRECIO_CORTO_PLAZO,
                    precioContado = combo.PRECIO_CONTADO
                )
            }

            formInitialized = true
        }
    }

    // Handle save result
    LaunchedEffect(saveResult) {
        when (val result = saveResult) {
            is SaveResult.Success -> {
                showSuccessDialog = true
                viewModel.clearSaveResult()
            }
            is SaveResult.Error -> {
                errorMessage = result.message
                showErrorDialog = true
                viewModel.clearSaveResult()
            }
            null -> { }
        }
    }

    // Image helpers
    fun getImageSizeFromUri(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun validateImageSize(context: Context, uri: Uri): Boolean {
        val maxSizeInBytes = 20 * 1000 * 1000
        val imageSize = getImageSizeFromUri(context, uri)
        return imageSize <= maxSizeInBytes
    }

    fun createImageUri(context: Context): Uri {
        val imageFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (validateImageSize(context, it)) {
                newImageUris = newImageUris + it
                showImageSizeError = false
            } else {
                showImageSizeError = true
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { uri ->
                if (validateImageSize(context, uri)) {
                    newImageUris = newImageUris + uri
                    showImageSizeError = false
                } else {
                    showImageSizeError = true
                }
            }
        }
    }

    // --- Validación ---
    //
    // Esta pantalla tenía su propia COPIA de las reglas en funciones locales del
    // composable. Copiar reglas es como el incidente del 2026-08-13 se hizo
    // permanente: la copia del alta y la de la edición podían (y de hecho lo
    // hacían) discrepar entre sí y con el servidor sin que nada lo notara — el
    // teléfono de aquí solo medía `length == 10` sobre el texto crudo, y ni esta
    // pantalla ni la de alta validaban colonia/población/ciudad. Ahora ambas
    // llaman a `NewSaleFormValidator`, la única fuente de verdad, empaquetando el
    // estado local en un `NewSaleFormState`. Un vendedor no debe poder editar una
    // venta pendiente y dejarla en un estado que el servidor rechace: sería
    // reponer el atasco que estaba tratando de resolver.

    // Las funciones `validateX` de abajo ya NO contienen reglas: solo adaptan el
    // veredicto del validador compartido a los `remember` de error de esta
    // pantalla, para poder revalidar campo por campo mientras el vendedor teclea.

    fun validateClientName(name: String): Boolean {
        val isValid = NewSaleFormValidator.validateClientName(name)
        defectNameError = !isValid
        return isValid
    }

    fun validatePhone(phoneNumber: String): Boolean {
        val isValid = NewSaleFormValidator.validatePhone(phoneNumber, tipoVenta)
        phoneError = !isValid
        return isValid
    }

    fun validateLocation(loc: String): Boolean {
        val isValid = NewSaleFormValidator.validateStreet(loc)
        locationError = !isValid
        return isValid
    }

    fun validateColonia(value: String) {
        coloniaError = !NewSaleFormValidator.validateColonia(value)
    }

    fun validatePoblacion(value: String) {
        poblacionError = !NewSaleFormValidator.validatePoblacion(value)
    }

    fun validateCiudad(value: String) {
        ciudadError = !NewSaleFormValidator.validateCiudad(value)
    }

    fun validateInstallment(amount: String): Boolean {
        val isValid = NewSaleFormValidator.validateInstallmentEdit(amount, tipoVenta)
        installmentError = !isValid
        return isValid
    }

    fun validatePaymentFrequency(frequency: String): Boolean {
        val isValid = NewSaleFormValidator.validatePaymentFrequency(frequency, tipoVenta)
        paymentFrequencyError = !isValid
        return isValid
    }

    fun validateCollectionDay(day: String): Boolean {
        val isValid = NewSaleFormValidator.validateCollectionDay(day, tipoVenta)
        collectionDayError = !isValid
        return isValid
    }

    fun validateDownpayment(amount: String): Boolean {
        val isValid = NewSaleFormValidator.validateDownpayment(amount)
        downpaymentError = !isValid
        return isValid
    }

    /** Empaqueta los `remember` de esta pantalla en el estado que consume el validador compartido. */
    fun currentFormState(): NewSaleFormState = NewSaleFormState(
        clientName = defectName.text,
        phone = phone.text,
        street = location,
        numero = numero.text,
        colonia = colonia.text,
        poblacion = poblacion.text,
        ciudad = ciudad.text,
        tipoVenta = tipoVenta,
        selectedZoneId = selectedZoneId,
        selectedZoneName = selectedZoneName,
        downpayment = downpayment.text,
        installment = installment.text,
        collectionDay = collectionday,
        paymentFrequency = paymentfrequency
    )

    fun validateFields(): Boolean {
        // En edición las imágenes válidas son las que siguen en el servidor
        // (menos las marcadas para borrar) más las nuevas del carrete — un
        // conteo que no cabe en `NewSaleFormState.imageUris`, por eso viaja
        // aparte a `validateAll`.
        val remainingExistingImages = existingImages.count { it.LOCAL_SALE_IMAGE_ID !in imagesToDelete }
        val errors = NewSaleFormValidator.validateAll(
            state = currentFormState(),
            hasProducts = saleProductsViewModel.hasItems(),
            hasImages = remainingExistingImages + newImageUris.size > 0
        ).copy(
            // Única bandera que esta pantalla NO toma tal cual del alta; el porqué
            // está documentado en `NewSaleFormValidator.validateInstallmentEdit`.
            installment = !NewSaleFormValidator.validateInstallmentEdit(installment.text, tipoVenta)
        )

        defectNameError = errors.clientName
        phoneError = errors.phone
        locationError = errors.location
        coloniaError = errors.colonia
        poblacionError = errors.poblacion
        ciudadError = errors.ciudad
        installmentError = errors.installment
        paymentFrequencyError = errors.paymentFrequency
        collectionDayError = errors.collectionDay
        downpaymentError = errors.downpayment
        zoneError = errors.zone
        imageError = errors.image
        productsError = errors.products

        return !errors.hasAny
    }

    fun updateSale() {
        val userEmail = when (val userState = userData) {
            is ResultState.Success -> userState.data?.EMAIL ?: ""
            else -> ""
        }

        val comboEntities = saleProductsViewModel.getCombosList().map { combo ->
            LocalSaleComboEntity(
                COMBO_ID = combo.comboId,
                LOCAL_SALE_ID = localSaleId,
                NOMBRE_COMBO = combo.nombreCombo,
                PRECIO_LISTA = combo.precioLista,
                PRECIO_CORTO_PLAZO = combo.precioCortoPlazo,
                PRECIO_CONTADO = combo.precioContado
            )
        }

        viewModel.updateSaleWithImages(
            saleId = localSaleId,
            clientName = defectName.text,
            saleDate = saleDate,
            newImageUris = newImageUris,
            latitude = latitude,
            longitude = longitude,
            address = location.trim(),
            // Mismo `trim` que el alta: el servidor valida contra el valor ya
            // recortado, así que persistir "   " tal cual llegaría al API como
            // cadena vacía y reventaría en la cola, no en la pantalla.
            numero = numero.text.trim().ifBlank { null },
            colonia = colonia.text.trim().ifBlank { null },
            poblacion = poblacion.text.trim().ifBlank { null },
            ciudad = ciudad.text.trim().ifBlank { null },
            tipoVenta = tipoVenta,
            installment = if (tipoVenta == "CONTADO") 0.0 else installment.text.toDoubleOrNull() ?: 0.0,
            downpayment = if (tipoVenta == "CONTADO") 0.0 else downpayment.text.toDoubleOrNull() ?: 0.0,
            phone = phone.text.trim(),
            paymentfrequency = if (tipoVenta == "CONTADO") "" else paymentfrequency,
            avaloresponsable = if (tipoVenta == "CONTADO") "" else guarantor.text,
            note = note.text,
            collectionday = if (tipoVenta == "CONTADO") "" else collectionday,
            totalprice = saleProductsViewModel.getTotalPrecioListaWithCombos(),
            shorttermtime = 0,
            shorttermamount = saleProductsViewModel.getTotalMontoCortoPlazoWithCombos(),
            cashamount = saleProductsViewModel.getTotalMontoContadoWithCombos(),
            saleProducts = saleProductsViewModel.saleItems,
            combos = comboEntities,
            context = context,
            userEmail = userEmail,
            zonaClienteId = selectedZoneId,
            zonaClienteNombre = selectedZoneName
        )
    }

    // All images to display (existing + new)
    val displayableExistingImages = existingImages.filter { it.LOCAL_SALE_IMAGE_ID !in imagesToDelete }

    // Image viewer
    if (showImageViewer) {
        val allImageUris = displayableExistingImages.mapNotNull { img ->
            try {
                Uri.parse("file://${img.IMAGE_URI}")
            } catch (e: Exception) {
                null
            }
        } + newImageUris

        if (allImageUris.isNotEmpty()) {
            ImageViewerDialog(
                imageUris = allImageUris,
                initialIndex = selectedImageIndex,
                onDismiss = { showImageViewer = false }
            )
        }
    }

    // Success dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    text = "Venta Actualizada",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text("La venta se ha actualizado correctamente.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Aceptar")
                }
            }
        )
    }

    // Error dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Text(
                    text = "Error al Actualizar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column {
                    Text("No se pudo actualizar la venta:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showErrorDialog = false }
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    // Product selection bottom sheet
    if (showProductSheet) {
        ProductSelectionBottomSheet(
            products = productosCamioneta,
            saleProductsViewModel = saleProductsViewModel,
            onDismiss = { showProductSheet = false },
            onShowCreateComboDialog = {
                saleProductsViewModel.setCreatingCombo(true)
                showCreateComboDialog = true
            }
        )
    }

    // Create combo dialog
    CreateComboDialog(
        show = showCreateComboDialog,
        onDismiss = {
            saleProductsViewModel.setCreatingCombo(false)
            showCreateComboDialog = false
        },
        onConfirm = { nombre, precioLista, precioCortoPlazo, precioContado ->
            saleProductsViewModel.createCombo(
                nombreCombo = nombre,
                precioLista = precioLista,
                precioCortoPlazo = precioCortoPlazo,
                precioContado = precioContado
            )
            saleProductsViewModel.setCreatingCombo(false)
            showCreateComboDialog = false
        },
        selectedProductsCount = saleProductsViewModel.getSelectedProductsCount(),
        suggestedPrices = saleProductsViewModel.getSelectedItemsSuggestedPrices(),
        selectedProductNames = saleProductsViewModel.getSelectedProductNames(),
        tipoVenta = tipoVenta
    )

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Editar Venta",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) { innerPadding ->
        if (isLoading && selectedSale == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ModernSpinner(size = 60.dp)
            }
        } else if (selectedSale == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No se encontró la venta")
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        "Tipo de Venta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Box {
                        OutlinedTextField(
                            value = tipoVenta,
                            onValueChange = { },
                            label = { Text("Tipo de Venta") },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedTipoVenta = true },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { expandedTipoVenta = true }
                                )
                            },
                            shape = RoundedCornerShape(15.dp)
                        )
                        DropdownMenu(
                            expanded = expandedTipoVenta,
                            onDismissRequest = { expandedTipoVenta = false }
                        ) {
                            tipoVentaOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        tipoVenta = option
                                        saleProductsViewModel.setTipoVenta(option)
                                        expandedTipoVenta = false
                                        if (option == "CONTADO") {
                                            selectedZoneId = null
                                            selectedZoneName = ""
                                            zoneError = false
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Zone Selector - Solo mostrar para ventas a CREDITO
                    if (tipoVenta == "CREDITO") {
                        ZoneSelectorSimple(
                            selectedZoneId = selectedZoneId,
                            selectedZoneName = selectedZoneName,
                            onZoneSelected = { zoneId, zoneName ->
                                selectedZoneId = zoneId
                                selectedZoneName = zoneName
                                zoneError = false
                            },
                            error = if (zoneError) "Selecciona una zona" else null,
                            isRequired = true
                        )

                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        "Información del Cliente",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = defectName,
                        onValueChange = { newValue ->
                            defectName = newValue
                            if (newValue.text.isNotEmpty() || defectNameError) {
                                validateClientName(newValue.text)
                            }
                        },
                        label = { Text("Nombre completo del cliente *") },
                        isError = defectNameError,
                        supportingText = if (defectNameError) {
                            {
                                Text(
                                    "Favor de colocar el nombre",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { newValue ->
                            phone = newValue
                            if (newValue.text.isNotEmpty() || phoneError) {
                                validatePhone(newValue.text)
                            }
                        },
                        label = { Text(if (tipoVenta == "CONTADO") "Teléfono" else "Teléfono *") },
                        isError = phoneError,
                        supportingText = if (phoneError) {
                            {
                                Text(
                                    "El teléfono debe tener 10 dígitos",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Location - read only since it was captured on creation
                    OutlinedTextField(
                        value = location,
                        onValueChange = { newValue ->
                            location = newValue
                            if (newValue.isNotEmpty() || locationError) {
                                validateLocation(newValue)
                            }
                        },
                        label = { Text("Calle *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        shape = RoundedCornerShape(15.dp),
                        isError = locationError,
                        supportingText = if (locationError) {
                            {
                                Text(
                                    "Coloque al menos el nombre de la calle",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            null
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = numero,
                            onValueChange = { numero = it },
                            label = { Text("Número") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                        // Mismo cableado que el alta: error propio por campo, no un
                        // mensaje genérico. Editar una venta pendiente no debe
                        // poder dejarla en un estado que el servidor rechace —
                        // sería reponer justo el atasco que se está resolviendo.
                        OutlinedTextField(
                            value = colonia,
                            onValueChange = { newValue ->
                                colonia = newValue
                                if (newValue.text.isNotEmpty() || coloniaError) {
                                    validateColonia(newValue.text)
                                }
                            },
                            label = { Text("Colonia *") },
                            isError = coloniaError,
                            supportingText = if (coloniaError) {
                                {
                                    Text(
                                        "Colonia obligatoria",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = poblacion,
                            onValueChange = { newValue ->
                                poblacion = newValue
                                if (newValue.text.isNotEmpty() || poblacionError) {
                                    validatePoblacion(newValue.text)
                                }
                            },
                            label = { Text("Población *") },
                            isError = poblacionError,
                            supportingText = if (poblacionError) {
                                {
                                    Text(
                                        "Población obligatoria",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                        OutlinedTextField(
                            value = ciudad,
                            onValueChange = { newValue ->
                                ciudad = newValue
                                if (newValue.text.isNotEmpty() || ciudadError) {
                                    validateCiudad(newValue.text)
                                }
                            },
                            label = { Text("Ciudad *") },
                            isError = ciudadError,
                            supportingText = if (ciudadError) {
                                {
                                    Text(
                                        "Ciudad obligatoria",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = guarantor,
                        onValueChange = { guarantor = it },
                        label = { Text("Aval o Responsable (Opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Información de Venta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (tipoVenta == "CREDITO") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = downpayment,
                                onValueChange = { newValue ->
                                    downpayment = newValue
                                    if (newValue.text.isNotEmpty() || downpaymentError) {
                                        validateDownpayment(newValue.text)
                                    }
                                },
                                label = { Text("Enganche") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal
                                ),
                                shape = RoundedCornerShape(15.dp),
                                prefix = { Text("$") },
                                isError = downpaymentError,
                                supportingText = if (downpaymentError) {
                                    {
                                        Text(
                                            "El enganche debe ser mayor o igual a 0",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    null
                                }
                            )

                            OutlinedTextField(
                                value = installment,
                                onValueChange = { newValue ->
                                    installment = newValue
                                    if (newValue.text.isNotEmpty() || installmentError) {
                                        validateInstallment(newValue.text)
                                    }
                                },
                                isError = installmentError,
                                supportingText = if (installmentError) {
                                    {
                                        Text(
                                            "La parcialidad debe ser mayor a 0",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    null
                                },
                                label = { Text("Parcialidad *") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal
                                ),
                                shape = RoundedCornerShape(15.dp),
                                prefix = { Text("$") }
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Información de Pago",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Box {
                            OutlinedTextField(
                                value = paymentfrequency,
                                onValueChange = { },
                                isError = paymentFrequencyError,
                                label = { Text("Frecuencia de Pago *") },
                                supportingText = if (paymentFrequencyError) {
                                    {
                                        Text(
                                            "Selecciona una frecuencia de pago",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    null
                                },
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedfrequency = true },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { expandedfrequency = true }
                                    )
                                },
                                shape = RoundedCornerShape(15.dp)
                            )
                            DropdownMenu(
                                expanded = expandedfrequency,
                                onDismissRequest = { expandedfrequency = false }
                            ) {
                                frequencyOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            paymentfrequency = option
                                            expandedfrequency = false
                                            validatePaymentFrequency(option)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Box {
                            OutlinedTextField(
                                value = collectionday,
                                onValueChange = { },
                                isError = collectionDayError,
                                supportingText = if (collectionDayError) {
                                    {
                                        Text(
                                            "Selecciona un día de cobranza",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    null
                                },
                                label = { Text("Día de Cobranza *") },
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedDia = true },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { expandedDia = true }
                                    )
                                },
                                shape = RoundedCornerShape(15.dp)
                            )
                            DropdownMenu(
                                expanded = expandedDia,
                                onDismissRequest = { expandedDia = false }
                            ) {
                                dayOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            collectionday = option
                                            expandedDia = false
                                            validateCollectionDay(option)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Notas (Opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Imágenes *",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                cameraImageUri = createImageUri(context)
                                cameraImageUri?.let { uri ->
                                    cameraLauncher.launch(uri)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tomar foto",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cámara")
                        }

                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Seleccionar imagen",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Galería")
                        }
                    }

                    if (imageError) {
                        Text(
                            "Debes tener al menos una imagen",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                        )
                    }

                    if (showImageSizeError) {
                        Text(
                            "La imagen es muy grande (máximo 20MB).",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Display existing images
                    if (displayableExistingImages.isNotEmpty() || newImageUris.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Existing images
                            items(displayableExistingImages) { imageEntity ->
                                val imageUri = try {
                                    Uri.parse("file://${imageEntity.IMAGE_URI}")
                                } catch (e: Exception) {
                                    null
                                }

                                if (imageUri != null) {
                                    Box(modifier = Modifier.size(80.dp)) {
                                        Image(
                                            painter = rememberAsyncImagePainter(imageUri),
                                            contentDescription = "Imagen existente",
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    1.dp,
                                                    Color.Gray,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    val allImages = displayableExistingImages.mapNotNull { img ->
                                                        try {
                                                            Uri.parse("file://${img.IMAGE_URI}")
                                                        } catch (
                                                            e: Exception
                                                        ) {
                                                            null
                                                        }
                                                    } + newImageUris
                                                    selectedImageIndex = allImages.indexOfFirst {
                                                        it.toString().contains(imageEntity.IMAGE_URI)
                                                    }.coerceAtLeast(0)
                                                    showImageViewer = true
                                                },
                                            contentScale = ContentScale.Crop
                                        )

                                        Text(
                                            "✕",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .clickable {
                                                    viewModel.markImageForDeletion(
                                                        imageEntity.LOCAL_SALE_IMAGE_ID
                                                    )
                                                }
                                                .background(
                                                    Color.Black.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(50)
                                                )
                                                .padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }

                            // New images
                            items(newImageUris) { uri ->
                                Box(modifier = Modifier.size(80.dp)) {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = "Nueva imagen",
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                val allImages = displayableExistingImages.mapNotNull { img ->
                                                    try {
                                                        Uri.parse("file://${img.IMAGE_URI}")
                                                    } catch (
                                                        e: Exception
                                                    ) {
                                                        null
                                                    }
                                                } + newImageUris
                                                selectedImageIndex = displayableExistingImages.size + newImageUris.indexOf(uri)
                                                showImageViewer = true
                                            },
                                        contentScale = ContentScale.Crop
                                    )

                                    Text(
                                        "✕",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .clickable {
                                                newImageUris = newImageUris.filterNot { it == uri }
                                            }
                                            .background(
                                                Color.Black.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 4.dp)
                                    )

                                    // Badge for new images
                                    Text(
                                        "NUEVA",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(2.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    ProductSaleSummary(
                        saleProductsViewModel = saleProductsViewModel,
                        productosCamioneta = productosCamioneta,
                        onOpenProductSheet = { showProductSheet = true },
                        hasError = productsError,
                        tipoVenta = tipoVenta
                    )

                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        if (validateFields()) {
                            updateSale()
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "Actualizar Venta",
                        color = Color.White
                    )
                }
            }
        }
    }
}
