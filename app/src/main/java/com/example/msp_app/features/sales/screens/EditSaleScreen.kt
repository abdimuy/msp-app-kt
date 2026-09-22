package com.example.msp_app.features.sales.screens

import android.net.Uri
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.ModernSpinner
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion
import com.example.msp_app.feature.ventacorreccion.ui.CorreccionUiState
import com.example.msp_app.feature.ventacorreccion.ui.CorreccionVentaViewModel
import com.example.msp_app.feature.ventacorreccion.ui.components.AvisoNoCorregible
import com.example.msp_app.features.productsInventory.components.CarouselItem
import com.example.msp_app.features.productsInventory.components.CarrouselImage
import com.example.msp_app.features.sales.components.cityselector.CitySelector
import com.example.msp_app.features.sales.components.combo.CreateComboDialog
import com.example.msp_app.features.sales.components.productselector.ProductSaleSummary
import com.example.msp_app.features.sales.components.productselector.ProductSelectionBottomSheet
import com.example.msp_app.features.sales.components.zoneselector.ZoneSelectorSimple
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.example.msp_app.features.sales.viewmodels.NewSaleFormState
import com.example.msp_app.features.sales.viewmodels.NewSaleFormValidator
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.features.warehouses.WarehouseViewModel
import com.example.msp_app.utils.PriceParser

