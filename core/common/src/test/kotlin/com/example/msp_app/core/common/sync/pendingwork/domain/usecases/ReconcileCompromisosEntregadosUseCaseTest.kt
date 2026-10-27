package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisoEnServidor
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisoEnServidorRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisosEntregadosStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.EntregadaConCompromiso
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.visitas.envio.CompromisoLocal
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conciliación inversa del compromiso (plan visitas P2, Tarea 18; E-APP-132;
 * spec §4.6-1). Puertos falsos: lo que se prueba es la decisión.
 */
class ReconcileCompromisosEntregadosUseCaseTest {

    private val promesa = CompromisoLocal(100, "2026-10-20", 15000L, null, null)
    private val cita = CompromisoLocal(null, null, null, "2026-10-17", "10:00")

    private fun id(n: Int) = "6f1c2a3b-0000-4000-8000-%012d".format(n)

    private class Almacen(
        val locales: List<EntregadaConCompromiso>,
        val falla: Boolean = false,
        val devolverFalla: Boolean = false
    ) : CompromisosEntregadosStore {
        val devueltas = mutableListOf<List<String>>()
        var desdePedido: String? = null
        override suspend fun entregadasConCompromisoDesde(
            desde: String
        ): List<EntregadaConCompromiso> {
            desdePedido = desde
            if (falla) throw IOException("room")
            return locales
        }

        override suspend fun devolverAPendiente(ids: List<String>): Int {
            if (devolverFalla) throw IOException("room")
            devueltas += ids
            return ids.size
        }
    }

    private class Servidor(
        val tiene: Map<String, CompromisoLocal> = emptyMap(),
        val tandaFalla: Int? = null,
        val ajenos: List<CompromisoEnServidor> = emptyList(),
        val cancela: Boolean = false
    ) : CompromisoEnServidorRegistry {
        val preguntas = mutableListOf<List<String>>()
        override suspend fun findCompromisos(visitIds: List<String>): List<CompromisoEnServidor> {
            if (cancela) throw CancellationException("cancelado")
            preguntas += visitIds
            if (tandaFalla == preguntas.size) throw IOException("sin red")
            return visitIds.mapNotNull { v -> tiene[v]?.let { CompromisoEnServidor(v, it) } } + ajenos
        }
    }

    private class Reporte : SyncErrorReporter {
        val codigos = mutableListOf<String>()
        val props = mutableListOf<Map<String, String>>()
        override fun report(code: String, message: String, props: Map<String, String>) {
            codigos += code
            this.props += props
        }
    }

    private val ahora = Instant.parse("2026-10-27T15:00:00Z")

    private fun correr(
        almacen: Almacen,
        servidor: Servidor,
        reporte: Reporte = Reporte()
    ): ConciliacionDeCompromisos = runBlocking {
        ReconcileCompromisosEntregadosUseCase(almacen, servidor, reporte) { ahora }.execute()
    }

    @Test
    fun `completo no hace nada ni reporta`() {
        val reporte = Reporte()
        val almacen = Almacen(listOf(EntregadaConCompromiso(id(1), promesa)))

        val r = correr(almacen, Servidor(tiene = mapOf(id(1) to promesa)), reporte)

        assertEquals(ConciliacionDeCompromisos(1, 0, 0, 0, 0, 0), r)
        assertTrue(almacen.devueltas.isEmpty())
        assertTrue(reporte.codigos.isEmpty())
    }

    @Test
    fun `al servidor le falta el bloque - se reporta y se devuelve a pendiente`() {
        val reporte = Reporte()
        val almacen = Almacen(listOf(EntregadaConCompromiso(id(1), promesa)))

        val r = correr(almacen, Servidor(tiene = mapOf(id(1) to CompromisoLocal.NINGUNO)), reporte)

        assertEquals(ConciliacionDeCompromisos(1, 1, 1, 0, 1, 0), r)
        assertEquals(listOf(listOf(id(1))), almacen.devueltas)
        assertEquals(
            listOf(ReconcileCompromisosEntregadosUseCase.ERROR_CODE_INCOMPLETA),
            reporte.codigos
        )
    }

    @Test
    fun `compromiso distinto solo se reporta y no se reenvia - decision del dueno`() {
        val reporte = Reporte()
        val almacen = Almacen(listOf(EntregadaConCompromiso(id(1), promesa)))

        val r = correr(
            almacen,
            Servidor(tiene = mapOf(id(1) to promesa.copy(promesaFecha = "2026-10-25"))),
            reporte
        )

        assertEquals(ConciliacionDeCompromisos(1, 1, 0, 1, 0, 0), r)
        assertTrue("un distinto no vuelve a pendiente", almacen.devueltas.isEmpty())
        assertEquals(
            listOf(ReconcileCompromisosEntregadosUseCase.ERROR_CODE_INCOMPLETA),
            reporte.codigos
        )
    }

