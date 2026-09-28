package com.example.msp_app.features.forgiveness.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.components.fullscreendialog.FullScreenDialog
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.payment.toEntity
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.features.forgiveness.viewmodels.CondonacionUiState
import com.example.msp_app.features.forgiveness.viewmodels.CondonacionViewModel
import com.example.msp_app.features.payments.newpayment.currentPaymentTimestamp
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.sales.SaleIdSpaces
import com.example.msp_app.services.UpdateLocationService
import com.example.msp_app.ui.theme.ThemeController
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker
import java.util.UUID

/**
 * **El diálogo de condonación.** Dos puertas lo montan (E-APP-044):
 * `ForgivenessScreen` (detalle de venta y detalle de cliente nuevos) y
 * `SaleActionSection` (detalle legado).
 *
 * ## Qué cambió el 2026-09-27, y por qué
 *
 * - **Espera a que la escritura termine** (E-APP-047). Escribe por
 *   [CondonacionViewModel] → `RegistroDeCondonacion`, una transacción que relee el
 *   saldo y rechaza, sin insertar, un monto que no quepa. Sólo cuando volvió
 *   [com.example.msp_app.data.pagos.ResultadoDeLaCondonacion.GUARDADA] se llama a
 *   [onGuardada] con el id: **quien navega es la puerta, no el diálogo**.
 * - **[onGuardada] y [onDismissRequest] son dos cosas.** Antes el diálogo
 *   navegaba al ticket y luego llamaba a `onDismissRequest`, que en la puerta
 *   nueva es `popBackStack()` — y sacaba de la pila el ticket recién empujado,
 *   dejando la condonación abierta con el saldo viejo (E-APP-031). Ahora cerrar
 *   es cerrar, y guardar es guardar.
 * - **El saldo que se pinta, prellena y topa es el releído de Room**
 *   ([CondonacionUiState.saldo]), no el de [sale], que es la foto con la que se
 *   abrió la pantalla (E-APP-043, #4).
 * - **"Confirmar" se deshabilita mientras guarda** y el ViewModel ignora un
 *   segundo toque (E-APP-043, #2). Si la escritura falla, el error se queda
 *   visible y no se navega.
 * - **Encola la subida en la misma corrutina** que ve la escritura terminada
 *   (E-APP-046): hasta ahora el único encolado de la condonación era el efecto
 *   secundario del servicio de ubicación.
 *
 * [sale] sigue siendo la fuente de los datos de atribución (cliente, cobrador,
 * zona), que no cambian entre la carga y el toque.
 */
@SuppressLint("ContextCastToActivity")
@Composable
fun NewForgivenessDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onGuardada: (pagoId: String) -> Unit,
    sale: Sale
) {
    if (!show) return

    val context = LocalContext.current

    val activity = LocalContext.current as ComponentActivity
    val authViewModel: AuthViewModel = viewModel(activity)
    val paymentsViewModel: PaymentsViewModel = viewModel()
    // Una instancia del formulario POR APERTURA del diálogo: guardada, queda
    // terminada para siempre (`CondonacionUiState.terminada`), y la puerta legada
    // —donde el ViewModel vive tanto como la pantalla— necesita una nueva la
    // próxima vez que se abra. `remember` vive mientras el diálogo esté abierto.
    val apertura = remember { UUID.randomUUID().toString() }
    val condonacionViewModel: CondonacionViewModel = viewModel(
        key = "condonacion-${SaleIdSpaces.forSaleRow(sale)}-$apertura",
        factory = CondonacionViewModel.Factory(context)
    )
    val userData by authViewModel.userData.collectAsState()

    CondonacionEnPantalla(
        sale = sale,
        userData = userData,
        condonacionViewModel = condonacionViewModel,
        isDark = ThemeController.isDarkMode,
        onDismissRequest = onDismissRequest,
        onGuardada = onGuardada,
        efectosTrasGuardar = { pagoId ->
            encolarSubida(context, pagoId)
            pedirUbicacion(context, pagoId)
            paymentsViewModel.getGroupedPaymentsBySaleId(SaleIdSpaces.forSalePayments(sale))
        }
    )
}

/**
 * El formulario de la condonación, sin Firebase ni servicios: todo lo que decide
 * qué se puede tocar y cuándo vive aquí, para poder medirlo en una prueba de
 * Compose. [efectosTrasGuardar] son los efectos de la puerta real (encolar la
 * subida, pedir la ubicación, recargar el historial legado).
 */
