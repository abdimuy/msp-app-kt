package com.example.msp_app.core.common.cobranza.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cobertura: una venta tiene "algo" en la semana si recibió al menos un pago de
 * cualquier forma (rechazado y condonación incluidos) o una visita que le
 * aplique con la MISMA regla de alcance que [EstadoCuentaDeriver]. Spec de
 * desempeño §2 "Cobertura".
 */
class CoberturaDelPeriodoTest {

    private val ventana = VentanaCobro(
        inicio = Instant.parse("2026-10-09T13:46:00Z"),
        fin = Instant.parse("2026-10-14T22:40:00Z")
    )
    private val dentro = Instant.parse("2026-10-11T16:00:00Z")
    private val antes = Instant.parse("2026-10-09T13:45:59Z")

    private val ventaUno = 1
    private val ventaDos = 2
    private val clienteRosario = 10

    private val cuentas = listOf(
        CuentaDelPeriodo(ventaUno, clienteRosario, BigDecimal("150.00")),
        CuentaDelPeriodo(ventaDos, clienteRosario, BigDecimal("200.00"))
    )

    private fun pago(
        ventaId: Int,
        formaCobroId: Int = 157,
        fechaHora: Instant = dentro,
        rechazado: Boolean = false
    ) = PagoEnVentana(ventaId, BigDecimal("150.00"), formaCobroId, fechaHora, rechazado)

    private fun visita(
        tipo: String,
        ventaId: Int?,
        fechaHora: Instant = dentro,
        fechaCita: LocalDate? = null
    ) = VisitaEnVentana(clienteRosario, ventaId, tipo, fechaHora, fechaCita = fechaCita)

    private fun conAlgo(
        pagos: List<PagoEnVentana> = emptyList(),
        visitas: List<VisitaEnVentana> = emptyList()
    ) = CoberturaDelPeriodo.ventasConAlgo(cuentas, pagos, visitas, ventana)

    @Test
    fun `sin pagos ni visitas no hay cobertura`() {
        assertEquals(emptySet<Int>(), conAlgo())
    }

    @Test
    fun `pago marca solo su venta`() {
        assertEquals(setOf(ventaUno), conAlgo(pagos = listOf(pago(ventaUno))))
    }

    @Test
    fun `E-APP-057 pago rechazado cuenta como algo`() {
        assertEquals(setOf(ventaUno), conAlgo(pagos = listOf(pago(ventaUno, rechazado = true))))
    }

    @Test
    fun `condonacion cuenta como algo`() {
        assertEquals(
            setOf(ventaDos),
            conAlgo(pagos = listOf(pago(ventaDos, formaCobroId = 137026)))
        )
    }

    @Test
    fun `pago fuera de la ventana no cuenta`() {
        assertEquals(emptySet<Int>(), conAlgo(pagos = listOf(pago(ventaUno, fechaHora = antes))))
    }

    @Test
    fun `no estaba de alcance cliente marca todas las ventas del cliente`() {
        assertEquals(
            setOf(ventaUno, ventaDos),
            conAlgo(visitas = listOf(visita(TipoVisitaCatalogo.NO_SE_ENCONTRABA, ventaId = null)))
        )
    }

    @Test
    fun `cita con dia marca todas las ventas aunque el literal sea de venta`() {
        assertEquals(
            setOf(ventaUno, ventaDos),
            conAlgo(
                visitas = listOf(
                    visita(
                        TipoVisitaCatalogo.PIDE_TIEMPO,
                        ventaId = ventaUno,
                        fechaCita = LocalDate.parse("2026-10-15")
                    )
                )
            )
        )
    }

    @Test
    fun `visita de alcance venta marca solo su venta`() {
        assertEquals(
            setOf(ventaDos),
            conAlgo(
                visitas = listOf(visita(TipoVisitaCatalogo.NO_VA_A_DAR_PAGO, ventaId = ventaDos))
            )
        )
    }

    @Test
    fun `visita de alcance venta sin venta ligada no marca nada`() {
        assertEquals(
            emptySet<Int>(),
            conAlgo(visitas = listOf(visita(TipoVisitaCatalogo.NO_VA_A_DAR_PAGO, ventaId = null)))
        )
    }

    @Test
    fun `visita fuera de la ventana no cuenta`() {
        assertEquals(
            emptySet<Int>(),
            conAlgo(
                visitas = listOf(
                    visita(TipoVisitaCatalogo.NO_SE_ENCONTRABA, ventaId = null, fechaHora = antes)
                )
            )
        )
    }

    @Test
    fun `pago de una venta que no esta en cuentas no agrega nada`() {
        assertEquals(emptySet<Int>(), conAlgo(pagos = listOf(pago(ventaId = 99))))
    }
}
