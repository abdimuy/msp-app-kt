package com.example.msp_app.core.sync.tareas

import com.example.msp_app.core.database.entities.TareaZonaEntity
import com.example.msp_app.core.database.entities.TareasSyncStateEntity
import com.example.msp_app.core.sync.visitas.ResultadoSync
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.visits.SyncTareasZonaResponse
import com.example.msp_app.data.api.services.visits.TareaZonaDto
import com.example.msp_app.data.api.services.visits.TareasZonaSyncApi
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val ZONA = 7
private const val OTRA_ZONA = 42
private const val CURSOR_VIEJO = "2026-07-01T00:00:00Z"
private const val CURSOR_PAGINA_1 = "2026-10-01T10:00:00.123456Z"
private const val CURSOR_PAGINA_2 = "2026-10-01T11:00:00.654321Z"

/** Todas las filas con el mismo `UPDATED_AT`: el cursor solo (sin id) no alcanza. */
private const val UPDATED_AT_RELLENO = "2026-10-16T03:00:00.000000Z"

/** 2026-10-19 23:30 en America/Mexico_City: ya es 20 de octubre en UTC. */
private val AHORA_2330_LOCAL: Instant = Instant.parse("2026-10-20T05:30:00Z")

/** hoy (19-oct, hora del negocio) − 92 días, a las 00:00 de America/Mexico_City (UTC−6). */
private const val DESDE_ESPERADO = "2026-07-19T06:00:00Z"

/**
 * E-APP-122 · Bajada de las tareas de oficina por zona (plan visitas P3,
 * Tarea 11). Consume el motor común [CursorParSyncEngine] (E-APP-120): aquí no
 * se vuelven a probar las dieciséis reglas del motor (ya las prueba
 * `CursorParSyncEngineTest`), sino que esta bajada las cumple de punta a
 * punta contra Room real y que traduce, guarda y avisa bien:
 *
 * - el par y la generación, con los nombres de la Tarea 11 del plan;
 * - lo bajado va a `tarea_zona` y su posición a `tareas_sync_state`, juntos;
 * - una tarea cancelada llega como un cambio, no como lápida;
 * - la nota de la tarea viaja y se guarda (decisión del dueño, hasta 500);
 * - los goldens del servidor pasan por Retrofit y Gson reales.
 */
class TareasZonaSyncManagerTest : RoomTestBase() {

    private val dao get() = db.tareaZonaDao()

    private fun newManager(
        api: TareasZonaSyncApi,
        zona: Int? = ZONA,
        bajada: Boolean = true,
        ahora: Instant = AHORA_2330_LOCAL,
        alAplicar: suspend (List<TareaZonaEntity>) -> Unit = {}
    ) = TareasZonaSyncManager(
        api = api,
        dao = dao,
        zonaActual = { zona },
        killSwitch = { bajada },
        alAplicar = alAplicar,
        reloj = { ahora }
    )

    // ─── El par (UPDATED_AT, ID) ───────────────────────────────────────────

    @Test
    fun segundaCorridaRetomaDelAfterIdPersistidoConTodoElLoteEmpatado() = runTest {
        val api = FakeApi(
            Pagina(listOf(dto("t-001"), dto("t-002")), hasMore = true),
            Pagina(listOf(dto("t-003"), dto("t-004")), hasMore = false),
            Pagina(emptyList(), hasMore = false)
        )
        val mgr = newManager(api)
        mgr.sincronizar()
        mgr.sincronizar()

        assertEquals(listOf(null, UPDATED_AT_RELLENO, UPDATED_AT_RELLENO), api.cursores)
        assertEquals(listOf(null, "t-002", "t-004"), api.afterIds)
        assertEquals(
            TareasSyncStateEntity(ZONA, UPDATED_AT_RELLENO, "t-004", null),
            dao.estado(ZONA)
        )
        assertEquals(listOf("t-001", "t-002", "t-003", "t-004"), dao.deZona(ZONA).map { it.ID })
    }

    @Test
    fun paginaVaciaNoReiniciaElAfterIdPersistido() = runTest {
        val api = FakeApi(
            Pagina(listOf(dto("t-020")), hasMore = false),
            Pagina(emptyList(), hasMore = false),
            Pagina(emptyList(), hasMore = false)
        )
        val mgr = newManager(api)
        mgr.sincronizar()
        mgr.sincronizar()
        mgr.sincronizar()

        assertEquals(listOf(null, "t-020", "t-020"), api.afterIds)
        assertEquals(
            TareasSyncStateEntity(ZONA, UPDATED_AT_RELLENO, "t-020", null),
            dao.estado(ZONA)
        )
    }

