package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextoDelEnvioTest {

    private fun envio(pagos: Int = 0, ventas: Int = 0, minutos: Long? = null) = EstadoDeEnvio(
        pagosPendientes = pagos,
        ventasPendientes = ventas,
        ultimoEnvioHaceMinutos = minutos
    )

    @Test
    fun `sin pendientes es todo enviado con su tiempo`() {
        assertEquals("Todo enviado · hace 3 min", TextoDelEnvio.completo(envio(minutos = 3)))
        assertEquals(EstadoDeEnvio.Estado.TODO_ENVIADO, envio().estado)
    }

    @Test
    fun `sin tiempo conocido dice solo todo enviado`() {
        assertEquals("Todo enviado", TextoDelEnvio.completo(envio(minutos = null)))
    }

    @Test
    fun `un pago en singular y varios en plural`() {
        assertEquals(
            "1 pago esperando señal",
            TextoDelEnvio.completo(envio(pagos = 1, minutos = 5))
        )
        assertEquals("3 pagos esperando señal", TextoDelEnvio.completo(envio(pagos = 3)))
        assertEquals(EstadoDeEnvio.Estado.ESPERANDO_SENAL, envio(pagos = 1).estado)
    }

    @Test
    fun `solo ventas habla de ventas`() {
        assertEquals("1 venta esperando señal", TextoDelEnvio.titulo(envio(ventas = 1)))
        assertEquals("2 ventas esperando señal", TextoDelEnvio.titulo(envio(ventas = 2)))
    }

    @Test
    fun `pagos y ventas juntos se cuentan como envios`() {
        assertEquals("4 envíos esperando señal", TextoDelEnvio.titulo(envio(pagos = 3, ventas = 1)))
    }

    @Test
    fun `esperando senal nunca dice hace cuanto`() {
        assertNull(TextoDelEnvio.haceCuanto(envio(pagos = 1, minutos = 2)))
    }

    @Test
    fun `menos de un minuto es hace un momento`() {
        assertEquals("hace un momento", TextoDelEnvio.haceCuanto(envio(minutos = 0)))
    }

    @Test
    fun `minutos negativos no se pintan`() {
        assertNull(TextoDelEnvio.haceCuanto(envio(minutos = -3)))
    }

    @Test
    fun `horas y dias en vez de cientos de minutos`() {
        assertEquals("hace 59 min", TextoDelEnvio.haceCuanto(envio(minutos = 59)))
        assertEquals("hace 1 h", TextoDelEnvio.haceCuanto(envio(minutos = 60)))
        assertEquals("hace 23 h", TextoDelEnvio.haceCuanto(envio(minutos = 1_439)))
        assertEquals("hace 1 día", TextoDelEnvio.haceCuanto(envio(minutos = 1_440)))
        assertEquals("hace 3 días", TextoDelEnvio.haceCuanto(envio(minutos = 4_400)))
    }
}
