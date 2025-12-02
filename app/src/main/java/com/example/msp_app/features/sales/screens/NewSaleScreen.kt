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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.draft.SaleDraft
import com.example.msp_app.core.draft.SaleDraftManager
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.sales.components.map.LocationMap
import com.example.msp_app.features.sales.components.productselector.SimpleProductSelector
import com.example.msp_app.features.sales.components.saleimagesviewer.ImageViewerDialog
import com.example.msp_app.features.sales.components.zoneselector.SimpleZoneSelector
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.features.sales.viewmodels.SaveResult
import com.example.msp_app.features.warehouses.WarehouseViewModel
import com.example.msp_app.features.zones.ZonesViewModel
import com.example.msp_app.ui.theme.ThemeController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NewSaleScreen(navController: NavController) {
    val viewModel: NewLocalSaleViewModel = viewModel()
    val warehouseViewModel: WarehouseViewModel = viewModel()
    val zonesViewModel: ZonesViewModel = viewModel()
    val authViewModel = LocalAuthViewModel.current
    val saleProductsViewModel: SaleProductsViewModel = viewModel()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Draft Manager
    val draftManager = remember { SaleDraftManager(context) }
    var showDraftDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var loadedDraft by remember { mutableStateOf<SaleDraft?>(null) }

    var defectName by remember { mutableStateOf(TextFieldValue("")) }
    var showError by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageUris by remember { mutableStateOf(listOf<Uri>()) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showImageError by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var address by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var numero by remember { mutableStateOf(TextFieldValue("")) }
    var colonia by remember { mutableStateOf(TextFieldValue("")) }
    var poblacion by remember { mutableStateOf(TextFieldValue("")) }
    var ciudad by remember { mutableStateOf(TextFieldValue("")) }
    var tipoVenta by remember { mutableStateOf("CREDITO") }

    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var downpayment by remember { mutableStateOf(TextFieldValue("")) }
    var installment by remember { mutableStateOf(TextFieldValue("")) }
    var guarantor by remember { mutableStateOf(TextFieldValue("")) }
    var note by remember { mutableStateOf(TextFieldValue("")) }
    var collectionday by remember { mutableStateOf("") }
    var paymentfrequency by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var expandedfrequency by remember { mutableStateOf(false) }
    var expandedDia by remember { mutableStateOf(false) }
    var expandedTipoVenta by remember { mutableStateOf(false) }
    var hasValidLocation by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(false) }

    var defectNameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    var installmentError by remember { mutableStateOf(false) }
    var paymentFrequencyError by remember { mutableStateOf(false) }
    var collectionDayError by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf(false) }
    var productsError by remember { mutableStateOf(false) }
    var showImageSizeError by remember { mutableStateOf(false) }
    var downpaymentError by remember { mutableStateOf(false) }

    var selectedZoneId by remember { mutableIntStateOf(0) }
    var selectedZoneName by remember { mutableStateOf("") }
    var zoneError by remember { mutableStateOf(false) }

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

    // Save draft automatically
    fun saveDraftAuto() {
        coroutineScope.launch {
            // Extract file paths from URIs (convert content:// URIs to file paths)
            val imagePaths = imageUris.mapNotNull { uri ->
                try {
                    // If it's a content URI from FileProvider, extract the actual file path
                    if (uri.scheme == "content" && uri.authority == "${context.packageName}.fileprovider") {
                        // Extract path from FileProvider URI
                        val path = uri.path?.substringAfter("draft_images/")
                        if (path != null) {
                            "${context.filesDir}/draft_images/$path"
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            val draft = SaleDraft(
                clientName = defectName.text,
                phone = phone.text,
                street = location,
                numero = numero.text,
                colonia = colonia.text,
                poblacion = poblacion.text,
                ciudad = ciudad.text,
                tipoVenta = tipoVenta,
                downpayment = downpayment.text,
                installment = installment.text,
                guarantor = guarantor.text,
                note = note.text,
                collectionDay = collectionday,
                paymentFrequency = paymentfrequency,
                latitude = latitude,
                longitude = longitude,
                imageUris = imagePaths,
                productsJson = draftManager.saleItemsToJson(saleProductsViewModel.saleItems)
            )
            draftManager.saveDraft(draft)
        }
    }

    // Load draft data into fields
    fun loadDraftData(draft: SaleDraft) {
        defectName = TextFieldValue(draft.clientName)
        phone = TextFieldValue(draft.phone)
        location = draft.street
        numero = TextFieldValue(draft.numero)
        colonia = TextFieldValue(draft.colonia)
        poblacion = TextFieldValue(draft.poblacion)
        ciudad = TextFieldValue(draft.ciudad)
        tipoVenta = draft.tipoVenta
        downpayment = TextFieldValue(draft.downpayment)
        installment = TextFieldValue(draft.installment)
        guarantor = TextFieldValue(draft.guarantor)
        note = TextFieldValue(draft.note)
        collectionday = draft.collectionDay
        paymentfrequency = draft.paymentFrequency
        latitude = draft.latitude
        longitude = draft.longitude

        // Load images
        imageUris = draft.imageUris.mapNotNull { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    uri
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        // Load products
        val draftProducts = draftManager.jsonToDraftProducts(draft.productsJson)
        draftProducts.forEach { draftProduct ->
            val product = productosCamioneta.find { it.ARTICULO_ID == draftProduct.articuloId }
            if (product != null) {
                saleProductsViewModel.addProductToSale(product, draftProduct.quantity)
            }
        }
    }

    LaunchedEffect(camionetaId) {
        if (camionetaId != null) {
            warehouseViewModel.selectWarehouse(camionetaId)
        }
    }

    // Check for draft on screen start and clean old drafts
    LaunchedEffect(Unit) {
        // Clean drafts older than 7 days
        draftManager.clearOldDrafts(maxAgeDays = 7)

        // Load draft if exists (and wasn't just cleared)
        val draft = draftManager.loadDraft()
        if (draft != null) {
            loadedDraft = draft
            showDraftDialog = true
        }
    }

    // Auto-save draft when fields change
    LaunchedEffect(
        defectName.text, phone.text, location, numero.text, colonia.text,
        poblacion.text, ciudad.text, tipoVenta, downpayment.text,
        installment.text, guarantor.text, note.text, collectionday,
        paymentfrequency, latitude, longitude, imageUris,
        saleProductsViewModel.saleItems.size
    ) {
        // Don't save if user just declined to load a draft
        if (!showDraftDialog) {
            saveDraftAuto()
        }
    }

    rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(0.0, 0.0),
            15f
        )
    }

    fun getImageSizeFromUri(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun validateZone(zoneId: Int): Boolean {
        val isValid = zoneId > 0
        zoneError = !isValid
        return isValid
    }

    fun validateLocationData(): Boolean {
        val isValid = latitude != 0.0 && longitude != 0.0 && locationPermissionGranted
        hasValidLocation = isValid
        return isValid
    }

    fun validateImageSize(context: Context, uri: Uri): Boolean {
        val maxSizeInBytes = 20 * 1000 * 1000
        val imageSize = getImageSizeFromUri(context, uri)
        return imageSize <= maxSizeInBytes
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (validateImageSize(context, it)) {
                // Copy image to persistent storage and get URI via FileProvider
                val persistentPath = draftManager.copyImageToPersistentStorage(it)
                val file = File(persistentPath)
                val persistentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                imageUris = imageUris + persistentUri
                showImageSizeError = false
            } else {
                showImageSizeError = true
            }
        }
        showImageSourceDialog = false
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { uri ->
                if (validateImageSize(context, uri)) {
                    // Copy image to persistent storage and get URI via FileProvider
                    val persistentPath = draftManager.copyImageToPersistentStorage(uri)
                    val file = File(persistentPath)
                    val persistentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    imageUris = imageUris + persistentUri
                    showImageSizeError = false
                } else {
                    showImageSizeError = true
                }
            }
        }
        showImageSourceDialog = false
    }

    fun createImageUri(context: Context): Uri {
        val imageFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    if (showImageViewer && imageUris.isNotEmpty()) {
        ImageViewerDialog(
            imageUris = imageUris,
            initialIndex = selectedImageIndex,
            onDismiss = { showImageViewer = false }
        )
    }

    fun validateClientName(name: String): Boolean {
        val isValid = name.isNotBlank() && name.length >= 3
        defectNameError = !isValid
        return isValid
    }

    fun validatePhone(phoneNumber: String): Boolean {
        if (tipoVenta == "CONTADO") {
            phoneError = false
            return true
        }
        val isValid = phoneNumber.isNotBlank() && phoneNumber.length == 10
        phoneError = !isValid
        return isValid
    }

    fun validateLocation(loc: String): Boolean {
        val isValid = loc.isNotBlank() && loc.length >= 5
        locationError = !isValid
        return isValid
    }

    fun validateInstallment(amount: String): Boolean {
        if (tipoVenta == "CONTADO") {
            installmentError = false
            return true
        }
        val amountDouble = amount.toDoubleOrNull()
        val isValid = amountDouble != null && amountDouble > 0
        installmentError = !isValid
        return isValid
    }

    fun validatePaymentFrequency(frequency: String): Boolean {
        if (tipoVenta == "CONTADO") {
            paymentFrequencyError = false
            return true
        }
        val isValid = frequency.isNotBlank()
        paymentFrequencyError = !isValid
        return isValid
    }

    fun validateCollectionDay(day: String): Boolean {
        if (tipoVenta == "CONTADO") {
            collectionDayError = false
            return true
        }
        val isValid = day.isNotBlank()
        collectionDayError = !isValid
        return isValid
    }

    fun validateImages(): Boolean {
        val isValid = imageUris.isNotEmpty()
        imageError = !isValid
        return isValid
    }

    fun validateProducts(): Boolean {
        val isValid = saleProductsViewModel.hasItems()
        productsError = !isValid
        return isValid
    }

    fun validateDownpayment(amount: String): Boolean {
        val amountDouble = amount.toDoubleOrNull()
        val isValid = amount.isBlank() || (amountDouble != null && amountDouble >= 0)
        downpaymentError = !isValid
        return isValid
    }

    fun validateFields(): Boolean {
        val clientNameValid = validateClientName(defectName.text)
        val phoneValid = validatePhone(phone.text)
        val locationValid = validateLocation(location)
        val locationDataValid = validateLocationData()
        val downpaymentValid = validateDownpayment(downpayment.text)
        val installmentValid = validateInstallment(installment.text)
        val paymentFrequencyValid = validatePaymentFrequency(paymentfrequency)
        val collectionDayValid = validateCollectionDay(collectionday)
        val imagesValid = validateImages()
        val productsValid = validateProducts()

        return clientNameValid && phoneValid && locationValid && locationDataValid &&
                installmentValid && paymentFrequencyValid && downpaymentValid && collectionDayValid &&
                imagesValid && productsValid
    }

    val saveResult by viewModel.saveResult.collectAsState()

    LaunchedEffect(saveResult) {
        when (val result = saveResult) {
            is SaveResult.Success -> {
                showSuccessDialog = true
                viewModel.clearSaveResult()
                // Clear draft after successful sale creation
                draftManager.clearDraft()
            }

            null -> { /* No hacer nada */
            }

            is SaveResult.Error -> {
                errorMessage = result.message
                showErrorDialog = true
                viewModel.clearSaveResult()
            }
        }
    }

    fun clearAllFields() {
        saleProductsViewModel.clearSale()
        defectName = TextFieldValue("")
        phone = TextFieldValue("")
        location = ""
        numero = TextFieldValue("")
        colonia = TextFieldValue("")
        poblacion = TextFieldValue("")
        ciudad = TextFieldValue("")
        tipoVenta = "CREDITO"
        downpayment = TextFieldValue("")
        installment = TextFieldValue("")
        guarantor = TextFieldValue("")
        note = TextFieldValue("")
        collectionday = ""
        paymentfrequency = ""
        latitude = 0.0
        longitude = 0.0
        imageUris = emptyList()

        defectNameError = false
        phoneError = false
        locationError = false
        downpaymentError = false
        installmentError = false
        paymentFrequencyError = false
        collectionDayError = false
        imageError = false
        productsError = false
    }

    // Draft recovery dialog
    if (showDraftDialog && loadedDraft != null) {
        val draft = loadedDraft!!
        val productCount = draftManager.jsonToDraftProducts(draft.productsJson).size

        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    text = "Venta pendiente encontrada",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Se encontró una venta sin completar:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (draft.clientName.isNotBlank()) {
                        Row {
                            Text("Cliente: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(draft.clientName, fontSize = 14.sp)
                        }
                    }

                    Row {
                        Text("Tipo: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            draft.tipoVenta,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (productCount > 0) {
                        Row {
                            Text("Productos: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("$productCount seleccionados", fontSize = 14.sp)
                        }
                    }

                    if (draft.street.isNotBlank()) {
                        Row {
                            Text("Ubicación: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                draft.street.take(30) + if (draft.street.length > 30) "..." else "",
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "¿Deseas continuar con esta venta?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        loadedDraft?.let { draft ->
                            loadDraftData(draft)
                        }
                        showDraftDialog = false
                        loadedDraft = null
                    }
                ) {
                    Text("Continuar", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDiscardConfirmDialog = true
                    }
                ) {
                    Text(
                        text = "Nueva venta",
                        color = if (ThemeController.isDarkMode) Color.White else Color.Black
                    )
                }
            }
        )
    }

    // Confirmation dialog for discarding draft
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = {
                Text(
                    text = "¿Descartar borrador?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Se eliminará toda la información guardada del borrador. Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            draftManager.clearDraft()
                        }
                        showDiscardConfirmDialog = false
                        showDraftDialog = false
                        loadedDraft = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sí, borrar", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDiscardConfirmDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    text = "¡Venta Guardada!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text("La venta se ha guardado correctamente. ¿Deseas crear una nueva venta?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        clearAllFields()
                    }
                ) {
                    Text("Crear Nueva Venta")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        navController.navigate("sales/details_list")
                    }
                ) {
                    Text("Ver Ventas")
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Text(
                    text = "Error al Guardar Venta",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column {
                    Text("No se pudo guardar la venta:")
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
                    onClick = {
                        showErrorDialog = false
                    }
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    fun saveSale() {
        val saleId = UUID.randomUUID().toString()
        val saleDate = java.time.Instant.now().toString()

        val userEmail = when (val userState = userData) {
            is ResultState.Success -> userState.data?.EMAIL ?: ""
            else -> ""
        }

        viewModel.createSaleWithImages(
            saleId = saleId,
            clientName = defectName.text,
            saleDate = saleDate,
            imageUris = imageUris,
            latitude = latitude,
            longitude = longitude,
            address = location,
            numero = numero.text.ifBlank { null },
            colonia = colonia.text.ifBlank { null },
            poblacion = poblacion.text.ifBlank { null },
            ciudad = ciudad.text.ifBlank { null },
            tipoVenta = tipoVenta,
            installment = if (tipoVenta == "CONTADO") 0.0 else installment.text.toDoubleOrNull()
                ?: 0.0,
            downpayment = if (tipoVenta == "CONTADO") 0.0 else downpayment.text.toDoubleOrNull()
                ?: 0.0,
            phone = phone.text.ifBlank { "" },
            paymentfrequency = if (tipoVenta == "CONTADO") "" else paymentfrequency,
            avaloresponsable = if (tipoVenta == "CONTADO") "" else guarantor.text,
            note = note.text,
            collectionday = if (tipoVenta == "CONTADO") "" else collectionday,
            totalprice = saleProductsViewModel.getTotalPrecioLista(),
            shorttermtime = 0,
            shorttermamount = saleProductsViewModel.getTotalMontoCortoplazo(),
            cashamount = saleProductsViewModel.getTotalMontoContado(),
            enviado = false,
            saleProducts = saleProductsViewModel.saleItems,
            context = context,
            userEmail = userEmail
        )
    }

    if (showImageViewer && imageUris.isNotEmpty()) {
        ImageViewerDialog(
            imageUris = imageUris,
            initialIndex = selectedImageIndex,
            onDismiss = { showImageViewer = false }
        )
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = openDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menú"
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Nueva Venta",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            16.dp
                        )
                ) {
                    if (showError) {
                        Text(
                            "Todos los campos con * son obligatorios",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,

                            )
                    }
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
                                        expandedTipoVenta = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

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
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth(),
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
                                    "El teléfono debe tener al menos 10 dígitos",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    LocationMap(
                        onAddressChange = { address = it },
                        onLocationChange = { loc ->
                            latitude = loc.latitude
                            longitude = loc.longitude
                            locationPermissionGranted = true
                            validateLocationData()
                        }
                    )
                    if (!locationPermissionGranted || !hasValidLocation) {
                        Text(
                            text = "* Se requieren permisos de ubicación para generar la venta",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = location,
                        onValueChange = { newValue ->
                            location = newValue
                            if (newValue.isNotEmpty() || locationError) {
                                validateLocation(newValue)
                            }
                        },
                        label = { Text("Calle *") },
                        modifier = Modifier
                            .fillMaxWidth(),
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
                        } else null,
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
                        OutlinedTextField(
                            value = colonia,
                            onValueChange = { colonia = it },
                            label = { Text("Colonia") },
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
                            onValueChange = { poblacion = it },
                            label = { Text("Población") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                        OutlinedTextField(
                            value = ciudad,
                            onValueChange = { ciudad = it },
                            label = { Text("Ciudad") },
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                                } else null,
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
                                } else null,
                                label = { Text("Parcialidad *") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                                } else null,
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
                                } else null,
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

                        Spacer(Modifier.height(16.dp))

                        SimpleZoneSelector(
                            zonesViewModel = zonesViewModel,
                            selectedZoneId = selectedZoneId.takeIf { it > 0 },
                            onZoneSelected = { zoneId, zoneName ->
                                selectedZoneId = zoneId
                                selectedZoneName = zoneName
                                if (zoneError) {
                                    validateZone(zoneId)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Zona de Cliente",
                            isRequired = true,
                            isError = zoneError,
                            errorMessage = if (zoneError) "Selecciona una zona" else null
                        )

                        Spacer(Modifier.height(16.dp))
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
                        "Agregar imágenes *",
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
                            onClick = {
                                imagePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Seleccionar imagen",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Galería"
                            )
                        }
                    }
                    if (showImageError) {
                        Text(
                            "Debes agregar al menos una imagen",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                        )
                    }
                    if (showImageSizeError) {
                        Text(
                            "La imagen es muy grande (máximo 20MB). Por favor, toma otra foto o selecciona una imagen más pequeña.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(imageUris.size) { index ->
                            val uri = imageUris[index]
                            showImageError = false
                            Box(modifier = Modifier.size(80.dp)) {
                                Image(
                                    painter = rememberAsyncImagePainter(uri),
                                    contentDescription = "Imagen",
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.Gray, shape = RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedImageIndex = index
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
                                            imageUris = imageUris.filterNot { it == uri }
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

                    SimpleProductSelector(
                        warehouseViewModel = warehouseViewModel,
                        saleProductsViewModel = saleProductsViewModel,
                        onAddProduct = { articuloId, cantidad ->
                            val producto = productosCamioneta.find { it.ARTICULO_ID == articuloId }
                            if (producto != null) {
                                saleProductsViewModel.addProductToSale(producto, cantidad)
                            }
                        }
                    )

                    Modifier.height(12.dp)
                }

                Button(
                    onClick = {
                        val isvalid = validateFields()
                        showImageError = imageUris.isEmpty()

                        if (isvalid) {
                            saveSale()
                        }

                    },
                    enabled = hasValidLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Generar Venta",
                        color = Color.White
                    )
                }

            }
        }
    }
}