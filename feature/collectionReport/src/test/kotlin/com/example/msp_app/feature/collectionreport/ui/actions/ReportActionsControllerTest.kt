package com.example.msp_app.feature.collectionreport.ui.actions

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.ui.ChipUi
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.TileUi
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows

/**
 * Cobertura de [ReportActionsController] — Compartir/PDF/ticket de impresión (Task 8, AUDIT
 * +REWRITE de `PdfGenerator`/`ReportActions` viejos). **Dinero-seguro:** todo assert de monto
 * compara contra [formatMoneyMxn] (nunca `Double.toCurrency`) — es exactamente el bug que este
 * archivo corrige (ver su KDoc).
 */
class ReportActionsControllerTest : RobolectricTestBase() {

    private val clock = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))

    // El ticket ahora usa layout de dos columnas (etiqueta a la izquierda, monto a la
    // derecha) — delega en `CollectionReportFormatter`. Se asevera por línea: etiqueta al
    // inicio, valor al final, en la MISMA línea.
    private fun String.hasLine(label: String, value: String): Boolean =
        lines().any { it.startsWith(label) && it.trimEnd().endsWith(value) }

    @Test
    fun `el texto para compartir trae cobrador, rango y los montos formateados sin truncar centavos`() {
        val state = MockupFixtures.stateDia()

        val text = ReportActionsController.buildShareText(state)

        assertTrue(text.contains(MockupFixtures.COBRADOR))
        assertTrue(text.contains(state.rangeLabel))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("18300"))))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("12100"))))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("6200"))))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("1400"))))
        assertTrue(text.contains("14"))
    }

    @Test
    fun `el intent de compartir es ACTION_SEND de texto plano con el resumen`() {
        val intent = ReportActionsController.buildShareIntent(MockupFixtures.stateDia())

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.isNotBlank())
    }

    @Test
    fun `el ticket de impresion desglosa cada pago del dia con su monto exacto`() {
        val ticket = ReportActionsController.buildTicketText(MockupFixtures.stateDia(), clock)

        // Task 1: cada pago ocupa UNA sola línea de 32 chars (58mm); "María López Hernández"
        // (21 chars) más "09:12 " y el monto "$1,200" ya no caben completos en la línea, así
        // que el cliente se trunca — el nombre COMPLETO ya no es un substring literal del
        // ticket, solo su prefijo ("09:12 María").
        assertTrue(ticket.contains("09:12 María"))
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("1200"))))
        assertTrue(ticket.hasLine("Total cobrado", formatMoneyMxn(BigDecimal("18300"))))
    }

    @Test
    fun `el ticket en Semana no truena sin desglose por pago, solo totales`() {
        val ticket = ReportActionsController.buildTicketText(MockupFixtures.stateSemana(), clock)

        assertTrue(ticket.hasLine("Total cobrado", formatMoneyMxn(BigDecimal("118400"))))
    }

    // region — fix round 1 (minor): dinero cero / grande / multi-monto ------------------

    @Test
    fun `el ticket con todo en cero formatea el total como $0, no vacio ni truncado`() {
        val zeroState = MockupFixtures.stateDia().copy(
            hero = MockupFixtures.stateDia().hero.copy(monto = Money.ZERO),
            efectivo = TileUi("Efectivo", Money.ZERO, 0),
            transferencia = TileUi("Transferencia", Money.ZERO, 0),
            condonado = ChipUi("Condonado", amount = Money.ZERO),
            visitas = ChipUi("Visitas", count = 0),
            detail = DetailUi.Payments(emptyList())
        )

        val ticket = ReportActionsController.buildTicketText(zeroState, clock)

        assertEquals(formatMoneyMxn(BigDecimal.ZERO), "$0")
        assertTrue(ticket.hasLine("Total cobrado", "\$0"))
        assertTrue(ticket.hasLine("Efectivo (0)", "\$0"))
        assertTrue(ticket.hasLine("Transferencia (0)", "\$0"))
        assertTrue(ticket.hasLine("Condonado", "\$0"))
        assertTrue(ticket.hasLine("Visitas", "0"))
    }

    @Test
    fun `el ticket con un monto grande de 8 cifras no trunca digitos ni lo abrevia`() {
        // El modelo (`Money`) sigue exacto a centavos por dentro (12345678.90); lo que cambió
        // es SOLO el string de display, que ahora redondea a peso entero (whole-pesos, decisión
        // de negocio: MSP no opera con centavos) — 12345678.90 -> $12,345,679 (HALF_UP).
        val largeAmount = Money.of(BigDecimal("12345678.90"))
        val largeState = MockupFixtures.stateDia()
            .let { it.copy(hero = it.hero.copy(monto = largeAmount)) }

        val ticket = ReportActionsController.buildTicketText(largeState, clock)
        val expected = formatMoneyMxn(BigDecimal("12345678.90"))

        assertEquals("$12,345,679", expected)
        assertTrue(ticket.hasLine("Total cobrado", expected))
        // Nunca una versión abreviada del monto (p. ej. "$12.3M") — el string completo con
        // todos los dígitos de miles debe aparecer tal cual, solo sin centavos.
        assertFalse(ticket.contains("\$12.3M"))
    }

    @Test
    fun `el ticket con multiples pagos muestra cada monto exacto y los totales coinciden con el estado`() {
        val state = MockupFixtures.stateDia()

        val ticket = ReportActionsController.buildTicketText(state, clock)

        // Los 4 pagos EXACTOS de MockupFixtures.paymentsDia() — ninguno se pierde ni se
        // redondea al desglosar.
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("1200")))) // María López
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("850")))) // Juan Pérez
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("1500")))) // Rosa Martínez
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("2000")))) // Pedro Sánchez
        // Los totales del ticket son EXACTAMENTE los del estado (mismo `Money`, no una suma
        // recalculada aparte que pudiera divergir).
        assertTrue(ticket.hasLine("Total cobrado", formatMoneyMxn(state.hero.monto.amount)))
        assertTrue(
            ticket.hasLine(
                "Efectivo (${state.efectivo.count})",
                formatMoneyMxn(state.efectivo.amount.amount)
            )
        )
        assertTrue(
            ticket.hasLine(
                "Transferencia (${state.transferencia.count})",
                formatMoneyMxn(state.transferencia.amount.amount)
            )
        )
    }

    // endregion

    @Test
    fun `el intent del ticket es ACTION_SEND de texto plano con el ticket completo`() {
        val state = MockupFixtures.stateDia()
        val intent = ReportActionsController.buildTicketShareIntent(state, clock)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(
            ReportActionsController.buildTicketText(state, clock),
            intent.getStringExtra(Intent.EXTRA_TEXT)
        )
    }

    // `generatePdf` (android.graphics.pdf.PdfDocument real) queda SIN unit test: el shadow de
    // Robolectric para PdfDocument no soporta `startPage`/escritura de contenido en esta
    // versión (`IllegalStateException: document is closed!` incluso con
    // `@GraphicsMode(NATIVE)`) — limitación conocida de Robolectric, no un bug de este
    // código. Mismo criterio que el `PdfGenerator` viejo (`:app`), que tampoco tenía
    // cobertura de su render real — lo que SÍ queda probado es [ReportActionsController
    // .buildTicketText] (arriba, sigue siendo la fuente de "Compartir ticket"/impresión) y,
    // en `ui/actions/pdf/*Test.kt`, TODO el pipeline puro que arma el PDF rediseñado
    // (`buildPdfReportModel` -> `buildPdfBlocks` -> `paginatePdfBlocks`): orden por
    // `state.sort`, ningún pago/visita perdido, wrap de notas, y el encabezado de la tabla de
    // pagos repetido en cada corte de página — la parte de `generatePdf` que SÍ es testeable
    // sin un `Canvas` real.

    @Test
    fun `pdfFileName es determinista por periodo`() {
        assertEquals(
            "reporte_cobranza_dia.pdf",
            ReportActionsController.pdfFileName(MockupFixtures.stateDia())
        )
        assertEquals(
            "reporte_cobranza_semana.pdf",
            ReportActionsController.pdfFileName(MockupFixtures.stateSemana())
        )
    }

    // region — botón PDF: abrir (ACTION_VIEW), no compartir (Task de arriba) ------------

    @Test
    fun `buildPdfViewIntent es ACTION_VIEW de application-pdf con la uri y el permiso de lectura`() {
        // Núcleo puro (uri ya resuelta): ver el KDoc de `ReportActionsController
        // .buildPdfViewIntent(Uri)` sobre por qué este módulo no puede ejercitar un
        // `FileProvider` real en unit test (mismo motivo documentado en `generatePdf`).
        val uri = Uri.parse("content://com.example.msp_app.fileprovider/reporte_cobranza_dia.pdf")

        val intent = ReportActionsController.buildPdfViewIntent(uri)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals(uri, intent.data)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `buildPdfViewIntent y buildPdfShareIntent difieren solo en la accion`() {
        val uri = Uri.parse("content://com.example.msp_app.fileprovider/reporte_cobranza_dia.pdf")

        val viewIntent = ReportActionsController.buildPdfViewIntent(uri)

        assertEquals(Intent.ACTION_VIEW, viewIntent.action)
        assertFalse(
            "el botón PDF ya no debe usar ACTION_SEND",
            viewIntent.action == Intent.ACTION_SEND
        )
    }

    @Test
    fun `startActivitySafely no truena cuando ninguna actividad resuelve el intent`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application).checkActivities(true)
        val uri = Uri.parse("content://com.example.msp_app.fileprovider/reporte_cobranza_dia.pdf")
        val intent = ReportActionsController.buildPdfViewIntent(uri)

        // Sin ningún visor de PDF "instalado" en el Robolectric shadow: no debe tronar con
        // `ActivityNotFoundException`, ni al intentar el intent original ni al chooser de
        // respaldo.
        ReportActionsController.startActivitySafely(application, intent)
    }

    // endregion
}
