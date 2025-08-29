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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.cart.viewmodels.CartViewModel
import com.example.msp_app.features.sales.components.map.LocationMap
import com.example.msp_app.features.sales.components.productselector.ProductSelector
import com.example.msp_app.features.sales.components.saleimagesviewer.ImageViewerDialog
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.features.sales.viewmodels.SaveResult
import com.example.msp_app.features.warehouses.WarehouseViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import java.io.File
import java.util.UUID


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NewSaleScreen(navController: NavController) {
    val viewModel: NewLocalSaleViewModel = viewModel()
    val warehouseViewModel: WarehouseViewModel = viewModel()
    val cartViewModel: CartViewModel = viewModel()
    val authViewModel = LocalAuthViewModel.current
    val saleProductsViewModel: SaleProductsViewModel = viewModel()

    var defectName by remember { mutableStateOf(TextFieldValue("")) }
    var showError by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageUris by remember { mutableStateOf(listOf<Uri>()) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showImageError by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var address by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }

    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var downpayment by remember { mutableStateOf(TextFieldValue("")) }
    var installment by remember { mutableStateOf(TextFieldValue("")) }
    var guarantor by remember { mutableStateOf(TextFieldValue("")) }
    var note by remember { mutableStateOf(TextFieldValue("")) }
    var collectionday by remember { mutableStateOf("") }
    var paymentfrequency by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var expandedfrequency by remember { mutableStateOf(false) }
    var expandedDia by remember { mutableStateOf(false) }
    cartViewModel.cartProducts

    val frequencyOptions = listOf("Semanal", "Quincenal", "Mensual")
    val dayOptions =
        listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    remember { mutableStateListOf<Pair<Int, Int>>() }

    val userData by authViewModel.userData.collectAsState()
    val warehouseState by warehouseViewModel.warehouseProducts.collectAsState()
    cartViewModel.cartProducts

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

    rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(0.0, 0.0),
            15f
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUris = imageUris + it }
        showImageSourceDialog = false
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraImageUri != null) {
            imageUris = imageUris + cameraImageUri!!
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

    fun validateFields(): Boolean {
        return defectName.text.isNotBlank() &&
                phone.text.isNotBlank() &&
                installment.text.toDoubleOrNull()?.let { it > 0 } == true &&
                paymentfrequency.isNotBlank() &&
                collectionday.isNotBlank() &&
                imageUris.isNotEmpty() &&
                saleProductsViewModel.hasItems()
    }

    val saveResult by viewModel.saveResult.collectAsState()

    LaunchedEffect(saveResult) {
        when (saveResult) {
            is SaveResult.Success -> {
                showSuccessDialog = true
            }

            null -> { /* No hacer nada */
            }

            is SaveResult.Error -> {}
        }
    }

    fun clearAllFields() {
        saleProductsViewModel.clearSale()
        defectName = TextFieldValue("")
        imageUris = emptyList()
        phone = TextFieldValue("")
        downpayment = TextFieldValue("")
        installment = TextFieldValue("")
        guarantor = TextFieldValue("")
        note = TextFieldValue("")
        location = ""
        collectionday = ""
        paymentfrequency = ""
        location = ""
        latitude = 0.0
        longitude = 0.0
        showError = false
        showImageError = false
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

    fun saveSale() {
        val saleId = UUID.randomUUID().toString()
        val saleDate = java.time.Instant.now().toString()

        viewModel.createSaleWithImages(
            saleId = saleId,
            clientName = defectName.text,
            saleDate = saleDate,
            imageUris = imageUris,
            latitude = latitude,
            longitude = longitude,
            address = location,
            installment = installment.text.toDoubleOrNull() ?: 0.0,
            downpayment = downpayment.text.toDoubleOrNull() ?: 0.0,
            phone = phone.text,
            paymentfrequency = paymentfrequency,
            avaloresponsable = guarantor.text,
            note = note.text,
            collectionday = collectionday,
            enviado = false,
            saleProducts = saleProductsViewModel.saleItems,
            context = context
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
                        "Información del Cliente",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = defectName,
                        onValueChange = {
                            defectName = it
                        },
                        label = { Text("Nombre completo del cliente *") },
                        isError = showError,
                        modifier = Modifier
                            .fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                        },
                        label = { Text("Teléfono *") },
                        isError = showError,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Direccion *") },
                        modifier = Modifier
                            .fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        shape = RoundedCornerShape(15.dp)

                    )

                    Spacer(Modifier.height(16.dp))

                    LocationMap(
                        onAddressChange = { address = it },
                        onLocationChange = { loc ->
                            latitude = loc.latitude
                            longitude = loc.longitude
                        }
                    )

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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = downpayment,
                            onValueChange = { downpayment = it },
                            label = { Text("Enganche") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(15.dp),
                            prefix = { Text("$") }
                        )

                        OutlinedTextField(
                            value = installment,
                            onValueChange = { installment = it },
                            isError = showError,
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
                            isError = showError,
                            label = { Text("Frecuencia de Pago *") },
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
                            isError = showError,
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
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

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

                    ProductSelector(
                        warehouseViewModel = warehouseViewModel,
                        saleProductsViewModel = saleProductsViewModel,
                        onAddProduct = { articuloId, cantidad ->
                            val producto = productosCamioneta.find { it.ARTICULO_ID == articuloId }
                            if (producto != null) {
                                saleProductsViewModel.addProductToSale(producto, cantidad)
                            }
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    val saleItems = saleProductsViewModel.saleItems

                    if (saleItems.isNotEmpty()) {
                        Text(
                            "Productos seleccionados:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        saleItems.forEach { saleItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${saleItem.product.ARTICULO} x${saleItem.quantity}")
                                Row {
                                    IconButton(
                                        onClick = {
                                            val newQuantity = saleItem.quantity - 1
                                            saleProductsViewModel.updateQuantity(
                                                saleItem.product,
                                                newQuantity
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Disminuir"
                                        )
                                    }
                                    Text(
                                        text = saleItem.quantity.toString(),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            val cantidadEnVenta =
                                                saleProductsViewModel.getQuantityForProduct(saleItem.product)
                                            val disponible =
                                                saleItem.product.EXISTENCIAS - cantidadEnVenta + saleItem.quantity

                                            if (saleItem.quantity < disponible) {
                                                val newQuantity = saleItem.quantity + 1
                                                saleProductsViewModel.updateQuantity(
                                                    saleItem.product,
                                                    newQuantity
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Aumentar"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            saleProductsViewModel.removeProductFromSale(saleItem.product)
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                                    }
                                }
                            }
                        }
                    }

                    Modifier.height(12.dp)
                }

                Button(
                    onClick = {
                        val valid = validateFields()
                        showError = !valid
                        showImageError = imageUris.isEmpty()

                        if (valid) {
                            saveSale()
                        }

                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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