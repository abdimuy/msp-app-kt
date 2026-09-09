package com.example.msp_app.core.database.dao.sale

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * El puente del enum legado de 5 valores al catálogo de ocho. Si este mapeo se
 * mueve, el histórico y el orden de la lista cambian de significado.
 */
class EstadoCobranzaEstadoCuentaTest {

    @Test
    fun `EstadoCobranza sigue teniendo exactamente 5 valores`() {
        assertEquals(5, EstadoCobranza.entries.size)
    }

    @Test
    fun `los 5 valores mapean a un estado, ninguno queda sin traduccion`() {
        EstadoCobranza.entries.forEach { legado ->
            assertNotNull("$legado se quedo sin estado", legado.aEstadoCuenta())
        }
    }

    @Test
    fun `PAGADO mapea a Pago`() {
        assertEquals(EstadoCuenta.PAGO, EstadoCobranza.PAGADO.aEstadoCuenta())
    }

    @Test
    fun `NO_PAGADO mapea a Se nego`() {
        assertEquals(EstadoCuenta.SE_NEGO, EstadoCobranza.NO_PAGADO.aEstadoCuenta())
    }

    @Test
    fun `VOLVER_VISITAR mapea a Visite vuelvo`() {
        assertEquals(EstadoCuenta.VISITE_VUELVO, EstadoCobranza.VOLVER_VISITAR.aEstadoCuenta())
    }

    @Test
    fun `VISITADO mapea a Visite vuelvo`() {
        assertEquals(EstadoCuenta.VISITE_VUELVO, EstadoCobranza.VISITADO.aEstadoCuenta())
    }

    @Test
    fun `PENDIENTE mapea a Sin tocar`() {
        assertEquals(EstadoCuenta.SIN_TOCAR, EstadoCobranza.PENDIENTE.aEstadoCuenta())
    }

    @Test
    fun `el mapeo conserva la particion que usaba SalesScreen - pagado a un lado, el resto al otro`() {
        val pagados = EstadoCobranza.entries.filter { it.aEstadoCuenta() == EstadoCuenta.PAGO }
        assertEquals(listOf(EstadoCobranza.PAGADO), pagados)
    }
}
