package com.example.msp_app.core.datos

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** E-APP-172: total real del UID con TrafficStats, reparto por red y plano. */
class MuestreadorDeTraficoTest {

    private class FuenteFalsa(var rx: Long = 0, var tx: Long = 0, var falla: Boolean = false) : FuenteDeTrafico {
        override fun rx(): Long = if (falla) error("boom") else rx
        override fun tx(): Long = if (falla) error("boom") else tx
    }

    private val clock = FakeClock.at("2026-11-10T18:00:00Z")
    private val registro = RegistroDeUso(clock)
    private val fuente = FuenteFalsa(rx = 5_000, tx = 2_000)
    private fun muestreador(red: TipoDeRed = TipoDeRed.WIFI, plano: Boolean = true) =
        MuestreadorDeTrafico(fuente, registro, red, plano)

    private fun dia() = registro.instantanea().singleOrNull()

    @Test fun `la primera muestra fija la base y no cuenta lo anterior`() {
        val m = muestreador()
        m.muestrear()
        assertNull(dia())
        assertEquals(MuestraDeTrafico(5_000, 2_000), m.ultimaMuestra())
    }

    @Test fun `suma deltas a la red y plano vigentes`() {
        val m = muestreador(TipoDeRed.MOVIL, plano = true)
        m.muestrear()
        fuente.rx += 1_000
        fuente.tx += 300
        m.muestrear()
        val d = dia()!!
        assertEquals(1_000L, d.rxTotal)
        assertEquals(300L, d.txTotal)
        assertEquals(1_300L, d.movil)
        assertEquals(1_300L, d.primerPlano)
    }

    @Test fun `sin cambios no escribe nada`() {
        val m = muestreador()
        m.muestrear()
        m.muestrear()
        assertNull(dia())
    }

    @Test fun `reinicio del telefono cuenta lo posterior y no resta`() {
        val m = muestreador()
        m.muestrear()
        fuente.rx = 400 // contadores volvieron a 0 y subieron a 400
        fuente.tx = 100
        m.muestrear()
        val d = dia()!!
        assertEquals(400L, d.rxTotal)
        assertEquals(100L, d.txTotal)
        fuente.rx = 500
        m.muestrear()
        assertEquals(500L, dia()!!.rxTotal)
    }

    @Test fun `sin soporte no cuenta ni truena`() {
        fuente.rx = FuenteDeTrafico.UNSUPPORTED
        fuente.tx = FuenteDeTrafico.UNSUPPORTED
        val m = muestreador()
        m.muestrear()
        m.muestrear()
        assertNull(dia())
        assertNull(m.ultimaMuestra())
    }

    @Test fun `una fuente que lanza no propaga`() {
        val m = muestreador()
        fuente.falla = true
        m.muestrear()
        assertNull(dia())
    }

    @Test fun `el cambio de red atribuye lo anterior a la red anterior`() {
        val m = muestreador(TipoDeRed.MOVIL)
        m.muestrear()
        fuente.rx += 700
        m.cambiarRed(TipoDeRed.WIFI)
        fuente.rx += 200
        m.muestrear()
        val d = dia()!!
        assertEquals(700L, d.movil)
        assertEquals(200L, d.wifi)
    }

    @Test fun `el paso a segundo plano atribuye lo anterior a primer plano`() {
        val m = muestreador(plano = true)
        m.muestrear()
        fuente.tx += 900
        m.cambiarPlano(false)
        fuente.tx += 100
        m.muestrear()
        val d = dia()!!
        assertEquals(900L, d.primerPlano)
        assertEquals(100L, d.segundoPlano)
    }

    @Test fun `el delta cae en el dia de negocio de la muestra`() {
        clock.setNow(Instant.parse("2026-11-10T05:59:00Z")) // 23:59 del 9
        val m = muestreador()
        m.muestrear()
        fuente.rx += 10
        m.muestrear()
        clock.setNow(Instant.parse("2026-11-10T06:01:00Z")) // 00:01 del 10
        fuente.rx += 20
        m.muestrear()
        val dias = registro.instantanea().associateBy { it.fecha }
        assertEquals(10L, dias.getValue("2026-11-09").rxTotal)
        assertEquals(20L, dias.getValue("2026-11-10").rxTotal)
    }

    @Test fun `cambio de hora no altera los deltas`() {
        val m = muestreador()
        m.muestrear()
        fuente.rx += 10
        clock.setNow(Instant.parse("2026-03-01T00:00:00Z")) // reloj salta meses atras
        m.muestrear()
        assertEquals(10L, registro.instantanea().sumOf { it.rxTotal })
    }

    @Test fun `tras morir el proceso el delta se mide contra la ultima muestra guardada`() {
        val m = muestreador()
        m.restaurarUltima(MuestraDeTrafico(4_000, 1_500))
        m.muestrear()
        val d = dia()!!
        assertEquals(1_000L, d.rxTotal)
        assertEquals(500L, d.txTotal)
    }

    // H3: si el muestreo corre antes de que termine `restaurar()`, la base restaurada
    // sustituye a la provisional y el tráfico entre el último guardado y el arranque se cuenta.
    @Test fun `la base restaurada despues del primer muestreo sustituye a la provisional`() {
        val m = muestreador()
        m.muestrear() // una actividad arrancó antes de restaurar: base provisional 5000/2000
        m.restaurarUltima(MuestraDeTrafico(4_000, 1_500))
        val d = dia()!!
        assertEquals(1_000L, d.rxTotal)
        assertEquals(500L, d.txTotal)
    }

    @Test fun `la base restaurada no sustituye si ya se atribuyo trafico`() {
        val m = muestreador()
        m.muestrear()
        fuente.rx += 100
        m.muestrear() // ya se contaron 100
        m.restaurarUltima(MuestraDeTrafico(0, 0))
        assertEquals(100L, dia()!!.rxTotal)
    }
}
