package com.example.msp_app.feature.ubicacion.domain

import com.example.msp_app.core.geo.IndiceDePuntosCompartidos
import com.example.msp_app.core.geo.LugaresDelCliente
import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.core.geo.Punto
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.PUERTA
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.desplazado
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.indiceVacio
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.medicion
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.rutaCompartida
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas del mapa de lugares, sin Compose ni mapa. Cada una se comprobó
 * roja al revertirla (ver el reporte de la tarea).
 */
class MapaDelClienteTest {

    private val zona: ZoneId = ZoneId.of("America/Mexico_City")

    private fun grupo(
        centro: Punto,
        n: Int,
        desdeDias: Long,
        prefijo: String,
        transferencia: Boolean = false
    ) = (0 until n).map {
        medicion(
            desplazado(centro, it * 1.5),
            diasAtras = desdeDias + it * 7,
            id = "$prefijo$it",
            esTransferencia = transferencia
        )
    }

    private fun mapaDe(
        m: List<MedicionDelCobro>,
        indice: IndiceDePuntosCompartidos = indiceVacio()
    ) = MapaDelCliente.de(LugaresDelCliente.de(m, indice))

    @Test
    fun `clasifica principal, otro, antes, compartido y transferencias`() {
        val otro = desplazado(PUERTA, 440.0)
        val antes = desplazado(PUERTA, -390.0)
        val comp = desplazado(PUERTA, 0.0, 370.0)
        val tran = desplazado(PUERTA, 0.0, -600.0)
        val m = grupo(PUERTA, 10, 0, "p") + // días 0..63
            grupo(otro, 4, 3, "o") + // se cruza con el principal
            grupo(antes, 4, 200, "a") + // acabó antes del primero del principal
            grupo(comp, 3, 5, "c") +
            grupo(tran, 3, 6, "t", transferencia = true)
        val mapa =
            mapaDe(
                m,
                IndiceDePuntosCompartidos.de(rutaCompartida(comp, clientes = 7, cobradores = 2))
            )
        val clases = mapa.lugares.map { it.clase }
        assertEquals(
            listOf(
                ClaseDeLugar.DONDE_MAS_PAGA,
                ClaseDeLugar.OTRO_LUGAR,
                ClaseDeLugar.PAGABA_ANTES,
                ClaseDeLugar.COMPARTIDO
            ),
            clases
        )
        assertEquals(listOf(ClaseDeLugar.TRANSFERENCIAS), mapa.transferencias.map { it.clase })
        assertTrue("cambió de lugar no se detectó", mapa.cambioDeLugar)
        assertEquals(4, mapa.cuantosLugares)
        assertEquals(10 + 4 + 4 + 3, mapa.cobrosEnLugares)
    }

    @Test
    fun `los nombres nunca inventan que es el lugar`() {
        val prohibidas = listOf("casa", "trabajo", "domicilio", "tienda", "puerta")
        ClaseDeLugar.entries.forEach { c ->
            prohibidas.forEach { w ->
                assertFalse(
                    "${c.titulo} dice $w",
                    c.titulo.lowercase().contains(w)
                )
            }
        }
        assertEquals("Donde más paga", ClaseDeLugar.DONDE_MAS_PAGA.titulo)
        assertEquals("Otro lugar donde paga", ClaseDeLugar.OTRO_LUGAR.titulo)
        assertEquals("Pagaba aquí antes", ClaseDeLugar.PAGABA_ANTES.titulo)
    }

    @Test
    fun `suelto es 1 o 2 cobros SIN umbral de distancia`() {
        val cerca = desplazado(PUERTA, 100.0)
        val lejisimos = Punto(19.43, -99.13 + 12.0) // más de 1,000 km
        val m = grupo(PUERTA, 6, 0, "p") +
            listOf(medicion(cerca, 2, id = "s1")) +
            grupo(lejisimos, 2, 4, "s2")
        val mapa = mapaDe(m)
        assertEquals(2, mapa.sueltos.size)
        assertTrue(mapa.sueltos.all { it.clase == ClaseDeLugar.SUELTO })
        // Con 3 cobros ya no es suelto, aunque esté lejísimos.
        val tres = mapaDe(grupo(PUERTA, 6, 0, "p") + grupo(lejisimos, 3, 4, "x"))
        assertTrue(tres.sueltos.isEmpty())
    }

    @Test
    fun `el principal nunca es suelto aunque tenga 2 cobros`() {
        val mapa = mapaDe(grupo(PUERTA, 2, 0, "p"))
        assertEquals(ClaseDeLugar.DONDE_MAS_PAGA, mapa.lugares.single().clase)
        assertTrue(mapa.sueltos.isEmpty())
    }

    @Test
    fun `el encuadre de entrada nunca incluye sueltos, ni el ultimo si cayo en uno`() {
        val otro = desplazado(PUERTA, 440.0)
        val lejisimos = Punto(19.43, -87.0)
        val m = grupo(PUERTA, 6, 10, "p") + grupo(otro, 3, 12, "o") +
            listOf(medicion(lejisimos, diasAtras = 0, id = "ultimo-suelto"))
        val mapa = mapaDe(m)
        assertEquals("ultimo-suelto", mapa.ultimoCobro?.pagoId)
        assertFalse(mapa.encuadreDeEntrada.contains(mapa.sueltos.single().centro))
        assertEquals(2, mapa.encuadreDeEntrada.size)
        val c = Encuadre.de(mapa.encuadreDeEntrada, 360f, 440f, 40f)!!
        assertTrue("el zoom se abrió de más por el suelto", c.zoom > 14f)
        assertEquals(1, mapa.sueltosFueraDeLaVista(c, 360f, 440f).size)
    }

