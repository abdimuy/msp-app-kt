package com.example.msp_app.features.sales.components.editpackageprices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.msp_app.data.models.sale.localsale.LocalSaleProductPackage
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel

@Composable
fun EditPackagePricesDialog(
    productPackage: LocalSaleProductPackage,
    saleProductsViewModel: SaleProductsViewModel,
    onDismiss: () -> Unit
) {
    var precioLista by remember { mutableStateOf(productPackage.precioLista.toString()) }
    var precioCortoplazo by remember { mutableStateOf(productPackage.precioCortoplazo.toString()) }
    var precioContado by remember { mutableStateOf(productPackage.precioContado.toString()) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar precios del paquete") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                OutlinedTextField(
                    value = precioContado,
                    onValueChange = { precioContado = it; errorMessage = "" },
                    label = { Text("Precio Contado") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = precioCortoplazo,
                    onValueChange = { precioCortoplazo = it; errorMessage = "" },
                    label = { Text("Precio Corto Plazo") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = precioLista,
                    onValueChange = { precioLista = it; errorMessage = "" },
                    label = { Text("Precio Lista") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lista = precioLista.toDoubleOrNull()
                    val cortoplazo = precioCortoplazo.toDoubleOrNull()
                    val contado = precioContado.toDoubleOrNull()

                    if (lista == null || cortoplazo == null || contado == null) {
                        errorMessage = "Ingrese valores numéricos válidos"
                        return@Button
                    }

                    val success = saleProductsViewModel.updatePackagePrices(
                        packageId = productPackage.packageId,
                        precioLista = lista,
                        precioCortoplazo = cortoplazo,
                        precioContado = contado
                    )

                    if (success) {
                        onDismiss()
                    } else {
                        errorMessage = "Los precios no cumplen con las validaciones"
                    }
                }
            ) {
                Text("Guardar", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}