package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.feature.pagos.domain.model.CompromisoDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDeCompromiso
import com.example.msp_app.feature.pagos.domain.model.TipoDeCompromiso
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La presentación del compromiso (plan visitas P2, Tarea 20; spec §5.7 B, C,
 * E, F; E-APP-115), probada de frente. Los casos por estado × hora × abonos
 * están en [PresentacionDeCompromisoCasosTest]; aquí van las reglas con
 * nombre del plan.
 */
class PresentacionDeCompromisoTest {

    private val hoy = LocalDate.of(2026, 10, 20)

    private fun cita(
        estado: EstadoDeCompromiso = EstadoDeCompromiso.VIGENTE_FUTURA,
        dia: LocalDate = LocalDate.of(2026, 10, 23),
        hora: LocalTime? = null
    ) = CompromisoDeLaVenta(TipoDeCompromiso.CITA, estado, dia, hora)

    private fun promesa(
        estado: EstadoDeCompromiso = EstadoDeCompromiso.VIGENTE_FUTURA,
        monto: Long? = 50_000
    ) = CompromisoDeLaVenta(
        TipoDeCompromiso.PROMESA,
        estado,
        LocalDate.of(2026, 10, 23),
        null,
        monto
    )

    /** F: una cita sin hora es una cita con su día, violeta. */
    @Test
    fun citaSinHoraEsCitaConSuDia() {
        val p = presentacionDe(cita(hora = null), hoy)!!
        assertEquals("Cita", p.titulo)
        assertEquals("Vie 23 oct, sin hora", p.detalle)
        assertEquals(TratoDelEstado.CITA, p.trato)
    }

    /** F: nunca el trato ni el texto de "regresas" para una cita vigente sin hora. */
    @Test
    fun citaSinHoraNoEsRegresas() {
        for (estado in listOf(EstadoDeCompromiso.VIGENTE_FUTURA, EstadoDeCompromiso.VIGENTE_HOY)) {
            val p = presentacionDe(cita(estado = estado, dia = hoy, hora = null), hoy)!!
            assertNotEquals(TratoDelEstado.REGRESAS, p.trato)
            assertFalse(p.detalle.contains("regresas", ignoreCase = true))
        }
    }

    /** B: con abonos la tarjeta pinta el chip de dinero Y el compromiso, no uno u otro. */
    @Test
    fun chipDeDineroYCompromisoJuntos() {
        val piezas =
            piezasDeLaTarjeta(
                conAbonos = true,
                compromiso = cita(hora = LocalTime.of(16, 30)),
                hoy = hoy
            )
        assertEquals(PiezaPrincipal.ESTADO, piezas.principal)
        assertEquals("Cita", piezas.compromiso?.titulo)
    }

    /** C: una venta sin abonos pinta "Primer abono" y también el compromiso. */
    @Test
    fun ventaNuevaMuestraCompromiso() {
        val piezas = piezasDeLaTarjeta(conAbonos = false, compromiso = promesa(), hoy = hoy)
        assertEquals(PiezaPrincipal.PRIMER_ABONO, piezas.principal)
        assertEquals("Promesa", piezas.compromiso?.titulo)
        assertEquals("Vie 23 oct, $500", piezas.compromiso?.detalle)
    }

    /** `reemplazada` no es estado: la venta no trae entrada y no se pinta nada. */
    @Test
    fun reemplazadaNoSeMuestra() {
        assertNull(presentacionDe(null, hoy))
        assertNull(piezasDeLaTarjeta(conAbonos = true, compromiso = null, hoy = hoy).compromiso)
    }

    @Test
    fun cerradaSinCumplirNoSeMuestra() {
        assertNull(presentacionDe(cita(estado = EstadoDeCompromiso.CERRADA_SIN_CUMPLIR), hoy))
        assertNull(presentacionDe(promesa(estado = EstadoDeCompromiso.CERRADA_SIN_CUMPLIR), hoy))
        // Control positivo: el mismo compromiso vigente sí se pinta.
        assertNotNull(presentacionDe(promesa(), hoy))
    }

    /** El ojo tapa toda cantidad: también el monto prometido. El día se queda. */
    @Test
    fun montosOcultosTapanElMonto() {
        val p = presentacionDe(promesa(), hoy, montosOcultos = true)!!
        assertEquals("Vie 23 oct, $MASKED_MONEY", p.detalle)
    }

    /** El monto de la promesa se redondea al peso como el resto de la app. */
    @Test
    fun elMontoSeRedondeaAlPesoComoLaApp() {
        assertEquals("Vie 23 oct, $501", presentacionDe(promesa(monto = 50_050), hoy)!!.detalle)
    }

    /** Hoy se dice "Hoy": es lo primero que el cobrador pregunta. */
    @Test
    fun elDiaDeHoySeDiceHoy() {
        val p = presentacionDe(
            cita(estado = EstadoDeCompromiso.VIGENTE_HOY, dia = hoy, hora = LocalTime.of(9, 5)),
            hoy
        )!!
        assertEquals("Hoy, 09:05", p.detalle)
    }

    /** §7 de los textos: mayúscula inicial, sin punto final, nunca "ciclo", "Casa" ni "Trabajo". */
    @Test
    fun losTextosSiguenLasReglas() {
        val prohibidas = listOf("ciclo", "casa", "trabajo")
        val textos = TipoDeCompromiso.entries.flatMap { tipo ->
            EstadoDeCompromiso.entries.flatMap { estado ->
                listOf(null, LocalTime.of(16, 30)).mapNotNull { hora ->
                    val c = CompromisoDeLaVenta(tipo, estado, hoy.plusDays(1), hora, 10_000)
                    presentacionDe(c, hoy)
                }
            }
        }.flatMap { listOf(it.titulo, it.detalle) }
        assertTrue(textos.size > 10)
        for (texto in textos) {
            assertTrue(texto, texto.first().isUpperCase())
            assertFalse(texto, texto.endsWith("."))
            assertTrue(texto, prohibidas.none { texto.contains(it, ignoreCase = true) })
        }
    }
}
