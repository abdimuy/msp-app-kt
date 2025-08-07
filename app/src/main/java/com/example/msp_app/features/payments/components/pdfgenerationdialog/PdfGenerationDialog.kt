package com.example.msp_app.features.payments.components.pdfgenerationdialog

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun PdfGenerationDialog(
    pdfUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF Generado") },
        text = { Text("¿Deseas abrirlo o compartirlo?") },
        confirmButton = {
            Button(onClick = {
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(pdfUri, "application/pdf")
                    flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(openIntent)
                onDismiss()
            }) {
                Text("Abrir", color = Color.White)
            }
        },
        dismissButton = {
            Button(onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Compartir PDF")
                )
                onDismiss()
            }) {
                Text("Compartir", color = Color.White)
            }
        }
    )
}