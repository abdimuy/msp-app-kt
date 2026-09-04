package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.aEstadoCuenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **El puente entre las pestañas viejas y los chips nuevos.**
 *
 * La pantalla que esta lista reemplaza filtra por `Sale.ESTADO_COBRANZA`, el
 * enum legado de cinco valores, y aplica el orden **solo** a la pestaña
 * `POR VISITAR` (`VOLVER_VISITAR ∪ PENDIENTE ∪ VISITADO`). Si los chips nuevos
 * se definieran en otro vocabulario sin comprobar la correspondencia, el orden
 * seguiría aplicándose pero a otro conjunto, y nadie se enteraría.
 *
 * Aquí se recorre valor por valor con `EstadoCobranza.aEstadoCuenta()` —el
 * puente que `:core:database` ya tiene escrito para exactamente esto— y se
 * afirma en qué chip cae cada uno.
 *
 * Nota de alcance: `aEstadoCuenta()` sirve para **leer el histórico**, no para
 * decidir el periodo en curso. El periodo lo deriva `EstadoCuentaDeriver` del
 * dinero y de las visitas, y jamás consulta esa columna (la escribe
 * `PaymentsViewModel.savePayment` en `PAGADO` con cada pago, sin comparar el
 * monto contra `PARCIALIDAD`). Este test usa el puente para lo que existe.
 */
class SegmentosVsPestanasLegadasTest {

    private val hoyDePrueba = ListaFixtures.HOY

    private fun comoEstadoDelPeriodo(legado: EstadoCobranza) = EstadoDelPeriodo(
        estado = legado.aEstadoCuenta(),
        abonoDelPeriodo = ListaFixtures.dinero("0"),
        parcialidad = ListaFixtures.dinero("350")
    )

    private fun chipDe(legado: EstadoCobranza): SegmentoDeCobranza? = SegmentoDeCobranza.entries
        .filter { it != SegmentoDeCobranza.TODOS }
        .firstOrNull { it.contiene(comoEstadoDelPeriodo(legado), hoyDePrueba) }

    @Test
    fun `pendiente es sin visitar`() {
        assertEquals(SegmentoDeCobranza.SIN_VISITAR, chipDe(EstadoCobranza.PENDIENTE))
    }

    @Test
    fun `visitado y volver a visitar son vencidos`() {
        assertEquals(SegmentoDeCobranza.VENCIDOS, chipDe(EstadoCobranza.VISITADO))
        assertEquals(SegmentoDeCobranza.VENCIDOS, chipDe(EstadoCobranza.VOLVER_VISITAR))
    }

    @Test
    fun `no pagado es vencidos`() {
        assertEquals(SegmentoDeCobranza.VENCIDOS, chipDe(EstadoCobranza.NO_PAGADO))
    }

    @Test
    fun `pagado no tiene chip de trabajo, solo todos`() {
        assertEquals(null, chipDe(EstadoCobranza.PAGADO))
        assertTrue(
            SegmentoDeCobranza.TODOS.contiene(
                comoEstadoDelPeriodo(EstadoCobranza.PAGADO),
                hoyDePrueba
            )
        )
    }

    @Test
    fun `la pestana POR VISITAR es exactamente vencidos mas sin visitar`() {
        val porVisitar = setOf(
            EstadoCobranza.VOLVER_VISITAR,
            EstadoCobranza.PENDIENTE,
            EstadoCobranza.VISITADO
        )
        assertEquals(
            setOf(SegmentoDeCobranza.VENCIDOS, SegmentoDeCobranza.SIN_VISITAR),
            porVisitar.mapNotNull(::chipDe).toSet()
        )
    }

    /**
     * `NO_PAGADO` vivía en la pestaña `VISITADOS`, que iba **sin orden
     * explícito**, y ahora cae en *vencidos*, que sí se ordena. Es la
     * consecuencia directa de la decisión de esta tarea —el orden aplica a los
     * cuatro segmentos— y se afirma aquí para que quede escrita y no sea una
     * sorpresa al comparar las dos pantallas.
     */
    @Test
    fun `lo que estaba en VISITADOS sin orden ahora se ordena en vencidos`() {
        assertEquals(SegmentoDeCobranza.VENCIDOS, chipDe(EstadoCobranza.NO_PAGADO))
    }

    @Test
    fun `ningun valor legado se queda sin traduccion`() {
        EstadoCobranza.entries.forEach { legado ->
            // `aEstadoCuenta()` es un `when` exhaustivo: si alguien agrega un
            // sexto valor, esto revienta antes que la pantalla.
            assertTrue(
                "$legado no cae en 'todos'",
                SegmentoDeCobranza.TODOS.contiene(comoEstadoDelPeriodo(legado), hoyDePrueba)
            )
        }
    }
}
