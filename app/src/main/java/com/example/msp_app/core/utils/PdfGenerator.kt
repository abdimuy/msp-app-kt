package com.example.msp_app.core.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.dailyReport.domain.models.DailyReportData
import com.example.msp_app.features.payments.models.ForgivenessTextData
import com.example.msp_app.features.payments.models.PaymentTextData
import com.example.msp_app.features.payments.models.VisitTextData
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

object PdfGenerator {

    fun generatePdfFromLines(
        context: Context,
        data: PaymentTextData,
        visits: VisitTextData,
        forgiveness: ForgivenessTextData,
        title: String,
        nameCollector: String,
        fileName: String,
        snapshotId: String? = null
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
        yPos += 15

        if (snapshotId != null) {
            canvas.drawText("ID: $snapshotId", marginLeft, yPos.toFloat(), paint)
            yPos += 15
        }
        yPos += 15

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
            for ((date, client, amount, method) in forgiveness.lines) {
                if (yPos > pageHeight - 80) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPos = 40
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

    fun generateWarehouseInventoryPdf(
        context: Context,
        warehouseName: String,
        totalStock: Int,
        assignedUsers: List<User>,
        products: List<ProductInventory>,
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

        // Title
        paint.isFakeBoldText = true
        paint.textSize = 14f
        canvas.drawText("INVENTARIO DE ALMACÉN", pageWidth / 2f - 95, yPos.toFloat(), paint)
        yPos += 25

        paint.textSize = 10f

        // Warehouse info
        canvas.drawText("Almacén: $warehouseName", marginLeft, yPos.toFloat(), paint)
        yPos += 20
        canvas.drawText("Stock Total: $totalStock unidades", marginLeft, yPos.toFloat(), paint)
        yPos += 20

        // Vendors
        if (assignedUsers.isNotEmpty()) {
            canvas.drawText("Vendedores Asignados:", marginLeft, yPos.toFloat(), paint)
            yPos += 15
            paint.isFakeBoldText = false
            assignedUsers.forEach { user ->
                canvas.drawText("  - ${user.NOMBRE}", marginLeft + 10, yPos.toFloat(), paint)
                yPos += 15
            }
            paint.isFakeBoldText = true
        } else {
            canvas.drawText("Vendedores: Sin asignar", marginLeft, yPos.toFloat(), paint)
            yPos += 15
        }

        yPos += 10
        val printDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Fecha de generación: $printDate", marginLeft, yPos.toFloat(), paint)
        yPos += 25

        // Table headers
        val headerProduct = "Producto"
        val headerLine = "Línea"
        val headerStock = "Stock"

        val xProduct = marginLeft
        canvas.drawText(headerProduct, xProduct, yPos.toFloat(), paint)
        val xLine = xProduct + 250f
        canvas.drawText(headerLine, xLine, yPos.toFloat(), paint)
        val xStock = pageWidth - rightMargin - paint.measureText("999 unidades")
        canvas.drawText(headerStock, xStock, yPos.toFloat(), paint)

        yPos += lineSpacing
        canvas.drawLine(marginLeft, yPos.toFloat(), pageWidth - marginLeft, yPos.toFloat(), paint)
        yPos += lineSpacing

        // Products
        paint.isFakeBoldText = false
        val sortedProducts = products.sortedBy { it.ARTICULO }

        sortedProducts.forEach { product ->
            if (yPos > pageHeight - 80) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = 40
            }

            val productName = product.ARTICULO.take(40)
            canvas.drawText(productName, xProduct, yPos.toFloat(), paint)

            val lineName = product.LINEA_ARTICULO.take(25)
            canvas.drawText(lineName, xLine, yPos.toFloat(), paint)

            val stockText = "${product.EXISTENCIAS} unidades"
            canvas.drawText(stockText, xStock, yPos.toFloat(), paint)

            yPos += lineSpacing
        }

        yPos += lineSpacing
        canvas.drawLine(marginLeft, yPos.toFloat(), pageWidth - marginLeft, yPos.toFloat(), paint)
        yPos += lineSpacing

        // Summary
        paint.isFakeBoldText = true
        val totalProductsText = "Total de productos: ${products.size}"
        val totalStockText = "Total de unidades: $totalStock"

        val xTotalProducts = pageWidth - rightMargin - paint.measureText(totalProductsText)
        val xTotalStock = pageWidth - rightMargin - paint.measureText(totalStockText)

        canvas.drawText(totalProductsText, xTotalProducts, yPos.toFloat(), paint)
        yPos += lineSpacing
        canvas.drawText(totalStockText, xTotalStock, yPos.toFloat(), paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, fileName)
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }

    fun generateDailyReportPdf(
        context: Context,
        data: DailyReportData,
        fileName: String = "reporte_diario.pdf"
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
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = 40

        fun newPageIfNeeded(requiredSpace: Int = 80): Boolean {
            if (yPos > pageHeight - requiredSpace) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo =
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = 40
                return true
            }
            return false
        }

        fun drawSeparator() {
            canvas.drawLine(
                marginLeft,
                yPos.toFloat(),
                pageWidth - rightMargin,
                yPos.toFloat(),
                paint
            )
            yPos += lineSpacing
        }

        // ===== ENCABEZADO =====
        paint.isFakeBoldText = true
        paint.textSize = 14f
        canvas.drawText(
            "REPORTE DIARIO DE CAMIONETA",
            pageWidth / 2f - 130,
            yPos.toFloat(),
            paint
        )
        yPos += 25

        paint.textSize = 10f
        canvas.drawText("Camioneta: ${data.warehouseName}", marginLeft, yPos.toFloat(), paint)
        yPos += 20
        canvas.drawText("Vendedor: ${data.vendedorName}", marginLeft, yPos.toFloat(), paint)
        yPos += 20
        canvas.drawText("Fecha: ${data.reportDate}", marginLeft, yPos.toFloat(), paint)
        yPos += 15

        val printTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Generado: $printTime", marginLeft, yPos.toFloat(), paint)
        yPos += 25

        drawSeparator()
        yPos += 5

        // ===== SECCION 1: EXISTENCIAS ACTUALES =====
        paint.isFakeBoldText = true
        paint.textSize = 12f
        canvas.drawText("EXISTENCIAS ACTUALES", marginLeft, yPos.toFloat(), paint)
        yPos += 20

        paint.textSize = 10f
        val xProduct = marginLeft
        val xLine = xProduct + 250f
        val xStock = pageWidth - rightMargin - paint.measureText("999 unidades")

        canvas.drawText("Producto", xProduct, yPos.toFloat(), paint)
        canvas.drawText("Linea", xLine, yPos.toFloat(), paint)
        canvas.drawText("Stock", xStock, yPos.toFloat(), paint)
        yPos += lineSpacing
        drawSeparator()

        paint.isFakeBoldText = false
        if (data.currentInventory.isEmpty()) {
            canvas.drawText("Sin existencias", marginLeft, yPos.toFloat(), paint)
            yPos += lineSpacing
        } else {
            data.currentInventory.forEach { item ->
                newPageIfNeeded()
                canvas.drawText(item.name.take(40), xProduct, yPos.toFloat(), paint)
                canvas.drawText(item.line.take(25), xLine, yPos.toFloat(), paint)
                canvas.drawText("${item.stock} uds", xStock, yPos.toFloat(), paint)
                yPos += lineSpacing
            }
        }

        yPos += lineSpacing
        drawSeparator()

        paint.isFakeBoldText = true
        val totalProductsText = "Total productos: ${data.totalInventoryProducts}"
        val totalUnitsText = "Total unidades: ${data.totalInventoryUnits}"
        canvas.drawText(
            totalProductsText,
            pageWidth - rightMargin - paint.measureText(totalProductsText),
            yPos.toFloat(),
            paint
        )
        yPos += lineSpacing
        canvas.drawText(
            totalUnitsText,
            pageWidth - rightMargin - paint.measureText(totalUnitsText),
            yPos.toFloat(),
            paint
        )
        yPos += lineSpacing * 2

        // ===== SECCION 2: VENTAS DEL DIA =====
        newPageIfNeeded(120)
        paint.isFakeBoldText = true
        paint.textSize = 12f
        canvas.drawText("VENTAS DEL DIA", marginLeft, yPos.toFloat(), paint)
        yPos += 20

        paint.textSize = 10f

        if (data.sales.isEmpty()) {
            paint.isFakeBoldText = false
            canvas.drawText("No se registraron ventas hoy", marginLeft, yPos.toFloat(), paint)
            yPos += lineSpacing * 2
        } else {
            data.sales.forEachIndexed { index, sale ->
                newPageIfNeeded(100)

                paint.isFakeBoldText = true
                canvas.drawText(
                    "${index + 1}. ${sale.clientName.take(35)} (${sale.saleType})",
                    marginLeft,
                    yPos.toFloat(),
                    paint
                )
                yPos += lineSpacing + 3

                // Encabezados de productos de la venta
                val xSaleProduct = marginLeft + 15f
                val xSaleQty = xSaleProduct + 220f
                val xSalePrice = xSaleQty + 60f
                val xSaleTotal = pageWidth - rightMargin - paint.measureText("$99,999")

                paint.isFakeBoldText = true
                canvas.drawText("Producto", xSaleProduct, yPos.toFloat(), paint)
                canvas.drawText("Cant", xSaleQty, yPos.toFloat(), paint)
                canvas.drawText("Precio", xSalePrice, yPos.toFloat(), paint)
                canvas.drawText("Total", xSaleTotal, yPos.toFloat(), paint)
                yPos += lineSpacing

                paint.isFakeBoldText = false
                sale.products.forEach { product ->
                    newPageIfNeeded()
                    canvas.drawText(
                        product.name.take(35),
                        xSaleProduct,
                        yPos.toFloat(),
                        paint
                    )
                    canvas.drawText(
                        "${product.quantity}",
                        xSaleQty,
                        yPos.toFloat(),
                        paint
                    )
                    canvas.drawText(
                        product.unitPrice.toCurrency(noDecimals = true),
                        xSalePrice,
                        yPos.toFloat(),
                        paint
                    )
                    canvas.drawText(
                        product.totalPrice.toCurrency(noDecimals = true),
                        xSaleTotal,
                        yPos.toFloat(),
                        paint
                    )
                    yPos += lineSpacing
                }

                // Total de la venta
                paint.isFakeBoldText = true
                val saleTotalText = "Total: ${sale.total.toCurrency(noDecimals = true)}"
                canvas.drawText(
                    saleTotalText,
                    pageWidth - rightMargin - paint.measureText(saleTotalText),
                    yPos.toFloat(),
                    paint
                )
                yPos += lineSpacing + 8
            }

            // Resumen de ventas
            newPageIfNeeded()
            drawSeparator()
            paint.isFakeBoldText = true
            val salesCountText = "Total ventas: ${data.totalSalesCount}"
            val salesAmountText = "Monto total: ${data.totalSalesAmount.toCurrency(
                noDecimals = true
            )}"
            canvas.drawText(
                salesCountText,
                pageWidth - rightMargin - paint.measureText(salesCountText),
                yPos.toFloat(),
                paint
            )
            yPos += lineSpacing
            canvas.drawText(
                salesAmountText,
                pageWidth - rightMargin - paint.measureText(salesAmountText),
                yPos.toFloat(),
                paint
            )
            yPos += lineSpacing * 2
        }

        // ===== SECCION 3: TRASPASOS DEL DIA =====
        newPageIfNeeded(120)
        paint.isFakeBoldText = true
        paint.textSize = 12f
        canvas.drawText("TRASPASOS DEL DIA", marginLeft, yPos.toFloat(), paint)
        yPos += 20

        paint.textSize = 10f

        if (data.transfers.isEmpty()) {
            paint.isFakeBoldText = false
            canvas.drawText(
                "No se registraron traspasos hoy",
                marginLeft,
                yPos.toFloat(),
                paint
            )
            yPos += lineSpacing
        } else {
            val inbound = data.transfers.filter { it.isInbound }
            val outbound = data.transfers.filter { !it.isInbound }

            if (inbound.isNotEmpty()) {
                paint.isFakeBoldText = true
                canvas.drawText("Entradas a la camioneta", marginLeft, yPos.toFloat(), paint)
                yPos += lineSpacing + 3

                inbound.forEachIndexed { index, transfer ->
                    newPageIfNeeded(80)
                    paint.isFakeBoldText = true
                    canvas.drawText(
                        "${index + 1}. ${transfer.originWarehouse} -> ${transfer.destinationWarehouse}",
                        marginLeft + 10f,
                        yPos.toFloat(),
                        paint
                    )
                    yPos += lineSpacing

                    transfer.description?.let { desc ->
                        paint.isFakeBoldText = false
                        canvas.drawText(
                            "  ${desc.take(60)}",
                            marginLeft + 10f,
                            yPos.toFloat(),
                            paint
                        )
                        yPos += lineSpacing
                    }

                    paint.isFakeBoldText = false
                    transfer.products.forEach { product ->
                        newPageIfNeeded()
                        canvas.drawText(
                            "  - ${product.name.take(35)}: ${product.quantity} uds",
                            marginLeft + 20f,
                            yPos.toFloat(),
                            paint
                        )
                        yPos += lineSpacing
                    }
                    yPos += 5
                }
                yPos += lineSpacing
            }

            if (outbound.isNotEmpty()) {
                newPageIfNeeded(80)
                paint.isFakeBoldText = true
                canvas.drawText("Salidas de la camioneta", marginLeft, yPos.toFloat(), paint)
                yPos += lineSpacing + 3

                outbound.forEachIndexed { index, transfer ->
                    newPageIfNeeded(80)
                    paint.isFakeBoldText = true
                    canvas.drawText(
                        "${index + 1}. ${transfer.originWarehouse} -> ${transfer.destinationWarehouse}",
                        marginLeft + 10f,
                        yPos.toFloat(),
                        paint
                    )
                    yPos += lineSpacing

                    transfer.description?.let { desc ->
                        paint.isFakeBoldText = false
                        canvas.drawText(
                            "  ${desc.take(60)}",
                            marginLeft + 10f,
                            yPos.toFloat(),
                            paint
                        )
                        yPos += lineSpacing
                    }

                    paint.isFakeBoldText = false
                    transfer.products.forEach { product ->
                        newPageIfNeeded()
                        canvas.drawText(
                            "  - ${product.name.take(35)}: ${product.quantity} uds",
                            marginLeft + 20f,
                            yPos.toFloat(),
                            paint
                        )
                        yPos += lineSpacing
                    }
                    yPos += 5
                }
            }

            // Resumen traspasos
            newPageIfNeeded()
            drawSeparator()
            paint.isFakeBoldText = true
            val transfersCountText = "Total traspasos: ${data.totalTransfersCount}"
            canvas.drawText(
                transfersCountText,
                pageWidth - rightMargin - paint.measureText(transfersCountText),
                yPos.toFloat(),
                paint
            )
            yPos += lineSpacing
        }

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, fileName)
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }
}
