package com.example.msp_app.feature.pagos.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **El dock del cliente apunta a una CUENTA, no a una persona.**
 *
 * El defecto que esto cierra: `DetalleClienteScreen` le pasaba el `CLIENTE_ID` a
 * `onRegistrarAbono`, cuyo destino es `pagos/abono/{ventaId}`. O sea que el id
 * de una persona aterrizaba en el lugar de un `DOCTO_CC_ACR_ID` y la **pantalla
 * del dinero** abría una venta que no era la de ese cliente, o ninguna. Es la
 * misma familia de defecto que el commit `721c5551` (dos espacios de id
 * confundidos), pero del lado del cobro.
 *
 * La cuenta elegida es la **primera de "sus ventas"**: la fila de arriba de la
 * lista que el cobrador tiene enfrente —no una elección escondida—, y la misma
 * que `CargarDetalleCliente` ya usa como representante del cliente. La pantalla
 * del abono encabeza con folio, producto y saldo de esa venta, así que un
 * cliente con dos cuentas ve cuál es antes de teclear un peso.
 */
class CuentaQueEncabezaTest {

    private fun estado(detalle: com.example.msp_app.feature.pagos.domain.model.DetalleCliente?) =
        DetalleClienteUiState(cargando = false, detalle = detalle)

    @Test
    fun `apunta a la primera venta del cliente`() {
        val detalle = PagosFixtures.detalleCliente()
        assertEquals(PagosFixtures.VENTA_PAGADA, cuentaQueEncabeza(estado(detalle)))
    }

    /**
     * **El control que nombra el defecto.** El id que sale NO es el del cliente.
     * Sin esta afirmación, un fixture donde `CLIENTE_ID` y `VENTA_PAGADA`
     * coincidieran dejaría pasar exactamente el bug de antes.
     */
    @Test
    fun `lo que sale NO es el id del cliente`() {
        val detalle = PagosFixtures.detalleCliente()
        assertNotEquals(PagosFixtures.CLIENTE_ID, cuentaQueEncabeza(estado(detalle)))
        // Y los dos números son distintos de verdad en el fixture: si fueran
        // iguales, la afirmación de arriba no probaría nada.
        assertNotEquals(PagosFixtures.CLIENTE_ID, PagosFixtures.VENTA_PAGADA)
    }

    /**
     * Con dos cuentas, la elegida es la de arriba y **no** la segunda: el orden
     * de "sus ventas" es el que decide, no el azar de la consulta.
     */
    @Test
    fun `con dos cuentas manda la de arriba, no la otra`() {
        val detalle = PagosFixtures.detalleCliente()
        assertEquals(2, detalle.ventas.size)
        assertNotEquals(PagosFixtures.VENTA_EN_PROMESA, cuentaQueEncabeza(estado(detalle)))
    }

    /** Sin detalle cargado no hay cuenta — y en ese estado el dock ni se pinta. */
    @Test
    fun `sin detalle no hay cuenta`() {
        assertNull(cuentaQueEncabeza(DetalleClienteUiState(cargando = true)))
        assertNull(cuentaQueEncabeza(estado(null)))
    }
}
