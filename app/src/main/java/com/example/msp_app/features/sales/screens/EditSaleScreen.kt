package com.example.msp_app.features.sales.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.example.msp_app.features.sales.viewmodels.SaveResult
import kotlinx.coroutines.delay

@Composable
fun EditSaleScreen(
    localSaleId: String,
    navController: NavController
) {
    val activity = LocalActivity.current as ComponentActivity
    val viewModel: NewLocalSaleViewModel = viewModel(viewModelStoreOwner = activity)

    val sale by viewModel.selectedSale.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var clientName by remember { mutableStateOf(TextFieldValue("")) }
    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var address by remember { mutableStateOf(TextFieldValue("")) }
    var numero by remember { mutableStateOf(TextFieldValue("")) }
    var colonia by remember { mutableStateOf(TextFieldValue("")) }
    var poblacion by remember { mutableStateOf(TextFieldValue("")) }
    var ciudad by remember { mutableStateOf(TextFieldValue("")) }
    var tipoVenta by remember { mutableStateOf("CREDITO") }
    var downpayment by remember { mutableStateOf(TextFieldValue("")) }
    var installment by remember { mutableStateOf(TextFieldValue("")) }
    var paymentfrequency by remember { mutableStateOf("") }
    var collectionday by remember { mutableStateOf("") }
    var guarantor by remember { mutableStateOf(TextFieldValue("")) }
    var note by remember { mutableStateOf(TextFieldValue("")) }

    var clientNameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var addressError by remember { mutableStateOf(false) }
    var downpaymentError by remember { mutableStateOf(false) }
    var installmentError by remember { mutableStateOf(false) }
    var paymentFrequencyError by remember { mutableStateOf(false) }
    var collectionDayError by remember { mutableStateOf(false) }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var expandedFrequency by remember { mutableStateOf(false) }
    var expandedDay by remember { mutableStateOf(false) }

    val frequencyOptions = listOf("Semanal", "Quincenal", "Mensual")
    val dayOptions =
        listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    LaunchedEffect(Unit) {
        viewModel.getSaleById(localSaleId)
    }

    LaunchedEffect(sale) {
        sale?.let { currentSale ->
            clientName = TextFieldValue(currentSale.NOMBRE_CLIENTE)
            phone = TextFieldValue(currentSale.TELEFONO ?: "")
            address = TextFieldValue(currentSale.DIRECCION ?: "")
            numero = TextFieldValue(currentSale.NUMERO ?: "")
            colonia = TextFieldValue(currentSale.COLONIA ?: "")
            poblacion = TextFieldValue(currentSale.POBLACION ?: "")
            ciudad = TextFieldValue(currentSale.CIUDAD ?: "")
            tipoVenta = currentSale.TIPO_VENTA ?: "CREDITO"
            downpayment = TextFieldValue(currentSale.ENGANCHE?.toString() ?: "")
            installment = TextFieldValue(currentSale.PARCIALIDAD?.toString() ?: "")
            paymentfrequency = currentSale.FREC_PAGO ?: ""
            collectionday = currentSale.DIA_COBRANZA ?: ""
            guarantor = TextFieldValue(currentSale.AVAL_O_RESPONSABLE ?: "")
            note = TextFieldValue(currentSale.NOTA ?: "")
        }
    }

    LaunchedEffect(saveResult) {
        when (val result = saveResult) {
            is SaveResult.Success -> {
                showSuccessDialog = true
                viewModel.clearSaveResult()
                delay(2000)
                navController.popBackStack()
            }

            is SaveResult.Error -> {
                errorMessage = result.message
                showErrorDialog = true
                viewModel.clearSaveResult()
            }

            null -> {}
        }
    }

    fun validateClientName(): Boolean {
        val isValid = clientName.text.isNotBlank() && clientName.text.length >= 3
        clientNameError = !isValid
        return isValid
    }

    fun validatePhone(): Boolean {
        if (tipoVenta == "CONTADO") {
            phoneError = false
            return true
        }
        val isValid = phone.text.isNotBlank() && phone.text.length == 10
        phoneError = !isValid
        return isValid
    }

    fun validateAddress(): Boolean {
        val isValid = address.text.isNotBlank() && address.text.length >= 5
        addressError = !isValid
        return isValid
    }

    fun validateDownpayment(): Boolean {
        val amountDouble = downpayment.text.toDoubleOrNull()
        val isValid = downpayment.text.isBlank() || (amountDouble != null && amountDouble >= 0)
        downpaymentError = !isValid
        return isValid
    }

    fun validateInstallment(): Boolean {
        if (tipoVenta == "CONTADO") {
            installmentError = false
            return true
        }
        val amountDouble = installment.text.toDoubleOrNull()
        val isValid = amountDouble != null && amountDouble > 0
        installmentError = !isValid
        return isValid
    }

    fun validatePaymentFrequency(): Boolean {
        if (tipoVenta == "CONTADO") {
            paymentFrequencyError = false
            return true
        }
        val isValid = paymentfrequency.isNotBlank()
        paymentFrequencyError = !isValid
        return isValid
    }

    fun validateCollectionDay(): Boolean {
        if (tipoVenta == "CONTADO") {
            collectionDayError = false
            return true
        }
        val isValid = collectionday.isNotBlank()
        collectionDayError = !isValid
        return isValid
    }

    fun validateAllFields(): Boolean {
        val clientNameValid = validateClientName()
        val phoneValid = validatePhone()
        val addressValid = validateAddress()
        val downpaymentValid = validateDownpayment()
        val installmentValid = validateInstallment()
        val paymentFrequencyValid = validatePaymentFrequency()
        val collectionDayValid = validateCollectionDay()

        return clientNameValid && phoneValid && addressValid && downpaymentValid &&
                installmentValid && paymentFrequencyValid && collectionDayValid
    }

    fun saveChanges() {
        if (validateAllFields()) {
            showConfirmDialog = true
        }
    }

    fun confirmSave() {
        viewModel.updateSale(
            saleId = localSaleId,
            nombreCliente = clientName.text,
            telefono = phone.text,
            direccion = address.text,
            numero = numero.text.ifBlank { null },
            colonia = colonia.text.ifBlank { null },
            poblacion = poblacion.text.ifBlank { null },
            ciudad = ciudad.text.ifBlank { null },
            tipoVenta = tipoVenta,
            enganche = if (tipoVenta == "CONTADO") 0.0 else downpayment.text.toDoubleOrNull()
                ?: 0.0,
            parcialidad = if (tipoVenta == "CONTADO") 0.0 else installment.text.toDoubleOrNull()
                ?: 0.0,
            frecPago = if (tipoVenta == "CONTADO") "" else paymentfrequency,
            diaCobranza = if (tipoVenta == "CONTADO") "" else collectionday,
            avalOResponsable = guarantor.text.ifBlank { null },
            nota = note.text.ifBlank { null }
        )
        showConfirmDialog = false
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "¿Guardar cambios?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Se actualizará la información de la venta.")
            },
            confirmButton = {
                Button(
                    onClick = { confirmSave() }
                ) {
                    Text("Guardar", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false }
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
                    text = "¡Venta Actualizada!",
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
                    Text("Entendido")
                }
            }
        )
    }

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
        if (isLoading && sale == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Cargando información...")
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

                    OutlinedTextField(
                        value = tipoVenta,
                        onValueChange = { },
                        label = { Text("Tipo de Venta") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Información del Cliente",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = clientName,
                        onValueChange = { newValue ->
                            clientName = newValue
                            if (newValue.text.isNotEmpty() || clientNameError) {
                                validateClientName()
                            }
                        },
                        label = { Text("Nombre completo del cliente *") },
                        isError = clientNameError,
                        supportingText = if (clientNameError) {
                            {
                                Text(
                                    "Favor de colocar el nombre",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null,
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
                                validatePhone()
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

                    OutlinedTextField(
                        value = address,
                        onValueChange = { newValue ->
                            address = newValue
                            if (newValue.text.isNotEmpty() || addressError) {
                                validateAddress()
                            }
                        },
                        label = { Text("Calle *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        shape = RoundedCornerShape(15.dp),
                        isError = addressError,
                        supportingText = if (addressError) {
                            {
                                Text(
                                    "Coloque al menos el nombre de la calle",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null
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

                    Spacer(Modifier.height(16.dp))

                    if (tipoVenta == "CREDITO") {
                        Text(
                            "Información de Pago",
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
                                onValueChange = { newValue ->
                                    downpayment = newValue
                                    if (newValue.text.isNotEmpty() || downpaymentError) {
                                        validateDownpayment()
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
                                } else null
                            )

                            OutlinedTextField(
                                value = installment,
                                onValueChange = { newValue ->
                                    installment = newValue
                                    if (newValue.text.isNotEmpty() || installmentError) {
                                        validateInstallment()
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

                        Spacer(Modifier.height(12.dp))

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
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expandedFrequency = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            shape = RoundedCornerShape(15.dp)
                        )

                        DropdownMenu(
                            expanded = expandedFrequency,
                            onDismissRequest = { expandedFrequency = false }
                        ) {
                            frequencyOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        paymentfrequency = option
                                        expandedFrequency = false
                                        validatePaymentFrequency()
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

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
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expandedDay = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            shape = RoundedCornerShape(15.dp)
                        )

                        DropdownMenu(
                            expanded = expandedDay,
                            onDismissRequest = { expandedDay = false }
                        ) {
                            dayOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        collectionday = option
                                        expandedDay = false
                                        validateCollectionDay()
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        "Notas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Notas (Opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Button(
                        onClick = { saveChanges() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                        } else {
                            Text("Guardar Cambios", color = Color.White)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}