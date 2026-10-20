package com.example.msp_app.core.common.visitas.domain

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.cobranza.domain.VisitScope
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unidades de [derivarCompromisos] que fijan lo que el juego compartido ejercita en bloque. */
class CompromisosTest {

    private val cdmx: ZoneId = ZoneId.of("America/Mexico_City")
    private val hoy: LocalDate = LocalDate.parse("2026-10-16")
    private val cuenta = CuentaCompromiso(ventaId = 100, clienteId = 10)

    private fun visita(
        id: String,
        instante: String,
        tipo: String = TipoVisitaCatalogo.PIDE_TIEMPO,
        promesa: PromesaCompromiso? = null,
        cita: CitaCompromiso? = null,
        ventaId: Int? = 100
    ) = VisitaCompromiso(id, 10, ventaId, tipo, Instant.parse(instante), promesa, cita)

    private fun promesa(fecha: String, ventaId: Int? = 100) = PromesaCompromiso(
        ventaId,
        LocalDate.parse(fecha),
        15000
    )

    // ── E-APP-105: desempate por id en el mismo segundo (Review Focus 1) ──

    @Test
    fun `empate de instante gana el menor id - E-APP-105`() {
        val conPromesa =
            visita(
                "00000000-0000-3000-8000-000000000001",
                "2026-10-15T17:00:00Z",
                promesa = promesa("2026-10-20")
            )
        val sinNada = visita(
            "00000000-0000-3000-8000-000000000002",
            "2026-10-15T17:00:00Z",
            tipo = TipoVisitaCatalogo.TIENE_PERO_NO_PAGA
        )
        for (orden in listOf(listOf(conPromesa, sinNada), listOf(sinNada, conPromesa))) {
            val r = derivarCompromisos(listOf(cuenta), orden, emptyList(), hoy, cdmx)
            assertEquals(conPromesa.id, r.getValue(100).visitaId)
        }
        // Al revés: si el menor id no trae compromiso, no hay entrada.
        val sinNadaMenor = sinNada.copy(id = "00000000-0000-3000-8000-000000000000")
        assertTrue(
            derivarCompromisos(
                listOf(cuenta),
                listOf(conPromesa, sinNadaMenor),
                emptyList(),
                hoy,
                cdmx
            ).isEmpty()
        )
    }

    @Test
    fun `ids fuera del plano basico se ordenan por bytes como Go - E-APP-105`() {
        val privado = "" // EE 80 80
        val emoji = "😀" // U+1F600: F0 9F 98 80
        assertTrue("bytes UTF-8: U+E000 < U+1F600", compararIdsPorBytes(privado, emoji) < 0)
        assertTrue("control positivo: UTF-16 los ordena al revés", privado > emoji)
        assertEquals(0, compararIdsPorBytes("abc", "abc"))
        assertTrue(compararIdsPorBytes("ab", "abc") < 0)
        val conPromesa = visita(privado, "2026-10-15T17:00:00Z", promesa = promesa("2026-10-20"))
        val sinNada =
            visita(emoji, "2026-10-15T17:00:00Z", tipo = TipoVisitaCatalogo.TIENE_PERO_NO_PAGA)
        val r =
            derivarCompromisos(listOf(cuenta), listOf(sinNada, conPromesa), emptyList(), hoy, cdmx)
        assertEquals(privado, r.getValue(100).visitaId)
    }

    // ── E-APP-106: el día civil en la zona del negocio (Review Focus 2) ──

    @Test
    fun `visita a las 2330 locales - el pago sin hora del mismo dia local cierra - E-APP-106`() {
        // 23:30 del 16 en CDMX = 05:30Z del 17.
        val v =
            visita(
                "00000000-0000-3000-8000-000000000001",
                "2026-10-17T05:30:00Z",
                promesa = promesa("2026-10-20")
            )
        val pagoOficina =
            PagoCompromiso(100, null, LocalDate.parse("2026-10-16"), rechazado = false)
        val r = derivarCompromisos(listOf(cuenta), listOf(v), listOf(pagoOficina), hoy, cdmx)
        assertEquals(EstadoCompromiso.CUMPLIDA, r.getValue(100).estado)
        // Control positivo: con el día en UTC (17) el mismo pago NO cerraría.
        val enUtc =
            derivarCompromisos(listOf(cuenta), listOf(v), listOf(pagoOficina), hoy, ZoneOffset.UTC)
        assertEquals(EstadoCompromiso.VIGENTE_FUTURA, enUtc.getValue(100).estado)
    }

