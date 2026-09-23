package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Cuándo tocar una fila pregunta, y cuándo abre el mapa sin preguntar.**
 *
 * La regla que el dueño pidió tiene dos mitades —*de hoy* y *el último de esa
 * cuenta*— y las dos se pueden perder en silencio: una implementación que
 * preguntara siempre abriría la hoja sobre un abono de marzo, y una que mirara
 * sólo la fecha ofrecería el ticket del penúltimo cobro del día. Por eso aquí se
 * afirman las **cuatro combinaciones**, no sólo la que enciende la hoja.
 *
 * Es dominio puro: el "hoy" entra por parámetro, así que este test fija el día y
 * su resultado no cambia mañana.
 */
class ToqueDelContactoTest {

    // --- Las cuatro combinaciones ---------------------------------------------

    @Test
    fun `de hoy y el ultimo de su cuenta, pregunta`() {
        val contactos = listOf(EL_DE_HOY, EL_DE_HOY_ANTERIOR, EL_VIEJO)

        assertEquals(
            "el cobro de hoy que cierra la cuenta tiene que ofrecer el ticket",
            ToqueDelContacto.PREGUNTAR,
            ToqueDelContacto.de(EL_DE_HOY, contactos, HOY)
        )
    }

    @Test
    fun `de hoy pero no el ultimo, abre el mapa`() {
        val contactos = listOf(EL_DE_HOY, EL_DE_HOY_ANTERIOR, EL_VIEJO)

        assertEquals(
            "sólo el ÚLTIMO cobro del día ofrece el ticket, no todos los de hoy",
            ToqueDelContacto.MAPA,
            ToqueDelContacto.de(EL_DE_HOY_ANTERIOR, contactos, HOY)
        )
    }

    @Test
    fun `de otro dia y el ultimo de su cuenta, abre el mapa`() {
        // `EL_VIEJO` es el último —y el único— de la cuenta 77.
        val contactos = listOf(EL_DE_HOY, EL_DE_HOY_ANTERIOR, EL_VIEJO)

        assertEquals(
            "ser el último no basta: el ticket de otro día no se reimprime",
            ToqueDelContacto.MAPA,
            ToqueDelContacto.de(EL_VIEJO, contactos, HOY)
        )
    }

    @Test
    fun `de otro dia y no el ultimo, abre el mapa`() {
        val penultimoViejo = cobro(id = "viejo-1", cuenta = 77, cuando = "2026-08-30T15:00:00Z")
        val contactos = listOf(EL_DE_HOY, EL_VIEJO, penultimoViejo)

        assertEquals(
            ToqueDelContacto.MAPA,
            ToqueDelContacto.de(penultimoViejo, contactos, HOY)
        )
    }

    // --- El borde: un solo pago en la cuenta ----------------------------------

    /**
     * El único pago de la cuenta, hecho hoy. Es el último **y** es de hoy, así
     * que pregunta. Vale la pena afirmarlo aparte porque es el caso real del
     * primer abono de una venta nueva: si la comparación de "el último"
     * estuviera escrita como *"hay alguno posterior"* al revés, una lista de un
     * solo elemento es donde se rompería.
     */
    @Test
    fun `el unico pago de la cuenta, hecho hoy, pregunta`() {
        assertEquals(
            ToqueDelContacto.PREGUNTAR,
            ToqueDelContacto.de(EL_DE_HOY, listOf(EL_DE_HOY), HOY)
        )
    }

    // --- Lo que no compite ----------------------------------------------------

    /**
     * Una visita registrada DESPUÉS del cobro no le quita al cobro el ser el
     * último de su cuenta: lo que se reimprime es el ticket de un pago, y una
     * puerta tocada no es un pago.
     */
    @Test
    fun `una visita posterior no le quita el ultimo al cobro`() {
        val visita = ContactoDeCobranza(
            id = "visita",
            fecha = Instant.parse("2026-09-01T23:00:00Z"),
            etiqueta = "No estaba",
            nota = null,
            estado = EstadoCuenta.VISITE_VUELVO,
            importe = null,
            tipo = TipoDeContacto.VISITA,
            ventaId = CUENTA,
            ubicacion = PUNTO
        )

        assertEquals(
            ToqueDelContacto.PREGUNTAR,
            ToqueDelContacto.de(EL_DE_HOY, listOf(visita, EL_DE_HOY), HOY)
        )
    }

    /** Una visita nunca ofrece ticket: no hay papel de una puerta tocada. */
    @Test
    fun `una visita de hoy abre el mapa`() {
        val visita = ContactoDeCobranza(
            id = "visita",
            fecha = Instant.parse("2026-09-01T18:00:00Z"),
            etiqueta = "Prometió pagar",
            nota = null,
            estado = EstadoCuenta.PROMETIO_PROXIMA,
            importe = null,
            tipo = TipoDeContacto.VISITA,
            ventaId = CUENTA,
            ubicacion = PUNTO
        )

        assertEquals(ToqueDelContacto.MAPA, ToqueDelContacto.de(visita, listOf(visita), HOY))
    }

