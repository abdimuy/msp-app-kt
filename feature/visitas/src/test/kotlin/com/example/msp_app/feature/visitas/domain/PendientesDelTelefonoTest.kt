package com.example.msp_app.feature.visitas.domain

import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * E-APP-096: la regla de N del aviso "N sin enviar · no cambies de teléfono".
 * N = visitas + fotos de visita + pagos + ventas locales sin enviar; sin
 * pendientes no hay aviso.
 */
class PendientesDelTelefonoTest {

    @Test
    fun `E-APP-096 sin pendientes no hay aviso`() {
        assertEquals(0, PendientesDelTelefono().total)
        assertNull(PendientesDelTelefono().textoDelAviso())
    }

    @Test
    fun `E-APP-096 cada fuente suma sola`() {
        assertEquals(1, PendientesDelTelefono(visitas = 1).total)
        assertEquals(1, PendientesDelTelefono(fotos = 1).total)
        assertEquals(1, PendientesDelTelefono(pagos = 1).total)
        assertEquals(1, PendientesDelTelefono(ventas = 1).total)
    }

    @Test
    fun `E-APP-096 N es la suma de las cuatro fuentes`() {
        val pendientes = PendientesDelTelefono(visitas = 2, fotos = 3, pagos = 5, ventas = 7)

        assertEquals(17, pendientes.total)
    }

    @Test
    fun `E-APP-096 el texto es exacto`() {
        assertEquals(
            "1 sin enviar · no cambies de teléfono",
            PendientesDelTelefono(fotos = 1).textoDelAviso()
        )
        assertEquals(
            "17 sin enviar · no cambies de teléfono",
            PendientesDelTelefono(visitas = 2, fotos = 3, pagos = 5, ventas = 7).textoDelAviso()
        )
    }

    @Test
    fun `E-APP-096 un conteo negativo no inventa aviso ni lo apaga`() {
        // Un COUNT(*) no da negativos; si alguno llegara, se trata como cero en
        // vez de restarle a los demás y esconder pendientes reales.
        assertEquals(2, PendientesDelTelefono(visitas = -5, fotos = 2).total)
        assertNull(PendientesDelTelefono(visitas = -1).textoDelAviso())
    }
}
