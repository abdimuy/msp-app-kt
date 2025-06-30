package com.example.msp_app.features.visit.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.components.fullscreendialog.FullScreenDialog
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.models.sale.Sale

@Composable
fun NewVisitDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    sale: Sale
) {
    if (!show) return
    var selectedOption by remember { mutableStateOf("") }
    var showAlertDialog by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

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
                value = text,
                onValueChange = { text = it },
                label = { Text("Mas información") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(90.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { showAlertDialog = true },
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
                    onClick = {}
                ) {
                    Text("Imprimir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {}
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