    @Test
    fun cursorNuncaSeReconstruyeDeTareaZona() = runTest {
        dao.aplicarPagina(
            ZONA,
            listOf(entidad("t-local-1", updatedAt = CURSOR_PAGINA_2), entidad("t-local-2")),
            null,
            null
        )
        val api = FakeApi(Pagina(listOf(dto("t-9", updatedAt = CURSOR_PAGINA_1)), hasMore = false))

        newManager(api).sincronizar()

        assertEquals(
            "hay filas pero ninguna posición: se pide desde el inicio",
            listOf<String?>(null),
            api.cursores
        )
        assertEquals(
            TareasSyncStateEntity(ZONA, CURSOR_PAGINA_1, "t-9", null),
            dao.estado(ZONA)
        )
    }

    // ─── Cambio de zona ────────────────────────────────────────────────────

    @Test
    fun cambioDeZonaDejaCursorYAfterIdNulos() = runTest {
        newManager(FakeApi(Pagina(listOf(dto("t-1"), dto("t-2")), hasMore = false))).sincronizar()
        assertEquals(
            TareasSyncStateEntity(ZONA, UPDATED_AT_RELLENO, "t-2", null),
            dao.estado(ZONA)
        )

        val apiZona42 = FakeApi(Pagina(listOf(dto("w-1", zona = OTRA_ZONA)), hasMore = false))
        val r = newManager(apiZona42, zona = OTRA_ZONA).sincronizar()

        assertTrue("$r", r is ResultadoSync.Ok && r.cambioDeZona)
        assertEquals("la zona nueva arranca sin cursor", listOf<String?>(null), apiZona42.cursores)
        assertEquals("y sin after_id", listOf<String?>(null), apiZona42.afterIds)
        assertEquals(emptyList<String>(), dao.deZona(ZONA).map { it.ID })
        assertEquals(
            "la zona vieja queda sin posición: al volver, sus tareas deben bajar otra vez",
            TareasSyncStateEntity(ZONA, null, null, null),
            dao.estado(ZONA)
        )
        assertEquals(listOf("w-1"), dao.deZona(OTRA_ZONA).map { it.ID })
    }

    // ─── La generación (sync_epoch) ────────────────────────────────────────

