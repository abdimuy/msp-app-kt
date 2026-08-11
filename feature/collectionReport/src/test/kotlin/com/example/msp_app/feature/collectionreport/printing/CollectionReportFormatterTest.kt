package com.example.msp_app.feature.collectionreport.printing

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.TicketLine
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.ChipUi
import com.example.msp_app.feature.collectionreport.ui.DetailSort
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.TileUi
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura de [CollectionReportFormatter] (pura, fakes-only, sin Robolectric). El ticket es
 * CUSTOMER-FACING, así que se cubre formato exacto: cero / monto grande / multi-pago /
 * condonado, ancho de línea, dinero en peso entero (nunca centavos), y la consistencia
 * [CollectionReportFormatter.toTicketLines] <-> [CollectionReportFormatter.toTicketText].
 */
class CollectionReportFormatterTest {

    private val clock = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))
    private val width = PrinterProfile.PROFILE_58MM.charsPerLine // 32

    // region — invariantes de ancho / estructura ----------------------------------------

    @Test
    fun `ninguna linea del ticket excede el ancho del perfil`() {
        val lines = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock).lines()

        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            assertTrue("línea excede el ancho ($width): '$line'", line.length <= width)
        }
    }

    @Test
    fun `las lineas separadoras son una regla completa del ancho del perfil`() {
        val text = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)

        assertTrue(text.contains("-".repeat(width)))
    }

    @Test
    fun `el encabezado es el titulo del periodo en mayusculas y centrado`() {
        val lines = CollectionReportFormatter.toTicketLines(MockupFixtures.stateDia(), clock)

        val header = lines.first()
        assertTrue(header is TicketLine.Header)
        val text = (header as TicketLine.Header).text
        assertEquals("REPORTE DE COBRANZA DEL DÍA", text.trim())
        assertTrue("el encabezado debe estar centrado", text.startsWith(" "))
    }

    // region — dinero en peso entero ----------------------------------------------------

    @Test
    fun `el total y los desgloses van en peso entero, sin centavos`() {
        val text = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)

        assertTrue(text.contains(formatMoneyMxn(BigDecimal("18300")))) // total
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("12100")))) // efectivo
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("6200")))) // transferencia
        // Nunca centavos en el string de display.
        assertFalse(text.contains(".00"))
    }

    @Test
    fun `el ticket con todo en cero formatea los montos como $0, no vacio ni truncado`() {
        val zero = MockupFixtures.stateDia().let {
            it.copy(
                hero = it.hero.copy(monto = Money.ZERO),
                efectivo = TileUi("Efectivo", Money.ZERO, 0),
                transferencia = TileUi("Transferencia", Money.ZERO, 0),
                condonado = ChipUi("Condonado", amount = Money.ZERO),
                visitas = ChipUi("Visitas", count = 0),
                detail = DetailUi.Payments(emptyList())
            )
        }

        val lines = CollectionReportFormatter.toTicketText(zero, clock).lines()

        assertEquals("$0", formatMoneyMxn(BigDecimal.ZERO))
        assertTrue(lines.any { it.startsWith("Total cobrado") && it.trimEnd().endsWith("\$0") })
        assertTrue(lines.any { it.startsWith("Efectivo (0)") && it.trimEnd().endsWith("\$0") })
        assertTrue(lines.any { it.startsWith("Condonado") && it.trimEnd().endsWith("\$0") })
        assertTrue(lines.any { it.startsWith("Visitas") && it.trimEnd().endsWith("0") })
    }

    @Test
    fun `un monto grande de 8 cifras no se trunca ni se abrevia`() {
        val large = MockupFixtures.stateDia().let {
            it.copy(hero = it.hero.copy(monto = Money.of(BigDecimal("12345678.90"))))
        }

        val text = CollectionReportFormatter.toTicketText(large, clock)
        val expected = formatMoneyMxn(BigDecimal("12345678.90"))

        assertEquals("$12,345,679", expected)
        assertTrue(text.contains(expected))
        assertFalse(text.contains("12.3M"))
    }

    // region — desglose por pago (Día) --------------------------------------------------

    // Task 1: el ticket viejo imprimía DOS líneas por pago (cliente arriba, forma de cobro +
    // monto abajo); ahora es UNA sola línea de 32 chars: "hora cliente" a la izquierda, monto
    // alineado a la derecha, sin la forma de cobro repetida por fila (esa sigue viviendo solo
    // en el bloque de totales, "Efectivo (N)"/"Transferencia (N)").
    @Test
    fun `en Dia cada pago ocupa una sola linea, con su monto exacto y sin forma de cobro por fila`() {
        val text = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)

        // Los 4 pagos EXACTOS de MockupFixtures.paymentsDia(): ninguno se pierde ni se redondea.
        assertTrue(text.contains("09:12 María"))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("1200"))))
        assertTrue(text.contains("09:40 Juan"))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("850"))))
        assertTrue(text.contains("10:05 Rosa"))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("1500"))))
        assertTrue(text.contains("11:20 Pedro"))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("2000"))))
        assertTrue(text.contains("DETALLE DE PAGOS"))
        // "Transfer." solo salía de la etiqueta de método por fila (ya eliminada) — su ausencia
        // confirma que el ticket no repite la forma de cobro pago por pago.
        assertFalse(text.contains("Transfer."))
    }

    @Test
    fun `cada linea de pago mide a lo mas 32 caracteres, el ancho del perfil 58mm`() {
        val text = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)
        val separator = "-".repeat(width)
        val paymentLines = text.substringAfter("DETALLE DE PAGOS")
            .substringBefore(separator)
            .lines()
            .filter { it.isNotBlank() }

        assertEquals(MockupFixtures.paymentsDia().size, paymentLines.size)
        paymentLines.forEach { line -> assertTrue(line.length <= width) }
    }

    @Test
    fun `el condonado se omite cuando el estado no lo trae`() {
        val sinCondonado = MockupFixtures.stateDia().let {
            it.copy(condonado = ChipUi("Condonado", amount = null))
        }

        val lines = CollectionReportFormatter.toTicketText(sinCondonado, clock).lines()

        assertFalse(lines.any { it.startsWith("Condonado") })
    }

    // region — Semana -------------------------------------------------------------------

    @Test
    fun `en Semana el detalle de pagos lista TODOS los pagos del ciclo, no solo los totales`() {
        val text = CollectionReportFormatter.toTicketText(MockupFixtures.stateSemana(), clock)

        assertTrue(text.contains("DETALLE DE PAGOS"))
        // El primer nombre de cada cliente nunca se trunca (maxClient siempre >= 19 chars con
        // el ancho de 58mm y el prefijo `dd/MM`, muy por encima de cualquier primer nombre de
        // la fixture) — no hace falta replicar el cómputo exacto de truncado del formatter
        // para verificar que NINGÚN pago del ciclo se pierde.
        MockupFixtures.dayPaymentsSemana().flatten().forEach { row ->
            val prefix = AppTime.formatForDisplay(row.paidAt, "dd/MM")
            val firstName = row.cliente.substringBefore(" ")
            assertTrue(
                "falta el pago de ${row.cliente} (${row.id})",
                text.contains("$prefix $firstName")
            )
        }
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("118400")))) // total del ciclo
        assertEquals(
            "REPORTE DE COBRANZA DE LA SEMANA",
            CollectionReportFormatter.reportTitle(ReportPeriod.SEMANA).uppercase()
        )
    }

    @Test
    fun `el encabezado de Semana usa el nuevo titulo en mayusculas y centrado`() {
        val lines = CollectionReportFormatter.toTicketLines(MockupFixtures.stateSemana(), clock)

        val header = lines.first()
        assertTrue(header is TicketLine.Header)
        assertEquals("REPORTE DE COBRANZA DE LA SEMANA", (header as TicketLine.Header).text.trim())
    }

    @Test
    fun `el prefijo de cada pago es solo hora en Dia y solo fecha en Semana`() {
        val diaText = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)
        val semanaText = CollectionReportFormatter.toTicketText(MockupFixtures.stateSemana(), clock)

        // Día: "09:12 María..." — sin fecha, porque un solo día no la necesita.
        assertTrue(diaText.contains("09:12 María"))
        assertFalse(diaText.contains("07/08 09:12"))

        // Semana: el ciclo cruza varios días, así que cada pago lleva SOLO la fecha "dd/MM"
        // (Task 1: ya NO la hora — con docenas de pagos por ticket el minuto exacto no aporta,
        // y libera espacio en la línea para el nombre del cliente).
        assertTrue(semanaText.contains("07/08 Lucía"))
        assertFalse(semanaText.contains("07/08 09:05"))
    }

    @Test
    fun `en Semana ninguna linea del ticket excede el ancho del perfil`() {
        val lines = CollectionReportFormatter
            .toTicketText(MockupFixtures.stateSemana(), clock)
            .lines()

        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            assertTrue("línea excede el ancho ($width): '$line'", line.length <= width)
        }
    }

    // region — desglose de visitas (Día y Semana) ----------------------------------------

    @Test
    fun `el ticket lista el detalle de visitas con nombre y nota en Dia y Semana`() {
        val diaText = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)
        val semanaText = CollectionReportFormatter.toTicketText(MockupFixtures.stateSemana(), clock)

        listOf(diaText, semanaText).forEach { text ->
            assertTrue(text.contains("DETALLE DE VISITAS"))
            assertTrue(text.contains("Carlos Vega"))
            assertTrue(text.contains("No estaba"))
            assertTrue(text.contains("Sofía Luna"))
            assertTrue(text.contains("Promesa de pago"))
            assertTrue(text.contains("Diego Mora"))
        }
    }

    // Task 2: antes el ticket solo mostraba cliente + nota, sin el TIPO de visita elegido al
    // capturarla (ni hora/fecha) — ahora cada visita lleva su propia línea de prefijo
    // (hora en Día, fecha en Semana) + cliente, el TIPO en su propia línea, y la nota COMPLETA
    // envuelta debajo (las visitas son la excepción a la regla de una línea por fila).
    @Test
    fun `el ticket muestra hora-fecha, TIPO y la nota completa de cada visita en Dia y Semana`() {
        val diaText = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)
        val semanaText = CollectionReportFormatter.toTicketText(MockupFixtures.stateSemana(), clock)

        // Día: prefijo = hora exacta de la visita.
        assertTrue(diaText.contains("14:15 Carlos Vega"))
        assertTrue(diaText.contains("16:40 Sofía Luna"))
        assertTrue(diaText.contains("17:05 Diego Mora"))

        // Semana: prefijo = solo fecha (mismo lenguaje que los pagos, Task 1/4).
        assertTrue(semanaText.contains("07/08 Carlos Vega"))
        assertTrue(semanaText.contains("07/08 Sofía Luna"))
        assertTrue(semanaText.contains("07/08 Diego Mora"))

        // El TIPO (motivo/resultado elegido al capturar la visita) y la nota completa aparecen
        // en AMBOS periodos, sin truncar.
        listOf(diaText, semanaText).forEach { text ->
            assertTrue(text.contains("No se encontraba"))
            assertTrue(text.contains("No estaba — dejé recado"))
            assertTrue(text.contains("Pidió que regrese otro día"))
            assertTrue(text.contains("Promesa de pago mañana"))
            assertTrue(text.contains("Fue grosero o agresivo"))
            assertTrue(text.contains("Cliente inconforme"))
        }
    }

    @Test
    fun `el detalle de visitas se omite entero cuando el estado no trae visitas`() {
        val sinVisitas = MockupFixtures.stateDia().copy(visitRows = emptyList())

        val text = CollectionReportFormatter.toTicketText(sinVisitas, clock)

        assertFalse(text.contains("DETALLE DE VISITAS"))
    }

    // region — orden del detalle impreso sigue el toggle (Task 4) -----------------------

    // El toggle Hora/Fecha·Nombre del tablero (`DetailHeader`) ahora también aparece en
    // Semana (antes solo existía en Día) y el ticket impreso lo honra en AMBOS periodos —
    // Día ya lo hacía indirectamente (el `detail.rows` que llega ya viene ordenado por
    // `CollectionReportStateBuilder`); este test cubre el caso nuevo: Semana con NOMBRE
    // imprime los pagos del ciclo COMPLETO en orden alfabético por cliente, sin importar el
    // día al que pertenecen.
    @Test
    fun `en Semana con orden NOMBRE el ticket lista los pagos del ciclo en orden alfabetico`() {
        val state = MockupFixtures.stateSemana().copy(sort = DetailSort.NOMBRE)
        val separator = "-".repeat(width)

        val text = CollectionReportFormatter.toTicketText(state, clock)
        val paymentLines = text.substringAfter("DETALLE DE PAGOS")
            .substringBefore(separator)
            .lines()
            .filter { it.isNotBlank() }

        val expectedOrder = MockupFixtures.dayPaymentsSemana().flatten()
            .sortedBy { it.cliente.lowercase() }
            .map { it.cliente.substringBefore(" ") }

        assertEquals(expectedOrder.size, paymentLines.size)
        expectedOrder.forEachIndexed { index, firstName ->
            val clientPart = paymentLines[index].removePrefix("07/08").trim()
            assertTrue(
                "línea $index debía empezar con $firstName: '${paymentLines[index]}'",
                clientPart.startsWith(firstName)
            )
        }
    }

    // region — consistencia lines <-> text ----------------------------------------------

    @Test
    fun `toTicketText es el render textual exacto de toTicketLines`() {
        val state = MockupFixtures.stateDia()

        val lines = CollectionReportFormatter.toTicketLines(state, clock)
        val text = CollectionReportFormatter.toTicketText(state, clock)

        // Mismo número de líneas y separadores expandidos por el renderer del módulo.
        assertEquals(lines.size, text.lines().size)
        assertTrue(text.lines().first().trim().startsWith("REPORTE"))
        assertTrue(text.trimEnd().lines().last().contains("Generado"))
    }
}
