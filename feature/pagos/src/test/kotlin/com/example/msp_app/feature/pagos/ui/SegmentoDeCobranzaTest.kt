package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **La partición, que es el invariante de esta pantalla** (D18, E-APP-070).
 *
 * Las tres pastillas son **disjuntas** y **cubren el catálogo entero**: ninguna
 * cuenta aparece bajo dos ni se queda fuera de las tres. La regla vive en
 * `columnaDe` (`:core:common`); aquí se prueba que la pantalla la consume tal
 * cual, incluidos los dos casos que cambiaron contra los cuatro chips: el abono
 * parcial pasa a Pagados y toda cita (y "se negó") pasa a Por visitar.
 *
 * [hoy] es una fecha fija inyectada: nunca `LocalDate.now()`.
 */
class SegmentoDeCobranzaTest {

    /** Martes 1-sep-2026. El único "hoy" de este archivo. */
    private val hoy: LocalDate = ListaFixtures.HOY

    private val ayer: LocalDate = hoy.minusDays(1)
    private val manana: LocalDate = hoy.plusDays(1)

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

    private fun promesa(fecha: LocalDate?) =
        estado(EstadoCuenta.PROMETIO_PROXIMA, fechaPromesa = fecha)

    /** Una cita **con hora** —sin ella `tratoDe` la manda a `REGRESAS`— y con el día dado. */
    private fun cita(fecha: LocalDate?) = estado(
        EstadoCuenta.CITA_A_UNA_HORA,
        fechaCita = fecha,
        horaCita = LocalTime.of(16, 0)
    )

    private fun chipsDe(estado: EstadoDelPeriodo): List<SegmentoDeCobranza> =
        SegmentoDeCobranza.entries.filter { it.contiene(estado, hoy) }

    private fun chipUnicoDe(estado: EstadoDelPeriodo): SegmentoDeCobranza {
        val chips = chipsDe(estado)
        assertEquals("$estado no cayó en exactamente una pastilla: $chips", 1, chips.size)
        return chips.single()
    }

    /**
     * Un caso representativo por trato. El `when` es **exhaustivo y sin `else`**:
     * un noveno trato no compila hasta que alguien escriba con qué dato se
     * representa, que es justo la decisión que no puede tomarse por omisión.
     *
     * [TratoDelEstado.DIFERIDO] y [TratoDelEstado.CITA] se representan **con
     * fecha** porque son los únicos dos tratos que sólo existen con ella: sin
     * fecha `tratoDe` ni siquiera produce la promesa, y la cita no se puede
     * ubicar. Los casos sin fecha se cobran aparte, abajo.
     */
    private fun representanteDe(trato: TratoDelEstado): EstadoDelPeriodo = when (trato) {
        TratoDelEstado.PAGADO -> estado(EstadoCuenta.PAGO)
        TratoDelEstado.PARCIAL -> estado(EstadoCuenta.ABONO_PARCIAL)
        TratoDelEstado.REGRESAS -> estado(EstadoCuenta.VISITE_VUELVO)
        TratoDelEstado.DIFERIDO -> promesa(manana)
        TratoDelEstado.ESCALAR -> estado(EstadoCuenta.SE_NEGO)
        TratoDelEstado.CITA -> cita(manana)
        TratoDelEstado.NADIE -> estado(EstadoCuenta.NO_ESTABA)
        TratoDelEstado.SIN_TRABAJAR -> estado(EstadoCuenta.SIN_TOCAR)
    }

    // ─── el invariante ───────────────────────────────────────────────────────

    @Test
    fun `los ocho tratos tienen casa, y exactamente una`() {
        val casas = TratoDelEstado.entries.associateWith { chipUnicoDe(representanteDe(it)) }

        assertEquals(
            mapOf(
                TratoDelEstado.SIN_TRABAJAR to SegmentoDeCobranza.POR_VISITAR,
                TratoDelEstado.PARCIAL to SegmentoDeCobranza.PAGADOS,
                TratoDelEstado.REGRESAS to SegmentoDeCobranza.POR_VISITAR,
                TratoDelEstado.NADIE to SegmentoDeCobranza.POR_VISITAR,
                TratoDelEstado.ESCALAR to SegmentoDeCobranza.POR_VISITAR,
                // La promesa lleva fecha FUTURA — ver `representanteDe`.
                TratoDelEstado.DIFERIDO to SegmentoDeCobranza.NO_PAGA,
                // E-APP-070: toda cita, aun la de mañana, es trabajo por visitar.
                TratoDelEstado.CITA to SegmentoDeCobranza.POR_VISITAR,
                TratoDelEstado.PAGADO to SegmentoDeCobranza.PAGADOS
            ),
            casas
        )
    }

