package com.example.msp_app.core.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.msp_app.features.payments.models.ForgivenessTextData
import com.example.msp_app.features.payments.models.PaymentTextData
import com.example.msp_app.features.payments.models.VisitTextData
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.Locale

object PdfGenerator {

    fun generatePdfFromLines(
        context: Context,
        data: PaymentTextData,
        visits: VisitTextData,
        forgiveness: ForgivenessTextData,
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
        canvas.drawText(title, pageWidth / 2f - 85, yPos.toFloat(), paint)
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
        for ((index, line) in data.lines.withIndex()) {
            val (date, client, amount, method) = line

            if (yPos > pageHeight - 80) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = 40
            }

            if (index % 2 == 0) {
                val backgroundPaint = Paint().apply {
                    color = android.graphics.Color.rgb(220, 220, 220)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(
                    marginLeft,
                    (yPos - 11).toFloat(),
                    (pageWidth - marginLeft),
                    (yPos + 4).toFloat(),
                    backgroundPaint
                )
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
        yPos += lineSpacing

        if (forgiveness.lines.isNotEmpty()) {
            if (yPos > pageHeight - 80) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = 40
            }

            paint.isFakeBoldText = true
            canvas.drawText("Condonaciones", marginLeft, yPos.toFloat(), paint)
            yPos += 20

            paint.isFakeBoldText = false
            for ((index, line) in forgiveness.lines.withIndex()) {
                val (date, client, amount, method) = line

                if (yPos > pageHeight - 80) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPos = 40
                }

                if (index % 2 == 0) {
                    val bgPaint = Paint().apply {
                        color = android.graphics.Color.rgb(220, 220, 220)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(
                        marginLeft,
                        (yPos - 11).toFloat(),
                        (pageWidth - marginLeft),
                        (yPos + 4).toFloat(),
                        bgPaint
                    )
                }

                val formattedAmount = amount.toCurrency(noDecimals = true)
                canvas.drawText(date, xDate, yPos.toFloat(), paint)
                canvas.drawText(client.take(35), xClient, yPos.toFloat(), paint)
                canvas.drawText(method.label.uppercase().take(30), xMethod, yPos.toFloat(), paint)
                canvas.drawText(formattedAmount, xAmount, yPos.toFloat(), paint)

                yPos += lineSpacing
            }
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

        yPos += lineSpacing
        data.breakdownByMethod.forEach { breakdown ->
            if (yPos > pageHeight - 80) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = 40
            }

            val label = "${breakdown.method.label} (${breakdown.count} pagos): ${
                breakdown.amount.toCurrency(noDecimals = true)
            }"
            val xLabel = pageWidth - rightMargin - paint.measureText(label)
            canvas.drawText(label, xLabel, yPos.toFloat(), paint)
            yPos += lineSpacing
        }

        pdfDocument.finishPage(page)

        var visitPageNumber = 2
        var visitPage = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, visitPageNumber).create()
        )
        var visitCanvas = visitPage.canvas
        var y = 40

        paint.isFakeBoldText = true
        visitCanvas.drawText("REPORTE DE VISITAS", pageWidth / 2f - 70, y.toFloat(), paint)
        y += 25
        visitCanvas.drawText("Cobrador: $nameCollector", marginLeft, y.toFloat(), paint)
        y += 20
        visitCanvas.drawText("Total visitas: ${visits.totalCount}", marginLeft, y.toFloat(), paint)
        y += 30

        paint.isFakeBoldText = true
        val hDate = "Fecha/Hora"
        val hType = "Tipo"
        val hNote = "Nota"
        val xVisitDate = marginLeft
        visitCanvas.drawText(hDate, xVisitDate, y.toFloat(), paint)
        val xVisitCollector = xVisitDate + paint.measureText(hDate) + 30f
        val xVisitType = xVisitCollector + 0f
        visitCanvas.drawText(hType, xVisitType, y.toFloat(), paint)
        val xVisitNote = xVisitType + 120f
        visitCanvas.drawText(hNote, xVisitNote, y.toFloat(), paint)

        y += lineSpacing
        visitCanvas.drawLine(marginLeft, y.toFloat(), pageWidth - marginLeft, y.toFloat(), paint)
        y += lineSpacing


        paint.isFakeBoldText = false
        for ((date, collector, type, note) in visits.lines) {
            if (y > pageHeight - 80) {
                pdfDocument.finishPage(visitPage)
                visitPageNumber++
                visitPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, visitPageNumber).create()
                )
                visitCanvas = visitPage.canvas
                y = 40
            }

            visitCanvas.drawText(date, xVisitDate, y.toFloat(), paint)
//            visitCanvas.drawText(collector.take(30), xVisitCollector, y.toFloat(), paint)
            visitCanvas.drawText(type.take(23), xVisitType, y.toFloat(), paint)
            visitCanvas.drawText(note.take(40), xVisitNote, y.toFloat(), paint)
            y += lineSpacing
        }

        pdfDocument.finishPage(visitPage)

        val file = File(context.cacheDir, fileName)
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }
}
