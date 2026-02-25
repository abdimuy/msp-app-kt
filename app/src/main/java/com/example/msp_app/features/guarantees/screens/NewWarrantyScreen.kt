package com.example.msp_app.features.guarantees.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.msp_app.components.ClienteSearchBottomSheet
import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.features.guarantees.screens.viewmodels.GuaranteesViewModel
import com.example.msp_app.features.sales.viewmodels.ClienteSearchViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewWarrantyScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val guaranteesViewModel: GuaranteesViewModel = viewModel()
    val clienteSearchViewModel: ClienteSearchViewModel = viewModel()

    var clienteNombre by remember { mutableStateOf(TextFieldValue("")) }
    var selectedClienteId by remember { mutableStateOf<Int?>(null) }
    var showClienteSearch by remember { mutableStateOf(false) }

    var articulo by remember { mutableStateOf(TextFieldValue("")) }
    var descripcionFalla by remember { mutableStateOf(TextFieldValue("")) }
    var observaciones by remember { mutableStateOf(TextFieldValue("")) }

    val imageUris = remember { mutableStateListOf<Uri>() }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        imageUris.addAll(uris)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            imageUris.add(tempCameraUri!!)
        }
    }

    fun launchCamera() {
        val photoFile = File(context.cacheDir, "warranty_photo_${System.currentTimeMillis()}.jpg")
        tempCameraUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            photoFile
        )
        cameraLauncher.launch(tempCameraUri!!)
    }

    val isFormValid = clienteNombre.text.isNotBlank() &&
            articulo.text.isNotBlank() &&
            descripcionFalla.text.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Garantía") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Cliente",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Box {
                OutlinedTextField(
                    value = clienteNombre,
                    onValueChange = { },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    placeholder = { Text("Seleccionar cliente...") },
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showClienteSearch = true }
                )
            }

            Text(
                text = "Artículo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = articulo,
                onValueChange = { articulo = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nombre del artículo...") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Text(
                text = "Descripción de la falla",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = descripcionFalla,
                onValueChange = { descripcionFalla = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Describe el problema...") },
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )

            Text(
                text = "Observaciones (opcional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = observaciones,
                onValueChange = { observaciones = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("Observaciones adicionales...") },
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            Text(
                text = "Imágenes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(imageUris) { uri ->
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { imageUris.remove(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Eliminar",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Botón cámara
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { launchCamera() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Tomar foto",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Cámara",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.secondary,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Galería",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Galería",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        val externalId = UUID.randomUUID().toString()
                        val fechaSolicitud =
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(Date())

                        val guarantee = GuaranteeEntity(
                            EXTERNAL_ID = externalId,
                            DOCTO_CC_ID = null,
                            CLIENTE_NOMBRE = clienteNombre.text.ifBlank { null },
                            ARTICULO = articulo.text.ifBlank { null },
                            ESTADO = "PENDIENTE",
                            DESCRIPCION_FALLA = descripcionFalla.text,
                            OBSERVACIONES = observaciones.text.ifBlank { null },
                            UPLOADED = 0,
                            FECHA_SOLICITUD = fechaSolicitud
                        )

                        guaranteesViewModel.insertGuarantee(guarantee)

                        if (imageUris.isNotEmpty()) {
                            guaranteesViewModel.saveGuaranteeImages(
                                context = context,
                                uris = imageUris.toList(),
                                guaranteeExternalId = externalId,
                                description = null,
                                fechaSubida = fechaSolicitud
                            )
                        }

                        isSaving = false
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = isFormValid && !isSaving,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isSaving) "Guardando..." else "Guardar Garantía")
            }
        }
    }

    if (showClienteSearch) {
        ClienteSearchBottomSheet(
            viewModel = clienteSearchViewModel,
            onDismiss = { showClienteSearch = false },
            onClienteSelected = { clienteId, nombre ->
                selectedClienteId = clienteId
                clienteNombre = TextFieldValue(nombre)
                showClienteSearch = false
            },
            onNewCliente = { nombre ->
                selectedClienteId = null
                clienteNombre = TextFieldValue(nombre)
                showClienteSearch = false
            }
        )
    }
}
