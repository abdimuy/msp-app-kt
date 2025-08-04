package com.example.msp_app.features.guarantees.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.msp_app.components.selectbluetoothdevice.SelectBluetoothDevice
import com.example.msp_app.core.utils.Constants.NOTIFICADO
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.guarantees.screens.viewmodels.GuaranteesViewModel
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuaranteeScreen(saleId: Int, navController: NavController) {
    val guaranteesViewModel: GuaranteesViewModel = viewModel()
    val saleViewModel: SaleDetailsViewModel = viewModel()
    var defectDescription by remember { mutableStateOf(TextFieldValue("")) }
    var observations by remember { mutableStateOf(TextFieldValue("")) }
    var imageUris by remember { mutableStateOf(listOf<Uri>()) }
    var showError by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var showImageError by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val guaranteeState = remember { mutableStateOf<GuaranteeEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var ticket by remember { mutableStateOf<String?>(null) }
    val saleResult by saleViewModel.saleState.collectAsState()

    val sale = when (saleResult) {
        is ResultState.Success -> (saleResult as ResultState.Success<Sale?>).data
        is ResultState.Loading -> null
        is ResultState.Error -> null
        else -> null
    }

    LaunchedEffect(Unit) {
        try {
            guaranteeState.value = guaranteesViewModel.getGuaranteeSaleById(saleId)
            saleViewModel.loadSaleDetails(saleId)
        } catch (e: Exception) {
            println("Error loading guarantee or sale: ${e.message}")
        }
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

    val currentGuarantee = guaranteeState.value
    if (currentGuarantee?.ESTADO == NOTIFICADO) {
        val date = try {
            DateUtils.formatIsoDate(
                iso = currentGuarantee.FECHA_SOLICITUD,
                pattern = "dd/MM/yyyy",
                locale = Locale("es", "MX")
            )
        } catch (e: Exception) {
            "N/A"
        }
        val lineBlanck = (" ").repeat(32)

        ticket = buildString {
            appendLine(lineBlanck)
            appendLine(ThermalPrinting.centerText("AVISO DE PROCESO DE GARANTIA", 32))
            appendLine(lineBlanck)

            appendLine("Estimado cliente:")
            appendLine(lineBlanck)

            appendLine("Le informamos que su producto")
            appendLine("ha sido recibido para revision")
            appendLine("bajo proceso de garantia.")
            appendLine("El tiempo estimado para este")
            appendLine("tramite es de 1 a 2 meses,")
            appendLine("dependiendo del diagnostico y")
            appendLine("tiempos de respuesta del")
            appendLine("proveedor o fabricante.")
            appendLine(lineBlanck)

            appendLine("Al firmar este documento,")
            appendLine("usted autoriza la recoleccion")
            appendLine("del articulo dañado y")
            appendLine("reconoce que:")
            appendLine(lineBlanck)

            appendLine("- El producto sera evaluado")
            appendLine("  para determinar si aplica")
            appendLine("  o no la garantia.")
            appendLine(lineBlanck)

            appendLine("En caso de no proceder, se le")
            appendLine("notificara para que decida si")
            appendLine("desea una reparacion con")
            appendLine("costo adicional o la")
            appendLine("devolucion del articulo.")
            appendLine(lineBlanck)

            appendLine("El articulo permanecera bajo")
            appendLine("resguardo hasta la resolucion")
            appendLine("del caso.")
            appendLine(lineBlanck)

            appendLine("Agradecemos su comprension.")
            appendLine("Para dudas o seguimiento,")
            appendLine("quedamos a su disposicion.")
            appendLine(lineBlanck)

            appendLine("Atentamente")
            appendLine("Muebles San Pablo")
            appendLine("Departamento de Garantias")
            appendLine(lineBlanck)

            appendLine("Nombre del cliente:")
            appendLine("${sale?.CLIENTE ?: "N/A"}")
            appendLine(lineBlanck)

            appendLine(ThermalPrinting.centerText("Firma del cliente:", 32))
            appendLine(lineBlanck)
            appendLine(lineBlanck)
            appendLine("_".repeat(32))
            appendLine(lineBlanck)

            appendLine("Fecha de solicitud:")
            appendLine(date ?: "N/A")
            appendLine(lineBlanck)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AVISO DE PROCESO DE GARANTÍA") }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Estimado cliente:\n" +
                            "\n" +
                            "Le informamos que su producgo ha sido recibido para revision bajo proceso de garantia. El tiempo estimado para este tramite es de uno a dos meses, dependiendo del diagnostico y tiempos de respuesta del proveedor o fabricante.\n" +
                            "\n" +
                            "Al frimar este documento, usted autoriza la recoleccion del articulo dañado y reconoce que:\n" +
                            "\n" +
                            "- El producto sera evaluado para determinar si aplica o no la garantia.\n" +
                            "\n" +
                            "En caso de no proceder, se le notificara para que decida si desea continuar con una reparacion con costo adicional o la devolucion del articulo.\n" +
                            "\n" +
                            "El articulo permanecera bajo resguardo hasta la resolucion del caso.\n" +
                            "\n" +
                            "Agradecemos su comprension. Para cualquier duda o seguimiento, quedamos a su disposicion.\n" +
                            "\n" +
                            "Atentamente\n" +
                            "Muebles San Pablo\n" +
                            "Departamento de Garantias\n" +
                            "\n" +
                            "Nombre del cliente: ${sale?.CLIENTE} " +
                            "\n" +
                            "\n" +
                            "Firma del cliente: ______________________\n" +
                            "\n" +
                            "\n" +
                            "Fecha de solicitud: $date"
                )

                Spacer(modifier = Modifier.height(16.dp))
                SelectBluetoothDevice(
                    textToPrint = ticket!!,
                    modifier = Modifier.fillMaxWidth(),
                    onPrintRequest = { device, text ->
                        coroutineScope.launch {
                            try {
                                ThermalPrinting.printText(device, text, context)
                            } catch (_: Exception) {
                            }
                        }
                    }
                )
            }
        }

    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Iniciar Garantía") }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = defectDescription,
                        onValueChange = {
                            defectDescription = it
                            if (it.text.isNotBlank()) showError = false
                        },
                        label = { Text("Defecto del artículo *") },
                        isError = showError,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        shape = RoundedCornerShape(15.dp)
                    )
                    if (showError) {
                        Text(
                            "Este campo es obligatorio",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = observations,
                        onValueChange = { observations = it },
                        label = { Text("Observaciones (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 10,
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Agregar imágenes",
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
                            Text("Galería")
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

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(imageUris) { uri ->
                            Box(modifier = Modifier.size(80.dp)) {
                                Image(
                                    painter = rememberAsyncImagePainter(uri),
                                    contentDescription = "Imagen",
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.Gray, shape = RoundedCornerShape(8.dp)),
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

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        val isDescriptionValid = defectDescription.text.isNotBlank()
                        val isImageValid = imageUris.isNotEmpty()

                        showError = !isDescriptionValid
                        showImageError = !isImageValid

                        if (isDescriptionValid && isImageValid) {
                            showDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar garantía")
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirmar acción") },
            text = { Text("¿Estás seguro que deseas generar esta garantía?") },
            confirmButton = {
                Button(onClick = {
                    val id = UUID.randomUUID().toString()
                    val newGuarantee = GuaranteeEntity(
                        ID = 0,
                        EXTERNAL_ID = id,
                        DOCTO_CC_ID = saleId,
                        ESTADO = NOTIFICADO,
                        DESCRIPCION_FALLA = defectDescription.text,
                        OBSERVACIONES = observations.text.takeIf { it.isNotBlank() },
                        UPLOADED = 1,
                        FECHA_SOLICITUD = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    )
                    guaranteesViewModel.insertGuarantee(newGuarantee)
                    guaranteesViewModel.saveGuaranteeImages(
                        context = context,
                        uris = imageUris,
                        guaranteeExternalId = id,
                        description = defectDescription.text,
                        fechaSubida = newGuarantee.FECHA_SOLICITUD
                    )
                    showDialog = false
                    navController.popBackStack()
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
