package com.example.msp_app.core.datos

import com.example.msp_app.core.testing.time.FakeClock
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** E-APP-174: reporte diario a la oficina. */
class ReporteDeUsoDeDatosTest {

    private lateinit var carpeta: File
    private val clock = FakeClock.at("2026-11-10T18:00:00Z") // 10 nov en CDMX

    @Before fun setUp() {
        carpeta = Files.createTempDirectory("rep").toFile()
    }

    @After fun tearDown() {
        carpeta.deleteRecursively()
    }

    private fun dia(fecha: String, rx: Long = 1000) = UsoDelDia.vacio(fecha).copy(
        rxTotal = rx,
        wifi = rx,
        primerPlano = rx,
        endpoints = mapOf("GET /clientes" to UsoDeEndpoint(rx / 2, 10, 1))
    )

    private fun servicio(): ServicioDeUsoDeDatos {
        val registro = RegistroDeUso(clock)
        val fuente = object : FuenteDeTrafico {
            override fun rx() = 0L
            override fun tx() = 0L
        }
        return ServicioDeUsoDeDatos(
            registro,
            MuestreadorDeTrafico(fuente, registro, TipoDeRed.WIFI, true),
            AlmacenEnArchivo(File(carpeta, "uso.json"))
        )
    }

    // ---- contenido ----

    @Test fun `el reporte tiene solo los campos de la lista blanca`() {
        val r = ConstructorDeReporte.construir(dia("2026-11-09"), "2.21.0")
        assertEquals(ConstructorDeReporte.CAMPOS_PERMITIDOS, r.keys)
        r["servicios"].let { lista ->
            @Suppress("UNCHECKED_CAST")
            (lista as List<Map<String, Any?>>).forEach {
                assertEquals(setOf("nombre", "bajada", "subida", "peticiones"), it.keys)
            }
        }
    }

    @Test fun `top 10 agrupado por servicio y ordenado`() {
        val endpoints = (1..15).associate { "GET /v2/visitas/x$it" to UsoDeEndpoint(it * 100L, 0, 1) } +
            mapOf("GET /clientes" to UsoDeEndpoint(1_000_000, 0, 2))
        val r = ConstructorDeReporte.construir(
            UsoDelDia.vacio("2026-11-09").copy(rxTotal = 2_000_000, endpoints = endpoints),
            "2.21.0"
        )

        @Suppress("UNCHECKED_CAST")
        val s = r["servicios"] as List<Map<String, Any?>>
        assertEquals("Lista de clientes", s[0]["nombre"])
        assertEquals(1_000_000L, s[0]["bajada"])
        assertEquals("Visitas", s[1]["nombre"]) // las 15 rutas de visitas se agrupan en un servicio
        assertEquals((1..15).sumOf { it * 100L }, s[1]["bajada"])
        assertEquals(15L, s[1]["peticiones"])
        assertTrue(s.size <= 10)
    }

    @Test fun `mapa firebase y otros es el total menos el desglose`() {
        val d = UsoDelDia.vacio("2026-11-09").copy(
            rxTotal = 9_000,
            txTotal = 1_000,
            endpoints = mapOf("GET /clientes" to UsoDeEndpoint(4_000, 500, 1))
        )
        val r = ConstructorDeReporte.construir(d, "2.21.0")
        assertEquals(10_000L, r["totalBytes"])
        assertEquals(5_500L, r["mapaFirebaseYOtrosBytes"])
    }

    @Test fun `el desglose mayor que el total no da negativo`() {
        val d = UsoDelDia.vacio("2026-11-09").copy(
            rxTotal = 10,
            endpoints = mapOf("GET /clientes" to UsoDeEndpoint(4_000, 500, 1))
        )
        assertEquals(0L, ConstructorDeReporte.construir(d, "2.21.0")["mapaFirebaseYOtrosBytes"])
    }

    @Test fun `fotos subidas son las partes de imagen y sus bytes`() {
        val d = UsoDelDia.vacio("2026-11-09").copy(
            endpoints = mapOf(
                ClasificadorDeServicio.CLAVE_FOTOS to UsoDeEndpoint(0, 8_000, 3),
                "POST /v2/cobranza/pagos" to UsoDeEndpoint(10, 3_000, 9)
            )
        )
        val r = ConstructorDeReporte.construir(d, "2.21.0")
        assertEquals(3L, r["fotosSubidas"])
        assertEquals(8_000L, r["fotosBytes"])
    }

    @Test fun `cabe en menos de 2 KB aun en el peor caso`() {
        val nombres = ClasificadorDeServicio.TODOS
        val endpoints = (1..200).associate {
            "GET /v2/${nombres.random()}/e$it" to UsoDeEndpoint(Long.MAX_VALUE / 1000, Long.MAX_VALUE / 1000, 99_999)
        }
        val d = UsoDelDia.vacio("2026-11-09").copy(
            rxTotal = Long.MAX_VALUE / 4,
            txTotal = Long.MAX_VALUE / 4,
            movil = Long.MAX_VALUE / 8,
            wifi = Long.MAX_VALUE / 8,
            otraRed = Long.MAX_VALUE / 8,
            primerPlano = Long.MAX_VALUE / 8,
            segundoPlano = Long.MAX_VALUE / 8,
            endpoints = endpoints
        )
        val json = Gson().toJson(ConstructorDeReporte.construir(d, "2.21.0-local+abcdef0"))
        assertTrue("tamaño ${json.toByteArray().size}", json.toByteArray().size < 2048)
    }