    @Test
    fun `los ocho estados del catalogo tienen casa, aun sin fecha ni hora`() {
        EstadoCuenta.entries.forEach { valor ->
            val chips = chipsDe(estado(valor))
            assertEquals("$valor no cayó en exactamente una pastilla: $chips", 1, chips.size)
        }
    }

    @Test
    fun `las tres pastillas son disjuntas y cubren todo el corpus`() {
        corpus().forEach { caso -> chipUnicoDe(caso) }
    }

    private fun corpus(): List<EstadoDelPeriodo> = TratoDelEstado.entries.map(::representanteDe) +
        listOf(
            promesa(ayer),
            promesa(hoy),
            promesa(manana),
            promesa(null),
            cita(ayer),
            cita(hoy),
            cita(manana),
            cita(null),
            estado(EstadoCuenta.CITA_A_UNA_HORA)
        )

    // ─── lo que cambió contra los cuatro chips ───────────────────────────────

    @Test
    fun `E-APP-070 el abono parcial es Pagados`() {
        assertEquals(SegmentoDeCobranza.PAGADOS, chipUnicoDe(estado(EstadoCuenta.ABONO_PARCIAL)))
    }

    @Test
    fun `E-APP-070 se negó es Por visitar`() {
        assertEquals(SegmentoDeCobranza.POR_VISITAR, chipUnicoDe(estado(EstadoCuenta.SE_NEGO)))
    }

    @Test
    fun `E-APP-070 una cita con hora y sin dia cae en Por visitar`() {
        assertEquals(SegmentoDeCobranza.POR_VISITAR, chipUnicoDe(cita(null)))
    }

    @Test
    fun `E-APP-070 una cita nunca sale de Por visitar, ni la de manana`() {
        listOf(ayer, hoy, manana).forEach {
            assertEquals(SegmentoDeCobranza.POR_VISITAR, chipUnicoDe(cita(it)))
        }
    }

    // ─── la fecha decide, sólo para la promesa ───────────────────────────────

    @Test
    fun `una promesa de AYER o de HOY es Por visitar`() {
        assertEquals(SegmentoDeCobranza.POR_VISITAR, chipUnicoDe(promesa(ayer)))
        assertEquals(SegmentoDeCobranza.POR_VISITAR, chipUnicoDe(promesa(hoy)))
    }

    @Test
    fun `una promesa de MANANA es No paga`() {
        assertEquals(SegmentoDeCobranza.NO_PAGA, chipUnicoDe(promesa(manana)))
    }

    @Test
    fun `mover solo la fecha de la promesa cambia de pastilla`() {
        assertEquals(
            listOf(
                SegmentoDeCobranza.POR_VISITAR,
                SegmentoDeCobranza.POR_VISITAR,
                SegmentoDeCobranza.NO_PAGA
            ),
            listOf(ayer, hoy, manana).map { chipUnicoDe(promesa(it)) }
        )
    }

    @Test
    fun `una promesa sin fecha es Por visitar, no un hueco`() {
        assertEquals(SegmentoDeCobranza.POR_VISITAR, chipUnicoDe(promesa(null)))
    }

    @Test
    fun `una cita sin hora es Por visitar`() {
        assertEquals(
            SegmentoDeCobranza.POR_VISITAR,
            chipUnicoDe(estado(EstadoCuenta.CITA_A_UNA_HORA))
        )
    }

    // ─── la forma ────────────────────────────────────────────────────────────

    @Test
    fun `E-APP-070 las etiquetas y las columnas son las del escritorio`() {
        assertEquals(
            listOf("Por visitar", "No paga", "Pagados"),
            SegmentoDeCobranza.entries.map {
                it.etiqueta
            }
        )
        assertEquals(
            listOf("por_visitar", "no_paga", "pagados"),
            SegmentoDeCobranza.entries.map { it.columna.codigo }
        )
    }
}
