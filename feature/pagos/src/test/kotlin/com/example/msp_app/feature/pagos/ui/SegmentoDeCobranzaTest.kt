package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cada chip, contra el catálogo de ocho.
 *
 * Los tres chips de trabajo tienen que ser **disjuntos** y **cubrir todo lo que
 * pide trabajo**: si un estado se cae de los tres, hay trabajo que la pantalla
 * esconde, que es exactamente lo que hacían las tres pestañas viejas.
 */
class SegmentoDeCobranzaTest {

    private val hoyDePrueba = ListaFixtures.HOY

    private fun estado(
        estado: EstadoCuenta,
        fechaPromesa: LocalDate? = null,
        fechaCita: LocalDate? = null,
        horaCita: LocalTime? = null
    ) = EstadoDelPeriodo(
        estado = estado,
        abonoDelPeriodo = ListaFixtures.dinero("0"),
        parcialidad = ListaFixtures.dinero("350"),
        fechaPromesa = fechaPromesa,
        fechaCita = fechaCita,
        horaCita = horaCita
    )

    private fun chipsDe(estado: EstadoDelPeriodo): List<SegmentoDeCobranza> =
        SegmentoDeCobranza.entries.filter { it.contiene(estado, hoyDePrueba) }

    @Test
    fun `todos incluye absolutamente todo, incluso lo cobrado`() {
        EstadoCuenta.entries.forEach { valor ->
            assertTrue(
                "$valor se cayó de 'todos'",
                SegmentoDeCobranza.TODOS.contiene(estado(valor), hoyDePrueba)
            )
        }
    }

    @Test
    fun `sin visitar es exactamente lo que nadie ha trabajado`() {
        assertEquals(
            listOf(SegmentoDeCobranza.TODOS, SegmentoDeCobranza.SIN_VISITAR),
            chipsDe(estado(EstadoCuenta.SIN_TOCAR))
        )
    }

    @Test
    fun `abono parcial, visite vuelvo, no estaba y se nego son vencidos`() {
        listOf(
            EstadoCuenta.ABONO_PARCIAL,
            EstadoCuenta.VISITE_VUELVO,
            EstadoCuenta.NO_ESTABA,
            EstadoCuenta.SE_NEGO
        ).forEach { valor ->
            assertEquals(
                "$valor no cayó en vencidos",
                listOf(SegmentoDeCobranza.TODOS, SegmentoDeCobranza.VENCIDOS),
                chipsDe(estado(valor))
            )
        }
    }

    @Test
    fun `lo cobrado no pide trabajo, asi que solo esta en todos`() {
        assertEquals(listOf(SegmentoDeCobranza.TODOS), chipsDe(estado(EstadoCuenta.PAGO)))
    }

    @Test
    fun `una promesa para hoy cae en hoy`() {
        assertEquals(
            listOf(SegmentoDeCobranza.TODOS, SegmentoDeCobranza.HOY),
            chipsDe(estado(EstadoCuenta.PROMETIO_PROXIMA, fechaPromesa = hoyDePrueba))
        )
    }

    @Test
    fun `una promesa que ya se paso cae en vencidos`() {
        assertEquals(
            listOf(SegmentoDeCobranza.TODOS, SegmentoDeCobranza.VENCIDOS),
            chipsDe(estado(EstadoCuenta.PROMETIO_PROXIMA, fechaPromesa = hoyDePrueba.minusDays(1)))
        )
    }

    @Test
    fun `una promesa futura no cae en ningun chip de trabajo`() {
        assertEquals(
            listOf(SegmentoDeCobranza.TODOS),
            chipsDe(estado(EstadoCuenta.PROMETIO_PROXIMA, fechaPromesa = hoyDePrueba.plusDays(7)))
        )
    }

