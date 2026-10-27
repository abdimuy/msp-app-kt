package com.example.msp_app.core.sync.zona

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DESDE = "2026-07-19T06:00:00Z"
private const val C0 = "2026-07-01T00:00:00Z"
private const val C1 = "2026-10-01T10:00:00.123456Z"
private const val C2 = "2026-10-01T11:00:00.654321Z"

/**
 * E-APP-120 · El motor común de cursor par (plan visitas P3, Tarea 10).
 *
 * Prueba las reglas del motor sin Room ni Retrofit, con un almacén en memoria
 * que se comporta como el DAO (página y posición juntas). Las mismas reglas
 * las prueban, de punta a punta y sin tocarse, las pruebas de
 * `VisitasZonaSyncManagerTest` (P2): este archivo fija el contrato del motor
 * para que la bajada de tareas (Tarea 11) herede exactamente lo mismo.
 */
class CursorParSyncEngineTest {

    private data class Fila(val id: String?, val valida: Boolean = true)

    private class Pag(
        val items: List<Fila> = emptyList(),
        val cursor: String? = null,
        val afterId: String? = null,
        val hasMore: Boolean = false,
        val epoch: Int? = null,
        val falla: Boolean = false
    )

    /** Almacén en memoria con la semántica del DAO: aplicar = filas + par juntos. */
    private class Almacen(var estado: EstadoSync = EstadoSync(null, null, null)) {
        val filas = linkedMapOf<String, Fila>()
        var reinicios = 0
        val epochsGuardados = mutableListOf<Int>()
    }

    private class Servidor(vararg paginas: Pag) {
        private val cola = ArrayDeque(paginas.toList())
        val cursores = mutableListOf<String?>()
        val afterIds = mutableListOf<String?>()
        val desdes = mutableListOf<String>()

        fun pagina(cursor: String?, afterId: String?, desde: String): PaginaSync<Fila> {
            cursores += cursor
            afterIds += afterId
            desdes += desde
            val p = cola.removeFirstOrNull() ?: error("el servidor no esperaba otra página")
            if (p.falla) throw IOException("red caída")
            // Semántica del servidor (dto_sync.go): con filas, cursor de la
            // última; sin filas, el cursor recibido salvo que se diga otro.
            return PaginaSync(
                items = p.items,
                cursor = p.cursor ?: cursor ?: if (p.items.isNotEmpty()) C1 else null,
                afterId = p.afterId,
                hasMore = p.hasMore,
                syncEpoch = p.epoch
            )
        }
    }

    private fun motor(srv: Servidor, a: Almacen) = CursorParSyncEngine<Fila>(
        pagina = { c, id, d -> srv.pagina(c, id, d) },
        idDe = { it.id },
        aplicar = { items, c, id ->
            require(c != null || id == null) { "media posición" }
            val validas = items.filter { it.valida && it.id != null }
            validas.forEach { a.filas[it.id!!] = it }
            a.estado = a.estado.copy(cursor = c, afterId = id)
            validas.size
        },
        estado = { a.estado },
        reiniciar = {
            a.reinicios++
            a.filas.clear()
            a.estado = a.estado.copy(cursor = null, afterId = null)
        },
        guardarEpoch = { e ->
            a.epochsGuardados += e
            a.estado = a.estado.copy(epoch = e)
        }
    )

    // ─── el par ────────────────────────────────────────────────────────────

    @Test
    fun laPosicionEsLaUltimaFilaYSeRetomaDeAhi_EAPP120() = runTest {
        val a = Almacen()
        val srv = Servidor(
            Pag(listOf(Fila("t-1"), Fila("t-2")), hasMore = true),
            Pag(listOf(Fila("t-3")), hasMore = false),
            Pag(hasMore = false)
        )
        motor(srv, a).correr(DESDE)
        motor(srv, a).correr(DESDE)

        assertEquals(listOf(null, C1, C1), srv.cursores)
        assertEquals(listOf(null, "t-2", "t-3"), srv.afterIds)
        assertEquals(EstadoSync(C1, "t-3", null), a.estado)
    }

