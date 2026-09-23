package com.example.msp_app.feature.pagos.printing

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.printing.adapters.foldToPrintableAscii
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.application.TicketRenderer
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.feature.pagos.ui.TicketFixtures
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El contenido del papel: qué dice, en cuántas columnas, y con qué dinero.
 *
 * Los dos hechos que estas pruebas existen para fijar:
 *  - **el ticket cabe en 32 columnas** una vez plegado a ASCII, y
 *  - **el dinero se imprime en peso entero** aunque el modelo traiga centavos.
 */
class TicketDePagoFormatterTest {

    private val ancho = PrinterProfile.PROFILE_58MM.charsPerLine

    private fun texto(permiso: PrintPermission = PrintPermission.PrimeraImpresion): String =
        TicketDePagoFormatter.toTicketText(TicketFixtures.ticket(), permiso)

    @Test
    fun `la primera copia NO lleva marca de reimpresion`() {
        assertFalse(texto().contains("REIMPRESION"))
    }

    @Test
    fun `la copia lleva la marca, el numero de copia y la hora de la primera`() {
        val papel = texto(
            PrintPermission.Reimpresion(previas = 1, primeraVez = TicketFixtures.PRIMERA_COPIA)
        )

        assertTrue(papel.contains("*** REIMPRESION ***"))
        // Segunda copia; la primera salió a las 12:05 CDMX.
        assertTrue(papel.contains("copia 2 - primera 12:05"))
    }

    @Test
    fun `una copia con hora desconocida sigue llevando la marca`() {
        // El registro conservó el conteo y perdió la fecha (prefs corruptas).
        // El papel dice QUE es copia y CUÁL, que es lo que la hace detectable;
        // perder el conteo para no perder la hora sería exactamente al revés.
        val papel = texto(PrintPermission.Reimpresion(previas = 1, primeraVez = null))

        assertTrue(papel.contains("*** REIMPRESION ***"))
        assertTrue(papel.contains("copia 2"))
        assertFalse(papel.contains("primera"))
    }

    @Test
    fun `la tercera copia dice copia 3`() {
        val papel = texto(
            PrintPermission.Reimpresion(previas = 2, primeraVez = TicketFixtures.PRIMERA_COPIA)
        )

        assertTrue(papel.contains("copia 3 - primera 12:05"))
    }

    @Test
    fun `fuera del dia no estampa marca alguna`() {
        // El formatter no decide si se imprime — eso es de `PrintDayRule` y del
        // ViewModel. Aquí solo se comprueba que no inventa una banda.
        assertFalse(texto(PrintPermission.FueraDelDia).contains("REIMPRESION"))
    }

    @Test
    fun `ninguna linea excede el ancho del rollo despues del fold a ASCII`() {
        val lineas = texto(
            PrintPermission.Reimpresion(previas = 1, primeraVez = TicketFixtures.PRIMERA_COPIA)
        ).lines()

        // Control positivo: el papel tiene contenido, así que la ausencia de
        // líneas largas significa algo.
        assertTrue(lineas.size > MINIMO_DE_LINEAS)
        lineas.forEach { linea ->
            assertTrue(
                "linea de ${linea.length} columnas: $linea",
                foldToPrintableAscii(linea).length <= ancho
            )
        }
    }

    @Test
    fun `el dinero se imprime en peso entero aunque el modelo traiga centavos`() {
        val conCentavos = TicketFixtures.ticket(importe = Money.of(BigDecimal("350.49")))

        val papel = TicketDePagoFormatter.toTicketText(
            conCentavos,
            PrintPermission.PrimeraImpresion
        )

        assertTrue(papel.contains("$350"))
        // Ni un centavo llega al papel; el modelo, en cambio, los conserva.
        assertFalse(papel.contains("350.49"))
        assertEquals(BigDecimal("350.49"), conCentavos.importe.amount)
    }

    @Test
    fun `el medio peso sube, igual que en el tablero`() {
        val papel = TicketDePagoFormatter.toTicketText(
            TicketFixtures.ticket(importe = Money.of(BigDecimal("350.50"))),
            PrintPermission.PrimeraImpresion
        )

        assertTrue(papel.contains("$351"))
    }

    @Test
    fun `el bloque de ultimos pagos se omite entero cuando es el primer abono`() {
        val primerAbono = TicketFixtures.ticket(ultimosPagos = emptyList())

        val papel = TicketDePagoFormatter.toTicketText(
            primerAbono,
            PrintPermission.PrimeraImpresion
        )

        assertFalse(papel.contains("ULTIMOS PAGOS"))
        // Control positivo: con pagos previos el rótulo SÍ aparece, así que la
        // ausencia de arriba no es un rótulo que nunca se imprime.
        assertTrue(texto().contains("ULTIMOS PAGOS"))
    }

    @Test
    fun `el abono, el saldo y quien cobro estan en el papel`() {
        val papel = texto()

        assertTrue(papel.contains("ABONO"))
        assertTrue(papel.contains("$350"))
        assertTrue(papel.contains("Saldo anterior"))
        assertTrue(papel.contains("$1,800"))
        assertTrue(papel.contains("Saldo actual"))
        assertTrue(papel.contains("$1,450"))
        assertTrue(papel.contains("Martín Salgado"))
        assertTrue(papel.contains("V-5188"))
    }

    @Test
    fun `el importe queda pegado al borde derecho`() {
        val renglon = texto().lines().first { it.startsWith("ABONO") }

        assertEquals(ancho, renglon.length)
        assertTrue(renglon.endsWith("$350"))
    }

    @Test
    fun `el ticket semantico y el texto plano son el MISMO contenido`() {
        val lineas = TicketDePagoFormatter.toTicketLines(
            TicketFixtures.ticket(),
            PrintPermission.PrimeraImpresion
        )

        assertEquals(
            TicketRenderer.render(lineas, PrinterProfile.PROFILE_58MM).joinToString("\n"),
            texto()
        )
    }

    private companion object {
        const val MINIMO_DE_LINEAS = 20
    }
}