    @Test
    fun `el ultimo contacto entra al encuadre cuando no es suelto`() {
        val otro = desplazado(PUERTA, 900.0)
        val m = grupo(PUERTA, 6, 10, "p") + grupo(otro, 3, 0, "o")
        val mapa = mapaDe(m)
        assertEquals(ClaseDeLugar.OTRO_LUGAR, mapa.lugarDelUltimo?.clase)
        assertTrue(mapa.encuadreDeEntrada.contains(mapa.lugarDelUltimo!!.centro))
    }

    // ── "Suele pagar" ──

    private fun sabadoALas(hora: Int, semana: Long, minuto: Int = 10): Instant =
        java.time.ZonedDateTime.of(
            2026,
            9,
            19,
            hora,
            minuto,
            0,
            0,
            zona
        ).minusWeeks(semana).toInstant()

    private fun conFecha(f: Instant, id: String) = medicion(PUERTA, id = id, fecha = f)

    @Test
    fun `suele pagar sale cuando la franja junta la mitad`() {
        val m = listOf(
            conFecha(sabadoALas(10, 0), "a"),
            conFecha(sabadoALas(11, 1), "b"),
            conFecha(sabadoALas(10, 2), "c"),
            conFecha(sabadoALas(16, 3), "d"),
            conFecha(sabadoALas(18, 4).plusSeconds(86_400 * 2), "e"),
            conFecha(sabadoALas(9, 5).plusSeconds(86_400 * 3), "f")
        )
        val s = SuelePagar.de(m, zona)
        assertEquals(SuelePagar(DayOfWeek.SATURDAY, 10), s)
        assertEquals("Sáb 10–12", Textos.suelePagar(s!!))
    }

    @Test
    fun `suele pagar NO sale si la costumbre no es clara`() {
        // 3 de 7 en la misma franja: menos de la mitad.
        val m = listOf(
            conFecha(sabadoALas(10, 0), "a"),
            conFecha(sabadoALas(10, 1), "b"),
            conFecha(sabadoALas(11, 2), "c"),
            conFecha(sabadoALas(16, 3), "d"),
            conFecha(sabadoALas(8, 4).plusSeconds(86_400), "e"),
            conFecha(sabadoALas(13, 5).plusSeconds(86_400 * 2), "f"),
            conFecha(sabadoALas(18, 6).plusSeconds(86_400 * 3), "g")
        )
        assertNull(SuelePagar.de(m, zona))
        // Y con menos de 3 cobros tampoco, aunque sean idénticos.
        assertNull(SuelePagar.de(m.take(2), zona))
    }

    @Test
    fun `en empate la franja empieza donde hay cobros`() {
        val m = (0L until 4L).map { conFecha(sabadoALas(14, it, minuto = 20), "w$it") }
        assertEquals(SuelePagar(DayOfWeek.SATURDAY, 14), SuelePagar.de(m, zona))
    }

    // ── Abono típico, cobrador, distancia ──

    @Test
    fun `el abono tipico es la mediana sin interpolar`() {
        fun con(vararg i: Int) = i.mapIndexed { n, v -> medicion(PUERTA, id = "i$n", importe = v) }
        assertEquals(BigDecimal(150), abonoTipico(con(100, 150, 400)))
        // Par: el de abajo de los dos del medio, un abono que sí se dio.
        assertEquals(BigDecimal(150), abonoTipico(con(100, 150, 200, 400)))
        assertNull("sin importes no hay abono típico", abonoTipico(con()))
    }

    @Test
    fun `el cobrador frecuente gana por conteo`() {
        val m = listOf(
            medicion(PUERTA, 0, id = "1", cobrador = "GABRIEL ROQUE"),
            medicion(PUERTA, 1, id = "2", cobrador = "MARISOL VEGA"),
            medicion(PUERTA, 2, id = "3", cobrador = "MARISOL VEGA")
        )
        assertEquals("MARISOL VEGA", cobradorFrecuente(m))
        assertEquals("Marisol Vega", Textos.nombre("MARISOL VEGA"))
    }

    @Test
    fun `la distancia se redondea a 10 m y pasa a km`() {
        assertEquals("440 m", distanciaLegible(437.0))
        assertEquals("10 m", distanciaLegible(2.0))
        assertEquals("1.2 km", distanciaLegible(1_234.0))
        assertEquals("1,234 km", distanciaLegible(1_234_000.0))
    }

    @Test
    fun `la distancia al principal viaja en cada lugar`() {
        val otro = desplazado(PUERTA, 440.0)
        val mapa = mapaDe(grupo(PUERTA, 6, 0, "p") + grupo(otro, 3, 1, "o"))
        assertNull(mapa.principal!!.distanciaAlPrincipalM)
        assertEquals(440.0, mapa.lugares[1].distanciaAlPrincipalM!!, 5.0)
    }
}
