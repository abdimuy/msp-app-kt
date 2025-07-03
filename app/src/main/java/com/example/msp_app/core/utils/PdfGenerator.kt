package com.example.msp_app.core.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.msp_app.features.payments.screens.PaymentTextData
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.Locale

object PdfGenerator {

    fun generatePdfFromLines(
        context: Context,
        data: PaymentTextData,
        title: String,
        nameCollector: String,
        fileName: String
    ): File? {
        val pdfDocument = PdfDocument()
        val paint = Paint().apply {
            textSize = 10f
            typeface = Typeface.SANS_SERIF
        }

        val pageWidth = 612
        val pageHeight = 792
        val marginLeft = 40f
        val rightMargin = 40f
        val lineSpacing = 15

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = 40

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

        val headerDate = "Fecha/Hora"
        val headerClient = "Cliente"
        val headerMethod = "Método de pago"
        val headerAmount = "Importe"
        val xDate = marginLeft
        canvas.drawText(headerDate, xDate, yPos.toFloat(), paint)
        val xClient = xDate + paint.measureText(headerDate) + 40f
        canvas.drawText(headerClient, xClient, yPos.toFloat(), paint)
        val xMethod = xClient + paint.measureText(headerClient) + 200f
        canvas.drawText(headerMethod, xMethod, yPos.toFloat(), paint)
        val xAmount = pageWidth - rightMargin - paint.measureText(headerAmount)
        canvas.drawText(headerAmount, xAmount, yPos.toFloat(), paint)
        yPos += lineSpacing
        canvas.drawLine(marginLeft, yPos.toFloat(), pageWidth - marginLeft, yPos.toFloat(), paint)
        yPos += lineSpacing

        paint.isFakeBoldText = false
        for ((date, client, amount, method) in data.lines) {
            if (yPos > pageHeight - 80) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = 40
            }

            val formattedAmount = amount.toCurrency(noDecimals = true)
            val xDateRow = xDate
            canvas.drawText(date, xDateRow, yPos.toFloat(), paint)
            val clientText = client.take(35)
            val xClientRow = xClient
            canvas.drawText(clientText, xClientRow, yPos.toFloat(), paint)
            val methodText = method.label.uppercase().take(30)
            val xMethodRow = xMethod
            canvas.drawText(methodText, xMethodRow, yPos.toFloat(), paint)
            val xAmountRow = xAmount
            canvas.drawText(formattedAmount, xAmountRow, yPos.toFloat(), paint)

            yPos += lineSpacing
        }


        yPos += lineSpacing
        canvas.drawLine(marginLeft, yPos.toFloat(), pageWidth - marginLeft, yPos.toFloat(), paint)
        yPos += lineSpacing

        paint.isFakeBoldText = true
        val totalPaymentsText = "Total de pagos: ${data.totalCount}"
        val totalAmountsText = "Total recaudado: ${data.totalAmount.toCurrency(noDecimals = true)}"

        val xTotalPayments = pageWidth - rightMargin - paint.measureText(totalPaymentsText)
        val xTotalAmounts = pageWidth - rightMargin - paint.measureText(totalAmountsText)

        canvas.drawText(totalPaymentsText, xTotalPayments, yPos.toFloat(), paint)
        yPos += lineSpacing
        canvas.drawText(totalAmountsText, xTotalAmounts, yPos.toFloat(), paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, fileName)
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }
}