    @Test
    fun elAfterIdDelServidorGanaSobreLaUltimaFila_EAPP120() = runTest {
        val a = Almacen()
        motor(Servidor(Pag(listOf(Fila("t-1"), Fila("t-2")), afterId = "t-9")), a).correr(DESDE)

        assertEquals(EstadoSync(C1, "t-9", null), a.estado)
    }

    @Test
    fun paginaVaciaConElMismoCursorConservaElAfterId_EAPP120() = runTest {
        val a = Almacen(EstadoSync(C0, "t-5", null))
        val srv = Servidor(Pag(), Pag())
        motor(srv, a).correr(DESDE)
        motor(srv, a).correr(DESDE)

        assertEquals(listOf("t-5", "t-5"), srv.afterIds)
        assertEquals(EstadoSync(C0, "t-5", null), a.estado)
    }

    @Test
    fun cursorMovidoSinFilasDejaElAfterIdNulo_EAPP120() = runTest {
        val a = Almacen(EstadoSync(C0, "t-5", null))
        motor(Servidor(Pag(cursor = C2)), a).correr(DESDE)

        assertEquals(EstadoSync(C2, null, null), a.estado)
    }

    @Test
    fun sinCursorNoHayMediaPosicion_EAPP120() = runTest {
        val a = Almacen()
        val srv = Servidor(Pag(afterId = "t-1"))
        motor(srv, a).correr(DESDE)

        assertEquals(EstadoSync(null, null, null), a.estado)
    }

    @Test
    fun corridaQueMuereConservaLaUltimaPaginaAplicada_EAPP120() = runTest {
        val a = Almacen()
        val srv = Servidor(Pag(listOf(Fila("t-1")), hasMore = true), Pag(falla = true))
        val error = runCatching { motor(srv, a).correr(DESDE) }.exceptionOrNull()

        assertTrue("$error", error is IOException)
        assertEquals(EstadoSync(C1, "t-1", null), a.estado)
    }

    @Test
    fun desdeViajaIgualEnTodasLasPaginas_EAPP120() = runTest {
        val srv = Servidor(Pag(listOf(Fila("t-1")), hasMore = true), Pag(listOf(Fila("t-2"))))
        motor(srv, Almacen()).correr(DESDE)

        assertEquals(listOf(DESDE, DESDE), srv.desdes)
    }

    @Test
    fun cuentaAplicadasYDescartadas_EAPP120() = runTest {
        val r = motor(
            Servidor(
                Pag(listOf(Fila("t-1"), Fila(null), Fila("t-3", valida = false), Fila("t-4")))
            ),
            Almacen()
        ).correr(DESDE)

        assertEquals(
            CorridaCursorPar(paginas = 1, aplicadas = 2, descartadas = 2, replayPorEpoch = false),
            // La posición de cierre (E-APP-110) tiene su propia prueba en el manager.
            r.copy(cursor = null, afterId = null)
        )
    }

    // ─── la generación ─────────────────────────────────────────────────────

    @Test
    fun epochNuevoReiniciaUnaVezYSeGuardaAlTerminar_EAPP120() = runTest {
        val a = Almacen(EstadoSync(C0, "t-0", 7))
        a.filas["vieja"] = Fila("vieja")
        val srv = Servidor(
            Pag(listOf(Fila("t-1")), epoch = 8),
            Pag(listOf(Fila("t-1")), hasMore = true, epoch = 8),
            Pag(listOf(Fila("t-2")), epoch = 8)
        )
        val r = motor(srv, a).correr(DESDE)

        assertTrue(r.replayPorEpoch)
        assertEquals(1, a.reinicios)
        assertEquals(listOf(C0, null, C1), srv.cursores)
        assertEquals(listOf("t-1", "t-2"), a.filas.keys.toList())
        assertEquals(listOf(8), a.epochsGuardados)
    }