    @Test fun `no contiene identificadores ni rutas`() {
        val d = dia("2026-11-09")
        val json = Gson().toJson(ConstructorDeReporte.construir(d, "2.21.0"))
        assertFalse(json.contains("/clientes"))
        assertFalse(json.contains("GET "))
        assertFalse(json.contains("@"))
    }

    // ---- plan ----

    private val hoy = LocalDate.of(2026, 11, 10)
    private fun dias(vararg f: String) = f.associateWith { dia(it) }

    @Test fun `solo el dia anterior cuando todo esta al dia`() {
        val p = PlanDeReporte.pendientes(hoy, dias("2026-11-10", "2026-11-09"), emptySet())
        assertEquals(listOf("2026-11-09"), p)
    }

    @Test fun `no repite lo ya reportado`() {
        val p = PlanDeReporte.pendientes(hoy, dias("2026-11-09"), setOf("2026-11-09"))
        assertEquals(emptyList<String>(), p)
    }

    @Test fun `atraso de varios dias manda todos del mas viejo al mas nuevo hasta 7 atras`() {
        val p = PlanDeReporte.pendientes(
            hoy,
            dias(
                "2026-11-09",
                "2026-11-08",
                "2026-11-05",
                "2026-11-03",
                "2026-11-02",
                "2026-10-31"
            ),
            emptySet()
        )
        // 11-03 es el día -7: entra; 11-02 y 10-31 ya no.
        assertEquals(listOf("2026-11-03", "2026-11-05", "2026-11-08", "2026-11-09"), p)
    }

    @Test fun `dias sin datos no se reportan`() {
        val p = PlanDeReporte.pendientes(
            hoy,
            mapOf("2026-11-09" to UsoDelDia.vacio("2026-11-09")),
            emptySet()
        )
        assertEquals(emptyList<String>(), p)
    }

    // ---- envío ----

    private class Emisor(var resultado: Boolean = true) : EmisorDeReporte {
        val enviados = mutableListOf<Map<String, Any?>>()
        override fun emitir(datos: Map<String, Any?>): Boolean {
            if (resultado) enviados += datos
            return resultado
        }
    }

    @Test fun `es idempotente aunque el worker corra dos veces`() = runTest {
        val s = servicio()
        s.restaurar()
        s.registro.restaurar(listOf(dia("2026-11-09")))
        val emisor = Emisor()
        val r = ReporteDeUsoDeDatos(s, emisor, "2.21.0", clock)
        assertEquals(1, r.enviarPendientes())
        assertEquals(0, r.enviarPendientes())
        assertEquals(1, emisor.enviados.size)
        // y un proceso nuevo tampoco lo repite
        val s2 = servicio()
        s2.restaurar()
        val emisor2 = Emisor()
        assertEquals(0, ReporteDeUsoDeDatos(s2, emisor2, "2.21.0", clock).enviarPendientes())
    }

    @Test fun `si no hay red no marca y se manda despues`() = runTest {
        val s = servicio()
        s.restaurar()
        s.registro.restaurar(listOf(dia("2026-11-09")))
        val emisor = Emisor(resultado = false)
        val r = ReporteDeUsoDeDatos(s, emisor, "2.21.0", clock)
        assertEquals(0, r.enviarPendientes())
        assertFalse(s.fueReportado("2026-11-09"))
        emisor.resultado = true
        assertEquals(1, r.enviarPendientes())
        assertTrue(s.fueReportado("2026-11-09"))
    }

    @Test fun `tras varios dias sin red manda todos en orden`() = runTest {
        val s = servicio()
        s.restaurar()
        s.registro.restaurar(listOf(dia("2026-11-07"), dia("2026-11-08"), dia("2026-11-09")))
        val emisor = Emisor()
        assertEquals(3, ReporteDeUsoDeDatos(s, emisor, "2.21.0", clock).enviarPendientes())
        assertEquals(
            listOf("2026-11-07", "2026-11-08", "2026-11-09"),
            emisor.enviados.map { it["fecha"] }
        )
    }

    @Test fun `el dia cambia segun la zona de negocio`() = runTest {
        clock.setNow(Instant.parse("2026-11-10T05:00:00Z")) // 23:00 del 9 en CDMX: hoy=9
        val s = servicio()
        s.restaurar()
        s.registro.restaurar(listOf(dia("2026-11-09")))
        val emisor = Emisor()
        assertEquals(0, ReporteDeUsoDeDatos(s, emisor, "2.21.0", clock).enviarPendientes())
    }
}