@Composable
internal fun CondonacionEnPantalla(
    sale: Sale,
    userData: ResultState<User?>,
    condonacionViewModel: CondonacionViewModel,
    isDark: Boolean,
    onDismissRequest: () -> Unit,
    onGuardada: (pagoId: String) -> Unit,
    efectosTrasGuardar: (pagoId: String) -> Unit
) {
    val estado by condonacionViewModel.estado.collectAsState()

    val currentUser = (userData as? ResultState.Success)?.data

    var inputValue by remember { mutableStateOf("") }
    var tocado by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAlertDialog by remember { mutableStateOf(false) }

    // La PK de `sales`, que es lo que `SaleDao.getById` filtra (SaleIdSpaces).
    val ventaPk = SaleIdSpaces.forSaleRow(sale)
    LaunchedEffect(ventaPk) {
        condonacionViewModel.cargar(ventaPk)
    }

    // Prellena con el saldo RELEÍDO, y sólo mientras el cobrador no haya escrito.
    // Nunca después de guardar: un formulario terminado no ofrece otro monto.
    LaunchedEffect(estado.saldo) {
        val saldo = estado.saldo
        if (!estado.terminada && !tocado && saldo != null && saldo > 0.0) {
            inputValue = saldo.toString()
        }
    }

    // La escritura terminó y quedó guardada: ahora sí, lo que antes corría sin
    // esperarla (E-APP-047).
    LaunchedEffect(estado.guardadaId) {
        val pagoId = estado.guardadaId ?: return@LaunchedEffect
        efectosTrasGuardar(pagoId)
        showAlertDialog = false
        // Sin limpiar el campo ni reabrir el ViewModel: el formulario se queda
        // TERMINADO —campo y botones apagados— durante la transición al ticket.
        onGuardada(pagoId)
    }

    fun handleSaveForgiveness() {
        if (inputValue.isBlank() || errorMessage != null || estado.guardando || estado.terminada) {
            return
        }
        if (currentUser?.COBRADOR_ID == null || currentUser.COBRADOR_ID == 0) {
            errorMessage = "No se pudo obtener el ID del cobrador. Intenta nuevamente."
            return
        }
        val forgivenessAmount = inputValue.toDoubleOrNull()
        if (forgivenessAmount != null && forgivenessAmount > 0) {
            val forgiveness = Payment(
                CLIENTE_ID = sale.CLIENTE_ID,
                ID = UUID.randomUUID().toString(),
                LAT = 0.0,
                LNG = 0.0,
                IMPORTE = forgivenessAmount,
                NOMBRE_CLIENTE = sale.CLIENTE,
                FECHA_HORA_PAGO = currentPaymentTimestamp(),
                COBRADOR = sale.NOMBRE_COBRADOR,
                COBRADOR_ID = currentUser.COBRADOR_ID,
                DOCTO_CC_ID = 0,
                FORMA_COBRO_ID = Constants.CONDONACION_ID,
                DOCTO_CC_ACR_ID = sale.DOCTO_CC_ACR_ID,
                ZONA_CLIENTE_ID = sale.ZONA_CLIENTE_ID,
                GUARDADO_EN_MICROSIP = false
            )
            condonacionViewModel.condonar(forgiveness.toEntity())
        } else {
            errorMessage = "Ingrese un monto válido"
        }
    }

    errorMessage = validarMontoDeCondonacion(inputValue, estado.saldo)

    FullScreenDialog(
        show = true,
        onDismissRequest = onDismissRequest
    ) {
        when (val state = userData) {
            is ResultState.Idle, is ResultState.Loading, is ResultState.Offline -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }

            is ResultState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Error: ${state.message}")
                }
            }

            is ResultState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Agregar Condonación",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Text(
                        text = sale.CLIENTE,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    Text(
                        buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                append("Saldo actual: ")
                            }
                            withStyle(
                                style = SpanStyle(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                append(estado.saldo?.toCurrency(noDecimals = true) ?: "—")
                            }
                        }
                    )

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = inputValue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag(MONTO_CONDONACION_TAG),
                        onValueChange = {
                            inputValue = it
                            tocado = true
                        },
                        label = { Text("Ingrese monto") },
                        enabled = !estado.guardando && !estado.terminada,
                        textStyle = TextStyle(fontSize = 20.sp),
                        isError = errorMessage != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        )
                    )

                    val mensaje = errorMessage ?: estado.error
                    if (mensaje != null) {
                        Text(
                            text = mensaje,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { showAlertDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(GUARDAR_CONDONACION_TAG),
                        enabled = inputValue.isNotBlank() &&
                            errorMessage == null &&
                            !estado.guardando &&
                            !estado.terminada
                    ) {
                        Text(
                            text = "GUARDAR CONDONACIÓN",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showAlertDialog) {
        ConfirmacionDeCondonacion(
            monto = inputValue.toDoubleOrNull()?.toCurrency(noDecimals = true).orEmpty(),
            guardando = estado.guardando || estado.terminada,
            isDark = isDark,
            onConfirmar = { handleSaveForgiveness() },
            onCancelar = { showAlertDialog = false }
        )
    }
}

/**
 * El mensaje de error del monto tecleado, o `null` si se puede condonar.
 *
 * El tope es [saldoVigente] —el `SALDO_REST` releído de Room por
 * [CondonacionViewModel]—, **nunca** el de la venta con la que se abrió la
 * pantalla: contra esa foto pasaron las tres condonaciones de más de E-APP-029.
 * Sin saldo leído todavía no hay tope contra qué medir, y el monto no se deja
 * pasar. La escritura vuelve a validar dentro de su transacción
 * (`RegistroDeCondonacion`); ésta es la primera línea, la que avisa en pantalla.
 */
internal fun validarMontoDeCondonacion(input: String, saldoVigente: Double?): String? {
    val monto = input.toDoubleOrNull()
    return when {
        input.isBlank() -> null
        monto == null -> "Ingrese un número válido"
        monto <= 0 -> "El monto debe ser mayor a cero"
        saldoVigente == null -> "Cargando saldo"
        monto > saldoVigente -> "El monto no puede ser mayor al saldo restante"
        else -> null
    }
}

/** `testTag` del campo del monto de la condonación. */
internal const val MONTO_CONDONACION_TAG = "condonacion_monto"

/** `testTag` del botón "Guardar condonación". */
internal const val GUARDAR_CONDONACION_TAG = "condonacion_guardar"

/** `testTag` del botón "Confirmar" de la condonación. */
internal const val CONFIRMAR_CONDONACION_TAG = "condonacion_confirmar"

/**
 * La confirmación del monto. **"Confirmar" se deshabilita mientras [guardando]**,
 * y así un segundo toque no llega ni al ViewModel (que de todos modos lo ignora).
 */
@Composable
internal fun ConfirmacionDeCondonacion(
    monto: String,
    guardando: Boolean,
    isDark: Boolean,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!guardando) onCancelar() },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                enabled = !guardando,
                modifier = Modifier.testTag(CONFIRMAR_CONDONACION_TAG)
            ) {
                Text(if (guardando) "Guardando" else "Confirmar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelar,
                enabled = !guardando
            ) {
                Text("Cancelar")
            }
        },
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Realizar Condonación",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Confirmar la condonación por la cantidad de: ",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = monto,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                        style = TextStyle(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    )
}

