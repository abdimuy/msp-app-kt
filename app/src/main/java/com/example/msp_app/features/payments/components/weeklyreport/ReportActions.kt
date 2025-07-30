package com.example.msp_app.features.payments.components.weeklyreport

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.msp_app.components.selectbluetoothdevice.SelectBluetoothDevice
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.PdfGenerator
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.features.payments.components.pdfgenerationdialog.PdfGenerationDialog
import com.example.msp_app.features.payments.models.ForgivenessTextData
import com.example.msp_app.features.payments.models.PaymentTextData
import com.example.msp_app.features.payments.models.VisitTextData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun ReportActions(
    paymentTextData: PaymentTextData,
    visitTextData: VisitTextData,
    forgivenessTextData: ForgivenessTextData,
    ticketText: String,
    collectorName: String,
    startIso: String,
    endIso: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Button(
            enabled = !isGeneratingPdf,
            onClick = {
                coroutineScope.launch {
                    isGeneratingPdf = true
                    val file = withContext(Dispatchers.IO) {
                        PdfGenerator.generatePdfFromLines(
                            context = context,
                            data = paymentTextData,
                            visits = visitTextData,
                            forgiveness = forgivenessTextData,
                            title = "REPORTE DE PAGOS SEMANAL",
                            nameCollector = collectorName,
                            fileName = "reporte_semanal_${
                                DateUtils.formatIsoDate(startIso, "dd_MM_yy")
                            }_${
                                DateUtils.formatIsoDate(endIso, "dd_MM_yy")
                            }.pdf"
                        )
                    }
                    isGeneratingPdf = false
                    if (file != null && file.exists()) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            file
                        )
                        pdfUri = uri
                        showDialog = true
                    } else {
                        Toast.makeText(
                            context,
                            "Error al generar PDF",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isGeneratingPdf) "GENERANDO PDF..." else "GENERAR PDF",
                color = Color.White
            )
        }

        if (showDialog && pdfUri != null) {
            PdfGenerationDialog(
                pdfUri = pdfUri!!,
                onDismiss = {
                    showDialog = false
                    pdfUri = null
                }
            )
        }

        SelectBluetoothDevice(
            textToPrint = ticketText,
            modifier = Modifier.fillMaxWidth(),
            onPrintRequest = { device, text ->
                coroutineScope.launch {
                    try {
                        ThermalPrinting.printText(device, text, context)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Error al imprimir: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}