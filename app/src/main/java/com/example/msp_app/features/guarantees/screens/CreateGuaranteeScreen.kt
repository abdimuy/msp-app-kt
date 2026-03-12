package com.example.msp_app.features.guarantees.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.features.guarantees.screens.components.GuaranteeImagePicker
import com.example.msp_app.features.guarantees.screens.viewmodels.CreateGuaranteeViewModel
import com.example.msp_app.features.sales.screens.ClienteSearchBottomSheet
import com.example.msp_app.features.sales.viewmodels.ClienteSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGuaranteeScreen(navController: NavController) {
    val viewModel: CreateGuaranteeViewModel = viewModel()
    val clienteSearchViewModel: ClienteSearchViewModel = viewModel()
    val context = LocalContext.current

    val clienteNombre by viewModel.clienteNombre.collectAsState()
    val productoNombre by viewModel.productoNombre.collectAsState()
    val descripcionFalla by viewModel.descripcionFalla.collectAsState()
    val observaciones by viewModel.observaciones.collectAsState()
    val imageUris by viewModel.imageUris.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var showClienteSheet by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var showClienteError by remember { mutableStateOf(false) }
    var showProductoError by remember { mutableStateOf(false) }
    var showFallaError by remember { mutableStateOf(false) }
    var showImageError by remember { mutableStateOf(false) }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Nueva Garantía") },
                    navigationIcon = {
                        IconButton(onClick = openDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    }
                )
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
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Cliente selector
                    Box {
                        OutlinedTextField(
                            value = clienteNombre,
                            onValueChange = {},
                            label = { Text("Cliente *") },
                            placeholder = { Text("Toca para buscar cliente") },
                            readOnly = true,
                            isError = showClienteError,
                            modifier = Modifier.fillMaxWidth(),
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
                                .clickable { showClienteSheet = true }
                        )
                    }
                    if (showClienteError) {
                        Text(
                            "Debes seleccionar un cliente",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Producto
                    OutlinedTextField(
                        value = productoNombre,
                        onValueChange = {
                            viewModel.onProductoChange(it)
                            if (it.isNotBlank()) showProductoError = false
                        },
                        label = { Text("Producto *") },
                        isError = showProductoError,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                    if (showProductoError) {
                        Text(
                            "Este campo es obligatorio",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Descripción de falla
                    OutlinedTextField(
                        value = descripcionFalla,
                        onValueChange = {
                            viewModel.onDescripcionFallaChange(it)
                            if (it.isNotBlank()) showFallaError = false
                        },
                        label = { Text("Descripción de la falla *") },
                        isError = showFallaError,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(15.dp)
                    )
                    if (showFallaError) {
                        Text(
                            "Este campo es obligatorio",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Observaciones
                    OutlinedTextField(
                        value = observaciones,
                        onValueChange = { viewModel.onObservacionesChange(it) },
                        label = { Text("Observaciones (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Images
                    GuaranteeImagePicker(
                        imageUris = imageUris,
                        onAddImage = {
                            viewModel.addImage(it)
                            showImageError = false
                        },
                        onRemoveImage = { viewModel.removeImage(it) },
                        showError = showImageError
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        showClienteError = clienteNombre.isBlank()
                        showProductoError = productoNombre.isBlank()
                        showFallaError = descripcionFalla.isBlank()
                        showImageError = imageUris.isEmpty()

                        if (viewModel.isFormValid()) {
                            showDialog = true
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isSaving) "Guardando..." else "Guardar garantía")
                }
            }
        }
    }

    if (showClienteSheet) {
        ClienteSearchBottomSheet(
            viewModel = clienteSearchViewModel,
            onDismiss = { showClienteSheet = false },
            onClienteSelected = { _, nombre ->
                viewModel.onClienteSelected(nombre)
                showClienteSheet = false
                showClienteError = false
            },
            onNewCliente = { nombre ->
                viewModel.onClienteSelected(nombre)
                showClienteSheet = false
                showClienteError = false
            }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirmar acción") },
            text = { Text("¿Estás seguro que deseas generar esta garantía?") },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    viewModel.saveGuarantee(context) {
                        navController.popBackStack()
                    }
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