    @Test
    fun epochNoSePersisteHastaQueElReplayTermina() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "t-0", epoch = 7)

        val apiInterrumpido = FakeApi(
            Pagina(emptyList(), hasMore = false, epoch = 8),
            Pagina(listOf(dto("t-3103", updatedAt = CURSOR_PAGINA_1)), hasMore = true, epoch = 8),
            Pagina(emptyList(), hasMore = false, epoch = 8, falla = true)
        )
        assertTrue(newManager(apiInterrumpido).sincronizar() is ResultadoSync.Error)

        val aMedias = dao.estado(ZONA)!!
        assertEquals("la generación nueva NO se persiste a medio replay", 7, aMedias.EPOCH)
        assertEquals(CURSOR_PAGINA_1, aMedias.CURSOR)

        val apiReintento = FakeApi(
            Pagina(emptyList(), hasMore = false, epoch = 8),
            Pagina(listOf(dto("t-3104", updatedAt = CURSOR_PAGINA_2)), hasMore = false, epoch = 8)
        )
        newManager(apiReintento).sincronizar()

        assertEquals(listOf(CURSOR_PAGINA_1, null), apiReintento.cursores)
        assertEquals(8, dao.estado(ZONA)!!.EPOCH)
        assertEquals(listOf("t-3104"), dao.deZona(ZONA).map { it.ID })
    }

    @Test
    fun epochCeroSeIgnoraYNoEntraEnBucleDeReplays() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "t-0", epoch = 8)
        val api = FakeApi(
            Pagina(listOf(dto("t-3107", updatedAt = CURSOR_PAGINA_1)), hasMore = false, epoch = 0),
            Pagina(listOf(dto("t-3108", updatedAt = CURSOR_PAGINA_2)), hasMore = false, epoch = -1)
        )
        val mgr = newManager(api)
        mgr.sincronizar()
        mgr.sincronizar()

        assertEquals(listOf(CURSOR_VIEJO, CURSOR_PAGINA_1), api.cursores)
        assertEquals(8, dao.estado(ZONA)!!.EPOCH)
    }

    @Test
    fun epochNuevoReplicaYBorraLoBajadoAntes() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "t-vieja", epoch = 7)
        dao.aplicarPagina(ZONA, listOf(entidad("t-vieja")), CURSOR_VIEJO, "t-vieja")
        val api = FakeApi(
            Pagina(listOf(dto("t-3101")), hasMore = false, epoch = 8),
            Pagina(listOf(dto("t-3101")), hasMore = false, epoch = 8)
        )

        val r = newManager(api).sincronizar()

        assertTrue("$r", r is ResultadoSync.Ok && r.replayPorEpoch)
        assertEquals(listOf(CURSOR_VIEJO, null), api.cursores)
        assertEquals(listOf("t-3101"), dao.deZona(ZONA).map { it.ID })
        assertEquals(8, dao.estado(ZONA)!!.EPOCH)
    }

    // ─── Qué se guarda ─────────────────────────────────────────────────────

    /** Una cancelación es un cambio con `updated_at` nuevo, no una lápida: reemplaza la fila. */
    @Test
    fun tareaCanceladaLlegaComoCambio() = runTest {
        val api = FakeApi(
            Pagina(listOf(dto("t-1")), hasMore = false),
            Pagina(
                listOf(dto("t-1", updatedAt = CURSOR_PAGINA_1, canceladaAt = CURSOR_PAGINA_1)),
                hasMore = false
            )
        )
        val mgr = newManager(api)
        mgr.sincronizar()
        assertNull(dao.deZona(ZONA).single().CANCELADA_AT)

        mgr.sincronizar()

        val fila = dao.deZona(ZONA).single()
        assertEquals(CURSOR_PAGINA_1, fila.CANCELADA_AT)
        assertEquals(CURSOR_PAGINA_1, fila.UPDATED_AT)
    }

    /** Decisión del dueño (2026-10-19): la nota de la tarea de oficina viaja, hasta 500. */
    @Test
    fun laNotaDeLaTareaSeGuardaCompletaHastaLos500() = runTest {
        val nota = "á".repeat(500)
        val api = FakeApi(Pagina(listOf(dto("t-nota", nota = nota)), hasMore = false))

        newManager(api).sincronizar()

        assertEquals(nota, dao.deZona(ZONA).single().NOTA)
    }

    @Test
    fun alAplicarRecibeLasFilasDeCadaPaginaYaGuardadas() = runTest {
        val api = FakeApi(
            Pagina(listOf(dto("t-1"), dto("t-2")), hasMore = true),
            Pagina(listOf(dto("t-3")), hasMore = false)
        )
        val recibidas = mutableListOf<List<String>>()
        val guardadasAlAvisar = mutableListOf<List<String>>()

        newManager(api) { filas ->
            recibidas += filas.map { it.ID }
            guardadasAlAvisar += dao.deZona(ZONA).map { it.ID }
        }.sincronizar()

        assertEquals(listOf(listOf("t-1", "t-2"), listOf("t-3")), recibidas)
        assertEquals(
            "al avisar, la página ya está en tarea_zona",
            listOf(listOf("t-1", "t-2"), listOf("t-1", "t-2", "t-3")),
            guardadasAlAvisar
        )
    }

    /** El aviso es comodidad: si revienta, la posición ya avanzó y el sync no falla. */
    @Test
    fun unAvisoQueRevientaNoRompeElSync() = runTest {
        val api = FakeApi(Pagina(listOf(dto("t-1")), hasMore = false))

        val r = newManager(
            api
        ) { throw IllegalStateException("notificaciones negadas") }.sincronizar()

        assertTrue("$r", r is ResultadoSync.Ok)
        assertEquals(listOf("t-1"), dao.deZona(ZONA).map { it.ID })
        assertEquals("t-1", dao.estado(ZONA)!!.AFTER_ID)
    }

    // ─── Kill-switch, zona, ventana ────────────────────────────────────────

    @Test
    fun killSwitchApagadoNoBaja() = runTest {
        val api = FakeApi(Pagina(listOf(dto("t-1")), hasMore = false))

        val r = newManager(api, bajada = false).sincronizar()

        assertEquals(ResultadoSync.Apagado, r)
        assertEquals(0, api.llamadas)
        assertNull(dao.estado(ZONA))
        assertEquals(emptyList<String>(), dao.deZona(ZONA).map { it.ID })
    }

    @Test
    fun sinZonaNoBaja() = runTest {
        val api = FakeApi(Pagina(listOf(dto("t-1")), hasMore = false))

        assertEquals(ResultadoSync.SinZona, newManager(api, zona = null).sincronizar())
        assertEquals(0, api.llamadas)
    }

    @Test
    fun syncEnviaElMismoDesdeEnTodasLasPaginas() = runTest {
        val api = FakeApi(
            Pagina(listOf(dto("t-1")), hasMore = true),
            Pagina(listOf(dto("t-2")), hasMore = false)
        )

        newManager(api).sincronizar()

        assertEquals(listOf(DESDE_ESPERADO, DESDE_ESPERADO), api.desdes)
    }

    // ─── Gson y los goldens del servidor ───────────────────────────────────

    /**
     * Por el convertidor real (Gson sin builder, como `RetrofitClientFactory`):
     * una fila con todo lo opcional en `null`, una sin `id` y una respuesta sin
     * `sync_epoch` ni `has_more`. La completa entra, la que no tiene id se
     * descarta y se cuenta, y el sync no revienta.
     */
    @Test
    fun itemConCamposNulosNoRevientaElSync() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"items":[
                      {"id":"t-nulos","venta_id":501,"cliente_id":10,"zona_cliente_id":null,
                       "fecha":"2026-10-20","hora":null,"nota":null,"reemplaza_a":null,
                       "created_at":"2026-10-18T22:15:00Z","updated_at":"$CURSOR_PAGINA_1",
                       "cancelada_at":null},
                      {"id":null,"venta_id":502,"cliente_id":11,"fecha":"2026-10-20",
                       "created_at":"2026-10-18T22:15:00Z","updated_at":"$CURSOR_PAGINA_1"}
                     ],
                     "cursor":"$CURSOR_PAGINA_1","after_id":"t-nulos"}
                    """.trimIndent()
                )
            )
            server.start()

            val r = newManager(apiReal(server)).sincronizar()

            assertTrue("$r", r is ResultadoSync.Ok)
            r as ResultadoSync.Ok
            assertEquals(1, r.aplicadas)
            assertEquals(1, r.descartadas)
            val fila = dao.deZona(ZONA).single()
            assertEquals("t-nulos", fila.ID)
            assertNull(fila.HORA)
            assertNull(fila.NOTA)
            assertNull(fila.CANCELADA_AT)
            assertEquals(
                TareasSyncStateEntity(ZONA, CURSOR_PAGINA_1, "t-nulos", null),
                dao.estado(ZONA)
            )
        } finally {
            server.shutdown()
        }
    }

    /** Las rutas y parámetros coinciden con `routes.go` y `handlers_tareas.go` del servidor. */
    @Test
    fun laPeticionUsaLaRutaYLosParametrosDelServidor() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[],"cursor":"$CURSOR_VIEJO","after_id":null,"has_more":false}"""
                )
            )
            server.start()
            sembrarEstado(cursor = CURSOR_VIEJO, afterId = "t-0", epoch = null)

            newManager(apiReal(server)).sincronizar()

            val url = server.takeRequest().requestUrl!!
            assertEquals("/v2/visitas/sync/tareas/zona/$ZONA", url.encodedPath)
            assertEquals(DESDE_ESPERADO, url.queryParameter("desde"))
            assertEquals(CURSOR_VIEJO, url.queryParameter("cursor"))
            assertEquals("t-0", url.queryParameter("after_id"))
            assertEquals("${TareasZonaSyncApi.LIMITE_PAGINA}", url.queryParameter("limit"))
        } finally {
            server.shutdown()
        }
    }

    /**
     * Los dos goldens del servidor (`tareas-sync-pagina` y `-pagina-vacia`,
     * copiados de msp-api `feat/visitas-p3-servidor` @b7ae847) por el
     * convertidor real: la forma que el servidor promete es la que el teléfono
     * guarda. La segunda página trae un cursor MOVIDO sin filas: `after_id`
     * nulo y, terminado el replay, la generación 3 queda aplicada.
     */
    @Test
    fun losGoldensDelServidorPasanPorElSyncReal() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(golden("tareas-sync-pagina")))
            server.enqueue(MockResponse().setBody(golden("tareas-sync-pagina-vacia")))
            server.start()
            val recibidas = mutableListOf<TareaZonaEntity>()

            val r = newManager(apiReal(server), zona = ZONA) { recibidas += it }.sincronizar()

            assertTrue("$r", r is ResultadoSync.Ok)
            r as ResultadoSync.Ok
            assertEquals(2, r.paginas)
            assertEquals(3, r.aplicadas)
            assertEquals(0, r.descartadas)
            val filas = dao.deZona(ZONA).associateBy { it.ID }
            assertEquals(3, filas.size)
            val conNota = filas.getValue("0a7e0c3b-0000-4000-8000-000000000001")
            assertEquals("toca el timbre azul; pregunta por doña Inés", conNota.NOTA)
            assertEquals("10:30", conNota.HORA)
            assertEquals(501, conNota.VENTA_ID)
            assertEquals(10, conNota.CLIENTE_ID)
            assertEquals("2026-10-20", conNota.FECHA)
            assertEquals("2026-10-18T22:15:00Z", conNota.CREATED_AT)
            assertNull(filas.getValue("0a7e0c3b-0000-4000-8000-000000000007").HORA)
            assertEquals(
                "el cursor movido sin filas deja after_id nulo y la generación aplicada",
                TareasSyncStateEntity(ZONA, "2026-10-19T15:45:30.123456789Z", null, 3),
                dao.estado(ZONA)
            )
            assertEquals(3, recibidas.size)
            val primera = server.takeRequest()
            assertNotNull(primera)
            val peticion2 = server.takeRequest().requestUrl!!
            assertEquals("2026-10-18T23:00:00Z", peticion2.queryParameter("cursor"))
            assertEquals(
                "0a7e0c3b-0000-4000-8000-000000000003",
                peticion2.queryParameter("after_id")
            )
        } finally {
            server.shutdown()
        }
    }

    // ─── utilidades ─────────────────────────────────────────────────────────

    private fun golden(nombre: String): String {
        val url =
            requireNotNull(
                javaClass.classLoader?.getResource("tareas-golden/$nombre.golden.json")
            ) {
                "falta el golden $nombre"
            }
        return url.readText(Charsets.UTF_8)
    }

    private fun apiReal(server: MockWebServer): TareasZonaSyncApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TareasZonaSyncApi::class.java)

    private suspend fun sembrarEstado(cursor: String?, afterId: String?, epoch: Int?) {
        dao.aplicarPagina(ZONA, emptyList(), cursor, afterId)
        if (epoch != null) dao.guardarEpoch(ZONA, epoch)
    }

    private data class Pagina(
        val items: List<TareaZonaDto>,
        val hasMore: Boolean,
        val epoch: Int? = null,
        val falla: Boolean = false
    )

    /**
     * Imita al servidor: con filas, `cursor` = `updated_at` de la última y
     * `after_id` = su id; sin filas, el cursor RECIBIDO y `after_id = null`.
     */
    private class FakeApi(vararg paginas: Pagina) : TareasZonaSyncApi {
        private val cola = ArrayDeque(paginas.toList())
        val cursores = mutableListOf<String?>()
        val afterIds = mutableListOf<String?>()
        val desdes = mutableListOf<String>()
        var llamadas = 0

        override suspend fun sync(
            zona: Int,
            desde: String,
            cursor: String?,
            afterId: String?,
            limit: Int?
        ): SyncTareasZonaResponse {
            llamadas++
            cursores += cursor
            afterIds += afterId
            desdes += desde
            val p = cola.removeFirstOrNull() ?: throw IOException("sin más páginas")
            if (p.falla) throw IOException("red caída")
            val ultima = p.items.lastOrNull()
            return SyncTareasZonaResponse(
                items = p.items,
                cursor = ultima?.updated_at ?: cursor,
                after_id = ultima?.id,
                has_more = p.hasMore,
                sync_epoch = p.epoch
            )
        }
    }

    private fun dto(
        id: String,
        updatedAt: String = UPDATED_AT_RELLENO,
        zona: Int = ZONA,
        nota: String? = null,
        canceladaAt: String? = null
    ) = TareaZonaDto(
        id = id,
        venta_id = 501,
        cliente_id = 10,
        zona_cliente_id = zona,
        fecha = "2026-10-20",
        hora = "10:30",
        nota = nota,
        reemplaza_a = null,
        created_at = "2026-10-15T16:00:00.000000Z",
        updated_at = updatedAt,
        cancelada_at = canceladaAt
    )

    private fun entidad(id: String, updatedAt: String = CURSOR_VIEJO) = TareaZonaEntity(
        ID = id,
        VENTA_ID = 501,
        CLIENTE_ID = 10,
        ZONA_CLIENTE_ID = ZONA,
        FECHA = "2026-10-20",
        HORA = null,
        NOTA = null,
        REEMPLAZA_A = null,
        CREATED_AT = "2026-10-15T16:00:00.000000Z",
        UPDATED_AT = updatedAt,
        CANCELADA_AT = null
    )
}
