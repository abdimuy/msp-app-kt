package com.example.msp_app.core.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.msp_app.features.payments.screens.PaymentTextData
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.util.Locale

object PdfGenerator {

    fun generatePdfFromLines(
        context: Context,
        data: PaymentTextData,
        title: String,
        nameCollector: String
    ): File? {
        val pdfDocument = PdfDocument()
        val paint = Paint().apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }

        val pageWidth = 612
        val pageHeight = 792
        val marginLeft = 40f
        val rightMargin = 40f
        val lineSpacing = 20
        val importFormat = DecimalFormat("#,##0")

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = 40

        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText(title, pageWidth / 2f - 100, yPos.toFloat(), paint)
        yPos += 25

        canvas.drawText("Cobrador: $nameCollector", marginLeft, yPos.toFloat(), paint)
        yPos += 20

        val printDate = DateUtils.formatIsoDate(
            DateUtils.getIsoDateTime(LocalDateTime.now()),
            "dd/MM/yyyy hh:mm a",
            Locale("es", "MX")
        )
        canvas.drawText("Creado el: $printDate", marginLeft, yPos.toFloat(), paint)
        yPos += 30

        val header = "Fecha/Hora             Cliente                              Importe"
        canvas.drawText(header, marginLeft, yPos.toFloat(), paint)
        yPos += lineSpacing
        canvas.drawLine(marginLeft, yPos.toFloat(), pageWidth - marginLeft, yPos.toFloat(), paint)
        yPos += lineSpacing

        paint.isFakeBoldText = false
        for ((fecha, cliente, importe) in data.lines) {
            if (yPos > pageHeight - 80) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = 40
            }

            val formattedImporte = "$${importFormat.format(importe)}"
            val line = String.format(
                Locale("es", "MX"),
                "%-22s %-35s %8s",
                fecha,
                cliente.take(35),
                formattedImporte
            )
            canvas.drawText(line, marginLeft, yPos.toFloat(), paint)
            yPos += lineSpacing
        }


        yPos += lineSpacing
        canvas.drawLine(marginLeft, yPos.toFloat(), pageWidth - marginLeft, yPos.toFloat(), paint)
        yPos += lineSpacing

        paint.isFakeBoldText = true
        val totalPaymentsText = "Total de pagos: ${data.totalCount}"
        val totalAmountsText = "Total recaudado: $${importFormat.format(data.totalAmount)}"

        val xTotalPagos = pageWidth - rightMargin - paint.measureText(totalPaymentsText)
        val xTotalImportes = pageWidth - rightMargin - paint.measureText(totalAmountsText)

        canvas.drawText(totalPaymentsText, xTotalPagos, yPos.toFloat(), paint)
        yPos += lineSpacing
        canvas.drawText(totalAmountsText, xTotalImportes, yPos.toFloat(), paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "reporte_pagos.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }
}
