package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.JUEVES
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.MARTES
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.SABADO
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.VIERNES
import com.example.msp_app.feature.pagos.ui.DiaDeCobroFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

/** El grupo "El cobro" de "Datos de la venta". */
class DatosDelCobroTest {

    @Test
    fun `completo, los cuatro renglones en orden`() {
        val r = datosDelCobro(DiaDeCobroFixtures.coincide())
        assertEquals(
            listOf("Día acordado", "Suele pagar", "Otros días", "Calculado"),
            r.map { it.rotulo }
        )
        assertEquals(
            RenglonDelCobro("Día acordado", "Sábado", "lo anotó el vendedor al vender"),
            r[0]
        )
        assertEquals(RenglonDelCobro("Suele pagar", "Sábado", "5 de sus últimos 10 pagos"), r[1])
        // 3 y 2 pagos: distintos, así que cada día lleva el suyo.
        assertEquals(RenglonDelCobro("Otros días", "Martes 3, jueves 2"), r[2])
    }

    @Test
    fun `otros dias con un pago cada uno dicen 1 pago cada uno`() {
        val r = datosDelCobro(
            DiaDeCobroDeLaVenta(
                null,
                DiaDeCobroFixtures.estimacion(
                    SABADO,
                    SABADO to 7,
                    MARTES to 1,
                    JUEVES to 1,
                    VIERNES to 1
                ),
                null
            )
        )
        assertEquals(
            RenglonDelCobro("Otros días", "Martes, jueves, viernes", "1 pago cada uno"),
            r.last()
        )
    }

    @Test
    fun `calculado es la fecha y hora de la oficina`() {
        val calculado = datosDelCobro(DiaDeCobroFixtures.coincide()).last()
        assertEquals("Calculado", calculado.rotulo)
        // 18:40Z = 12:40 en America/Mexico_City.
        assertEquals("11 nov 2026, 12:40", calculado.valor.replace(".", ""))
    }

    @Test
    fun `sin acordado no hay renglon de dia acordado`() {
        val r = datosDelCobro(DiaDeCobroFixtures.soloEstimacion())
        assertEquals(listOf("Suele pagar", "Otros días", "Calculado"), r.map { it.rotulo })
    }

    @Test
    fun `solo acordado no inventa suele pagar ni otros dias`() {
        val r = datosDelCobro(DiaDeCobroFixtures.soloAcordado())
        assertEquals(listOf("Día acordado", "Calculado"), r.map { it.rotulo })
    }

    @Test
    fun `mensual escribe Dia 15`() {
        assertEquals("Día 15", datosDelCobro(DiaDeCobroFixtures.mensual()).first().valor)
    }

    @Test
    fun `con empate suele pagar dice Distintos dias y otros dias lista todos con su cuenta`() {
        val r = datosDelCobro(DiaDeCobroFixtures.empateSinAcordado())
        assertEquals(
            RenglonDelCobro("Suele pagar", "Distintos días", "de sus últimos 8 pagos"),
            r[0]
        )
        assertEquals("Martes 3, viernes 3, jueves 2", r[1].valor)
    }

    @Test
    fun `sin fecha de calculo no hay renglon Calculado y sin nada no hay grupo`() {
        val sinFecha = DiaDeCobroFixtures.coincide().copy(calculadoEn = null)
        assertEquals(false, datosDelCobro(sinFecha).any { it.rotulo == "Calculado" })
        assertEquals(emptyList<RenglonDelCobro>(), datosDelCobro(null))
        assertEquals(
            emptyList<RenglonDelCobro>(),
            datosDelCobro(DiaDeCobroDeLaVenta(null, null, DiaDeCobroFixtures.CALCULADO))
        )
    }
}