    @Test
    fun `cita sin hora empieza a las 0000 locales - E-APP-106`() {
        val cita = visita(
            "00000000-0000-3000-8000-000000000001",
            "2026-10-14T17:00:00Z",
            cita = CitaCompromiso(LocalDate.parse("2026-10-16"), null)
        )
        // 00:30 del 16 en CDMX = 06:30Z del 16: ya empezó la cita → la cumple.
        val despues = visita(
            "00000000-0000-3000-8000-000000000002",
            "2026-10-16T06:30:00Z",
            tipo = TipoVisitaCatalogo.TIENE_PERO_NO_PAGA
        )
        val r = derivarCompromisos(listOf(cuenta), listOf(cita, despues), emptyList(), hoy, cdmx)
        assertEquals(EstadoCompromiso.CITA_CUMPLIDA, r.getValue(100).estado)
        // 23:30 del 15 en CDMX = 05:30Z del 16: todavía no (y no la reemplaza, E-VIS-038).
        val antes = despues.copy(instante = Instant.parse("2026-10-16T05:30:00Z"))
        val r2 = derivarCompromisos(listOf(cuenta), listOf(cita, antes), emptyList(), hoy, cdmx)
        assertEquals(EstadoCompromiso.VIGENTE_HOY, r2.getValue(100).estado)
    }

    // ── Regla ──

    @Test
    fun `estado por dia con la gracia de 14`() {
        fun estado(dia: String) = derivarCompromisos(
            listOf(cuenta),
            listOf(
                visita(
                    "00000000-0000-3000-8000-000000000001",
                    "2026-09-01T17:00:00Z",
                    promesa = promesa(dia)
                )
            ),
            emptyList(),
            hoy,
            cdmx
        )[100]?.estado
        assertEquals(EstadoCompromiso.VIGENTE_FUTURA, estado("2026-10-17"))
        assertEquals(EstadoCompromiso.VIGENTE_HOY, estado("2026-10-16"))
        assertEquals(EstadoCompromiso.INCUMPLIDA, estado("2026-10-15"))
        assertEquals(EstadoCompromiso.INCUMPLIDA, estado("2026-10-02"))
        assertEquals(EstadoCompromiso.CERRADA_SIN_CUMPLIR, estado("2026-10-01"))
    }

    @Test
    fun `pago con hora cierra solo si es estrictamente posterior`() {
        val v =
            visita(
                "00000000-0000-3000-8000-000000000001",
                "2026-10-15T17:00:00Z",
                promesa = promesa("2026-10-20")
            )
        fun estado(pago: PagoCompromiso) =
            derivarCompromisos(listOf(cuenta), listOf(v), listOf(pago), hoy, cdmx)[100]?.estado
        val dia = LocalDate.parse("2026-10-15")
        assertEquals(
            EstadoCompromiso.VIGENTE_FUTURA,
            estado(PagoCompromiso(100, v.instante, dia, false))
        )
        assertEquals(
            EstadoCompromiso.CUMPLIDA,
            estado(PagoCompromiso(100, v.instante.plusSeconds(1), dia, false))
        )
        assertEquals(
            EstadoCompromiso.VIGENTE_FUTURA,
            estado(PagoCompromiso(100, v.instante.plusSeconds(1), dia, true))
        )
        assertEquals(
            EstadoCompromiso.VIGENTE_FUTURA,
            estado(PagoCompromiso(101, v.instante.plusSeconds(1), dia, false))
        )
    }

    @Test
    fun `cita con promesa de otra venta no lleva el monto`() {
        val v = visita(
            "00000000-0000-3000-8000-000000000001",
            "2026-10-15T17:00:00Z",
            promesa = promesa("2026-10-20", ventaId = 999),
            cita = CitaCompromiso(LocalDate.parse("2026-10-18"), LocalTime.of(16, 0))
        )
        val cv = derivarCompromisos(listOf(cuenta), listOf(v), emptyList(), hoy, cdmx).getValue(100)
        assertEquals(TipoCompromiso.CITA, cv.tipo)
        assertNull(cv.montoCentavos)
        assertEquals(LocalTime.of(16, 0), cv.hora)
        val conMonto = derivarCompromisos(
            listOf(cuenta),
            listOf(v.copy(promesa = promesa("2026-10-20", ventaId = null))),
            emptyList(),
            hoy,
            cdmx
        ).getValue(100)
        assertEquals(15000L, conMonto.montoCentavos)
    }

