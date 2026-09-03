package com.example.msp_app.core.common.cobranza.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catálogo de literales: la reconciliación 8/10/11/14 y la garantía de que
 * TODO lo que puede llegar aterriza en exactamente un estado.
 */
class TipoVisitaCatalogoTest {

    // ── La reconciliación, verificada como números ──

    @Test
    fun `el catalogo cerrado tiene 14 literales`() {
        assertEquals(14, TipoVisitaCatalogo.LITERALES.size)
    }

    @Test
    fun `14 se parte en 11 vigentes mas 3 retirados`() {
        assertEquals(11, TipoVisitaCatalogo.LITERALES_VIGENTES.size)
        assertEquals(3, TipoVisitaCatalogo.LITERALES_RETIRADOS.size)
        assertEquals(
            TipoVisitaCatalogo.LITERALES,
            TipoVisitaCatalogo.LITERALES_VIGENTES + TipoVisitaCatalogo.LITERALES_RETIRADOS
        )
    }

    @Test
    fun `no hay literales repetidos en el catalogo`() {
        assertEquals(
            TipoVisitaCatalogo.LITERALES.size,
            TipoVisitaCatalogo.LITERALES.toSet().size
        )
    }

    @Test
    fun `los ocho estados son ocho`() {
        assertEquals(8, EstadoCuenta.entries.size)
    }

    // ── Cada uno de los 14 aterriza en exactamente un estado ──

    @Test
    fun `los 14 literales del catalogo son conocidos`() {
        TipoVisitaCatalogo.LITERALES.forEach { literal ->
            assertTrue(
                "'$literal' deberia estar en el catalogo",
                TipoVisitaCatalogo.esConocido(literal)
            )
        }
    }

    @Test
    fun `los 14 literales aterrizan en un estado no nulo`() {
        TipoVisitaCatalogo.LITERALES.forEach { literal ->
            assertNotNull("'$literal' se quedo sin estado", TipoVisitaCatalogo.estadoDe(literal))
        }
    }

