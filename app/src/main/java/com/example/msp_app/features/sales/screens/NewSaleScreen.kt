package com.example.msp_app.features.sales.screens

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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.draft.SaleDraft
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.entities.LocalSaleComboEntity
import com.example.msp_app.features.sales.components.combo.CreateComboDialog
import com.example.msp_app.features.sales.components.confirmation.SaleConfirmationData
import com.example.msp_app.features.sales.components.confirmation.SaleConfirmationDialog
import com.example.msp_app.features.sales.components.map.LocationMap
import com.example.msp_app.features.sales.components.productselector.ProductSaleSummary
import com.example.msp_app.features.sales.components.productselector.ProductSelectionBottomSheet
import com.example.msp_app.features.sales.components.saleimagesviewer.ImageViewerDialog
import com.example.msp_app.features.sales.components.zoneselector.ZoneSelectorSimple
import com.example.msp_app.features.sales.viewmodels.ClienteSearchViewModel
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.example.msp_app.features.sales.viewmodels.NewSaleFormViewModel
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.features.sales.viewmodels.SaveResult
import com.example.msp_app.features.warehouses.WarehouseViewModel
import com.example.msp_app.ui.theme.ThemeController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NewSaleScreen(navController: NavController) {
    val viewModel: NewLocalSaleViewModel = viewModel()
    val warehouseViewModel: WarehouseViewModel = viewModel()
    val authViewModel = LocalAuthViewModel.current
    val saleProductsViewModel: SaleProductsViewModel = viewModel()
    val clienteSearchViewModel: ClienteSearchViewModel = viewModel()
    val formViewModel: NewSaleFormViewModel = viewModel()
    val context = LocalContext.current

    val formState by formViewModel.formState.collectAsState()

    // UI-only state
    var showDraftDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var loadedDraft by remember { mutableStateOf<SaleDraft?>(null) }
    var showError by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageError by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var showImageSizeError by remember { mutableStateOf(false) }
    var showCreateComboDialog by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var expandedfrequency by remember { mutableStateOf(false) }
    var expandedDia by remember { mutableStateOf(false) }
    var expandedTipoVenta by remember { mutableStateOf(false) }
    var showProductSheet by remember { mutableStateOf(false) }
    var showClienteSearch by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

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

    LaunchedEffect(camionetaId) {
        if (camionetaId != null) {
            warehouseViewModel.selectWarehouse(camionetaId)
        }
    }

    // Check for draft on screen start and clean old drafts
    LaunchedEffect(Unit) {
        formViewModel.clearOldDrafts()
        val draft = formViewModel.loadDraftSuspend()
        if (draft != null) {
            loadedDraft = draft
            showDraftDialog = true
        }
    }

    // Auto-save draft when fields change
    LaunchedEffect(
        formState.clientName, formState.phone, formState.street, formState.numero,
        formState.colonia, formState.poblacion, formState.ciudad, formState.tipoVenta,
        formState.downpayment, formState.installment, formState.guarantor, formState.note,
        formState.collectionDay, formState.paymentFrequency, formState.latitude,
        formState.longitude, formState.imageUris,
        saleProductsViewModel.saleItems.size, formState.selectedZoneId,
        formState.selectedZoneName, saleProductsViewModel.combos.size
    ) {
        if (!showDraftDialog && !formState.saleCompleted) {
            formViewModel.saveDraftAuto(
                saleItems = saleProductsViewModel.saleItems,
                comboItems = saleProductsViewModel.getCombosList()
            )
        }
    }

    rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(0.0, 0.0),
            15f
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val hasOversized = formViewModel.processPickedImages(uris)
        showImageSizeError = hasOversized
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { uri ->
                showImageSizeError = formViewModel.processCameraImage(uri)
            }
        }
    }

    val saveResult by viewModel.saveResult.collectAsState()
    val isSaving by viewModel.isLoading.collectAsState()
    val isCreatingCombo by saleProductsViewModel.isCreatingCombo.collectAsState()

    LaunchedEffect(saveResult) {
        when (val result = saveResult) {
            is SaveResult.Success -> {
                formViewModel.markSaleCompleted()
                showSuccessDialog = true
                viewModel.clearSaveResult()
                formViewModel.clearDraft()
            }
            null -> {}
            is SaveResult.Error -> {
                errorMessage = result.message
                showErrorDialog = true
                viewModel.clearSaveResult()
            }
        }
    }

    fun saveSale() {
        val data = formViewModel.buildSaleData()

        val userEmail = when (val userState = userData) {
            is ResultState.Success -> userState.data?.EMAIL ?: ""
            else -> ""
        }

        val comboEntities = saleProductsViewModel.getCombosList().map { combo ->
            LocalSaleComboEntity(
                COMBO_ID = combo.comboId,
                LOCAL_SALE_ID = data.saleId,
                NOMBRE_COMBO = combo.nombreCombo,
                PRECIO_LISTA = combo.precioLista,
                PRECIO_CORTO_PLAZO = combo.precioCortoPlazo,
                PRECIO_CONTADO = combo.precioContado
            )
        }

        viewModel.createSaleWithImages(
            saleId = data.saleId,
            clientName = data.clientName,
            saleDate = data.saleDate,
            imageUris = data.imageUris,
            latitude = data.latitude,
            longitude = data.longitude,
            address = data.address,
            numero = data.numero,
            colonia = data.colonia,
            poblacion = data.poblacion,
            ciudad = data.ciudad,
            tipoVenta = data.tipoVenta,
            installment = data.installment,
            downpayment = data.downpayment,
            phone = data.phone,
            paymentfrequency = data.paymentFrequency,
            avaloresponsable = data.guarantor,
            note = data.note,
            collectionday = data.collectionDay,
            totalprice = saleProductsViewModel.getTotalPrecioListaWithCombos(),
            shorttermtime = 0,
            shorttermamount = saleProductsViewModel.getTotalMontoCortoPlazoWithCombos(),
            cashamount = saleProductsViewModel.getTotalMontoContadoWithCombos(),
            enviado = false,
            saleProducts = saleProductsViewModel.saleItems,
            context = context,
            userEmail = userEmail,
            zonaClienteId = data.zonaClienteId,
            zonaClienteNombre = data.zonaClienteNombre,
            combos = comboEntities,
            clienteId = data.clienteId
        )
    }

    // Image viewer dialog
    if (showImageViewer && formState.imageUris.isNotEmpty()) {
        ImageViewerDialog(
            imageUris = formState.imageUris,
            initialIndex = selectedImageIndex,
            onDismiss = { showImageViewer = false }
        )
    }

    // Draft recovery dialog
    if (showDraftDialog && loadedDraft != null) {
        val draft = loadedDraft!!
        val productCount = formViewModel.draftManager.jsonToDraftProducts(draft.productsJson).size

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
                        loadedDraft?.let { d ->
                            saleProductsViewModel.clearSale()
                            formViewModel.applyDraft(d)

                            // Restore products and combos
                            val draftProducts = formViewModel.draftManager.jsonToDraftProducts(
                                d.productsJson
                            )
                            val draftCombos = formViewModel.draftManager.jsonToCombos(d.combosJson)

                            draftProducts.forEach { draftProduct ->
                                val product = productosCamioneta.find { it.ARTICULO_ID == draftProduct.articuloId }
                                if (product != null) {
                                    saleProductsViewModel.addProductToSale(
                                        product,
                                        draftProduct.quantity
                                    )
                                }
                            }

                            draftCombos.forEach { draftCombo ->
                                val comboProductIds = draftProducts
                                    .filter { it.comboId == draftCombo.comboId }
                                    .map { it.articuloId }

                                if (comboProductIds.isNotEmpty()) {
                                    comboProductIds.forEach { articleId ->
                                        saleProductsViewModel.toggleProductSelection(articleId)
                                    }
                                    saleProductsViewModel.createComboWithId(
                                        comboId = draftCombo.comboId,
                                        nombreCombo = draftCombo.nombreCombo,
                                        precioLista = draftCombo.precioLista,
                                        precioCortoPlazo = draftCombo.precioCortoPlazo,
                                        precioContado = draftCombo.precioContado
                                    )
                                }
                            }
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
                        formViewModel.clearDraft()
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
                        formViewModel.clearAllFields()
                        saleProductsViewModel.clearAll()
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

    // Dialog para crear combo
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
        tipoVenta = formState.tipoVenta
    )

    // Dialog de confirmación de venta
    SaleConfirmationDialog(
        show = showConfirmationDialog,
        onDismiss = { showConfirmationDialog = false },
        onConfirm = {
            showConfirmationDialog = false
            saveSale()
        },
        data = SaleConfirmationData(
            clientName = formState.clientName,
            phone = formState.phone,
            street = formState.street,
            numero = formState.numero,
            colonia = formState.colonia,
            poblacion = formState.poblacion,
            ciudad = formState.ciudad,
            tipoVenta = formState.tipoVenta,
            zoneName = formState.selectedZoneName.ifBlank { null },
            downpayment = formState.downpayment,
            installment = formState.installment,
            guarantor = formState.guarantor,
            paymentFrequency = formState.paymentFrequency,
            collectionDay = formState.collectionDay,
            note = formState.note,
            imageCount = formState.imageUris.size,
            individualProducts = saleProductsViewModel.getIndividualProducts(),
            combos = saleProductsViewModel.getCombosList(),
            getProductsInCombo = { comboId -> saleProductsViewModel.getProductsInCombo(comboId) },
            totalPrecioLista = saleProductsViewModel.getTotalPrecioListaWithCombos(),
            totalCortoPlazo = saleProductsViewModel.getTotalMontoCortoPlazoWithCombos(),
            totalContado = saleProductsViewModel.getTotalMontoContadoWithCombos()
        )
    )

    // Bottom sheet de selección de productos
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

    // Bottom sheet de búsqueda de clientes
    if (showClienteSearch) {
        ClienteSearchBottomSheet(
            viewModel = clienteSearchViewModel,
            onDismiss = { showClienteSearch = false },
            onClienteSelected = { clienteId, nombre ->
                formViewModel.updateSelectedClienteId(clienteId)
                formViewModel.updateClientName(nombre)
                showClienteSearch = false
            },
            onNewCliente = { nombre ->
                formViewModel.updateSelectedClienteId(null)
                formViewModel.updateClientName(nombre)
                showClienteSearch = false
            }
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
                    .fillMaxSize()
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
                            style = MaterialTheme.typography.bodyLarge

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
                            value = formState.tipoVenta,
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
                                        formViewModel.updateTipoVenta(option)
                                        saleProductsViewModel.setTipoVenta(option)
                                        expandedTipoVenta = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Zone Selector - Solo mostrar para ventas a CREDITO
                    if (formState.tipoVenta == "CREDITO") {
                        ZoneSelectorSimple(
                            selectedZoneId = formState.selectedZoneId,
                            selectedZoneName = formState.selectedZoneName,
                            onZoneSelected = { zoneId, zoneName ->
                                formViewModel.updateZone(zoneId, zoneName)
                            },
                            error = if (formState.errors.zone) "Selecciona una zona" else null,
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

                    Box {
                        OutlinedTextField(
                            value = formState.clientName,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Nombre completo del cliente *") },
                            isError = formState.errors.clientName,
                            supportingText = if (formState.errors.clientName) {
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
                            shape = RoundedCornerShape(15.dp),
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar cliente"
                                )
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showClienteSearch = true }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = formState.phone,
                        onValueChange = { formViewModel.updatePhone(it) },
                        label = {
                            Text(
                                if (formState.tipoVenta == "CONTADO") "Teléfono" else "Teléfono *"
                            )
                        },
                        isError = formState.errors.phone,
                        supportingText = if (formState.errors.phone) {
                            {
                                Text(
                                    "El teléfono debe tener al menos 10 dígitos",
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

                    LocationMap(
                        onAddressChange = { /* address display handled externally */ },
                        onLocationChange = { loc ->
                            formViewModel.updateLocation(loc.latitude, loc.longitude)
                        }
                    )
                    if (!formState.locationPermissionGranted || !formState.hasValidLocation) {
                        Text(
                            text = "* Se requieren permisos de ubicación para generar la venta",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = formState.street,
                        onValueChange = { formViewModel.updateStreet(it) },
                        label = { Text("Calle *") },
                        modifier = Modifier
                            .fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        shape = RoundedCornerShape(15.dp),
                        isError = formState.errors.location,
                        supportingText = if (formState.errors.location) {
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
                            value = formState.numero,
                            onValueChange = { formViewModel.updateNumero(it) },
                            label = { Text("Número") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                        OutlinedTextField(
                            value = formState.colonia,
                            onValueChange = { formViewModel.updateColonia(it) },
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
                            value = formState.poblacion,
                            onValueChange = { formViewModel.updatePoblacion(it) },
                            label = { Text("Población") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                        OutlinedTextField(
                            value = formState.ciudad,
                            onValueChange = { formViewModel.updateCiudad(it) },
                            label = { Text("Ciudad") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = formState.guarantor,
                        onValueChange = { formViewModel.updateGuarantor(it) },
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

                    if (formState.tipoVenta == "CREDITO") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = formState.downpayment,
                                onValueChange = { formViewModel.updateDownpayment(it) },
                                label = { Text("Enganche") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal
                                ),
                                shape = RoundedCornerShape(15.dp),
                                prefix = { Text("$") },
                                isError = formState.errors.downpayment,
                                supportingText = if (formState.errors.downpayment) {
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
                                value = formState.installment,
                                onValueChange = { formViewModel.updateInstallment(it) },
                                isError = formState.errors.installment,
                                supportingText = if (formState.errors.installment) {
                                    {
                                        Text(
                                            "La parcialidad debe ser un número entero mayor a 0",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    null
                                },
                                label = { Text("Parcialidad *") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
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
                                value = formState.paymentFrequency,
                                onValueChange = { },
                                isError = formState.errors.paymentFrequency,
                                label = { Text("Frecuencia de Pago *") },
                                supportingText = if (formState.errors.paymentFrequency) {
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
                                            formViewModel.updatePaymentFrequency(option)
                                            expandedfrequency = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Box {
                            OutlinedTextField(
                                value = formState.collectionDay,
                                onValueChange = { },
                                isError = formState.errors.collectionDay,
                                supportingText = if (formState.errors.collectionDay) {
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
                                            formViewModel.updateCollectionDay(option)
                                            expandedDia = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = formState.note,
                        onValueChange = { formViewModel.updateNote(it) },
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
                                cameraImageUri = formViewModel.createCameraUri()
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
                        items(formState.imageUris.size) { index ->
                            val uri = formState.imageUris[index]
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
                                            formViewModel.removeImageUri(uri)
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

                    ProductSaleSummary(
                        saleProductsViewModel = saleProductsViewModel,
                        productosCamioneta = productosCamioneta,
                        onOpenProductSheet = { showProductSheet = true },
                        hasError = formState.errors.products,
                        tipoVenta = formState.tipoVenta
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        val isValid = formViewModel.validateFields(saleProductsViewModel.hasItems())
                        showImageError = formState.imageUris.isEmpty()

                        if (isValid) {
                            showConfirmationDialog = true
                        }
                    },
                    enabled = formState.hasValidLocation && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Guardando...",
                            color = Color.White
                        )
                    } else {
                        Text(
                            "Generar Venta",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
