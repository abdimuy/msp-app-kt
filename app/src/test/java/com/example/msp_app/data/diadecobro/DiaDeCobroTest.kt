package com.example.msp_app.data.diadecobro

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.diadecobro.DiaDeCobroApi
import com.example.msp_app.data.api.services.diadecobro.DiaDeCobroResponse
import com.example.msp_app.feature.pagos.domain.DiaAcordado
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El día de cobro en `:app`: el mapeo del DTO (con campos ausentes y nulos), el
 * almacén en disco, la descarga de una vez al día y la lectura **sin red**.
 */
class DiaDeCobroTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val gson = Gson()

    /** Literal del contrato del spec §10, no un objeto armado a mano: lo que Gson de verdad construye. */
    private val jsonDelContrato = """
        {
          "calculado_en": "2026-11-11T18:40:00Z",
          "ventas": [
            { "docto_cc_id": 123456,
              "acordado": { "tipo": "semana", "dia": "SABADO" },
              "estimacion": { "pagos_considerados": 10, "principal": "SABADO",
                "dias": [ { "dia": "SABADO", "pagos": 5 }, { "dia": "MARTES", "pagos": 3 }, { "dia": "JUEVES", "pagos": 2 } ] } },
            { "docto_cc_id": 2, "acordado": { "tipo": "mes", "dia": 15 }, "estimacion": null },
            { "docto_cc_id": 3, "acordado": null,
              "estimacion": { "pagos_considerados": 8, "principal": null,
                "dias": [ { "dia": "MARTES", "pagos": 4 }, { "dia": "VIERNES", "pagos": 4 } ] } }
          ]
        }
    """.trimIndent()

    private fun respuesta(json: String = jsonDelContrato): DiaDeCobroResponse =
        gson.fromJson(json, DiaDeCobroResponse::class.java)

    // --- Mapeo del DTO ---------------------------------------------------------------------

    @Test
    fun `mapea acordado de semana, estimacion y fecha de calculo`() {
        val v = respuesta().deLaVenta(123456)!!
        assertEquals(DiaAcordado.DeLaSemana(DiaDeLaSemana.SABADO), v.acordado)
        assertEquals(DiaDeLaSemana.SABADO, v.estimacion!!.principal)
        assertEquals(10, v.estimacion!!.pagosConsiderados)
        assertEquals(listOf(5, 3, 2), v.estimacion!!.dias.map { it.pagos })
        assertEquals(Instant.parse("2026-11-11T18:40:00Z"), v.calculadoEn)
    }

    @Test
    fun `mapea acordado de mes con dia numerico y estimacion nula`() {
        val v = respuesta().deLaVenta(2)!!
        assertEquals(DiaAcordado.DelMes(15), v.acordado)
        assertNull(v.estimacion)
    }

    @Test
    fun `principal null es empate, no un dato roto`() {
        val e = respuesta().deLaVenta(3)!!.estimacion!!
        assertNull(e.principal)
        assertEquals(2, e.dias.size)
    }

    @Test
    fun `una venta que no esta en la respuesta es null`() {
        assertNull(respuesta().deLaVenta(999))
    }

    @Test
    fun `campos ausentes no revientan, Gson deja null`() {
        // Sin `ventas`, sin `calculado_en`, con una venta sin nada.
        assertNull(respuesta("{}").deLaVenta(1))
        assertNull(respuesta("""{"ventas":[{"docto_cc_id":1}]}""").deLaVenta(1))
        assertNull(respuesta("""{"ventas":[{"docto_cc_id":1,"acordado":{}}]}""").deLaVenta(1))
        val sinFecha = respuesta(
            """{"ventas":[{"docto_cc_id":1,"acordado":{"tipo":"semana","dia":"LUNES"}}]}"""
        ).deLaVenta(1)!!
        assertNull(sinFecha.calculadoEn)
    }

    @Test
    fun `lo que no se entiende se descarta, no se adivina`() {
        fun uno(acordado: String, estimacion: String) = respuesta(
            """{"ventas":[{"docto_cc_id":1,"acordado":$acordado,"estimacion":$estimacion}]}"""
        ).deLaVenta(1)

        assertNull(uno("""{"tipo":"semana","dia":"FUNDAY"}""", "null"))
        assertNull(uno("""{"tipo":"quincena","dia":"LUNES"}""", "null"))
        assertNull(uno("""{"tipo":"mes","dia":45}""", "null"))
        assertNull(uno("""{"tipo":"mes","dia":"quince"}""", "null"))
        // Estimación con un principal que NO es un día: se descarta entera (no se vuelve empate).
        assertNull(
            uno(
                "null",
                """{"pagos_considerados":6,"principal":"FUNDAY","dias":[{"dia":"LUNES","pagos":6}]}"""
            )
        )
        // Estimación sin días o sin total.
        assertNull(uno("null", """{"pagos_considerados":6,"principal":"LUNES","dias":[]}"""))
        assertNull(uno("null", """{"principal":"LUNES","dias":[{"dia":"LUNES","pagos":6}]}"""))
        // Un día roto entre dos buenos solo pierde el roto.
        val e = uno(
            "null",
            """{"pagos_considerados":6,"principal":"LUNES",
               "dias":[{"dia":"LUNES","pagos":4},{"dia":null,"pagos":1},{"dia":"MARTES","pagos":null},{"dia":"VIERNES","pagos":2}]}"""
        )!!.estimacion!!
        assertEquals(listOf(DiaDeLaSemana.LUNES, DiaDeLaSemana.VIERNES), e.dias.map { it.dia })
    }

    // --- Almacén ---------------------------------------------------------------------------

    private fun almacen() = DiaDeCobroAlmacen(File(carpeta.root, "dia_de_cobro.json"))

    @Test
    fun `lo guardado se lee igual, y sin archivo no hay nada`() {
        val a = almacen()
        assertNull(a.leer())
        val g = DiaDeCobroGuardado(21, Instant.parse("2026-11-11T19:00:00Z"), respuesta())
        a.guardar(g)
        // Un almacén NUEVO sobre el mismo archivo: sobrevive al proceso.
        val leido = almacen().leer()!!
        assertEquals(21, leido.zona)
        assertEquals(g.descargadoEn, leido.descargadoEn)
        assertNotNull(leido.respuesta.deLaVenta(123456))
    }

    @Test
    fun `un archivo ilegible es nada guardado, no un fallo`() {
        File(carpeta.root, "dia_de_cobro.json").writeText("{ esto no es json")
        assertNull(almacen().leer())
        File(carpeta.root, "dia_de_cobro.json").writeText("""{"zona":21}""")
        assertNull(almacen().leer())
    }

    // --- Descarga: una vez al día, y el fallo conserva lo guardado ----------------------------

    private class ApiFalsa(var respuesta: DiaDeCobroResponse?, var falla: Exception? = null) :
        DiaDeCobroApi {
        var llamadas = 0

        override suspend fun diaDeCobro(zonaId: Int): DiaDeCobroResponse {
            llamadas++
            falla?.let { throw it }
            return respuesta!!
        }
    }

    private val manana8am = "2026-11-11T14:00:00Z" // 08:00 en la oficina

    @Test
    fun `baja y guarda, y el mismo dia no vuelve a pedir`() = runTest {
        val api = ApiFalsa(respuesta())
        val reloj = FakeClock.at(manana8am)
        val descargar = DescargarDiaDeCobro(api, almacen(), reloj)

        assertTrue(descargar(21) is ResultadoDeDiaDeCobro.Descargado)
        reloj.advanceHours(5)
        assertEquals(ResultadoDeDiaDeCobro.YaAlDia, descargar(21))
        assertEquals(1, api.llamadas)

        // Al día siguiente de la oficina SÍ toca.
        reloj.advanceDays(1)
        assertTrue(descargar(21) is ResultadoDeDiaDeCobro.Descargado)
        assertEquals(2, api.llamadas)
    }

    @Test
    fun `otra zona vuelve a bajar aunque sea el mismo dia`() = runTest {
        val api = ApiFalsa(respuesta())
        val descargar = DescargarDiaDeCobro(api, almacen(), FakeClock.at(manana8am))
        descargar(21)
        assertTrue(descargar(22) is ResultadoDeDiaDeCobro.Descargado)
        assertEquals(22, almacen().leer()!!.zona)
    }

    @Test
    fun `sin red se queda lo ultimo guardado y se muestra con su fecha`() = runTest {
        val api = ApiFalsa(respuesta())
        val reloj = FakeClock.at(manana8am)
        val a = almacen()
        DescargarDiaDeCobro(api, a, reloj)(21)

        // Mañana no hay señal.
        reloj.advanceDays(1)
        api.falla = IOException("sin red")
        val resultado = DescargarDiaDeCobro(api, a, reloj)(21)
        assertTrue(resultado is ResultadoDeDiaDeCobro.Fallo)

        // Lo guardado sigue ahí, intacto, con la fecha en que el servidor lo calculó.
        val v = DiaDeCobroAdapter(almacen()).deLaVenta(123456)!!
        assertEquals(Instant.parse("2026-11-11T18:40:00Z"), v.calculadoEn)
        assertEquals(DiaAcordado.DeLaSemana(DiaDeLaSemana.SABADO), v.acordado)
        // Y el momento de la descarga NO avanzó: mañana se vuelve a intentar.
        assertEquals(Instant.parse(manana8am), almacen().leer()!!.descargadoEn)
    }

    @Test
    fun `una respuesta sin lista de ventas no pisa lo guardado`() = runTest {
        val api = ApiFalsa(respuesta())
        val reloj = FakeClock.at(manana8am)
        DescargarDiaDeCobro(api, almacen(), reloj)(21)

        reloj.advanceDays(1)
        api.respuesta = respuesta("{}")
        assertTrue(DescargarDiaDeCobro(api, almacen(), reloj)(21) is ResultadoDeDiaDeCobro.Fallo)
        assertNotNull(almacen().leer()!!.respuesta.deLaVenta(123456))
    }

    @Test
    fun `una zona sin ventas SI se guarda como vacia`() = runTest {
        val api = ApiFalsa(respuesta("""{"calculado_en":"2026-11-11T18:40:00Z","ventas":[]}"""))
        val r = DescargarDiaDeCobro(api, almacen(), FakeClock.at(manana8am))(21)
        assertEquals(ResultadoDeDiaDeCobro.Descargado(0), r)
        assertNull(DiaDeCobroAdapter(almacen()).deLaVenta(1))
    }

    @Test
    fun `forzar baja aunque ya se haya bajado hoy`() = runTest {
        val api = ApiFalsa(respuesta())
        val descargar = DescargarDiaDeCobro(api, almacen(), FakeClock.at(manana8am))
        descargar(21)
        assertTrue(descargar(21, forzar = true) is ResultadoDeDiaDeCobro.Descargado)
        assertEquals(2, api.llamadas)
    }
}
