package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catálogo cerrado de la ficha: que sea **corto**, que sus literales sean
 * **estables**, que sus ventanas **partan la jornada**, y que ningún valor
 * pueda llegar a la pantalla **sin etiqueta**.
 *
 * Cada aserto de aquí es de los que se ponen rojos con el defecto puesto — no
 * de los que pasan siempre. Los dos que más importan:
 *
 * - `enLaVentanaDe` recorrido minuto a minuto detecta un hueco o un traslape
 *   que un aserto por extremos no vería (control positivo del particionado).
 * - La lista literal de nombres se rompe con un rename, que es exactamente lo
 *   que desconectaría toda ficha ya guardada de su señal.
 */
class CatalogoDeLaFichaTest {

    /**
     * Los literales que se persisten en `cliente_ficha_senales.SENAL`.
     *
     * Escritos a mano y no derivados de `entries`: un aserto derivado del enum
     * pasaría con el enum renombrado, que es justo el cambio que rompe las
     * fichas guardadas. Este test tiene que fallar cuando eso pase.
     */
    private val literales = listOf(
        "ESTA_EN_LA_MANANA",
        "ESTA_EN_LA_TARDE",
        "ESTA_EN_LA_NOCHE",
        "ATIENDE_OTRA_PERSONA"
    )

    private val etiquetas = mapOf(
        SenalDeFicha.ESTA_EN_LA_MANANA to "está en la mañana",
        SenalDeFicha.ESTA_EN_LA_TARDE to "está en la tarde",
        SenalDeFicha.ESTA_EN_LA_NOCHE to "está en la noche",
        SenalDeFicha.ATIENDE_OTRA_PERSONA to "atiende otra persona"
    )

    @Test
    fun `el catalogo nace corto - cuatro valores y estos literales`() {
        assertEquals(literales, SenalDeFicha.entries.map { it.name })
    }

    @Test
    fun `cada senal tiene etiqueta en espanol y ninguna esta en blanco`() {
        assertEquals(SenalDeFicha.entries.toSet(), etiquetas.keys)
        SenalDeFicha.entries.forEach { senal ->
            assertEquals(etiquetas[senal], etiquetaDe(senal))
            assertTrue("etiqueta en blanco para $senal", etiquetaDe(senal).isNotBlank())
        }
    }

    @Test
    fun `ninguna etiqueta repite a otra`() {
        val vistas = SenalDeFicha.entries.map(::etiquetaDe)
        assertEquals(vistas.size, vistas.toSet().size)
    }

    @Test
    fun `la etiqueta respeta la norma de texto - minusculas y sin punto final`() {
        SenalDeFicha.entries.forEach { senal ->
            val etiqueta = etiquetaDe(senal)
            assertEquals("$senal en mayusculas", etiqueta.lowercase(), etiqueta)
            assertFalse("$senal termina en punto", etiqueta.endsWith("."))
        }
    }

    @Test
    fun `solo las senales de horario declaran ventana`() {
        assertNotNull(SenalDeFicha.ESTA_EN_LA_MANANA.ventana)
        assertNotNull(SenalDeFicha.ESTA_EN_LA_TARDE.ventana)
        assertNotNull(SenalDeFicha.ESTA_EN_LA_NOCHE.ventana)
        assertNull(
            "atender otra persona no dice nada de la hora",
            SenalDeFicha.ATIENDE_OTRA_PERSONA.ventana
        )
    }

    /**
     * **El control positivo del particionado.** Se recorre la jornada minuto a
     * minuto: cada uno cae en EXACTAMENTE una ventana. Un hueco entre dos
     * tramos daría `null` en algún minuto; un traslape daría dos señales.
     *
     * Con el defecto puesto —mover un corte, dejar un tramo fuera— este test se
     * pone rojo en el minuto exacto, y lo dice.
     */
    @Test
    fun `las tres ventanas parten la jornada sin hueco y sin traslape`() {
        val jornada = SenalDeFicha.JORNADA
        var hora = jornada.desde
        while (hora < jornada.hastaExclusivo) {
            val caen = SenalDeFicha.entries.filter { it.ventana?.contiene(hora) == true }
            assertEquals("a las $hora caen $caen", 1, caen.size)
            assertEquals(caen.first(), SenalDeFicha.enLaVentanaDe(hora))
            hora = hora.plusMinutes(1)
        }
    }

    @Test
    fun `fuera de la jornada no cae en ninguna ventana`() {
        assertNull(SenalDeFicha.enLaVentanaDe(LocalTime.of(7, 59)))
        assertNull(SenalDeFicha.enLaVentanaDe(SenalDeFicha.JORNADA.hastaExclusivo))
        assertNull(SenalDeFicha.enLaVentanaDe(LocalTime.MIDNIGHT))
    }

    @Test
    fun `el borde de cada ventana es medio abierto`() {
        val tarde = requireNotNull(SenalDeFicha.ESTA_EN_LA_TARDE.ventana)
        assertTrue("el inicio entra", tarde.contiene(tarde.desde))
        assertFalse("el final NO entra", tarde.contiene(tarde.hastaExclusivo))
        assertTrue(tarde.contiene(tarde.hastaExclusivo.minusMinutes(1)))
    }

    @Test
    fun `deLiteral da la vuelta completa por el nombre de la constante`() {
        SenalDeFicha.entries.forEach { senal ->
            assertEquals(senal, SenalDeFicha.deLiteral(senal.name))
        }
    }

    @Test
    fun `un literal retirado se lee como desconocido en vez de tumbar la pantalla`() {
        assertNull(SenalDeFicha.deLiteral("SE_MUDO_DE_CASA"))
        assertNull(SenalDeFicha.deLiteral(""))
        assertNull(
            "el literal es sensible a mayusculas: asi se guardo",
            SenalDeFicha.deLiteral("esta_en_la_manana")
        )
    }

    @Test
    fun `una ficha recien construida esta vacia y una con senales no`() {
        assertTrue(FichaDelCliente().vacia)
        assertFalse(FichaDelCliente(senales = setOf(SenalDeFicha.ESTA_EN_LA_TARDE)).vacia)
        assertFalse(FichaDelCliente(nota = "hay perro").vacia)
    }
}