    @Test
    fun `una visita que el servidor no nombra no se toca`() {
        val almacen = Almacen(listOf(EntregadaConCompromiso(id(1), promesa)))

        val r = correr(almacen, Servidor())

        assertEquals(ConciliacionDeCompromisos(0, 0, 0, 0, 0, 0), r)
        assertTrue(almacen.devueltas.isEmpty())
    }

    @Test
    fun `una tanda que falla no decide nada y las demas siguen`() {
        val locales = (1..150).map { EntregadaConCompromiso(id(it), cita) }
        val almacen = Almacen(locales)
        val servidor = Servidor(
            tiene = locales.associate { it.visitaId to CompromisoLocal.NINGUNO },
            tandaFalla = 1
        )

        val r = correr(almacen, servidor)

        assertEquals(2, servidor.preguntas.size)
        assertEquals(100, servidor.preguntas[0].size)
        assertEquals(1, r.tandasFallidas)
        // Solo la segunda tanda (50) decidió.
        assertEquals(50, r.porRellenar)
        assertEquals(locales.drop(100).map { it.visitaId }, almacen.devueltas.single())
    }

    @Test
    fun `lo que la respuesta trae de mas no se decide`() {
        val almacen = Almacen(listOf(EntregadaConCompromiso(id(1), promesa)))
        val ajeno = CompromisoEnServidor(id(99), CompromisoLocal.NINGUNO)

        val r = correr(almacen, Servidor(tiene = mapOf(id(1) to promesa), ajenos = listOf(ajeno)))

        assertEquals(1, r.revisadas)
        assertEquals(0, r.incompletas)
        assertTrue(almacen.devueltas.isEmpty())
    }

    @Test
    fun `un id que no es uuid no se pregunta`() {
        val reporte = Reporte()
        val almacen = Almacen(
            listOf(
                EntregadaConCompromiso("visita-001", promesa),
                EntregadaConCompromiso(id(1), promesa)
            )
        )
        val servidor = Servidor(tiene = mapOf(id(1) to promesa))

        correr(almacen, servidor, reporte)

        assertEquals(listOf(listOf(id(1))), servidor.preguntas)
        assertEquals(
            listOf(ReconcileCompromisosEntregadosUseCase.ERROR_CODE_ID_INVALIDO),
            reporte.codigos
        )
    }

    @Test
    fun `la lectura local que falla no pregunta nada`() {
        val servidor = Servidor()
        val reporte = Reporte()

        val r = correr(Almacen(emptyList(), falla = true), servidor, reporte)

        assertTrue(r.ilegibles)
        assertTrue(servidor.preguntas.isEmpty())
        assertEquals(
            listOf(ReconcileCompromisosEntregadosUseCase.ERROR_CODE_ILEGIBLES),
            reporte.codigos
        )
    }

    @Test
    fun `devolver a pendiente que falla se cuenta y no se pierde la corrida`() {
        val almacen = Almacen(listOf(EntregadaConCompromiso(id(1), promesa)), devolverFalla = true)

        val r = correr(almacen, Servidor(tiene = mapOf(id(1) to CompromisoLocal.NINGUNO)))

        assertEquals(1, r.porRellenar)
        assertEquals(0, r.devueltas)
        assertEquals(1, r.devolucionesFallidas)
    }

    @Test
    fun `la cancelacion se propaga`() {
        val almacen = Almacen(listOf(EntregadaConCompromiso(id(1), promesa)))
        var propagada = false
        runBlocking {
            try {
                ReconcileCompromisosEntregadosUseCase(
                    almacen,
                    Servidor(cancela = true),
                    Reporte()
                ) { ahora }.execute()
            } catch (e: CancellationException) {
                propagada = true
            }
        }
        assertTrue("la cancelación no se traga", propagada)
        assertTrue(almacen.devueltas.isEmpty())
    }

    @Test
    fun `la ventana es la de la inversa de visitas`() {
        val almacen = Almacen(emptyList())

        correr(almacen, Servidor())

        // 30 días antes de ahora (ReglaDePoda.CONFIRMADAS_DIAS).
        assertEquals("2026-09-27T15:00:00Z", almacen.desdePedido)
    }
}