/**
 * Encola la subida de la condonación ya escrita. Total: un fallo aquí no puede
 * volver "no guardada" una condonación que ya está en Room — la recoge
 * `PaymentsPendingSynchronizer` en el siguiente login (E-APP-046).
 */
@Suppress("TooGenericExceptionCaught")
private fun encolarSubida(context: Context, pagoId: String) {
    try {
        enqueuePendingPaymentsWorker(context, pagoId)
    } catch (fallo: Exception) {
        Log.w(TAG_CONDONACION, "no se pudo encolar la condonacion; queda pendiente", fallo)
    }
}

/**
 * Pide la ubicación de la condonación ya escrita (y, como efecto de
 * `UpdateLocationHandler`, un segundo encolado). Android 12+ puede negar el
 * arranque de un servicio en primer plano; eso no toca el dinero ya escrito.
 */
@Suppress("TooGenericExceptionCaught")
private fun pedirUbicacion(context: Context, pagoId: String) {
    try {
        val intent = Intent(context, UpdateLocationService::class.java).apply {
            putExtra("payment_id", pagoId)
        }
        ContextCompat.startForegroundService(context, intent)
    } catch (fallo: Exception) {
        Log.w(TAG_CONDONACION, "no se pudo pedir la ubicacion de la condonacion", fallo)
    }
}

private const val TAG_CONDONACION = "NewForgivenessDialog"