    @Test
    fun epochIgualNoReplica_EAPP120() = runTest {
        val a = Almacen(EstadoSync(C0, "t-0", 8))
        motor(Servidor(Pag(epoch = 8)), a).correr(DESDE)

        assertEquals(0, a.reinicios)
        assertEquals(emptyList<Int>(), a.epochsGuardados)
    }

    @Test
    fun epochNoSePersisteSiElReplayMuereAMedias_EAPP120() = runTest {
        val a = Almacen(EstadoSync(C0, "t-0", 7))
        val srv = Servidor(
            Pag(epoch = 8),
            Pag(listOf(Fila("t-1")), hasMore = true, epoch = 8),
            Pag(falla = true)
        )
        runCatching { motor(srv, a).correr(DESDE) }

        assertEquals(emptyList<Int>(), a.epochsGuardados)
        assertEquals(7, a.estado.epoch)
    }

    @Test
    fun epochAusenteCeroONegativoEsApagado_EAPP120() = runTest {
        listOf(null, 0, -3).forEach { e ->
            val a = Almacen(EstadoSync(C0, "t-0", 5))
            motor(Servidor(Pag(epoch = e)), a).correr(DESDE)

            assertEquals("epoch=$e", 0, a.reinicios)
            assertEquals("epoch=$e", 5, a.estado.epoch)
        }
    }

    @Test
    fun epochQueRetrocedeTambienReplica_EAPP120() = runTest {
        val a = Almacen(EstadoSync(C0, "t-0", 9))
        motor(Servidor(Pag(epoch = 4), Pag(epoch = 4)), a).correr(DESDE)

        assertEquals(1, a.reinicios)
        assertEquals(4, a.estado.epoch)
    }

    @Test
    fun epochQueCambiaAMediaPaginacionNoSePersisteNiReiniciaOtraVez_EAPP120() = runTest {
        val a = Almacen(EstadoSync(C0, "t-0", 7))
        val srv = Servidor(
            Pag(epoch = 8),
            Pag(listOf(Fila("t-1")), hasMore = true, epoch = 8),
            Pag(listOf(Fila("t-2")), epoch = 9)
        )
        motor(srv, a).correr(DESDE)

        assertEquals("un solo reinicio por corrida", 1, a.reinicios)
        assertEquals(emptyList<Int>(), a.epochsGuardados)
    }

    @Test
    fun sinCursorGuardadoNoReiniciaYGuardaLaGeneracionAlTerminar_EAPP120() = runTest {
        val a = Almacen(EstadoSync(null, null, null))
        val r = motor(Servidor(Pag(listOf(Fila("t-1")), epoch = 3)), a).correr(DESDE)

        assertFalse(r.replayPorEpoch)
        assertEquals(0, a.reinicios)
        assertEquals(listOf(3), a.epochsGuardados)
    }

    // ─── forma ─────────────────────────────────────────────────────────────

    /**
     * El motor no conoce tablas: no nombra DAOs, entidades, transacciones ni
     * SQL. Todo lo que escribe pasa por las funciones que le dan (I8 para
     * visitas, y lo mismo para tareas). Control positivo: la expresión caza
     * un texto que sí los nombra.
     */
    @Test
    fun elMotorNoNombraTablasNiEscribeSQL_EAPP120() {
        val prohibido = Regex(
            """\b\w+(?:Dao|Entity)\b|\bwithTransaction\b|\bexecSQL\b|\bopenHelper\b|\bRoomDatabase\b"""
        )
        val texto = File(
            "src/main/java/com/example/msp_app/core/sync/zona/CursorParSyncEngine.kt"
        ).readText()
        assertTrue(
            "el motor existe y es el que se lee",
            texto.contains("class CursorParSyncEngine")
        )
        assertEquals(emptyList<String>(), prohibido.findAll(texto).map { it.value }.toList())
        assertEquals(
            listOf("VisitaZonaDao", "PaymentEntity", "withTransaction"),
            prohibido.findAll(
                "VisitaZonaDao PaymentEntity db.withTransaction { }"
            ).map { it.value }.toList()
        )
    }
}
