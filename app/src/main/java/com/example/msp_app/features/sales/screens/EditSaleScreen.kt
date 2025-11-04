package com.example.msp_app.features.sales.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.msp_app.features.sales.components.productselector.SimpleProductSelector
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.features.sales.viewmodels.SaveResult
import com.example.msp_app.features.warehouses.WarehouseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSaleScreen(saleId: String, navController: NavController) {
    val viewModel: NewLocalSaleViewModel = viewModel()
    val warehouseViewModel: WarehouseViewModel = viewModel()
    val saleProductsViewModel: SaleProductsViewModel = viewModel()

    val sale by viewModel.selectedSale.collectAsState()
    val saleProducts by viewModel.saleProducts.collectAsState()
    val existingImages by viewModel.saleImages.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    var defectName by remember { mutableStateOf(TextFieldValue("")) }
    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var location by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf(TextFieldValue("")) }
    var colonia by remember { mutableStateOf(TextFieldValue("")) }
    var poblacion by remember { mutableStateOf(TextFieldValue("")) }
    var ciudad by remember { mutableStateOf(TextFieldValue("")) }
    var downpayment by remember { mutableStateOf(TextFieldValue("")) }
    var installment by remember { mutableStateOf(TextFieldValue("")) }
    var guarantor by remember { mutableStateOf(TextFieldValue("")) }
    var note by remember { mutableStateOf(TextFieldValue("")) }
    var collectionday by remember { mutableStateOf("") }
    var paymentfrequency by remember { mutableStateOf("") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var newImageUris by remember { mutableStateOf(listOf<Uri>()) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(saleId) {
        viewModel.getSaleById(saleId)
        viewModel.loadImagesBySaleId(saleId)
        viewModel.loadProductsBySaleId(saleId)
    }

    LaunchedEffect(sale) {
        sale?.let {
            defectName = TextFieldValue(it.NOMBRE_CLIENTE)
            phone = TextFieldValue(it.TELEFONO)
            location = it.DIRECCION
            numero = TextFieldValue(it.NUMERO ?: "")
            colonia = TextFieldValue(it.COLONIA ?: "")
            poblacion = TextFieldValue(it.POBLACION ?: "")
            ciudad = TextFieldValue(it.CIUDAD ?: "")
            downpayment = TextFieldValue(it.ENGANCHE?.toString() ?: "")
            installment = TextFieldValue(it.PARCIALIDAD.toString())
            guarantor = TextFieldValue(it.AVAL_O_RESPONSABLE ?: "")
            note = TextFieldValue(it.NOTA ?: "")
            collectionday = it.DIA_COBRANZA
            paymentfrequency = it.FREC_PAGO
            latitude = it.LATITUD
            longitude = it.LONGITUD
        }
    }

    LaunchedEffect(saleProducts) {
        if (saleProducts.isNotEmpty()) {
            saleProductsViewModel.clearSale()
            saleProducts.forEach { productEntity ->
                val productInventory =
                    com.example.msp_app.data.models.productInventory.ProductInventory(
                        ARTICULO_ID = productEntity.ARTICULO_ID,
                        ARTICULO = productEntity.ARTICULO,
                        PRECIOS = "${productEntity.PRECIO_LISTA},${productEntity.PRECIO_CORTO_PLAZO},${productEntity.PRECIO_CONTADO}",
                        EXISTENCIAS = 0,
                        LINEA_ARTICULO_ID = 0,
                        LINEA_ARTICULO = "",
                    )
                saleProductsViewModel.addProductToSale(productInventory, productEntity.CANTIDAD)
            }
        }
    }

    LaunchedEffect(saveResult) {
        when (saveResult) {
            is SaveResult.Success -> {
                showSuccessDialog = true
                viewModel.clearSaveResult()
            }

            is SaveResult.Error -> {
                viewModel.clearSaveResult()
            }

            null -> {}
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { newImageUris = newImageUris + it }
    }

    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        // Handle camera
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Venta Actualizada") },
            text = { Text("La venta se actualizó correctamente") },
            confirmButton = {
                TextButton(onClick = {
                    showSuccessDialog = false
                    navController.popBackStack()
                }) {
                    Text("Aceptar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "Volver")
                }
                Spacer(Modifier.width(12.dp))
                Text("Editar Venta", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Reutilizar campos de NewSaleScreen aquí
                OutlinedTextField(
                    value = defectName,
                    onValueChange = { defectName = it },
                    label = { Text("Nombre del cliente *") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono") },
                    modifier = Modifier.fillMaxWidth()
                )

                // ... resto de campos similares a NewSaleScreen ...

                // Selector de productos
                SimpleProductSelector(
                    warehouseViewModel = warehouseViewModel,
                    saleProductsViewModel = saleProductsViewModel,
                    onAddProduct = { articuloId, cantidad ->

                    }
                )

                // Botón para agregar imágenes
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Agregar Imagen")
                }

                // Mostrar imágenes existentes y nuevas
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(existingImages.size) { index ->
                        Box(modifier = Modifier.size(80.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(existingImages[index].IMAGE_URI),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    items(newImageUris.size) { index ->
                        Box(modifier = Modifier.size(80.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(newImageUris[index]),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.updateSaleWithImages(
                        saleId = saleId,
                        clientName = defectName.text,
                        imageUris = existingImages.map { Uri.parse(it.IMAGE_URI) },
                        newImageUris = newImageUris,
                        latitude = latitude,
                        longitude = longitude,
                        address = location,
                        numero = numero.text,
                        colonia = colonia.text,
                        poblacion = poblacion.text,
                        ciudad = ciudad.text,
                        installment = installment.text.toDoubleOrNull() ?: 0.0,
                        downpayment = downpayment.text.toDoubleOrNull() ?: 0.0,
                        phone = phone.text,
                        paymentfrequency = paymentfrequency,
                        avaloresponsable = guarantor.text,
                        note = note.text,
                        collectionday = collectionday,
                        totalprice = saleProductsViewModel.getTotalPrecioLista(),
                        shorttermamount = saleProductsViewModel.getTotalMontoCortoplazo(),
                        cashamount = saleProductsViewModel.getTotalMontoContado(),
                        saleProducts = saleProductsViewModel.saleItems,
                        context = context
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Actualizar Venta")
            }
        }
    }
}