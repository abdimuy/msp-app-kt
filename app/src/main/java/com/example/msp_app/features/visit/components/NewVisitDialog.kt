package com.example.msp_app.features.visit.components

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.fullscreendialog.FullScreenDialog
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.features.visit.newvisit.VisitFactory
import com.example.msp_app.features.visit.viewmodels.VisitsViewModel
import com.example.msp_app.navigation.Screen
import com.example.msp_app.services.UpdateLocationService
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewVisitDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    sale: Sale,
    navController: NavController
) {
    if (!show) return

    val context = LocalContext.current

    val visitsViewModel: VisitsViewModel = viewModel()
    val activity = LocalContext.current as ComponentActivity
    val authViewModel: AuthViewModel = viewModel(activity)
    val userData by authViewModel.userData.collectAsState()
    val currentUser = (userData as? ResultState.Success)?.data
    var selectedOption by remember { mutableStateOf(Constants.NO_SE_ENCONTRABA) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedTime by remember { mutableStateOf<LocalTime?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val visitConditionForm = listOf(
        Constants.NO_SE_ENCONTRABA to "No se encontraba",
        Constants.PIDE_REAGENDAR to "Pidió reagendar visita",
        Constants.NO_VA_A_DAR_PAGO to "No pagará esta ocasión",
        Constants.CASA_CERRADA to "Casa cerrada con candado",
        Constants.SOLO_MENORES to "Solo había menores",
        Constants.TIENE_PERO_NO_PAGA to "Tiene dinero pero no quiso pagar",
        Constants.FUE_GROSERO to "Fue grosero o agresivo",
        Constants.SE_ESCONDE to "Se asomó pero no salió",
        Constants.NO_RESPONDE to "No responde aunque está",
        Constants.SE_ESCUCHAN_RUIDOS to "Se escuchan ruidos pero no habre"
    )

    fun updateNoteWithDateTime() {
        if (selectedDate != null) {
            val timeToUse = selectedTime ?: LocalTime.MIDNIGHT // 00:00 por defecto
            val dateTime = LocalDateTime.of(selectedDate, timeToUse)
            val formatted = formatRescheduleDateTime(dateTime)
            note = "La cita ha sido reagendada para el $formatted"
        }
    }

    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            selectedDate = Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            updateNoteWithDateTime()
            showDatePicker = false
        }
    }

    LaunchedEffect(showTimePicker) {
        if (showTimePicker) {
            val dialog = TimePickerDialog(
                context,
                { _, hour, minute ->
                    selectedTime = LocalTime.of(hour, minute)
                    updateNoteWithDateTime()
                    showTimePicker = false
                },
                selectedTime?.hour ?: 0,
                selectedTime?.minute ?: 0,
                true
            )
            dialog.setOnDismissListener {
                showTimePicker = false
            }

            dialog.show()
        }
    }

    fun handleSaveVisit() {
        val user = currentUser
        if (user?.COBRADOR_ID == 0) {
            errorMessage = "No se pudo obtener el ID del cobrador. Intenta nuevamente."
            return
        }

        coroutineScope.launch {
            val id = UUID.randomUUID().toString()
            val date = Instant.now().toString()

            val visit = VisitFactory.fromSale(
                sale = sale,
                currentUser = user,
                tipoVisita = selectedOption,
                formaCobroId = 0,
                nota = note,
                id = id,
                fecha = date
            )

            if (selectedOption == Constants.PIDE_REAGENDAR) {
                val date = selectedDate ?: return@launch
                val time = selectedTime ?: LocalTime.MIDNIGHT
                val dateTime = LocalDateTime.of(date, time)
                val isoDate = dateTime
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toString()
                visitsViewModel.saveVisit(visit, sale.DOCTO_CC_ID, isoDate)
            } else {
                visitsViewModel.saveVisit(visit, sale.DOCTO_CC_ID, null)
            }

            val intent = Intent(context, UpdateLocationService::class.java).apply {
                putExtra("visit_id", visit.ID)
            }
            ContextCompat.startForegroundService(context, intent)

            note = ""
            selectedOption = Constants.NO_SE_ENCONTRABA
            selectedTime = null
            selectedDate = null
            showAlertDialog = true
        }
    }

    fun formatLocalTime(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("HH:mm"))

    fun formatLocalDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    FullScreenDialog(
        show = true,
        onDismissRequest = onDismissRequest
    ) {
        when (userData) {
            is ResultState.Idle, is ResultState.Offline -> {
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

            is ResultState.Loading -> {
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
                    Text("Error: Error al cargar datos del cobrador")
                }
            }

            is ResultState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = "Agregar Visita",
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        visitConditionForm.forEach { (option, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .selectable(
                                        selected = (option == selectedOption),
                                        onClick = { selectedOption = option },
                                        role = Role.RadioButton
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (option == selectedOption),
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (selectedOption == Constants.PIDE_REAGENDAR) {
                        Button(
                            onClick = {
                                showDatePicker = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = selectedDate?.let {
                                    formatLocalDate(it)
                                } ?: "SELECCIONAR FECHA",
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { showTimePicker = true },
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = selectedTime?.let {
                                    formatLocalTime(it)
                                } ?: "SELECCIONAR HORA",
                                color = Color.White
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Observaciones (Opcional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { handleSaveVisit() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "GUARDAR VISITA", color = Color.White)
                    }
                }

                if (showDatePicker) {
                    Popup(
                        alignment = Alignment.Center,
                        onDismissRequest = { showDatePicker = false }
                    ) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            DatePicker(
                                state = datePickerState,
                                showModeToggle = false
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAlertDialog) {
        AlertDialog(
            onDismissRequest = { showAlertDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAlertDialog = false
                        onDismissRequest()
                        navController.navigate(
                            Screen.VisitTicket.createRoute(sale.DOCTO_CC_ACR_ID.toString())
                        )
                    }
                ) {
                    Text("Imprimir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAlertDialog = false
                        onDismissRequest()
                    }
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
                        text = "Imprimir Recibo",
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
                    Text("Desea imprimir un recibo de la visita realizada")
                }
            }
        )
    }
}

/**
 * Formats the reschedule date/time chosen via the date/time pickers for the "cita reagendada"
 * note text.
 *
 * [dateTime] is a naive (zone-less) `LocalDateTime` built from `selectedDate` (the calendar day
 * picked in [DatePicker], read out of its UTC-epoch millis) and `selectedTime` (the wall clock
 * picked in [TimePickerDialog]) — it represents the calendar date and time the cobrador chose
 * for the rescheduled visit, always interpreted in [BUSINESS_ZONE] (Mexico City), matching how
 * the rest of the app treats picker-selected wall-clock values. It is never the device zone.
 *
 * Previously `DateUtils.formatLocalDateTime` formatted this naive `LocalDateTime` directly with
 * no zone attached at all. Routing it through an explicit [BUSINESS_ZONE] instant keeps the
 * printed pattern identical (`AppTime.Formats.DATE_TIME_24H` == the old hardcoded
 * `"dd/MM/yyyy HH:mm"`, `AppTime.BUSINESS_LOCALE` == the old hardcoded `Locale("es", "MX")`) —
 * display-only, output unchanged for a fixed zone since Mexico no longer observes DST.
 */
internal fun formatRescheduleDateTime(dateTime: LocalDateTime): String =
    AppTime.formatForDisplay(dateTime.atZone(BUSINESS_ZONE).toInstant())