    @Test
    fun `ventas fuera de cuentas no aparecen y sin visitas no hay entrada`() {
        assertTrue(derivarCompromisos(emptyList(), emptyList(), emptyList(), hoy, cdmx).isEmpty())
        assertTrue(
            derivarCompromisos(listOf(cuenta), emptyList(), emptyList(), hoy, cdmx).isEmpty()
        )
        val ajena =
            visita(
                "00000000-0000-3000-8000-000000000001",
                "2026-10-15T17:00:00Z",
                promesa = promesa("2026-10-20")
            )
        val r =
            derivarCompromisos(
                listOf(CuentaCompromiso(200, 20)),
                listOf(ajena),
                emptyList(),
                hoy,
                cdmx
            )
        assertTrue(r.isEmpty())
    }

    @Test
    fun `columna sugerida - tabla`() {
        fun col(tipo: TipoCompromiso, estado: EstadoCompromiso) =
            columnaSugeridaDe(CompromisoVigente(1, "x", tipo, estado, hoy, null, null))
        assertEquals(
            ColumnaSugerida.POR_VISITAR,
            col(TipoCompromiso.PROMESA, EstadoCompromiso.VIGENTE_HOY)
        )
        assertEquals(
            ColumnaSugerida.POR_VISITAR,
            col(TipoCompromiso.CITA, EstadoCompromiso.VIGENTE_HOY)
        )
        assertEquals(
            ColumnaSugerida.POR_VISITAR,
            col(TipoCompromiso.PROMESA, EstadoCompromiso.INCUMPLIDA)
        )
        assertEquals(
            ColumnaSugerida.POR_VISITAR,
            col(TipoCompromiso.CITA, EstadoCompromiso.INCUMPLIDA)
        )
        assertEquals(
            ColumnaSugerida.POR_VISITAR,
            col(TipoCompromiso.CITA, EstadoCompromiso.VIGENTE_FUTURA)
        )
        assertEquals(
            ColumnaSugerida.NO_PAGA,
            col(TipoCompromiso.PROMESA, EstadoCompromiso.VIGENTE_FUTURA)
        )
        assertEquals(
            ColumnaSugerida.SIN_OPINION,
            col(TipoCompromiso.PROMESA, EstadoCompromiso.CUMPLIDA)
        )
        assertEquals(
            ColumnaSugerida.SIN_OPINION,
            col(TipoCompromiso.CITA, EstadoCompromiso.CITA_CUMPLIDA)
        )
        assertEquals(
            ColumnaSugerida.SIN_OPINION,
            col(TipoCompromiso.PROMESA, EstadoCompromiso.CERRADA_SIN_CUMPLIR)
        )
        assertEquals(
            ColumnaSugerida.SIN_OPINION,
            col(TipoCompromiso.CITA, EstadoCompromiso.CERRADA_SIN_CUMPLIR)
        )
    }

    @Test
    fun `codigos iguales a los del servidor`() {
        assertEquals(
            listOf(
                "cumplida",
                "cita_cumplida",
                "vigente_futura",
                "vigente_hoy",
                "incumplida",
                "cerrada_sin_cumplir"
            ),
            EstadoCompromiso.entries.map { it.codigo }
        )
        assertEquals(
            listOf("por_visitar", "no_paga", ""),
            ColumnaSugerida.entries.map { it.codigo }
        )
        assertEquals(listOf("promesa", "cita"), TipoCompromiso.entries.map { it.codigo })
        assertEquals(14L, DIAS_DE_GRACIA_INCUMPLIDA)
    }

    @Test
    fun `alcance - los tres no estaba son de cliente, la cita fuerza cliente, lo desconocido es venta`() {
        val deCliente =
            setOf(
                TipoVisitaCatalogo.NO_SE_ENCONTRABA,
                TipoVisitaCatalogo.CASA_CERRADA,
                TipoVisitaCatalogo.SOLO_MENORES
            )
        for (lit in TipoVisitaCatalogo.LITERALES) {
            val esperado = if (lit in deCliente) VisitScope.CLIENTE else VisitScope.VENTA
            assertEquals(lit, esperado, alcanceDe(lit, tieneCita = false))
            assertEquals(lit, VisitScope.CLIENTE, alcanceDe(lit, tieneCita = true))
        }
        assertEquals(14, TipoVisitaCatalogo.LITERALES.size)
        assertEquals(VisitScope.VENTA, alcanceDe("literal inventado", tieneCita = false))
        assertNotEquals(VisitScope.CLIENTE, alcanceDe("no se encontraba", tieneCita = false))
    }
}