/**
 * Cáscara sobre el formulario de edición (plan "Corregir una venta antes de que suba", Task 5):
 * el formulario en sí (campos, selectores, resumen) es el MISMO de siempre — no se toca su
 * layout — pero el guardado/candado/cancelación ya NO pasan por `EditLocalSaleViewModel`
 * (borrado: apuntaba al backend legado, rotaba la `Idempotency-Key` y ponía `ENVIADO = false` a
 * ciegas). Todo eso ahora es [CorreccionVentaViewModel]: reclama el candado al entrar, guarda por
 * el caso de uso (guardia dentro de la MISMA transacción, sin tocar la llave), y suelta el
 * candado al salir — con o sin guardado, el `cancelar()` de una corrección ya guardada es un
 * no-op (el candado ya se cerró como parte del commit).
 *
 * Fuera de esta cáscara (decisión del plan, "Fuera de alcance" #6): las imágenes NO se editan
 * aquí. `CorreccionUiState.Editando` no las trae — se ven en `SaleDescriptionScreen`, no se
 * tocan durante la corrección.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSaleScreen(localSaleId: String, navController: NavController) {
    val correccionViewModel: CorreccionVentaViewModel = hiltViewModel()
    val warehouseViewModel: WarehouseViewModel = hiltViewModel()
    val authViewModel = LocalAuthViewModel.current
    val saleProductsViewModel: SaleProductsViewModel = viewModel()
    // Sólo para VER las fotos de la venta (ronda de arreglo 1) — mismo viewmodel/componente que
    // `SaleDescriptionScreen`, sin conectarlo al guardado de la corrección.
    val imagesViewModel: NewLocalSaleViewModel = viewModel()

    val correccionState by correccionViewModel.state.collectAsState()
    val saleImages by imagesViewModel.saleImages.collectAsState()

    // El servidor ya tiene esta venta, así que la corrección no viaja en el `POST` del alta sino
    // en tres peticiones (header, cliente, líneas). **Ninguna de las tres lleva el tipo de
    // venta** — no existe endpoint que lo cambie. Dejar el desplegable vivo sería ofrecer un
    // cambio que se guarda en el teléfono y nunca llega a la oficina, sin que nada avise: el
    // mismo defecto silencioso que el teléfono tuvo hasta que se agregó `PATCH /ventas/{id}`.
    val ventaYaEnviada = (correccionState as? CorreccionUiState.Editando)?.yaEnviada == true

    var showProductSheet by remember { mutableStateOf(false) }
    var showCreateComboDialog by remember { mutableStateOf(false) }

    // Form state
    var defectName by remember { mutableStateOf(TextFieldValue("")) }
    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var location by remember { mutableStateOf("") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var numero by remember { mutableStateOf(TextFieldValue("")) }
    var colonia by remember { mutableStateOf(TextFieldValue("")) }
    var poblacion by remember { mutableStateOf(TextFieldValue("")) }
    // Ciudad es `String` (no `TextFieldValue` como sus vecinos) porque la captura
    // pasa por `CitySelector`, cuyo campo libre es de texto plano: el cursor lo
    // administra el propio `TextField` interno.
    var ciudad by remember { mutableStateOf("") }
    // Estado de la fila del catálogo de la que salió la ciudad. La venta editada
    // no lo trae de Room (`LocalSaleEntity` no tiene columna ESTADO): arranca
    // vacío y se llena en cuanto el selector reconcilia el texto contra el
    // catálogo, o cuando el vendedor elige otra ciudad.
    var estado by remember { mutableStateOf("") }
    var ciudadEnCatalogo by remember { mutableStateOf(false) }
    var tipoVenta by remember { mutableStateOf("CREDITO") }
    var downpayment by remember { mutableStateOf(TextFieldValue("")) }
    var installment by remember { mutableStateOf(TextFieldValue("")) }
    var guarantor by remember { mutableStateOf(TextFieldValue("")) }
    var note by remember { mutableStateOf(TextFieldValue("")) }
    var collectionday by remember { mutableStateOf("") }
    var paymentfrequency by remember { mutableStateOf("") }
    var selectedZoneId by remember { mutableStateOf<Int?>(null) }
    var selectedZoneName by remember { mutableStateOf("") }
    var saleDate by remember { mutableStateOf("") }
    // No editable en este formulario (sin campo propio): se conserva tal cual llegó en
    // `CorreccionUiState.Editando.campos` y viaja de regreso sin cambios al guardar.
    var clienteId by remember { mutableStateOf<Int?>(null) }

    // Dropdowns
    var expandedfrequency by remember { mutableStateOf(false) }
    var expandedDia by remember { mutableStateOf(false) }
    var expandedTipoVenta by remember { mutableStateOf(false) }

    // Validation errors
    var defectNameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    // Colonia/Población/Ciudad: obligatorias en el API desde siempre, sin error
    // propio en esta pantalla hasta el incidente del 2026-08-13.
    var coloniaError by remember { mutableStateOf(false) }
    var poblacionError by remember { mutableStateOf(false) }
    var ciudadError by remember { mutableStateOf(false) }
    var installmentError by remember { mutableStateOf(false) }
    var paymentFrequencyError by remember { mutableStateOf(false) }
    var collectionDayError by remember { mutableStateOf(false) }
    var productsError by remember { mutableStateOf(false) }
    var downpaymentError by remember { mutableStateOf(false) }
    var zoneError by remember { mutableStateOf(false) }

    // Dialogs
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Flag to track if form was initialized
    var formInitialized by remember { mutableStateOf(false) }

    // Guarda contra doble-tap: `CorreccionUiState` no expone un estado intermedio de "guardando"
    // (el guardado local es una transacción de Room, no una llamada de red) — sin este flag
    // propio de la pantalla, dos toques rápidos sobre "Guardar corrección" lanzarían dos
    // corrutinas `guardar()` concurrentes. Se apaga en cuanto `correccionState` sale de
    // `Editando` (guardado aceptado → `Guardada`, o rechazado → `NoCorregible`).
    var guardando by remember { mutableStateOf(false) }

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

    // Reclama el candado de edición al entrar (Task 3: reentrante para una sesión ANTERIOR del
    // mismo teléfono; una venta ajena/ya subida/en vuelo cae en `CorreccionUiState.NoCorregible`).
    LaunchedEffect(localSaleId) {
        correccionViewModel.reclamar(localSaleId)
    }

    // Carga las fotos para VERLAS (ronda de arreglo 1) — independiente del candado de corrección:
    // una consulta de sólo lectura, no forma parte de `CorreccionUiState`.
    LaunchedEffect(localSaleId) {
        imagesViewModel.loadImagesBySaleId(localSaleId)
    }

    // Suelta el candado al salir de la pantalla, por CUALQUIER camino (flecha de regreso, gesto
    // del sistema, o después de guardar). `CorreccionVentaViewModel.cancelar` es un no-op si el
    // estado ya no es `Editando` — tras un guardado exitoso el candado ya se cerró como parte del
    // commit, así que esta llamada no le quita nada a nadie.
    DisposableEffect(localSaleId) {
        onDispose {
            val userEmail = when (val userState = authViewModel.userData.value) {
                is ResultState.Success -> userState.data?.EMAIL ?: ""
                else -> ""
            }
            correccionViewModel.cancelar(userEmail)
        }
    }

    // Load warehouse products
    LaunchedEffect(camionetaId) {
        if (camionetaId != null) {
            warehouseViewModel.selectWarehouse(camionetaId)
        }
    }

    // Initialize form with the sale reclamada
    LaunchedEffect(correccionState, productosCamioneta) {
        val editando = correccionState as? CorreccionUiState.Editando
        if (!formInitialized && editando != null && productosCamioneta.isNotEmpty()) {
            val campos = editando.campos
            defectName = TextFieldValue(campos.nombreCliente)
            phone = TextFieldValue(campos.telefono)
            location = campos.direccion
            latitude = campos.latitud
            longitude = campos.longitud
            numero = TextFieldValue(campos.numero ?: "")
            colonia = TextFieldValue(campos.colonia ?: "")
            poblacion = TextFieldValue(campos.poblacion ?: "")
            ciudad = campos.ciudad ?: ""
            tipoVenta = campos.tipoVenta ?: "CREDITO"
            saleProductsViewModel.setTipoVenta(tipoVenta)
            downpayment = TextFieldValue(campos.enganche?.toString() ?: "")
            installment = TextFieldValue(campos.parcialidad.toString())
            guarantor = TextFieldValue(campos.avalOResponsable ?: "")
            note = TextFieldValue(campos.nota ?: "")
            collectionday = campos.diaCobranza
            paymentfrequency = campos.frecPago
            selectedZoneId = campos.zonaClienteId
            selectedZoneName = campos.zonaCliente ?: ""
            saleDate = campos.fechaVenta
            clienteId = campos.clienteId

            // Load products into SaleProductsViewModel with their comboId
            editando.productos.forEach { productEntity ->
                val product = productosCamioneta.find { it.ARTICULO_ID == productEntity.ARTICULO_ID }
                if (product != null) {
                    saleProductsViewModel.addProductToSaleWithCombo(
                        product,
                        productEntity.CANTIDAD,
                        productEntity.COMBO_ID
                    )
                }
            }

            // Restore combo metadata
            editando.combos.forEach { combo ->
                saleProductsViewModel.createComboWithId(
                    comboId = combo.COMBO_ID,
                    nombreCombo = combo.NOMBRE_COMBO,
                    precioLista = combo.PRECIO_LISTA,
                    precioCortoPlazo = combo.PRECIO_CORTO_PLAZO,
                    precioContado = combo.PRECIO_CONTADO
                )
            }

            formInitialized = true
        }
    }

    // Handle save result: `Guardada` muestra el diálogo de confirmación con el texto exacto del
    // plan; `NoCorregible` (candado ajeno-pero-reentrante, o la venta se volvió no editable entre
    // el reclamo y el guardado) reusa el mismo aviso que `SaleDescriptionScreen` — no un diálogo
    // de error de formulario, porque no es un error de validación.
    LaunchedEffect(correccionState) {
        when (correccionState) {
            is CorreccionUiState.Guardada -> {
                guardando = false
                showSuccessDialog = true
            }
            is CorreccionUiState.NoCorregible -> guardando = false
            else -> Unit
        }
    }

    // --- Validación ---
    //
    // Esta pantalla tenía su propia COPIA de las reglas en funciones locales del
    // composable. Copiar reglas es como el incidente del 2026-08-13 se hizo
    // permanente: la copia del alta y la de la edición podían (y de hecho lo
    // hacían) discrepar entre sí y con el servidor sin que nada lo notara — el
    // teléfono de aquí solo medía `length == 10` sobre el texto crudo, y ni esta
    // pantalla ni la de alta validaban colonia/población/ciudad. Ahora ambas
    // llaman a `NewSaleFormValidator`, la única fuente de verdad, empaquetando el
    // estado local en un `NewSaleFormState`. Un vendedor no debe poder editar una
    // venta pendiente y dejarla en un estado que el servidor rechace: sería
    // reponer el atasco que estaba tratando de resolver.

    // Las funciones `validateX` de abajo ya NO contienen reglas: solo adaptan el
    // veredicto del validador compartido a los `remember` de error de esta
    // pantalla, para poder revalidar campo por campo mientras el vendedor teclea.

    fun validateClientName(name: String): Boolean {
        val isValid = NewSaleFormValidator.validateClientName(name)
        defectNameError = !isValid
        return isValid
    }

    fun validatePhone(phoneNumber: String): Boolean {
        val isValid = NewSaleFormValidator.validatePhone(phoneNumber, tipoVenta)
        phoneError = !isValid
        return isValid
    }

    fun validateLocation(loc: String): Boolean {
        val isValid = NewSaleFormValidator.validateStreet(loc)
        locationError = !isValid
        return isValid
    }

    fun validateColonia(value: String) {
        coloniaError = !NewSaleFormValidator.validateColonia(value)
    }

    fun validatePoblacion(value: String) {
        poblacionError = !NewSaleFormValidator.validatePoblacion(value)
    }

    fun validateCiudad(value: String) {
        ciudadError = !NewSaleFormValidator.validateCiudad(value)
    }

    fun validateInstallment(amount: String): Boolean {
        val isValid = NewSaleFormValidator.validateInstallmentEdit(amount, tipoVenta)
        installmentError = !isValid
        return isValid
    }

    fun validatePaymentFrequency(frequency: String): Boolean {
        val isValid = NewSaleFormValidator.validatePaymentFrequency(frequency, tipoVenta)
        paymentFrequencyError = !isValid
        return isValid
    }

    fun validateCollectionDay(day: String): Boolean {
        val isValid = NewSaleFormValidator.validateCollectionDay(day, tipoVenta)
        collectionDayError = !isValid
        return isValid
    }

    fun validateDownpayment(amount: String): Boolean {
        val isValid = NewSaleFormValidator.validateDownpayment(amount)
        downpaymentError = !isValid
        return isValid
    }

    /** Empaqueta los `remember` de esta pantalla en el estado que consume el validador compartido. */
    fun currentFormState(): NewSaleFormState = NewSaleFormState(
        clientName = defectName.text,
        phone = phone.text,
        street = location,
        numero = numero.text,
        colonia = colonia.text,
        poblacion = poblacion.text,
        ciudad = ciudad,
        estado = estado,
        ciudadEnCatalogo = ciudadEnCatalogo,
        tipoVenta = tipoVenta,
        selectedZoneId = selectedZoneId,
        selectedZoneName = selectedZoneName,
        downpayment = downpayment.text,
        installment = installment.text,
        collectionDay = collectionday,
        paymentFrequency = paymentfrequency
    )

    fun validateFields(): Boolean {
        // Las imágenes no se editan en la corrección (Fuera de alcance #6 del plan): una venta
        // sin imágenes ya es fallo permanente del subidor, así que llegar aquí en estado
        // `Corregible` YA implica que las tiene — `hasImages = true` siempre, no hay un conteo
        // que mantener en esta pantalla.
        val errors = NewSaleFormValidator.validateAll(
            state = currentFormState(),
            hasProducts = saleProductsViewModel.hasItems(),
            hasImages = true
        ).copy(
            // Única bandera que esta pantalla NO toma tal cual del alta; el porqué
            // está documentado en `NewSaleFormValidator.validateInstallmentEdit`.
            installment = !NewSaleFormValidator.validateInstallmentEdit(installment.text, tipoVenta)
        )

        defectNameError = errors.clientName
        phoneError = errors.phone
        locationError = errors.location
        coloniaError = errors.colonia
        poblacionError = errors.poblacion
        ciudadError = errors.ciudad
        installmentError = errors.installment
        paymentFrequencyError = errors.paymentFrequency
        collectionDayError = errors.collectionDay
        downpaymentError = errors.downpayment
        zoneError = errors.zone
        productsError = errors.products

        return !errors.hasAny
    }

    fun guardar() {
        correccionState as? CorreccionUiState.Editando ?: return
        val userEmail = when (val userState = userData) {
            is ResultState.Success -> userState.data?.EMAIL ?: ""
            else -> ""
        }

        val comboEntities = saleProductsViewModel.getCombosList().map { combo ->
            LocalSaleComboEntity(
                COMBO_ID = combo.comboId,
                LOCAL_SALE_ID = localSaleId,
                NOMBRE_COMBO = combo.nombreCombo,
                PRECIO_LISTA = combo.precioLista,
                PRECIO_CORTO_PLAZO = combo.precioCortoPlazo,
                PRECIO_CONTADO = combo.precioContado
            )
        }

        val productEntities = saleProductsViewModel.saleItems.map { saleItem ->
            val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
            LocalSaleProductEntity(
                LOCAL_SALE_ID = localSaleId,
                ARTICULO_ID = saleItem.product.ARTICULO_ID,
                ARTICULO = saleItem.product.ARTICULO,
                CANTIDAD = saleItem.quantity,
                PRECIO_LISTA = parsedPrices.precioLista,
                PRECIO_CORTO_PLAZO = parsedPrices.precioCortoplazo,
                PRECIO_CONTADO = parsedPrices.precioContado,
                COMBO_ID = saleItem.comboId
            )
        }

        val campos = CamposVentaCorregidos(
            nombreCliente = defectName.text,
            fechaVenta = saleDate,
            latitud = latitude,
            longitud = longitude,
            // Mismo `trim` que el alta: el servidor valida contra el valor ya
            // recortado, así que persistir "   " tal cual llegaría al API como
            // cadena vacía y reventaría en la cola, no en la pantalla.
            direccion = location.trim(),
            parcialidad = if (tipoVenta == "CONTADO") 0.0 else installment.text.toDoubleOrNull() ?: 0.0,
            enganche = if (tipoVenta == "CONTADO") 0.0 else downpayment.text.toDoubleOrNull() ?: 0.0,
            telefono = phone.text.trim(),
            frecPago = if (tipoVenta == "CONTADO") "" else paymentfrequency,
            avalOResponsable = if (tipoVenta == "CONTADO") "" else guarantor.text,
            nota = note.text,
            diaCobranza = if (tipoVenta == "CONTADO") "" else collectionday,
            precioTotal = saleProductsViewModel.getTotalPrecioListaWithCombos(),
            tiempoACortoPlazoMeses = 0,
            montoACortoPlazo = saleProductsViewModel.getTotalMontoCortoPlazoWithCombos(),
            montoDeContado = saleProductsViewModel.getTotalMontoContadoWithCombos(),
            numero = numero.text.trim().ifBlank { null },
            colonia = colonia.text.trim().ifBlank { null },
            poblacion = poblacion.text.trim().ifBlank { null },
            ciudad = ciudad.trim().ifBlank { null },
            tipoVenta = tipoVenta,
            zonaClienteId = selectedZoneId,
            zonaCliente = selectedZoneName,
            clienteId = clienteId
        )

        correccionViewModel.guardar(campos, productEntities, comboEntities, userEmail)
    }

    // Success dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    text = TextosCorreccion.CORRECCION_GUARDADA,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text("La venta se corrigió correctamente.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Aceptar")
                }
            }
        )
    }

    // Product selection bottom sheet
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

    // Create combo dialog
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
        tipoVenta = tipoVenta
    )

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
        val editando = correccionState as? CorreccionUiState.Editando
        val noCorregible = correccionState as? CorreccionUiState.NoCorregible

        if (noCorregible != null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Mismo componente que `SaleDescriptionScreen` (`EntradaCorreccion`,
                // `:feature:ventaCorreccion`) — el mismo estado se ve igual en las dos pantallas.
                AvisoNoCorregible(mensaje = noCorregible.mensaje)
            }
        } else if (editando == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ModernSpinner(size = 60.dp)
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

                    Box {
                        OutlinedTextField(
                            value = tipoVenta,
                            onValueChange = { },
                            label = { Text("Tipo de Venta") },
                            // `readOnly` y sin `clickable`, pero NO `enabled = false`: en Material 3
                            // el deshabilitado apaga también el VALOR, y "CONTADO"/"CRÉDITO" es
                            // justo el dato que hay que poder leer de un vistazo. El bloqueo lo dan
                            // la ausencia de `clickable`, la flecha que no se pinta y el
                            // desplegable que no se despliega.
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (ventaYaEnviada) {
                                        Modifier
                                    } else {
                                        Modifier.clickable { expandedTipoVenta = true }
                                    }
                                ),
                            trailingIcon = if (ventaYaEnviada) {
                                null
                            } else {
                                {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { expandedTipoVenta = true }
                                    )
                                }
                            },
                            // La cadena sale de `TextosCorreccion`, no escrita a mano aquí: un
                            // literal suelto en esta pantalla es el hueco que ya se cerró una vez
                            // con `GUARDAR_CORRECCION` (ronda 1 de la Task 5). `:app` no aplica
                            // Roborazzi, así que nada impediría que alguien lo recortara o le
                            // metiera un punto final sin que ninguna prueba se enterara; en
                            // `TextosCorreccion` sí lo cubre `TextosCorreccionTest`.
                            supportingText = if (ventaYaEnviada) {
                                { Text(TextosCorreccion.YA_SE_ENVIO) }
                            } else {
                                null
                            },
                            shape = RoundedCornerShape(15.dp)
                        )
                        DropdownMenu(
                            // Por si alguien llegara a abrirlo por otra vía: con la venta ya
                            // enviada no se despliega nunca.
                            expanded = expandedTipoVenta && !ventaYaEnviada,
                            onDismissRequest = { expandedTipoVenta = false }
                        ) {
                            tipoVentaOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        tipoVenta = option
                                        saleProductsViewModel.setTipoVenta(option)
                                        expandedTipoVenta = false
                                        if (option == "CONTADO") {
                                            selectedZoneId = null
                                            selectedZoneName = ""
                                            zoneError = false
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // La zona es obligatoria en todo tipo de venta: el
                    // servidor resuelve la caja desde ella y rechaza las
                    // ventas sin zona.
                    ZoneSelectorSimple(
                        selectedZoneId = selectedZoneId,
                        selectedZoneName = selectedZoneName,
                        onZoneSelected = { zoneId, zoneName ->
                            selectedZoneId = zoneId
                            selectedZoneName = zoneName
                            zoneError = false
                        },
                        error = if (zoneError) "Selecciona una zona" else null,
                        isRequired = true
                    )

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
                        } else {
                            null
                        },
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
                                validatePhone(newValue.text)
                            }
                        },
                        label = { Text(if (tipoVenta == "CONTADO") "Teléfono" else "Teléfono *") },
                        isError = phoneError,
                        supportingText = if (phoneError) {
                            {
                                Text(
                                    "El teléfono debe tener 10 dígitos",
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

                    // Location - read only since it was captured on creation
                    OutlinedTextField(
                        value = location,
                        onValueChange = { newValue ->
                            location = newValue
                            if (newValue.isNotEmpty() || locationError) {
                                validateLocation(newValue)
                            }
                        },
                        label = { Text("Calle *") },
                        modifier = Modifier.fillMaxWidth(),
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
                            value = numero,
                            onValueChange = { numero = it },
                            label = { Text("Número") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                        // Mismo cableado que el alta: error propio por campo, no un
                        // mensaje genérico. Editar una venta pendiente no debe
                        // poder dejarla en un estado que el servidor rechace —
                        // sería reponer justo el atasco que se está resolviendo.
                        OutlinedTextField(
                            value = colonia,
                            onValueChange = { newValue ->
                                colonia = newValue
                                if (newValue.text.isNotEmpty() || coloniaError) {
                                    validateColonia(newValue.text)
                                }
                            },
                            label = { Text("Colonia *") },
                            isError = coloniaError,
                            supportingText = if (coloniaError) {
                                {
                                    Text(
                                        "Colonia obligatoria",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = poblacion,
                        onValueChange = { newValue ->
                            poblacion = newValue
                            if (newValue.text.isNotEmpty() || poblacionError) {
                                validatePoblacion(newValue.text)
                            }
                        },
                        label = { Text("Población *") },
                        isError = poblacionError,
                        supportingText = if (poblacionError) {
                            {
                                Text(
                                    "Población obligatoria",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Mismo selector que el alta, por coherencia: una venta que se
                    // editó a mano no debe poder quedar con una ciudad que el alta
                    // ya no permite capturar. El texto libre sigue permitido — no
                    // bloquea capturar, bloquea aplicar.
                    CitySelector(
                        ciudad = ciudad,
                        estado = estado,
                        enCatalogo = ciudadEnCatalogo,
                        onCitySelected = { city ->
                            // Ciudad y estado se fijan juntos, desde la misma fila.
                            ciudad = city.ciudad.trim()
                            estado = city.estado.trim()
                            ciudadEnCatalogo = true
                            validateCiudad(ciudad)
                        },
                        onFreeTextChanged = { newValue ->
                            ciudad = newValue
                            estado = ""
                            ciudadEnCatalogo = false
                            if (newValue.isNotEmpty() || ciudadError) {
                                validateCiudad(newValue)
                            }
                        },
                        error = if (ciudadError) "Ciudad obligatoria" else null,
                        isRequired = true
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
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal
                                ),
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
                                } else {
                                    null
                                }
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
                                } else {
                                    null
                                },
                                label = { Text("Parcialidad *") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal
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
                                            collectionday = option
                                            expandedDia = false
                                            validateCollectionDay(option)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
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

                    // Fotos en SÓLO LECTURA (ronda de arreglo 1, decisión del orquestador): el
                    // plan prohíbe EDITARLAS aquí, no VERLAS — el vendedor debe poder confirmar
                    // qué fotos lleva la venta que está corrigiendo. `NewLocalSaleViewModel` +
                    // `CarrouselImage` son los MISMOS que ya usa `SaleDescriptionScreen`; sin
                    // botones de agregar/borrar y sin tocar `CorreccionUiState` ni el guardado.
                    val carouselItems = remember(saleImages) {
                        saleImages.mapIndexed { index, image ->
                            CarouselItem(
                                id = index,
                                imagePath = if (image.IMAGE_URI is String) {
                                    image.IMAGE_URI as String
                                } else {
                                    (image.IMAGE_URI as Uri).path ?: ""
                                },
                                description = "Imagen ${index + 1}"
                            )
                        }
                    }
                    if (carouselItems.isNotEmpty()) {
                        CarrouselImage(carouselItems = carouselItems)
                    } else {
                        Text(text = "No hay imágenes registradas")
                    }

                    Spacer(Modifier.height(16.dp))

                    ProductSaleSummary(
                        saleProductsViewModel = saleProductsViewModel,
                        productosCamioneta = productosCamioneta,
                        onOpenProductSheet = { showProductSheet = true },
                        hasError = productsError,
                        tipoVenta = tipoVenta
                    )

                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        if (!guardando && validateFields()) {
                            guardando = true
                            guardar()
                        }
                    },
                    enabled = !guardando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (guardando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        TextosCorreccion.GUARDAR_CORRECCION,
                        color = Color.White
                    )
                }
            }
        }
    }
}
