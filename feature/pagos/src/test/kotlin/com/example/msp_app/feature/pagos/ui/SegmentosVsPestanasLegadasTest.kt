package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.aEstadoCuenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **El puente entre las tres pestañas viejas y los cuatro chips nuevos.**
 *
 * `SalesScreen` —la pantalla que la Task 21 retiró— partía la lista en
 * `POR VISITAR` / `VISITADOS` / `PAGADOS` filtrando `Sale.ESTADO_COBRANZA`, el
 * enum legado de cinco valores, y aplicaba el orden **solo** a `POR VISITAR`
 * (`VOLVER_VISITAR ∪ PENDIENTE ∪ VISITADO`). Si los chips nuevos se definieran
 * en otro vocabulario sin comprobar la correspondencia, el orden se seguiría
 * aplicando pero a otro conjunto, y nadie se enteraría.
 *
 * Aquí se recorre valor por valor con `EstadoCobranza.aEstadoCuenta()` —el
 * puente que `:core:database` ya tiene escrito para exactamente esto— y se
 * afirma en qué chip cae cada uno.
 *
 * ## La tabla, que con los chips nuevos quedó más limpia
 *
 * | `EstadoCobranza` | `aEstadoCuenta()` | trato | chip | pestaña vieja |
 * |---|---|---|---|---|
 * | `PENDIENTE` | `SIN_TOCAR` | `SIN_TRABAJAR` | **sin visitar** | POR VISITAR |
 * | `VISITADO` | `VISITE_VUELVO` | `REGRESAS` | **volver a visitar** | POR VISITAR |
 * | `VOLVER_VISITAR` | `VISITE_VUELVO` | `REGRESAS` | **volver a visitar** | POR VISITAR |
 * | `NO_PAGADO` | `SE_NEGO` | `ESCALAR` | **ya no esta semana** | VISITADOS |
 * | `PAGADO` | `PAGO` | `PAGADO` | **pagados** | PAGADOS |
 *
 * Los chips de ahora se parecen **más** a aquellas pestañas que los de la
 * versión anterior, y en un punto concreto: `PAGADOS` volvió a ser **un chip**.
 * Con `TODOS`/`VENCIDOS`/`HOY`/`SIN_VISITAR` lo cobrado no tenía chip propio y
 * sólo se veía bajo *todos*; ahora la pestaña `PAGADOS` tiene su equivalente
 * exacto, uno a uno. Lo que no vuelve es el defecto de aquella pestaña: ésta
 * lee el estado que `EstadoCuentaDeriver` deriva del dinero y de las visitas, no
 * la columna `ESTADO_COBRANZA`, que `PaymentsViewModel.savePayment` escribe en
 * `PAGADO` con **cada** pago sin comparar el monto contra `PARCIALIDAD` — por
 * eso aquella pestaña se tragaba a quien abonó cincuenta pesos de una
 * parcialidad de trescientos.
 *
 * Nota de alcance: `aEstadoCuenta()` sirve para **leer el histórico**, no para
 * decidir el periodo en curso. Este test usa el puente para lo que existe.
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

    /**
     * **La tabla del KDoc, recorrida valor por valor.** `EstadoCobranza.entries`
     * y no una lista escrita a mano: un sexto valor legado aparece aquí solo —
     * `aEstadoCuenta()` es un `when` exhaustivo, así que primero rompe la
     * compilación, y si alguien le escribe una rama, esta prueba exige que esa
     * rama tenga chip.
     */
    @Test
    fun `los cinco valores legados caen en su chip, uno y solo uno`() {
        assertEquals(
            mapOf(
                EstadoCobranza.PENDIENTE to SegmentoDeCobranza.SIN_VISITAR,
                EstadoCobranza.VISITADO to SegmentoDeCobranza.VOLVER_A_VISITAR,
                EstadoCobranza.VOLVER_VISITAR to SegmentoDeCobranza.VOLVER_A_VISITAR,
                EstadoCobranza.NO_PAGADO to SegmentoDeCobranza.YA_NO_ESTA_SEMANA,
                EstadoCobranza.PAGADO to SegmentoDeCobranza.PAGADOS
            ),
            EstadoCobranza.entries.associateWith(::chipDe)
        )
    }

    /**
     * **La pestaña `POR VISITAR` es exactamente *sin visitar* ∪ *volver a
     * visitar***, que es el conjunto que heredó su orden. `CarteraEnPantalla`
     * ordena los cuatro chips, así que ese conjunto conserva el orden que ya
     * tenía y ningún otro chip se lo roba.
     */
    @Test
    fun `la pestana POR VISITAR es exactamente sin visitar mas volver a visitar`() {
        val porVisitar = setOf(
            EstadoCobranza.VOLVER_VISITAR,
            EstadoCobranza.PENDIENTE,
            EstadoCobranza.VISITADO
        )
        assertEquals(
            setOf(SegmentoDeCobranza.SIN_VISITAR, SegmentoDeCobranza.VOLVER_A_VISITAR),
            porVisitar.map(::chipDe).toSet()
        )
    }

    /**
     * **La pestaña `PAGADOS` tiene por fin su equivalente exacto.** Es la mejora
     * de esta ronda sobre los chips anteriores, donde lo cobrado no tenía chip y
     * sólo aparecía bajo *todos*; al retirarse `TODOS`, sin este chip habría
     * desaparecido de la pantalla.
     *
     * Control positivo pegado: ningún otro valor legado cae en *pagados*. Sin
     * esto, un `contiene` que devolviera `true` para todo dejaría pasar la
     * igualdad de arriba.
     */
    @Test
    fun `la pestana PAGADOS es exactamente el chip pagados`() {
        assertEquals(SegmentoDeCobranza.PAGADOS, chipDe(EstadoCobranza.PAGADO))
        assertEquals(
            listOf(EstadoCobranza.PAGADO),
            EstadoCobranza.entries.filter { chipDe(it) == SegmentoDeCobranza.PAGADOS }
        )
    }

    /**
     * **`NO_PAGADO` cambió de vecindario, y se escribe aquí para que no sea
     * sorpresa.** Vivía en la pestaña `VISITADOS`, que iba sin orden explícito;
     * con los chips anteriores caía en *vencidos*, junto a lo que había que
     * volver a tocar. Ahora cae en *ya no esta semana*: la cuenta se negó, hay
     * conflicto, y volver a esa puerta esta semana no ayuda — la misma
     * consecuencia para el día de hoy que un compromiso a futuro, aunque el
     * motivo sea el contrario.
     *
     * Y ya no comparte chip con "visité, vuelvo": eso es lo que separa "se negó"
     * de "pasé y no se resolvió", que la pestaña vieja mezclaba.
     */
    @Test
    fun `lo que estaba en VISITADOS ahora es ya no esta semana, y se separa de volver a visitar`() {
        assertEquals(SegmentoDeCobranza.YA_NO_ESTA_SEMANA, chipDe(EstadoCobranza.NO_PAGADO))
        assertEquals(SegmentoDeCobranza.VOLVER_A_VISITAR, chipDe(EstadoCobranza.VISITADO))
    }

    /**
     * **Ningún valor legado se queda sin chip.** Con `TODOS` retirado esto ya no
     * es una formalidad: un valor sin chip no se ve en **ninguna** parte de la
     * pantalla, y son filas viejas que sólo tienen esa columna.
     */
    @Test
    fun `ningun valor legado se queda fuera del tablero`() {
        EstadoCobranza.entries.forEach { legado ->
            val chips = chipsDe(legado)
            assertEquals("$legado no se ve en ningún chip", 1, chips.size)
        }
    }
}