    @Test
    fun `una promesa SIN fecha no se difiere, es trabajo vencido`() {
        // La regla heredada de la Task 16: sin fecha, `tratoDe` la manda a
        // REGRESAS. Si cayera en "hoy" o en ningún chip, el cobrador dejaría de
        // trabajar una puerta por un texto que solo dice "regrese".
        assertEquals(
            listOf(SegmentoDeCobranza.TODOS, SegmentoDeCobranza.VENCIDOS),
            chipsDe(estado(EstadoCuenta.PROMETIO_PROXIMA))
        )
    }

    @Test
    fun `una cita de HOY con hora es trabajo de hoy`() {
        assertEquals(
            listOf(SegmentoDeCobranza.TODOS, SegmentoDeCobranza.HOY),
            chipsDe(
                estado(
                    EstadoCuenta.CITA_A_UNA_HORA,
                    fechaCita = hoyDePrueba,
                    horaCita = LocalTime.of(17, 30)
                )
            )
        )
    }

    /**
     * El defecto que la revisión encontró antes de que Task 19 lo armara: la
     * rama decía `this == HOY` a secas, así que la cita del lunes salía bajo
     * "hoy" el jueves solo por caer dentro del periodo.
     */
    @Test
    fun `una cita de otro dia del periodo NO es trabajo de hoy`() {
        assertEquals(
            listOf(SegmentoDeCobranza.TODOS, SegmentoDeCobranza.VENCIDOS),
            chipsDe(
                estado(
                    EstadoCuenta.CITA_A_UNA_HORA,
                    fechaCita = hoyDePrueba.minusDays(3),
                    horaCita = LocalTime.of(16, 0)
                )
            )
        )
    }

    @Test
    fun `una cita futura no cae en ningun chip de trabajo`() {
        assertEquals(
            listOf(SegmentoDeCobranza.TODOS),
            chipsDe(
                estado(
                    EstadoCuenta.CITA_A_UNA_HORA,
                    fechaCita = hoyDePrueba.plusDays(2),
                    horaCita = LocalTime.of(16, 0)
                )
            )
        )
    }

    @Test
    fun `una cita con hora pero SIN dia no cuenta como hoy`() {
        assertFalse(
            SegmentoDeCobranza.HOY.contiene(
                estado(EstadoCuenta.CITA_A_UNA_HORA, horaCita = LocalTime.of(17, 30)),
                hoyDePrueba
            )
        )
    }

    @Test
    fun `una cita SIN hora tampoco se difiere`() {
        assertEquals(
            listOf(SegmentoDeCobranza.TODOS, SegmentoDeCobranza.VENCIDOS),
            chipsDe(estado(EstadoCuenta.CITA_A_UNA_HORA))
        )
    }

    @Test
    fun `los tres chips de trabajo son disjuntos`() {
        casosDeCadaEstado().forEach { caso ->
            val deTrabajo = chipsDe(caso).filter { it != SegmentoDeCobranza.TODOS }
            assertTrue("$caso cayó en más de un chip: $deTrabajo", deTrabajo.size <= 1)
        }
    }

    @Test
    fun `todo lo que pide trabajo tiene su chip`() {
        casosDeCadaEstado()
            .filter { EstadoCuentaUi.requiereAtencion(EstadoCuentaUi.tratoDe(it)) }
            .forEach { caso ->
                val deTrabajo = chipsDe(caso).filter { it != SegmentoDeCobranza.TODOS }
                assertFalse("$caso pide trabajo y no tiene chip", deTrabajo.isEmpty())
            }
    }

    /** Un caso por estado del catálogo, con las variantes que cambian de chip. */
    private fun casosDeCadaEstado(): List<EstadoDelPeriodo> =
        EstadoCuenta.entries.map { estado(it) } +
            listOf(
                estado(EstadoCuenta.PROMETIO_PROXIMA, fechaPromesa = hoyDePrueba),
                estado(EstadoCuenta.PROMETIO_PROXIMA, fechaPromesa = hoyDePrueba.minusDays(1)),
                estado(
                    EstadoCuenta.CITA_A_UNA_HORA,
                    fechaCita = hoyDePrueba,
                    horaCita = LocalTime.of(17, 30)
                )
            )
}
