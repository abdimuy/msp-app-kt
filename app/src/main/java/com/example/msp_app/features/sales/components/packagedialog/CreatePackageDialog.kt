package com.example.msp_app.features.sales.components.packagedialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.msp_app.features.sales.viewmodels.SaleItem
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel

@Composable
fun CreatePackageDialog(
    show: Boolean,
    saleProductsViewModel: SaleProductsViewModel,
    onDismiss: () -> Unit,
    onPackageCreated: () -> Unit
) {
    if (!show) return

    var selectedProducts by remember { mutableStateOf<List<SaleItem>>(emptyList()) }
    var precioLista by remember { mutableStateOf("") }
    var precioCortoplazo by remember { mutableStateOf("") }
    var precioContado by remember { mutableStateOf("") }

    var precioListaError by remember { mutableStateOf(false) }
    var precioCortoplazoError by remember { mutableStateOf(false) }
    var precioContadoError by remember { mutableStateOf(false) }
    var selectionError by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf("") }

    val availableProducts = saleProductsViewModel.saleItems

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                // Header
                Column {
                    Text(
                        text = "Crear Paquete",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(1f)
                    )


                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Selecciona al menos 2 productos para crear un paquete",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Lista de productos con checkboxes
                    Text(
                        text = "Productos (${selectedProducts.size} seleccionados)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (selectionError) {
                        Text(
                            text = "Debes seleccionar al menos 2 productos",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableProducts) { saleItem ->
                        ProductSelectionItem(
                            saleItem = saleItem,
                            isSelected = selectedProducts.any {
                                it.product.ARTICULO_ID == saleItem.product.ARTICULO_ID
                            },
                            onSelectionChange = { isSelected ->
                                selectedProducts = if (isSelected) {
                                    selectedProducts + saleItem
                                } else {
                                    selectedProducts.filter {
                                        it.product.ARTICULO_ID != saleItem.product.ARTICULO_ID
                                    }
                                }
                                selectionError = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Inputs de precios
                Text(
                    text = "Precios del Paquete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = precioContado,
                    onValueChange = {
                        precioContado = it
                        precioContadoError = false
                    },
                    label = { Text("Precio Contado *") },
                    prefix = { Text("$") },
                    isError = precioContadoError,
                    supportingText = {
                        if (precioContadoError) {
                            Text("El precio debe ser mayor a 0")
                        } else null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = precioCortoplazo,
                    onValueChange = {
                        precioCortoplazo = it
                        precioCortoplazoError = false
                    },
                    label = { Text("Precio Corto Plazo *") },
                    prefix = { Text("$") },
                    isError = precioCortoplazoError,
                    supportingText = {
                        if (precioContadoError) {
                            Text("El precio debe ser mayor a 0")
                        } else null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = precioLista,
                    onValueChange = {
                        precioLista = it
                        precioListaError = false
                    },
                    label = { Text("Precio Lista *") },
                    prefix = { Text("$") },
                    isError = precioListaError,
                    supportingText = {
                        if (precioContadoError) {
                            Text("El precio debe ser mayor a 0")
                        } else null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    )
                }

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            // Validaciones
                            var hasError = false

                            if (selectedProducts.size < 2) {
                                selectionError = true
                                hasError = true
                            }

                            val lista = precioLista.toDoubleOrNull()
                            if (lista == null || lista <= 0) {
                                precioListaError = true
                                hasError = true
                            }

                            val cortoplazo = precioCortoplazo.toDoubleOrNull()
                            if (cortoplazo == null || cortoplazo <= 0) {
                                precioCortoplazoError = true
                                hasError = true
                            }

                            val contado = precioContado.toDoubleOrNull()
                            if (contado == null || contado <= 0) {
                                precioContadoError = true
                                hasError = true
                            }

                            if (hasError) {
                                errorMessage = "Por favor corrige los errores antes de continuar"
                                return@Button
                            }

                            // Crear el paquete
                            val result = saleProductsViewModel.createPackage(
                                selectedProducts = selectedProducts,
                                precioLista = lista!!,
                                precioCortoplazo = cortoplazo!!,
                                precioContado = contado!!
                            )

                            result.fold(
                                onSuccess = {
                                    onPackageCreated()
                                    onDismiss()
                                },
                                onFailure = { error ->
                                    errorMessage = error.message ?: "Error al crear el paquete"
                                }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Crear Paquete", color = Color.White)
                    }
                }
            }
        }
    }
}

