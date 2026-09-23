package com.example.msp_app.core.printing.application

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.printing.domain.PrintRecord
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * *El ticket solo se imprime el día del cobro*, con **los tres bordes exactos**:
 * el día mismo, el día ANTERIOR y el día SIGUIENTE.
 *
 * El día es el de negocio (`America/Mexico_City`), no una ventana de 24 horas ni
 * el día UTC — de ahí los dos casos de medianoche, que son los que separan una
 * regla correcta de una que "casi" funciona.
 *
 * El reloj es siempre [FakeClock] a través de `AppTime`; no hay un solo
 * `Instant.now()` en la regla.
 */
class PrintDayRuleTest {

    // 2026-09-04, 10:00 hora de CDMX (UTC-6) = 16:00Z. Día del cobro.
    private val cobradoEn = Instant.parse("2026-09-04T16:00:00Z")

    @Test
    fun `el dia del cobro, sin impresiones previas, es primera impresion`() {
        val reloj = FakeClock(Instant.parse("2026-09-04T22:00:00Z")) // mismo día CDMX

        val permiso = PrintDayRule.evaluar(cobradoEn, reloj.now(), registro = null)

        assertEquals(PrintPermission.PrimeraImpresion, permiso)
    }

    @Test
    fun `el dia SIGUIENTE al cobro no imprime`() {
        val reloj = FakeClock(cobradoEn)
        reloj.advanceDays(1)

        val permiso = PrintDayRule.evaluar(cobradoEn, reloj.now(), registro = null)

        assertEquals(PrintPermission.FueraDelDia, permiso)
    }

    @Test
    fun `el dia ANTERIOR al cobro tampoco imprime`() {
        val reloj = FakeClock(cobradoEn)
        reloj.advanceDays(-1)

        val permiso = PrintDayRule.evaluar(cobradoEn, reloj.now(), registro = null)

        assertEquals(PrintPermission.FueraDelDia, permiso)
    }

    @Test
    fun `un minuto antes de la medianoche de negocio todavia imprime`() {
        // 23:59 CDMX del día del cobro = 05:59Z del día siguiente en UTC.
        val casiMedianoche = Instant.parse("2026-09-05T05:59:00Z")
        // Control positivo del borde: ese instante NO es el mismo día en UTC,
        // así que un test que pasara por comparar días UTC estaría mintiendo.
        assertNotEquals(
            cobradoEn.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
            casiMedianoche.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        )
        assertEquals(
            AppTime.toBusinessDate(cobradoEn),
            AppTime.toBusinessDate(casiMedianoche)
        )

        val permiso = PrintDayRule.evaluar(cobradoEn, casiMedianoche, registro = null)

        assertEquals(PrintPermission.PrimeraImpresion, permiso)
    }

    @Test
    fun `un minuto despues de la medianoche de negocio ya no imprime`() {
        // 00:01 CDMX del día siguiente = 06:01Z.
        val pasadaLaMedianoche = Instant.parse("2026-09-05T06:01:00Z")

        val permiso = PrintDayRule.evaluar(cobradoEn, pasadaLaMedianoche, registro = null)

        assertEquals(PrintPermission.FueraDelDia, permiso)
    }

    @Test
    fun `un cobro a las 23 50 y un intento a las 00 10 son dias distintos`() {
        val cobroTardio = Instant.parse("2026-09-05T05:50:00Z") // 23:50 CDMX del 4
        val intento = Instant.parse("2026-09-05T06:10:00Z") // 00:10 CDMX del 5

        // Veinte minutos después, pero otro día de negocio: la regla es del día,
        // no de una ventana de horas.
        assertEquals(PrintPermission.FueraDelDia, PrintDayRule.evaluar(cobroTardio, intento, null))
    }

    @Test
    fun `con una impresion previa el mismo dia es reimpresion, con su primera vez`() {
        val primeraVez = Instant.parse("2026-09-04T17:00:00Z")
        val registro = PrintRecord(ticketId = "abono-1", prints = 1, firstPrintedAt = primeraVez)

        val permiso = PrintDayRule.evaluar(
            cobradoEn,
            Instant.parse("2026-09-04T18:00:00Z"),
            registro
        )

        assertEquals(PrintPermission.Reimpresion(previas = 1, primeraVez = primeraVez), permiso)
    }

    @Test
    fun `la tercera copia sigue siendo reimpresion y conserva la primera vez`() {
        val primeraVez = Instant.parse("2026-09-04T17:00:00Z")
        val registro = PrintRecord(ticketId = "abono-1", prints = 2, firstPrintedAt = primeraVez)

        val permiso = PrintDayRule.evaluar(
            cobradoEn,
            Instant.parse("2026-09-04T19:00:00Z"),
            registro
        )

        assertEquals(PrintPermission.Reimpresion(previas = 2, primeraVez = primeraVez), permiso)
    }

    @Test
    fun `fuera del dia manda sobre el registro y ni la primera copia sale`() {
        val registro = PrintRecord(
            ticketId = "abono-1",
            prints = 1,
            firstPrintedAt = Instant.parse("2026-09-04T17:00:00Z")
        )
        val manana = Instant.parse("2026-09-05T16:00:00Z")

        assertEquals(PrintPermission.FueraDelDia, PrintDayRule.evaluar(cobradoEn, manana, registro))
    }

    @Test
    fun `sePuedeImprimir concuerda con evaluar en los tres bordes`() {
        val ayer = Instant.parse("2026-09-03T16:00:00Z")
        val manana = Instant.parse("2026-09-05T16:00:00Z")

        assertTrue(PrintDayRule.sePuedeImprimir(cobradoEn, cobradoEn))
        assertFalse(PrintDayRule.sePuedeImprimir(cobradoEn, ayer))
        assertFalse(PrintDayRule.sePuedeImprimir(cobradoEn, manana))
    }
}