    @Test
    fun `la tabla de agrupacion del brief - no estaba`() {
        assertEquals(
            EstadoCuenta.NO_ESTABA,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.NO_SE_ENCONTRABA)
        )
        assertEquals(
            EstadoCuenta.NO_ESTABA,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.CASA_CERRADA)
        )
        assertEquals(
            EstadoCuenta.NO_ESTABA,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.SOLO_MENORES)
        )
    }

    @Test
    fun `la tabla de agrupacion del brief - visite vuelvo`() {
        assertEquals(
            EstadoCuenta.VISITE_VUELVO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.SE_ESCONDE)
        )
        assertEquals(
            EstadoCuenta.VISITE_VUELVO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.NO_RESPONDE)
        )
        assertEquals(
            EstadoCuenta.VISITE_VUELVO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.SE_ESCUCHAN_RUIDOS)
        )
    }

    @Test
    fun `la tabla de agrupacion del brief - se nego`() {
        assertEquals(
            EstadoCuenta.SE_NEGO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.NO_VA_A_DAR_PAGO)
        )
        assertEquals(
            EstadoCuenta.SE_NEGO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.TIENE_PERO_NO_PAGA)
        )
        assertEquals(
            EstadoCuenta.SE_NEGO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.FUE_GROSERO)
        )
    }

    @Test
    fun `la tabla de agrupacion del brief - prometio`() {
        assertEquals(
            EstadoCuenta.PROMETIO_PROXIMA,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.PIDE_REAGENDAR)
        )
    }

    @Test
    fun `pide tiempo - la etiqueta 11 que el dialogo ya no ofrece - es visite vuelvo`() {
        assertEquals(
            EstadoCuenta.VISITE_VUELVO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.PIDE_TIEMPO)
        )
    }

    // ── Los tres retirados: la visita del teléfono viejo NO se pierde ──

    @Test
    fun `retirado - no va a dar pago cae en se nego, igual que su sucesor`() {
        assertEquals(
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.NO_VA_A_DAR_PAGO),
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.LEGACY_NO_VA_A_DAR_PAGO)
        )
        assertEquals(
            EstadoCuenta.SE_NEGO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.LEGACY_NO_VA_A_DAR_PAGO)
        )
    }

    @Test
    fun `retirado - dijo que no va a pagar, el que seguia llegando 852 veces, cae en se nego`() {
        assertEquals(
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.NO_VA_A_DAR_PAGO),
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.LEGACY_DIJO_QUE_NO_VA_A_PAGAR)
        )
        assertEquals(
            EstadoCuenta.SE_NEGO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.LEGACY_DIJO_QUE_NO_VA_A_PAGAR)
        )
    }

    @Test
    fun `retirado - se esconde y no sale cae en visite vuelvo, igual que su sucesor`() {
        assertEquals(
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.SE_ESCONDE),
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.LEGACY_SE_ESCONDE_Y_NO_SALE)
        )
        assertEquals(
            EstadoCuenta.VISITE_VUELVO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.LEGACY_SE_ESCONDE_Y_NO_SALE)
        )
    }

    @Test
    fun `los valores retirados son los medidos en Firebird, byte por byte`() {
        assertEquals("No va a dar pago", TipoVisitaCatalogo.LEGACY_NO_VA_A_DAR_PAGO)
        assertEquals("Dijo que no va a pagar", TipoVisitaCatalogo.LEGACY_DIJO_QUE_NO_VA_A_PAGAR)
        assertEquals("Se esconde y no sale", TipoVisitaCatalogo.LEGACY_SE_ESCONDE_Y_NO_SALE)
    }

    // ── Nada cae en la nada ──

    @Test
    fun `la cadena vacia de las 200 filas viejas aterriza en visite vuelvo`() {
        assertFalse(TipoVisitaCatalogo.esConocido(""))
        assertEquals(EstadoCuenta.VISITE_VUELVO, TipoVisitaCatalogo.estadoDe(""))
    }

    @Test
    fun `un literal jamas visto aterriza en visite vuelvo y no en sin tocar`() {
        val inventado = "Se lo llevo la grua"
        assertFalse(TipoVisitaCatalogo.esConocido(inventado))
        assertEquals(EstadoCuenta.VISITE_VUELVO, TipoVisitaCatalogo.estadoDe(inventado))
    }

    @Test
    fun `la comparacion es exacta - un acento de menos ya no es el mismo literal`() {
        assertFalse(TipoVisitaCatalogo.esConocido("Solo habia menores"))
        assertTrue(TipoVisitaCatalogo.esConocido("Solo había menores"))
    }

    // ── El alcance: idéntico a la Task 13 ──

    @Test
    fun `alcance cliente exactamente para las tres etiquetas de no estaba`() {
        val deAlcanceCliente = TipoVisitaCatalogo.LITERALES.filter {
            TipoVisitaCatalogo.alcanceDe(it) == VisitScope.CLIENTE
        }
        assertEquals(
            listOf(
                TipoVisitaCatalogo.NO_SE_ENCONTRABA,
                TipoVisitaCatalogo.CASA_CERRADA,
                TipoVisitaCatalogo.SOLO_MENORES
            ),
            deAlcanceCliente
        )
    }

    @Test
    fun `un literal desconocido cae en alcance venta - el default angosto de la Task 13`() {
        assertEquals(VisitScope.VENTA, TipoVisitaCatalogo.alcanceDe("cualquier cosa"))
    }

    @Test
    fun `el alcance del literal es el alcance de su estado`() {
        TipoVisitaCatalogo.LITERALES.forEach { literal ->
            assertEquals(
                "alcance inconsistente para '$literal'",
                TipoVisitaCatalogo.estadoDe(literal).alcance,
                TipoVisitaCatalogo.alcanceDe(literal)
            )
        }
    }

    // ── El alcance de los ocho estados (columna "Alcance" del brief) ──

    @Test
    fun `los dos estados de alcance cliente son no estaba y cita`() {
        val cliente = EstadoCuenta.entries.filter { it.alcance == VisitScope.CLIENTE }
        assertEquals(listOf(EstadoCuenta.CITA_A_UNA_HORA, EstadoCuenta.NO_ESTABA), cliente)
    }

    @Test
    fun `los otros seis estados son de alcance venta`() {
        val venta = EstadoCuenta.entries.filter { it.alcance == VisitScope.VENTA }
        assertEquals(
            listOf(
                EstadoCuenta.PAGO,
                EstadoCuenta.ABONO_PARCIAL,
                EstadoCuenta.VISITE_VUELVO,
                EstadoCuenta.PROMETIO_PROXIMA,
                EstadoCuenta.SE_NEGO,
                EstadoCuenta.SIN_TOCAR
            ),
            venta
        )
    }

    // ── Los dos estados que la Task 19 todavía debe habilitar ──

    @Test
    fun `cita a una hora no es alcanzable desde ningun literal - la trae la Task 19`() {
        val alcanzables = TipoVisitaCatalogo.LITERALES
            .map { TipoVisitaCatalogo.estadoDe(it) }
            .toSet()
        assertFalse(
            "CITA_A_UNA_HORA no debe derivarse de texto libre; llega con la captura de la Task 19",
            EstadoCuenta.CITA_A_UNA_HORA in alcanzables
        )
    }

    @Test
    fun `prometio proxima si es alcanzable, pero solo desde pidio reagendar`() {
        val queDanPrometio = TipoVisitaCatalogo.LITERALES.filter {
            TipoVisitaCatalogo.estadoDe(it) == EstadoCuenta.PROMETIO_PROXIMA
        }
        assertEquals(listOf(TipoVisitaCatalogo.PIDE_REAGENDAR), queDanPrometio)
    }
}
