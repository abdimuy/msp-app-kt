package com.example.msp_app.features.visit.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.components.fullscreendialog.FullScreenDialog
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.visit.Visit
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.features.visit.viewmodels.VisitsViewModel
import com.example.msp_app.services.UpdateLocationService
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@Composable
fun NewVisitDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    sale: Sale
) {
    if (!show) return

    val context = LocalContext.current

    val visitsViewModel: VisitsViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()

    val userData by authViewModel.userData.collectAsState()

    var selectedOption by remember { mutableStateOf(Constants.NO_SE_ENCONTRABA) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val currentUser = (userData as? ResultState.Success)?.data

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

    fun handleSaveVisit() {
        coroutineScope.launch {
            val id = UUID.randomUUID().toString()
            val date = Instant.now().toString()

            val visit = Visit(
                ID = id,
                COBRADOR_ID = currentUser?.COBRADOR_ID ?: 0,
                COBRADOR = sale.NOMBRE_COBRADOR,
                LNG = 0.0,
                LAT = 0.0,
                FORMA_COBRO_ID = 0,
                CLIENTE_ID = sale.CLIENTE_ID,
                ZONA_CLIENTE_ID = sale.ZONA_CLIENTE_ID,
                GUARDADO_EN_MICROSIP = 0,
                FECHA = date,
                IMPTE_DOCTO_CC_ID = sale.DOCTO_CC_ACR_ID,
                TIPO_VISITA = selectedOption,
                NOTA = note,
            )

            visitsViewModel.saveVisit(visit, sale.DOCTO_CC_ID)

            val intent = Intent(context, UpdateLocationService::class.java).apply {
                putExtra("visit_id", visit.ID)
            }
            ContextCompat.startForegroundService(context, intent)

            note = ""
            selectedOption = Constants.NO_SE_ENCONTRABA

            showAlertDialog = true
        }
    }

    FullScreenDialog(
        show = true,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
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
                    .height(300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp)
            ) {
                visitConditionForm.forEach { (text) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (text == selectedOption),
                            onClick = { selectedOption = text }
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Mas información") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(90.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    handleSaveVisit()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar")
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