    /**
     * **"El último" es de la CUENTA, no del cliente.** Dos ventas del mismo
     * domicilio cobradas el mismo día: las dos filas preguntan, porque los dos
     * tickets existen. Con la regla puesta sobre el cliente, el papel de la
     * primera cuenta sería inalcanzable — justo el caso que esto viene a
     * resolver.
     */
    @Test
    fun `dos cuentas cobradas hoy ofrecen sus dos tickets`() {
        val deLaOtraCuenta = cobro(
            id = "otra-cuenta",
            cuenta = OTRA_CUENTA,
            cuando = "2026-09-01T16:00:00Z"
        )
        val contactos = listOf(EL_DE_HOY, deLaOtraCuenta)

        assertEquals(
            ToqueDelContacto.PREGUNTAR,
            ToqueDelContacto.de(EL_DE_HOY, contactos, HOY)
        )
        assertEquals(
            "el cobro más viejo del día, pero el último de SU cuenta",
            ToqueDelContacto.PREGUNTAR,
            ToqueDelContacto.de(deLaOtraCuenta, contactos, HOY)
        )
    }

    // --- Sin punto medido -----------------------------------------------------

    /**
     * **El cobro de hoy capturado sin señal SÍ se toca, y abre el ticket.**
     *
     * Éste es el renglón que más se va a querer reimprimir —un teléfono sin
     * señal suele ser un teléfono cuya impresora también falló— y hasta la
     * ronda de arreglo era el único pago del día que no se alcanzaba: sin punto,
     * la fila era inerte. El nombre de este test está invertido respecto al que
     * tenía a propósito, porque lo que afirmaba dejó de ser cierto.
     *
     * No pregunta: sin punto no hay nada que elegir, y una hoja con una sola
     * opción útil sería peor que ir derecho.
     */
    @Test
    fun `sin punto medido, el cobro de hoy de esa cuenta abre el ticket directo`() {
        val sinPunto = EL_DE_HOY.copy(ubicacion = null)

        assertEquals(
            ToqueDelContacto.TICKET,
            ToqueDelContacto.de(sinPunto, listOf(sinPunto), HOY)
        )
    }

    /**
     * Sin punto, todo lo demás sigue sin tocarse. Abrir el ticket es la
     * excepción del cobro del día, no una puerta nueva para cualquier renglón
     * mudo: un abono de agosto sin coordenadas no lleva a ninguna parte, y una
     * fila tocable que no hace nada es peor que una que no se toca.
     */
    @Test
    fun `sin punto medido, un cobro de otro dia no se toca`() {
        val sinPunto = EL_VIEJO.copy(ubicacion = null)

        assertEquals(
            ToqueDelContacto.NADA,
            ToqueDelContacto.de(sinPunto, listOf(EL_DE_HOY, sinPunto), HOY)
        )
    }

    /** Sin punto y sin ser el último del día: tampoco. */
    @Test
    fun `sin punto medido, un cobro de hoy que no es el ultimo no se toca`() {
        val sinPunto = EL_DE_HOY_ANTERIOR.copy(ubicacion = null)

        assertEquals(
            ToqueDelContacto.NADA,
            ToqueDelContacto.de(sinPunto, listOf(EL_DE_HOY, sinPunto), HOY)
        )
    }

    /** Una visita sin punto sigue sin tocarse: no hay papel de una puerta tocada. */
    @Test
    fun `sin punto medido, una visita de hoy no se toca`() {
        val visita = ContactoDeCobranza(
            id = "visita",
            fecha = Instant.parse("2026-09-01T23:30:00Z"),
            etiqueta = "No estaba",
            nota = null,
            estado = EstadoCuenta.VISITE_VUELVO,
            importe = null,
            tipo = TipoDeContacto.VISITA,
            ventaId = CUENTA,
            ubicacion = null
        )

        assertEquals(ToqueDelContacto.NADA, ToqueDelContacto.de(visita, listOf(visita), HOY))
    }

    /** Un contacto que no está en la lista no puede ser el último de nada. */
    @Test
    fun `con la lista vacia el toque cae siempre en el mapa`() {
        assertEquals(
            ToqueDelContacto.MAPA,
            ToqueDelContacto.de(EL_DE_HOY, emptyList(), HOY)
        )
    }

    private companion object {
        /** Martes 1-sep-2026 — el mismo "hoy" que el resto de las fixtures del módulo. */
        val HOY: LocalDate = LocalDate.of(2026, 9, 1)

        const val CUENTA = 42
        const val OTRA_CUENTA = 43

        val PUNTO = UbicacionDelCobro(lat = 18.9061, lng = -98.4376)

        fun cobro(id: String, cuenta: Int, cuando: String) = ContactoDeCobranza(
            id = id,
            fecha = Instant.parse(cuando),
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = Money.of(BigDecimal("220.00")),
            tipo = TipoDeContacto.COBRO,
            ventaId = cuenta,
            ubicacion = PUNTO
        )

        /** 1-sep 17:00 CDMX: el cobro más reciente de la cuenta 42. */
        val EL_DE_HOY = cobro(id = "hoy-2", cuenta = CUENTA, cuando = "2026-09-01T23:00:00Z")

        /** 1-sep 10:00 CDMX: el mismo día y la misma cuenta, pero no el último. */
        val EL_DE_HOY_ANTERIOR =
            cobro(id = "hoy-1", cuenta = CUENTA, cuando = "2026-09-01T16:00:00Z")

        /** 31-ago: el último —y el único— de la cuenta 77. */
        val EL_VIEJO = cobro(id = "viejo-2", cuenta = 77, cuando = "2026-08-31T18:00:00Z")
    }
}
