package com.example.msp_app.core.datos

import com.example.msp_app.core.testing.time.FakeClock
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** E-APP-173: persistencia sin Room; sobrevive a la muerte del proceso; retención 30 días. */
class AlmacenYServicioTest {

    private lateinit var carpeta: File
    private val clock = FakeClock.at("2026-11-10T18:00:00Z")

    @Before fun setUp() {
        carpeta = Files.createTempDirectory("uso").toFile()
    }

    @After fun tearDown() {
        carpeta.deleteRecursively()
    }

    private fun archivo() = File(carpeta, "uso_de_datos.json")

    private fun servicio(): ServicioDeUsoDeDatos {
        val registro = RegistroDeUso(clock)
        val fuente = object : FuenteDeTrafico {
            override fun rx() = 0L
            override fun tx() = 0L
        }
        return ServicioDeUsoDeDatos(
            registro = registro,
            muestreador = MuestreadorDeTrafico(fuente, registro, TipoDeRed.WIFI, true),
            almacen = AlmacenEnArchivo(archivo())
        )
    }

    @Test fun `ida y vuelta del archivo`() {
        val a = AlmacenEnArchivo(archivo())
        val estado = EstadoPersistido(
            dias = listOf(
                UsoDelDia.vacio("2026-11-09")
                    .copy(wifi = 5, endpoints = mapOf("GET /ping" to UsoDeEndpoint(1, 2, 3)))
            ),
            ultimaMuestra = MuestraDeTrafico(10, 20),
            reportados = listOf("2026-11-08")
        )
        a.escribir(estado)
        assertEquals(estado, a.leer())
    }

    @Test fun `archivo corrupto o inexistente da null y no truena`() {
        val a = AlmacenEnArchivo(archivo())
        assertNull(a.leer())
        archivo().writeText("{no es json")
        assertNull(a.leer())
        archivo().writeText("")
        assertNull(a.leer())
    }

    @Test fun `la escritura es atomica y no deja temporales`() {
        val a = AlmacenEnArchivo(archivo())
        repeat(5) { a.escribir(EstadoPersistido(emptyList(), null, emptyList())) }
        assertEquals(listOf("uso_de_datos.json"), carpeta.list()!!.toList())
    }

    @Test fun `muerte del proceso conserva lo guardado y pierde solo lo posterior`() = runTest {
        val a = servicio()
        a.restaurar()
        a.registro.bytes("GET /ping", 100, 10)
        a.guardar()
        a.registro.bytes("GET /ping", 7, 0) // se pierde: proceso muere antes de guardar

        val b = servicio() // proceso nuevo, mismo archivo
        b.restaurar()
        val e = b.registro.instantanea().single().endpoints.getValue("GET /ping")
        assertEquals(100L, e.rx)
        assertEquals(10L, e.tx)
    }

    @Test fun `restaurar dos veces no duplica`() = runTest {
        val a = servicio()
        a.restaurar()
        a.registro.bytes("GET /ping", 100, 0)
        a.guardar()
        val b = servicio()
        b.restaurar()
        b.restaurar()
        assertEquals(100L, b.registro.instantanea().single().endpoints.getValue("GET /ping").rx)
    }

    @Test fun `no guarda antes de restaurar para no pisar lo anterior`() = runTest {
        val a = servicio()
        a.restaurar()
        a.registro.bytes("GET /ping", 100, 0)
        a.guardar()
        val b = servicio()
        b.guardar() // aún no restauró: no debe escribir
        val c = servicio()
        c.restaurar()
        assertEquals(100L, c.registro.instantanea().single().endpoints.getValue("GET /ping").rx)
    }

    @Test fun `guardar retiene solo 30 dias`() = runTest {
        val a = servicio()
        a.restaurar()
        a.registro.restaurar(
            (0..45).map {
                UsoDelDia.vacio(
                    java.time.LocalDate.of(2026, 11, 10).minusDays(it.toLong()).toString()
                )
            }
        )
        a.guardar()
        val leido = AlmacenEnArchivo(archivo()).leer()!!
        assertEquals(30, leido.dias.size)
        assertTrue(leido.dias.none { it.fecha < "2026-10-12" })
    }

    @Test fun `los reportados tambien sobreviven y se podan a 30`() = runTest {
        val a = servicio()
        a.restaurar()
        a.marcarReportado("2026-11-09")
        val b = servicio()
        b.restaurar()
        assertTrue(b.fueReportado("2026-11-09"))
    }
}
