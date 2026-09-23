package com.example.msp_app.core.common.cobranza.domain

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El periodo de cobro y su predicado de pertenencia. */
class VentanaCobroTest {

    private val lunes = Instant.parse("2026-08-31T06:00:00Z")

    @Test
    fun `la ventana abre en FECHA_CARGA_INICIAL y cierra en el ahora del reloj`() {
        val clock = FakeClock(Instant.parse("2026-09-03T18:00:00Z"))
        val ventana = VentanaCobro.desdeInicioSemana(lunes, clock)
        assertEquals(lunes, ventana.inicio)
        assertEquals(clock.now(), ventana.fin)
    }

    @Test
    fun `contiene es inclusivo en los dos extremos, igual que el filtro del servidor`() {
        val fin = Instant.parse("2026-09-03T18:00:00Z")
        val ventana = VentanaCobro(lunes, fin)
        assertTrue(ventana.contiene(lunes))
        assertTrue(ventana.contiene(fin))
        assertTrue(ventana.contiene(Instant.parse("2026-09-01T00:00:00Z")))
        assertFalse(ventana.contiene(lunes.minusMillis(1)))
        assertFalse(ventana.contiene(fin.plusMillis(1)))
    }

    @Test
    fun `una ventana de ancho cero contiene su unico instante`() {
        val ventana = VentanaCobro(lunes, lunes)
        assertTrue(ventana.contiene(lunes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una ventana invertida truena de inmediato en vez de devolver vacio en silencio`() {
        VentanaCobro(inicio = lunes, fin = lunes.minusSeconds(1))
    }

    @Test
    fun `las formas de cobro son efectivo, cheque y transferencia - la condonacion no`() {
        assertEquals(setOf(157, 158, 52569), VentanaCobro.FORMAS_COBRO_COBRANZA)
        assertFalse(137026 in VentanaCobro.FORMAS_COBRO_COBRANZA)
    }
}
