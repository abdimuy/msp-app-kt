package com.example.msp_app.features.payments.components.newpaymentpdf

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object PaymentPdfGenerator {

    fun paymentStructuredPdf(
        context: Context,
        folio: String,
        fechaVenta: String,
        cliente: String,
        direccion: String,
        telefono: String,
        enganche: String,
        parcialidad: String,
        precioTotal: String,
        precioMeses: String,
        precioContado: String,
        productos: List<String>,
        fechaPago: String,
        saldoAnterior: String,
        abonado: String,
        saldoActual: String,
        onComplete: (Boolean, String?) -> Unit,
        numeroMeses: String,
        historialpago: List<String>
    ) {
        try {
            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 12f
            }

            val titlePaint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 14f
                isFakeBoldText = true
            }

            val dividerPaint = Paint().apply {
                strokeWidth = 1.5f
                color = android.graphics.Color.LTGRAY
            }

            val leftX = 40f
            val rightX = 320f
            var y = 50

            canvas.drawText("Muebles San Pablo", leftX, y.toFloat(), titlePaint)
            canvas.drawText("Ticket de Pago", rightX, y.toFloat(), titlePaint)
            y += 20
            canvas.drawLine(40f, y.toFloat(), 550f, y.toFloat(), dividerPaint)
            y += 20
            val directionLine = wrapText(
                "Dirección: Privada 20 de Noviembre 3,\nSan Pablo Tepetzingo, Pue.",
                paint,
                280f
            )
            val initialY = y
            for (line in directionLine) {
                canvas.drawText(line, leftX, y.toFloat(), paint)
                y += 20
            }
            canvas.drawText("Folio: $folio", rightX, initialY.toFloat(), paint)
            canvas.drawText("Teléfono: 238-374-06-84", leftX, y.toFloat(), paint)
            canvas.drawText("Fecha: $fechaVenta", rightX, (initialY + 20).toFloat(), paint)
            y += 20
            canvas.drawText("WhatsApp: 238-110-50-61", leftX, y.toFloat(), paint)
            y += 20
            canvas.drawLine(40f, y.toFloat(), 550f, y.toFloat(), dividerPaint)
            y += 30

            canvas.drawText("Datos del cliente", leftX, y.toFloat(), titlePaint)
            y += 20
            canvas.drawText("Nombre: $cliente", leftX, y.toFloat(), paint)
            y += 20
            val directionLines = wrapText("Dirección: $direccion", paint, 500f)
            for (line in directionLines) {
                canvas.drawText(line, leftX, y.toFloat(), paint)
                y += 20
            }
            canvas.drawText("Teléfono: $telefono", leftX, y.toFloat(), paint)
            y += 20
            canvas.drawLine(40f, y.toFloat(), 550f, y.toFloat(), dividerPaint)
            y += 30

            canvas.drawText("Datos de Venta", leftX, y.toFloat(), titlePaint)
            y += 20
            canvas.drawText("Fecha: $fechaVenta", leftX, y.toFloat(), paint)
            canvas.drawText("Enganche: $enganche", rightX, y.toFloat(), paint)
            y += 20
            canvas.drawText("Precio Total: $precioTotal", leftX, y.toFloat(), paint)
            canvas.drawText("Parcialidad: $parcialidad", rightX, y.toFloat(), paint)
            y += 20
            canvas.drawText(
                "Precio a $numeroMeses mes(es): $precioMeses",
                leftX,
                y.toFloat(),
                paint
            )
            if (precioContado != "$0") {
                canvas.drawText("Precio de contado: $precioContado", rightX, y.toFloat(), paint)
            }
            y += 20
            canvas.drawLine(40f, y.toFloat(), 550f, y.toFloat(), dividerPaint)
            y += 30

            canvas.drawText("Producto(s)", leftX, y.toFloat(), titlePaint)
            y += 20
            productos.forEach { producto ->
                canvas.drawText(producto, leftX, y.toFloat(), paint)
                y += 20
            }
            canvas.drawLine(40f, y.toFloat(), 550f, y.toFloat(), dividerPaint)
            y += 30

            canvas.drawText("Información del pago", leftX, y.toFloat(), titlePaint)
            y += 20
            canvas.drawText("Fecha de Pago: $fechaPago", leftX, y.toFloat(), paint)
            y += 20

            paint.isFakeBoldText = false
            canvas.drawText("Saldo anterior: ", leftX, y.toFloat(), paint)
            val textWidth = paint.measureText("Saldo anterior: ")
            paint.isFakeBoldText = true
            canvas.drawText(saldoAnterior, leftX + textWidth, y.toFloat(), paint)
            paint.isFakeBoldText = false
            y += 20

            canvas.drawText("Abonado: ", leftX, y.toFloat(), paint)
            val textWiths = paint.measureText("Abonado: ")
            paint.isFakeBoldText = true
            canvas.drawText(abonado, leftX + textWiths, y.toFloat(), paint)
            paint.isFakeBoldText = false
            y += 20

            canvas.drawText("Saldo actual: $saldoActual", leftX, y.toFloat(), paint)
            val textWithw = paint.measureText("Saldo actual: ")
            paint.isFakeBoldText = true
            canvas.drawText(saldoActual, leftX + textWithw, y.toFloat(), paint)
            y += 20
            canvas.drawLine(40f, y.toFloat(), 550f, y.toFloat(), dividerPaint)
            y += 20
            canvas.drawText("Historial de pagos", leftX, y.toFloat(), titlePaint)
            y += 20
            historialpago.forEach { pago ->
                canvas.drawText(pago, leftX, y.toFloat(), paint)
                y += 20
            }

            pdf.finishPage(page)

            val file = File(context.cacheDir, "Ticket_${folio}.pdf")

            try {
                FileOutputStream(file).use { outputStream ->
                    pdf.writeTo(outputStream)
                }
                pdf.close()
                onComplete(true, file.absolutePath)
                return
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, null)
                return
            }

        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false, null)
        }
    }

    fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }

}
