package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La partición, que es el invariante de esta pantalla.**
 *
 * Los cuatro chips tienen que ser **disjuntos** —ninguna cuenta puede aparecer
 * bajo dos— y **cubrir el catálogo entero** —ninguna cuenta puede quedarse fuera
 * de los cuatro—. La segunda mitad es la que de verdad protege al cobrador:
 * desde que `TODOS` se retiró no queda ningún chip que lo enseñe todo, así que
 * un estado sin casa **desaparece del tablero** en vez de refugiarse en un chip
 * general. Eso es precisamente lo que hacían las tres pestañas de `SalesScreen`.
 *
 * ## Por qué se recorre el enum y no una lista escrita a mano
 *
 * Porque el riesgo no es que hoy esté mal: es que mañana alguien agregue un
 * noveno estado. Una lista escrita a mano seguiría verde el día que eso pase.
 * Recorrer [TratoDelEstado] y [EstadoCuenta] obliga a que el estado nuevo
 * aparezca, y [representanteDe] —un `when` **exhaustivo y sin `else`**— hace
 * que además no compile hasta que alguien decida qué dato lo representa.
 *
 * ## El reloj
 *
 * [hoy] es una fecha fija que se inyecta en cada llamada a
 * [SegmentoDeCobranza.contiene]. **Nunca `LocalDate.now()`**: la mitad de las
 * reglas de esta clase son comparaciones contra hoy, y una prueba que leyera el
 * reloj real pasaría o fallaría según el día en que se corra.
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
        assertEquals("$estado no cayó en exactamente un chip: $chips", 1, chips.size)
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

    /**
     * **Cada trato cae en exactamente un chip.** Recorre [TratoDelEstado.entries],
     * no una lista escrita a mano: un trato nuevo entra solo a esta compuerta.
     *
     * Y se afirma además **cuál** es ese chip, porque "tiene casa" sin decir cuál
     * dejaría pasar que mañana "se negó" se mude a *pagados* sin que nada chille.
     */
    @Test
    fun `los ocho tratos tienen casa, y exactamente una`() {
        val casas = TratoDelEstado.entries.associateWith { chipUnicoDe(representanteDe(it)) }

        assertEquals(
            mapOf(
                TratoDelEstado.SIN_TRABAJAR to SegmentoDeCobranza.SIN_VISITAR,
                TratoDelEstado.PARCIAL to SegmentoDeCobranza.VOLVER_A_VISITAR,
                TratoDelEstado.REGRESAS to SegmentoDeCobranza.VOLVER_A_VISITAR,
                TratoDelEstado.NADIE to SegmentoDeCobranza.VOLVER_A_VISITAR,
                TratoDelEstado.ESCALAR to SegmentoDeCobranza.YA_NO_ESTA_SEMANA,
                // Los dos fechados van con fecha FUTURA — ver `representanteDe`.
                TratoDelEstado.DIFERIDO to SegmentoDeCobranza.YA_NO_ESTA_SEMANA,
                TratoDelEstado.CITA to SegmentoDeCobranza.YA_NO_ESTA_SEMANA,
                TratoDelEstado.PAGADO to SegmentoDeCobranza.PAGADOS
            ),
            casas
        )
    }

    /**
     * **Y el catálogo de ocho también**, por el otro extremo del puente: lo que
     * la pantalla consume son [EstadoCuenta], no tratos, y los dos enums no
     * tienen por qué crecer al mismo tiempo.
     *
     * Sin dato adjunto a propósito: es la forma en que llega un estado del que
     * nadie capturó fecha ni hora. Las guardas de `tratoDe` mandan esos dos a
     * `REGRESAS`, así que **los ocho tienen casa igual**.
     */
    @Test
    fun `los ocho estados del catalogo tienen casa, aun sin fecha ni hora`() {
        EstadoCuenta.entries.forEach { valor ->
            val chips = chipsDe(estado(valor))
            assertEquals("$valor no cayó en exactamente un chip: $chips", 1, chips.size)
        }
    }

    /**
     * **Disjuntos**, sobre el corpus completo —los ocho tratos más las seis
     * variantes de fecha y los dos casos sin fecha—. Ningún caso puede caer en
     * dos chips: una misma puerta contada dos veces en la fila de filtros es un
     * tablero que miente.
     */
    @Test
    fun `los cuatro chips son disjuntos`() {
        corpus().forEach { caso ->
            val chips = chipsDe(caso)
            assertTrue("$caso cayó en más de un chip: $chips", chips.size <= 1)
        }
    }

    /** Los ocho tratos, las seis variantes de fecha y los dos casos sin fecha. */
    private fun corpus(): List<EstadoDelPeriodo> = TratoDelEstado.entries.map(::representanteDe) +
        listOf(
            promesa(ayer),
            promesa(hoy),
            promesa(manana),
            promesa(null),
            cita(ayer),
            cita(hoy),
            cita(manana),
            cita(null)
        )

    // ─── la fecha decide, no el tipo de compromiso ───────────────────────────

    /**
     * **Una promesa vencida es trabajo pendiente, no un acuerdo.** Es la que más
     * urge tocar: el cliente quedó de pagar y no pagó. Archivarla bajo "ya no
     * esta semana" sería esconderla donde nadie la busca.
     */
    @Test
    fun `una promesa de AYER es volver a visitar`() {
        assertEquals(SegmentoDeCobranza.VOLVER_A_VISITAR, chipUnicoDe(promesa(ayer)))
    }

    /**
     * **La de hoy también.** Es trabajo del día y tiene que aparecer en la lista
     * de pendientes, no en la de acuerdos a futuro. El matiz conocido —una cita
     * de las 4 de la tarde se ve ahí desde la mañana— está en el KDoc de
     * [SegmentoDeCobranza]: separarlas pediría un quinto chip.
     */
    @Test
    fun `una promesa de HOY es volver a visitar`() {
        assertEquals(SegmentoDeCobranza.VOLVER_A_VISITAR, chipUnicoDe(promesa(hoy)))
    }

    /** **A futuro hay acuerdo y nada que hacer hoy.** */
    @Test
    fun `una promesa de MANANA ya no es de esta semana`() {
        assertEquals(SegmentoDeCobranza.YA_NO_ESTA_SEMANA, chipUnicoDe(promesa(manana)))
    }

    /** La cita tiene la MISMA forma que la promesa, y por la misma razón. */
    @Test
    fun `una cita de AYER es volver a visitar`() {
        assertEquals(SegmentoDeCobranza.VOLVER_A_VISITAR, chipUnicoDe(cita(ayer)))
    }

    @Test
    fun `una cita de HOY es volver a visitar`() {
        assertEquals(SegmentoDeCobranza.VOLVER_A_VISITAR, chipUnicoDe(cita(hoy)))
    }

    @Test
    fun `una cita de MANANA ya no es de esta semana`() {
        assertEquals(SegmentoDeCobranza.YA_NO_ESTA_SEMANA, chipUnicoDe(cita(manana)))
    }

    /**
     * **Control positivo de las seis de arriba.** Si [SegmentoDeCobranza.contiene]
     * ignorara la fecha y devolviera un chip fijo por tipo de compromiso, las seis
     * pruebas anteriores podrían seguir pasando de a pares. Ésta exige que el
     * MISMO estado, movido sólo el reloj, cambie de chip — que es la afirmación
     * entera: *la fecha decide, no el tipo de compromiso*.
     */
    @Test
    fun `mover solo la fecha cambia de chip, con promesa y con cita`() {
        listOf(::promesa, ::cita).forEach { compromiso ->
            assertEquals(
                listOf(
                    SegmentoDeCobranza.VOLVER_A_VISITAR,
                    SegmentoDeCobranza.VOLVER_A_VISITAR,
                    SegmentoDeCobranza.YA_NO_ESTA_SEMANA
                ),
                listOf(ayer, hoy, manana).map { chipUnicoDe(compromiso(it)) }
            )
        }
    }

    // ─── sin fecha ───────────────────────────────────────────────────────────

    /**
     * **Una promesa SIN fecha no llega aquí como promesa.** `tratoDe` la manda a
     * `REGRESAS` (regla heredada de la Task 16), así que el chip que la recibe es
     * *volver a visitar* — no un hueco. Sin fecha, "no cae esta semana" es una
     * afirmación que nada sostiene, y sacar una puerta de la ruta por un texto
     * que sólo dice "regrese" es justo lo que aquella guarda impidió.
     */
    @Test
    fun `una promesa sin fecha es volver a visitar, no un hueco`() {
        assertEquals(SegmentoDeCobranza.VOLVER_A_VISITAR, chipUnicoDe(promesa(null)))
    }

    /** Una cita SIN hora tiene la misma guarda y el mismo destino. */
    @Test
    fun `una cita sin hora es volver a visitar`() {
        assertEquals(
            SegmentoDeCobranza.VOLVER_A_VISITAR,
            chipUnicoDe(estado(EstadoCuenta.CITA_A_UNA_HORA))
        )
    }

    /**
     * **Una cita con hora pero SIN día no cae en ningún chip**, y está escrito en
     * el KDoc de [SegmentoDeCobranza]: sin día, nada sostiene que sea de hoy ni
     * que ya pasó, y ubicarla sería adivinar.
     *
     * Es el único hueco de la partición, y **el dominio no lo puede producir**:
     * `TipoVisitaCatalogo.estadoDe` sólo devuelve `CITA_A_UNA_HORA` cuando
     * `fechaCita != null` (`tieneCita`), y `CitaEstructurada.fecha` ni siquiera es
     * anulable. O sea que este estado sólo existe armado a mano, como acá. Se
     * prueba para que el día en que alguien afloje esa regla del catálogo, el
     * hueco aparezca en esta compuerta y no en el teléfono de un cobrador —
     * porque sin `TODOS` una cuenta sin chip **ya no se ve en ningún lado**.
     */
    @Test
    fun `una cita con hora pero SIN dia no cae en ningun chip`() {
        assertEquals(emptyList<SegmentoDeCobranza>(), chipsDe(cita(null)))
    }
}
