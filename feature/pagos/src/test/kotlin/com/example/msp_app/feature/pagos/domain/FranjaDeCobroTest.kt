package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.DOMINGO
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.JUEVES
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.LUNES
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.MARTES
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.MIERCOLES
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.SABADO
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.VIERNES
import com.example.msp_app.feature.pagos.ui.DiaDeCobroFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **La regla de la franja, un caso por renglón de la tabla del spec §7** más los
 * etiquetas de día (mínimo 2 pagos, máximo 3, sin "otros").
 */
class FranjaDeCobroTest {

    private fun franja(dia: DiaDeCobroDeLaVenta?) = franjaDeCobro(dia)!!

    private fun principales(f: FranjaDeCobro) = f.dias.filter { it.principal }.map { it.rotulo }

    @Test
    fun `acordado igual al que mas paga, etiqueta gris Acordado y barra`() {
        val f = franja(DiaDeCobroFixtures.coincide())
        assertEquals(EtiquetaDelAcordado.COINCIDE, f.etiqueta)
        assertEquals("✓ Acordado", f.etiquetaTexto)
        assertEquals("Sábado", f.diaGrande)
        assertEquals(
            listOf("Sáb" to 5, "Mar" to 3, "Jue" to 2),
            f.dias.map { it.rotulo to it.pagos }
        )
        assertEquals(listOf("Sáb"), principales(f))
        assertEquals(10, f.pagosConsiderados)
    }

    @Test
    fun `acordado distinto, borde naranja Acordo sabado y manda el que mas paga`() {
        val f = franja(DiaDeCobroFixtures.distinto())
        assertEquals(EtiquetaDelAcordado.DISTINTO, f.etiqueta)
        assertEquals("Acordó sábado", f.etiquetaTexto)
        assertEquals("Martes", f.diaGrande)
        // Mar 4, Vie 3, Sáb 2; el pago suelto del lunes NO lleva etiqueta (sin "otros").
        assertEquals(
            listOf("Mar" to 4, "Vie" to 3, "Sáb" to 2),
            f.dias.map { it.rotulo to it.pagos }
        )
        assertEquals(listOf("Mar"), principales(f))
    }

    @Test
    fun `solo acordado, Acordado al vender, el dia acordado y sin barra`() {
        val f = franja(DiaDeCobroFixtures.soloAcordado())
        assertEquals(EtiquetaDelAcordado.AL_VENDER, f.etiqueta)
        assertEquals("Acordado al vender", f.etiquetaTexto)
        assertEquals("Sábado", f.diaGrande)
        assertEquals(emptyList<EtiquetaDeDia>(), f.dias)
        assertNull(f.pagosConsiderados)
    }

    @Test
    fun `solo estimacion, sin etiqueta, el que mas paga y barra`() {
        val f = franja(DiaDeCobroFixtures.soloEstimacion())
        assertNull(f.etiqueta)
        assertNull(f.etiquetaTexto)
        assertEquals("Martes", f.diaGrande)
        assertEquals(
            listOf("Mar" to 6, "Vie" to 2),
            f.dias.map { it.rotulo to it.pagos }
        )
    }

    @Test
    fun `empate con acordado, Acordado al vender y ninguna etiqueta en verde`() {
        val f = franja(DiaDeCobroFixtures.empateConAcordado())
        assertEquals(EtiquetaDelAcordado.AL_VENDER, f.etiqueta)
        assertEquals("Sábado", f.diaGrande)
        assertEquals(listOf("Mar", "Vie", "Jue"), f.dias.map { it.rotulo })
        assertEquals(emptyList<String>(), principales(f))
    }

    @Test
    fun `empate sin acordado, Distintos dias, sin etiqueta y ninguna etiqueta en verde`() {
        val f = franja(DiaDeCobroFixtures.empateSinAcordado())
        assertNull(f.etiqueta)
        assertEquals("Distintos días", f.diaGrande)
        assertEquals(3, f.dias.size)
        assertEquals(emptyList<String>(), principales(f))
    }

    @Test
    fun `sin nada la franja no aparece`() {
        assertNull(franjaDeCobro(null))
        assertNull(franjaDeCobro(DiaDeCobroDeLaVenta(null, null, null)))
        // Una estimación sin días no es una estimación.
        assertNull(
            franjaDeCobro(DiaDeCobroDeLaVenta(null, EstimacionDelDia(0, null, emptyList()), null))
        )
    }

    @Test
    fun `mensual muestra Dia 15 como acordado al vender, sin barra`() {
        val f = franja(DiaDeCobroFixtures.mensual())
        assertEquals("Día 15", f.diaGrande)
        assertEquals("Acordado al vender", f.etiquetaTexto)
        assertEquals(emptyList<EtiquetaDeDia>(), f.dias)
        assertNull(f.pagosConsiderados)
    }

    @Test
    fun `un acordado de mes con estimacion nunca coincide`() {
        val f = franja(
            DiaDeCobroDeLaVenta(
                DiaAcordado.DelMes(15),
                DiaDeCobroFixtures.estimacion(SABADO, SABADO to 5, MARTES to 3),
                null
            )
        )
        assertEquals(EtiquetaDelAcordado.DISTINTO, f.etiqueta)
        assertEquals("Acordó día 15", f.etiquetaTexto)
    }

    // --- La barra ---------------------------------------------------------------------------

    @Test
    fun `un dia con un solo pago no lleva etiqueta`() {
        val f = franja(
            DiaDeCobroDeLaVenta(
                null,
                DiaDeCobroFixtures.estimacion(SABADO, SABADO to 4, MARTES to 1, JUEVES to 1),
                null
            )
        )
        assertEquals(listOf("Sáb"), f.dias.map { it.rotulo })
    }

    @Test
    fun `maximo tres etiquetas`() {
        val f = franja(
            DiaDeCobroDeLaVenta(
                null,
                DiaDeCobroFixtures.estimacion(
                    SABADO,
                    SABADO to 4,
                    MARTES to 3,
                    JUEVES to 2,
                    VIERNES to 2,
                    LUNES to 2
                ),
                null
            )
        )
        // Con 2 pagos empatan lunes, jueves y viernes: entra el primero de la semana.
        assertEquals(listOf("Sáb", "Mar", "Lun"), f.dias.map { it.rotulo })
    }

    @Test
    fun `nunca hay mas de tres etiquetas ni una llamada otros`() {
        val f = franja(DiaDeCobroFixtures.distinto())
        assertEquals(false, f.dias.any { it.rotulo == "otros" })
        assertEquals(true, f.dias.size <= 3)
    }

    @Test
    fun `entre dos dias con los mismos pagos manda el orden de la semana`() {
        val f = franja(
            DiaDeCobroDeLaVenta(
                null,
                DiaDeCobroFixtures.estimacion(null, VIERNES to 3, MARTES to 3, DOMINGO to 2),
                null
            )
        )
        assertEquals(listOf("Mar", "Vie", "Dom"), f.dias.map { it.rotulo })
    }

    @Test
    fun `los siete dias se escriben con acento y su abreviatura`() {
        assertEquals("Miércoles", MIERCOLES.nombre)
        assertEquals("Mié", MIERCOLES.abreviatura)
        assertEquals("Sábado", SABADO.nombre)
        assertEquals(SABADO, DiaDeLaSemana.deLaClave(" sabado "))
        assertNull(DiaDeLaSemana.deLaClave("SÁBADO"))
        assertNull(DiaDeLaSemana.deLaClave(null))
        assertNotNull(franjaDeCobro(DiaDeCobroDeLaVenta(DiaAcordado.DeLaSemana(LUNES), null, null)))
    }
}
