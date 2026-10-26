package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.aEstadoCuenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **El puente entre las tres pestañas viejas y las tres pastillas de ahora**
 * (D18, E-APP-070).
 *
 * `SalesScreen` —la pantalla que la Task 21 retiró— partía la lista en
 * `POR VISITAR` / `VISITADOS` / `PAGADOS` filtrando `Sale.ESTADO_COBRANZA`, el
 * enum legado de cinco valores. Se recorre valor por valor con
 * `EstadoCobranza.aEstadoCuenta()` y se afirma en qué pastilla cae cada uno.
 *
 * | `EstadoCobranza` | `aEstadoCuenta()` | trato | pastilla | pestaña vieja |
 * |---|---|---|---|---|
 * | `PENDIENTE` | `SIN_TOCAR` | `SIN_TRABAJAR` | **Por visitar** | POR VISITAR |
 * | `VISITADO` | `VISITE_VUELVO` | `REGRESAS` | **Por visitar** | POR VISITAR |
 * | `VOLVER_VISITAR` | `VISITE_VUELVO` | `REGRESAS` | **Por visitar** | POR VISITAR |
 * | `NO_PAGADO` | `SE_NEGO` | `ESCALAR` | **Por visitar** | VISITADOS |
 * | `PAGADO` | `PAGO` | `PAGADO` | **Pagados** | PAGADOS |
 *
 * Con tres pastillas "se negó" vuelve a vivir con lo que hay que tocar: la
 * pregunta es "¿a qué puerta voy?" y a esa también. Nada del legado cae en
 * "No paga": sólo la promesa con fecha futura llega ahí, y el legado no la trae.
 *
 * Nota de alcance: `aEstadoCuenta()` sirve para **leer el histórico**, no para
 * decidir el periodo en curso.
 */
class SegmentosVsPestanasLegadasTest {

    private val hoyDePrueba = ListaFixtures.HOY

    private fun comoEstadoDelPeriodo(legado: EstadoCobranza) = EstadoDelPeriodo(
        estado = legado.aEstadoCuenta(),
        abonoDelPeriodo = ListaFixtures.dinero("0"),
        parcialidad = ListaFixtures.dinero("350")
    )

    /**
     * El chip de un valor legado. Devuelve **la lista**, no el primero: que sea
     * exactamente uno es parte de lo que se afirma, y un `firstOrNull` escondería
     * tanto el cero como el dos.
     */
    private fun chipsDe(legado: EstadoCobranza): List<SegmentoDeCobranza> =
        SegmentoDeCobranza.entries.filter {
            it.contiene(comoEstadoDelPeriodo(legado), hoyDePrueba)
        }

    private fun chipDe(legado: EstadoCobranza): SegmentoDeCobranza {
        val chips = chipsDe(legado)
        assertEquals("$legado no cayó en exactamente un chip: $chips", 1, chips.size)
        return chips.single()
    }

    /** **La tabla del KDoc, recorrida valor por valor.** */
    @Test
    fun `los cinco valores legados caen en su pastilla, una y solo una`() {
        assertEquals(
            mapOf(
                EstadoCobranza.PENDIENTE to SegmentoDeCobranza.POR_VISITAR,
                EstadoCobranza.VISITADO to SegmentoDeCobranza.POR_VISITAR,
                EstadoCobranza.VOLVER_VISITAR to SegmentoDeCobranza.POR_VISITAR,
                EstadoCobranza.NO_PAGADO to SegmentoDeCobranza.POR_VISITAR,
                EstadoCobranza.PAGADO to SegmentoDeCobranza.PAGADOS
            ),
            EstadoCobranza.entries.associateWith(::chipDe)
        )
    }

    /** La pestaña `POR VISITAR` es exactamente la pastilla Por visitar. */
    @Test
    fun `la pestana POR VISITAR es exactamente Por visitar`() {
        val porVisitar = setOf(
            EstadoCobranza.VOLVER_VISITAR,
            EstadoCobranza.PENDIENTE,
            EstadoCobranza.VISITADO
        )
        assertEquals(setOf(SegmentoDeCobranza.POR_VISITAR), porVisitar.map(::chipDe).toSet())
    }

    /**
     * La pestaña `PAGADOS` es exactamente la pastilla Pagados. Control positivo
     * pegado: ningún otro valor legado cae ahí.
     */
    @Test
    fun `la pestana PAGADOS es exactamente la pastilla Pagados`() {
        assertEquals(SegmentoDeCobranza.PAGADOS, chipDe(EstadoCobranza.PAGADO))
        assertEquals(
            listOf(EstadoCobranza.PAGADO),
            EstadoCobranza.entries.filter { chipDe(it) == SegmentoDeCobranza.PAGADOS }
        )
    }

    /** Lo que estaba en `VISITADOS` (se negó) ahora es trabajo por visitar. */
    @Test
    fun `E-APP-070 lo que estaba en VISITADOS ahora es Por visitar`() {
        assertEquals(SegmentoDeCobranza.POR_VISITAR, chipDe(EstadoCobranza.NO_PAGADO))
    }

    /** Ningún valor legado se queda sin pastilla. */
    @Test
    fun `ningun valor legado se queda fuera del tablero`() {
        EstadoCobranza.entries.forEach { legado ->
            val chips = chipsDe(legado)
            assertEquals("$legado no se ve en ninguna pastilla", 1, chips.size)
        }
    }
}
