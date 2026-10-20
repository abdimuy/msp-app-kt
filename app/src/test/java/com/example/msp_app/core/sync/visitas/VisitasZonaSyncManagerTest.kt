package com.example.msp_app.core.sync.visitas

import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.core.database.entities.VisitasSyncStateEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.visits.ByIdsVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.DigestVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.IdsVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.SyncVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.VisitaZonaDto
import com.example.msp_app.data.api.services.visits.VisitasZonaSyncApi
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val ZONA = 21
private const val OTRA_ZONA = 42

/** Cursor "ya avanzado" que simula un teléfono con la agenda sincronizada. */
private const val CURSOR_VIEJO = "2026-07-01T00:00:00Z"
private const val CURSOR_PAGINA_1 = "2026-10-01T10:00:00.123456Z"
private const val CURSOR_PAGINA_2 = "2026-10-01T11:00:00.654321Z"

/**
 * Todas las filas con el mismo `UPDATED_AT`: lo que deja el relleno en tandas
 * de `MSP_VISITAS.UPDATED_AT` (plan P2 Tarea 6) y lo que rompe un cursor que
 * solo guarda la mitad `UPDATED_AT` (`COBRANZA-SYNC.md` §2).
 */
private const val UPDATED_AT_RELLENO = "2026-10-16T03:00:00.000000Z"

/** 2026-10-19 23:30 en America/Mexico_City: ya es 20 de octubre en UTC. */
private val AHORA_2330_LOCAL: Instant = Instant.parse("2026-10-20T05:30:00Z")

/** hoy (19-oct, hora del negocio) − 92 días, a las 00:00 de America/Mexico_City (UTC−6). */
private const val DESDE_ESPERADO = "2026-07-19T06:00:00Z"

/**
 * E-APP-101 · Bajada de la agenda de visitas por zona (plan visitas P2,
 * Tarea 16). Copia de las reglas y los nombres de `CobranzaSyncManagerTest`
 * (`COBRANZA-SYNC.md` §2 y §3), en su versión de visitas:
 *
 * - el cursor es un PAR `(UPDATED_AT, ID)` y la página vacía no mueve el
 *   `AFTER_ID` (`null` = sin posición nueva);
 * - la generación (`sync_epoch`) se persiste solo cuando el replay terminó;
 * - lo bajado va a `visita_zona` y NUNCA a `Visit` (I8, E-APP-103);
 * - el cursor solo sale de la respuesta del servidor y se guarda en la MISMA
 *   transacción que la página (E-VIS-070, E-APP-102).
 *
 * El API falso imita la semántica del servidor (`dto_sync.go`): con filas
 * devuelve `cursor`/`after_id` de la última; sin filas, el cursor recibido y
 * `after_id = null`.
 */
class VisitasZonaSyncManagerTest : RoomTestBase() {

    private val dao get() = db.visitaZonaDao()
    private val visitDao get() = db.visitDao()

    private fun newManager(
        api: VisitasZonaSyncApi,
        zona: Int? = ZONA,
        bajada: Boolean = true,
        ahora: Instant = AHORA_2330_LOCAL
    ) = VisitasZonaSyncManager(
        api = api,
        dao = dao,
        zonaActual = { zona },
        killSwitch = { bajada },
        reloj = { ahora }
    )

    // ─── AFTER_ID: la otra mitad del cursor ────────────────────────────────

    /**
     * Review Focus 4 del plan: todas las filas comparten `updated_at`. La
     * segunda corrida arranca del `after_id` persistido; si arrancara en nulo
     * volvería a bajar el mismo lote en cada corrida.
     */
    @Test
    fun segundaCorridaRetomaDelAfterIdPersistidoConTodoElLoteEmpatado() = runTest {
        val api = FakeApi(
            Pagina(listOf(dto("v-001"), dto("v-002")), hasMore = true),
            Pagina(listOf(dto("v-003"), dto("v-004")), hasMore = false),
            Pagina(emptyList(), hasMore = false)
        )
        val mgr = newManager(api)
        mgr.sincronizar()
        mgr.sincronizar()

        assertEquals(
            "el cursor se ve idéntico en las llamadas 2 y 3: el cursor solo no alcanza",
            listOf(null, UPDATED_AT_RELLENO, UPDATED_AT_RELLENO),
            api.cursores
        )
        assertEquals(
            "la segunda corrida arranca donde quedó la primera, no al inicio del grupo",
            listOf(null, "v-002", "v-004"),
            api.afterIds
        )
        assertEquals(
            VisitasSyncStateEntity(ZONA, UPDATED_AT_RELLENO, "v-004", null),
            dao.estado(ZONA)
        )
        assertEquals(listOf("v-001", "v-002", "v-003", "v-004"), dao.deZona(ZONA).map { it.ID })
    }

    /**
     * Una página vacía NO es "sin posición": el servidor devuelve el cursor
     * recibido y `after_id = null`. Tres corridas seguidas es lo mínimo para
     * ver el bucle: la tercera sigue pidiendo desde el mismo `after_id`.
     */
    @Test
    fun paginaVaciaNoReiniciaElAfterIdPersistido() = runTest {
        val api = FakeApi(
            Pagina(listOf(dto("v-020")), hasMore = false),
            Pagina(emptyList(), hasMore = false),
            Pagina(emptyList(), hasMore = false)
        )
        val mgr = newManager(api)
        mgr.sincronizar()
        mgr.sincronizar()
        mgr.sincronizar()

        assertEquals(listOf(null, "v-020", "v-020"), api.afterIds)
        assertEquals(
            VisitasSyncStateEntity(ZONA, UPDATED_AT_RELLENO, "v-020", null),
            dao.estado(ZONA)
        )
    }

    @Test
    fun corridaInterrumpidaRetomaDesdeLaUltimaPaginaAplicada() = runTest {
        val apiCaido = FakeApi(
            Pagina(listOf(dto("v-010")), hasMore = true),
            Pagina(emptyList(), hasMore = false, falla = true)
        )
        assertTrue(newManager(apiCaido).sincronizar() is ResultadoSync.Error)
        assertEquals(
            "la página aplicada dejó su par persistido antes de morir",
            VisitasSyncStateEntity(ZONA, UPDATED_AT_RELLENO, "v-010", null),
            dao.estado(ZONA)
        )

        val apiReintento = FakeApi(Pagina(emptyList(), hasMore = false))
        newManager(apiReintento).sincronizar()

        assertEquals(listOf(UPDATED_AT_RELLENO), apiReintento.cursores)
        assertEquals(listOf("v-010"), apiReintento.afterIds)
    }

    /** El replay por generación descarta el cursor Y el `after_id` en el mismo paso. */
    @Test
    fun replayPorEpochReiniciaElAfterIdJuntoConElCursor() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-205", epoch = 7)

        val api = FakeApi(
            Pagina(listOf(dto("v-201")), hasMore = false, epoch = 8),
            Pagina(listOf(dto("v-201")), hasMore = false, epoch = 8)
        )
        newManager(api).sincronizar()

        assertEquals(listOf(CURSOR_VIEJO, null), api.cursores)
        assertEquals(
            "el replay arranca desde el principio: after_id vuelve a nulo junto con el cursor",
            listOf("v-205", null),
            api.afterIds
        )
    }

    // ─── Cambio de zona (Review Focus 5, I8) ───────────────────────────────

    /**
     * El teléfono cambia de zona con visitas propias pendientes: lo bajado de
     * la zona vieja se borra, cursor y `AFTER_ID` quedan nulos y la zona nueva
     * arranca desde el inicio; `Visit` queda intacta campo a campo.
     */
    @Test
    fun cambioDeZonaNoTocaVisitYDejaCursorYAfterIdNulos() = runTest {
        val propia = visitaPendiente("p-1", ZONA)
        visitDao.insertVisit(propia)
        newManager(FakeApi(Pagina(listOf(dto("v-1"), dto("v-2")), hasMore = false)))
            .sincronizar()
        assertEquals(
            VisitasSyncStateEntity(ZONA, UPDATED_AT_RELLENO, "v-2", null),
            dao.estado(ZONA)
        )

        val apiZona42 = FakeApi(Pagina(listOf(dto("w-1", zona = OTRA_ZONA)), hasMore = false))
        val r = newManager(apiZona42, zona = OTRA_ZONA).sincronizar()

        assertTrue("$r", r is ResultadoSync.Ok && r.cambioDeZona)
        assertEquals("la zona nueva arranca sin cursor", listOf<String?>(null), apiZona42.cursores)
        assertEquals("y sin after_id", listOf<String?>(null), apiZona42.afterIds)
        assertEquals(emptyList<String>(), dao.deZona(ZONA).map { it.ID })
        assertEquals(
            "la zona vieja queda sin posición: al volver, sus visitas deben bajar otra vez",
            VisitasSyncStateEntity(ZONA, null, null, null),
            dao.estado(ZONA)
        )
        assertEquals(listOf("w-1"), dao.deZona(OTRA_ZONA).map { it.ID })
        assertEquals("I8: Visit intacta", propia, visitDao.findVisitById("p-1"))
    }

    // ─── La generación (sync_epoch) ────────────────────────────────────────

    @Test
    fun epochNuevoLimpiaElCursorYReplicaDesdeElInicio() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-vieja", epoch = 7)
        dao.aplicarPagina(ZONA, listOf(entidad("v-vieja")), CURSOR_VIEJO, "v-vieja")

        val api = FakeApi(
            // Primera respuesta: trae la generación nueva y dispara el replay;
            // esta página ni se aplica.
            Pagina(listOf(dto("v-3101")), hasMore = false, epoch = 8),
            Pagina(listOf(dto("v-3101")), hasMore = false, epoch = 8)
        )
        val r = newManager(api).sincronizar()

        assertTrue("$r", r is ResultadoSync.Ok && r.replayPorEpoch)
        assertEquals(listOf(CURSOR_VIEJO, null), api.cursores)
        assertEquals(
            "terminado el replay, la generación queda aplicada",
            VisitasSyncStateEntity(ZONA, UPDATED_AT_RELLENO, "v-3101", 8),
            dao.estado(ZONA)
        )
        assertEquals(
            "el replay parte de cero: lo bajado antes no sobrevive",
            listOf("v-3101"),
            dao.deZona(ZONA).map { it.ID }
        )
    }

    /** Misma generación: si replicara, el falso se quedaría sin páginas y daría Error. */
    @Test
    fun epochIgualNoReplica() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-0", epoch = 8)

        val api = FakeApi(Pagina(listOf(dto("v-3102")), hasMore = false, epoch = 8))
        val r = newManager(api).sincronizar()

        assertTrue("$r", r is ResultadoSync.Ok)
        assertEquals(listOf(CURSOR_VIEJO), api.cursores)
        assertEquals(8, dao.estado(ZONA)!!.EPOCH)
    }

    /**
     * EL invariante del diseño: la generación se persiste solo cuando el replay
     * TERMINÓ. Si muere a media descarga, la guardada sigue siendo la vieja y
     * el próximo arranque replica otra vez.
     */
    @Test
    fun epochNoSePersisteHastaQueElReplayTermina() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-0", epoch = 7)

        val apiInterrumpido = FakeApi(
            Pagina(emptyList(), hasMore = false, epoch = 8),
            Pagina(
                listOf(dto("v-3103", updatedAt = CURSOR_PAGINA_1)),
                hasMore = true,
                epoch = 8
            ),
            Pagina(emptyList(), hasMore = false, epoch = 8, falla = true)
        )
        assertTrue(newManager(apiInterrumpido).sincronizar() is ResultadoSync.Error)

        val aMedias = dao.estado(ZONA)!!
        assertEquals("la generación nueva NO se persiste a medio replay", 7, aMedias.EPOCH)
        assertEquals(CURSOR_PAGINA_1, aMedias.CURSOR)

        val apiReintento = FakeApi(
            Pagina(emptyList(), hasMore = false, epoch = 8),
            Pagina(
                listOf(dto("v-3104", updatedAt = CURSOR_PAGINA_2)),
                hasMore = false,
                epoch = 8
            )
        )
        newManager(apiReintento).sincronizar()

        assertEquals(
            "el reintento vuelve a arrancar sin cursor",
            listOf(CURSOR_PAGINA_1, null),
            apiReintento.cursores
        )
        assertEquals(8, dao.estado(ZONA)!!.EPOCH)
        assertEquals(listOf("v-3104"), dao.deZona(ZONA).map { it.ID })
    }

    @Test
    fun servidorSinEpochNoReplicaNiPierdeLaGeneracionAplicada() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-0", epoch = 8)

        val api = FakeApi(
            Pagina(listOf(dto("v-3105", updatedAt = CURSOR_PAGINA_1)), hasMore = false),
            Pagina(listOf(dto("v-3106", updatedAt = CURSOR_PAGINA_2)), hasMore = false)
        )
        val mgr = newManager(api)
        mgr.sincronizar()
        mgr.sincronizar()

        assertEquals(listOf(CURSOR_VIEJO, CURSOR_PAGINA_1), api.cursores)
        assertEquals(8, dao.estado(ZONA)!!.EPOCH)
    }

    /**
     * El servidor de visitas manda `sync_epoch = 0` cuando el mecanismo está
     * apagado o falló la lectura (`dto_sync.go`); un negativo tampoco es una
     * generación. Ninguno dispara replay ni pisa la aplicada.
     */
    @Test
    fun epochCeroSeIgnoraYNoEntraEnBucleDeReplays() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-0", epoch = 8)

        val api = FakeApi(
            Pagina(
                listOf(dto("v-3107", updatedAt = CURSOR_PAGINA_1)),
                hasMore = false,
                epoch = 0
            ),
            Pagina(
                listOf(dto("v-3108", updatedAt = CURSOR_PAGINA_2)),
                hasMore = false,
                epoch = -1
            )
        )
        val mgr = newManager(api)
        mgr.sincronizar()
        mgr.sincronizar()

        assertEquals(listOf(CURSOR_VIEJO, CURSOR_PAGINA_1), api.cursores)
        assertEquals(8, dao.estado(ZONA)!!.EPOCH)
    }

    /** La generación es identidad, no orden: si retrocede replica UNA vez. */
    @Test
    fun epochQueRetrocedeReplicaUnaSolaVez() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-0", epoch = 9)

        val api = FakeApi(
            Pagina(emptyList(), hasMore = false, epoch = 3),
            Pagina(
                listOf(dto("v-3109", updatedAt = CURSOR_PAGINA_1)),
                hasMore = false,
                epoch = 3
            ),
            Pagina(
                listOf(dto("v-3110", updatedAt = CURSOR_PAGINA_2)),
                hasMore = false,
                epoch = 3
            )
        )
        val mgr = newManager(api)
        mgr.sincronizar()
        mgr.sincronizar()

        assertEquals(listOf(CURSOR_VIEJO, null, CURSOR_PAGINA_1), api.cursores)
        assertEquals(3, dao.estado(ZONA)!!.EPOCH)
    }

    @Test
    fun epochQueCambiaAMediaPaginacionNoSePersiste() = runTest {
        val api = FakeApi(
            Pagina(
                listOf(dto("v-3111", updatedAt = CURSOR_PAGINA_1)),
                hasMore = true,
                epoch = 5
            ),
            Pagina(
                listOf(dto("v-3112", updatedAt = CURSOR_PAGINA_2)),
                hasMore = false,
                epoch = 6
            )
        )
        newManager(api).sincronizar()

        assertNull("generación inestable: no se aplica ninguna", dao.estado(ZONA)!!.EPOCH)
        assertEquals(listOf("v-3111", "v-3112"), dao.deZona(ZONA).map { it.ID })
    }

    // ─── desde ─────────────────────────────────────────────────────────────

    /**
     * El mismo `desde` en TODAS las páginas, también en la que reinicia por
     * generación. Se calcula con el "hoy" del negocio: a las 23:30 de la Ciudad
     * de México ya es mañana en UTC (Review Focus 2), y el servidor usa hoy − V
     * (V = 92) a las 00:00 de America/Mexico_City.
     */
    @Test
    fun syncEnviaDesdeEnTodasLasPaginas() = runTest {
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-0", epoch = 1)
        val api = FakeApi(
            Pagina(emptyList(), hasMore = false, epoch = 2),
            Pagina(listOf(dto("v-501", updatedAt = CURSOR_PAGINA_1)), hasMore = true, epoch = 2),
            Pagina(listOf(dto("v-502", updatedAt = CURSOR_PAGINA_2)), hasMore = false, epoch = 2)
        )
        newManager(api, ahora = AHORA_2330_LOCAL).sincronizar()

        assertEquals(List(3) { DESDE_ESPERADO }, api.desdes)
    }

    // ─── I8: lo bajado nunca toca Visit ────────────────────────────────────

    /**
     * Replay por generación y cambio de zona con dos visitas propias sin
     * enviar, una de ellas con el MISMO id que una fila que baja. Ninguno de
     * los tres caminos (página, replay, cambio de zona) toca `Visit`.
     */
    @Test
    fun resyncConVisitasPendientesNoTocaVisit() = runTest {
        val gemela = visitaPendiente("v-propia", ZONA)
        val otra = visitaPendiente("p-otra", ZONA)
        visitDao.insertVisit(gemela)
        visitDao.insertVisit(otra)
        sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-0", epoch = 1)

        newManager(
            FakeApi(
                Pagina(emptyList(), hasMore = false, epoch = 2),
                Pagina(
                    listOf(dto("v-propia"), dto("v-x")),
                    hasMore = false,
                    epoch = 2
                )
            )
        ).sincronizar()
        assertEquals(listOf("v-propia", "v-x"), dao.deZona(ZONA).map { it.ID })

        newManager(FakeApi(Pagina(emptyList(), hasMore = false)), zona = OTRA_ZONA)
            .sincronizar()

        assertEquals(emptyList<String>(), dao.deZona(ZONA).map { it.ID })
        assertEquals("I8: la gemela no se pisa", gemela, visitDao.findVisitById("v-propia"))
        assertEquals("I8: ni la otra", otra, visitDao.findVisitById("p-otra"))
        assertNull("ninguna fila bajada aparece en Visit", visitDao.findVisitById("v-x"))
    }

    // ─── Kill-switch ───────────────────────────────────────────────────────

    /** Apagado (por omisión): ni una llamada de red, ni una escritura local. */
    @Test
    fun killSwitchApagadoNoBaja() = runTest {
        val api = FakeApi(Pagina(listOf(dto("v-1")), hasMore = false))

        val r = newManager(api, bajada = false).sincronizar()

        assertEquals(ResultadoSync.Apagado, r)
        assertEquals(0, api.llamadas)
        assertNull(dao.estado(ZONA))
        assertEquals(emptyList<String>(), dao.deZona(ZONA).map { it.ID })
    }

    // ─── Gson ──────────────────────────────────────────────────────────────

    /**
     * Por el convertidor real (Gson sin builder, como `RetrofitClientFactory`):
     * una fila con todo lo opcional en `null`, una sin `id` y una respuesta sin
     * `sync_epoch` ni `has_more`. La fila completa entra, la que no tiene id se
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
                      {"id":"v-nulos","cliente_id":11486,"cobrador_id":null,"cobrador":null,
                       "forma_cobro_id":null,"lat":null,"lng":null,"tipo_visita":"PROMESA",
                       "zona_cliente_id":null,"fecha":"2026-10-15T18:00:00Z",
                       "promesa_venta_id":null,"promesa_fecha":null,"promesa_monto_centavos":null,
                       "cita_fecha":null,"cita_hora":null,"updated_at":"$CURSOR_PAGINA_1"},
                      {"id":null,"cliente_id":11487,"tipo_visita":"PAGO",
                       "fecha":"2026-10-15T18:00:00Z","updated_at":"$CURSOR_PAGINA_1"}
                     ],
                     "cursor":"$CURSOR_PAGINA_1","after_id":"v-nulos"}
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
            assertEquals("v-nulos", fila.ID)
            assertNull(fila.COBRADOR_ID)
            assertNull(fila.PROMESA_MONTO_CENTAVOS)
            assertNull(fila.CITA_HORA)
            assertEquals(
                VisitasSyncStateEntity(ZONA, CURSOR_PAGINA_1, "v-nulos", null),
                dao.estado(ZONA)
            )
        } finally {
            server.shutdown()
        }
    }

    // ─── Extras: E-VIS-070 (cursor) y E-VIS-066 (nota) ─────────────────────

    /**
     * E-VIS-070, condición para T16: el cursor NUNCA se reconstruye desde lo
     * que hay en `visita_zona`. Hay filas bajadas pero ninguna posición
     * guardada: la primera llamada va sin cursor ni `after_id`, y lo que se
     * persiste es lo que dijo el servidor (`cursor`/`after_id` de la
     * respuesta), no el máximo `UPDATED_AT` ni el último id de las filas.
     */
    @Test
    fun cursorNuncaSeReconstruyeDesdeVisitaZona_EAPP102() = runTest {
        dao.aplicarPagina(
            ZONA,
            listOf(entidad("v-local-1", updatedAt = CURSOR_PAGINA_2), entidad("v-local-2")),
            null,
            null
        )

        val api = FakeApi(
            Pagina(
                listOf(dto("v-9", updatedAt = CURSOR_PAGINA_1)),
                hasMore = false,
                cursor = "2026-10-01T10:00:00.999999Z",
                afterId = "v-del-servidor"
            )
        )
        newManager(api).sincronizar()

        assertEquals(listOf<String?>(null), api.cursores)
        assertEquals(listOf<String?>(null), api.afterIds)
        assertEquals(
            VisitasSyncStateEntity(ZONA, "2026-10-01T10:00:00.999999Z", "v-del-servidor", null),
            dao.estado(ZONA)
        )
    }

    /**
     * E-VIS-070 / I6: la página y su cursor van en la MISMA transacción. Un
     * trigger de prueba aborta la escritura del cursor de la segunda página
     * ("matar el proceso" justo ahí): si las filas se escribieran aparte, la
     * página 2 quedaría en `visita_zona` con el cursor de la página 1, y el
     * siguiente arranque la volvería a pedir encima de lo ya escrito.
     */
    @Test
    fun paginaYCursorVanEnLaMismaTransaccion_EAPP102() = runTest {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER muerte_simulada BEFORE UPDATE ON visitas_sync_state " +
                "WHEN NEW.CURSOR = '$CURSOR_PAGINA_2' " +
                "BEGIN SELECT RAISE(ABORT, 'muerte simulada'); END"
        )
        val api = FakeApi(
            Pagina(listOf(dto("v-p1", updatedAt = CURSOR_PAGINA_1)), hasMore = true),
            Pagina(listOf(dto("v-p2", updatedAt = CURSOR_PAGINA_2)), hasMore = false)
        )

        assertTrue(newManager(api).sincronizar() is ResultadoSync.Error)

        assertEquals(listOf("v-p1"), dao.deZona(ZONA).map { it.ID })
        assertEquals(VisitasSyncStateEntity(ZONA, CURSOR_PAGINA_1, "v-p1", null), dao.estado(ZONA))
    }

    /** E-VIS-066: la nota no viaja; si un servidor la mandara, no se guarda. */
    @Test
    fun laNotaNoSeGuardaAunqueLlegue_EAPP103() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"items":[{"id":"v-nota","cliente_id":11486,"tipo_visita":"PROMESA",
                      "zona_cliente_id":$ZONA,"fecha":"2026-10-15T18:00:00Z",
                      "nota":"vive con su hermana, tel 2221234567",
                      "updated_at":"$CURSOR_PAGINA_1"}],
                     "cursor":"$CURSOR_PAGINA_1","after_id":"v-nota","has_more":false,"sync_epoch":3}
                    """.trimIndent()
                )
            )
            server.start()

            newManager(apiReal(server)).sincronizar()

            val fila = dao.deZona(ZONA).single()
            assertEquals("v-nota", fila.ID)
            assertNull("la nota no se guarda", fila.NOTA)
            assertEquals(3, dao.estado(ZONA)!!.EPOCH)
        } finally {
            server.shutdown()
        }
    }

    /** Las rutas y parámetros coinciden con `routes.go`/`dto_sync.go` del servidor. */
    @Test
    fun laPeticionUsaLaRutaYLosParametrosDelServidor_EAPP101() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[],"cursor":"$CURSOR_VIEJO","after_id":null,"has_more":false}"""
                )
            )
            server.start()
            sembrarEstado(cursor = CURSOR_VIEJO, afterId = "v-0", epoch = null)

            newManager(apiReal(server)).sincronizar()

            val url = server.takeRequest().requestUrl!!
            assertEquals("/v2/visitas/sync/zona/$ZONA", url.encodedPath)
            assertEquals(DESDE_ESPERADO, url.queryParameter("desde"))
            assertEquals(CURSOR_VIEJO, url.queryParameter("cursor"))
            assertEquals("v-0", url.queryParameter("after_id"))
            assertEquals("${VisitasZonaSyncApi.LIMITE_PAGINA}", url.queryParameter("limit"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun sinZonaNoBaja() = runTest {
        val api = FakeApi(Pagina(listOf(dto("v-1")), hasMore = false))

        assertEquals(ResultadoSync.SinZona, newManager(api, zona = null).sincronizar())
        assertEquals(0, api.llamadas)
    }

    // ─── utilidades ─────────────────────────────────────────────────────────

    private fun apiReal(server: MockWebServer): VisitasZonaSyncApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(VisitasZonaSyncApi::class.java)

    private suspend fun sembrarEstado(cursor: String?, afterId: String?, epoch: Int?) {
        dao.aplicarPagina(ZONA, emptyList(), cursor, afterId)
        if (epoch != null) dao.guardarEpoch(ZONA, epoch)
    }

    private data class Pagina(
        val items: List<VisitaZonaDto>,
        val hasMore: Boolean,
        val epoch: Int? = null,
        val falla: Boolean = false,
        /** Si se da, sustituye al cursor que el servidor calcularía. */
        val cursor: String? = null,
        val afterId: String? = null
    )

    /**
     * Imita al servidor: con filas, `cursor` = `updated_at` de la última y
     * `after_id` = su id; sin filas, el cursor RECIBIDO y `after_id = null`.
     */
    private class FakeApi(vararg paginas: Pagina) : VisitasZonaSyncApi {
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
        ): SyncVisitasZonaResponse {
            llamadas++
            cursores += cursor
            afterIds += afterId
            desdes += desde
            val p = cola.removeFirstOrNull() ?: throw IOException("sin más páginas")
            if (p.falla) throw IOException("red caída")
            val ultima = p.items.lastOrNull()
            return SyncVisitasZonaResponse(
                items = p.items,
                cursor = p.cursor ?: ultima?.updated_at ?: cursor,
                after_id = p.afterId ?: ultima?.id,
                has_more = p.hasMore,
                sync_epoch = p.epoch
            )
        }

        override suspend fun digest(zona: Int, desde: String): DigestVisitasZonaResponse =
            error("no se usa en T16")

        override suspend fun ids(
            zona: Int,
            desde: String,
            after: String?,
            limit: Int?
        ): IdsVisitasZonaResponse = error("no se usa en T16")

        override suspend fun byIds(
            zona: Int,
            desde: String,
            ids: String
        ): ByIdsVisitasZonaResponse = error("no se usa en T16")
    }

    private fun dto(id: String, updatedAt: String = UPDATED_AT_RELLENO, zona: Int = ZONA) =
        VisitaZonaDto(
            id = id,
            cliente_id = 11486,
            cobrador_id = 2991009,
            cobrador = "Mendoza Torres, Ana",
            forma_cobro_id = 157,
            lat = 19.4326077,
            lng = -99.133208,
            tipo_visita = "PROMESA",
            zona_cliente_id = zona,
            impte_docto_cc_id = 512345,
            fecha = "2026-10-15T18:00:00Z",
            promesa_venta_id = 512345,
            promesa_fecha = "2026-10-20",
            promesa_monto_centavos = 20000L,
            cita_fecha = null,
            cita_hora = null,
            updated_at = updatedAt
        )

    private fun entidad(id: String, updatedAt: String = CURSOR_VIEJO) = VisitaZonaEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 2991009,
        COBRADOR = "Mendoza Torres, Ana",
        FECHA = "2026-09-15T18:00:00Z",
        TIPO_VISITA = "PAGO",
        ZONA_CLIENTE_ID = ZONA,
        IMPTE_DOCTO_CC_ID = 512345,
        NOTA = null,
        PROMESA_VENTA_ID = null,
        PROMESA_FECHA = null,
        PROMESA_MONTO_CENTAVOS = null,
        CITA_FECHA = null,
        CITA_HORA = null,
        UPDATED_AT = updatedAt
    )

    private fun visitaPendiente(id: String, zona: Int) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Mendoza Torres, Ana",
        COBRADOR_ID = 2991009,
        FECHA = "2026-10-15T18:00:00Z",
        FORMA_COBRO_ID = 157,
        LAT = 19.4326077,
        LNG = -99.133208,
        NOTA = "Lo capturado en el teléfono",
        TIPO_VISITA = "PROMESA",
        ZONA_CLIENTE_ID = zona,
        IMPTE_DOCTO_CC_ID = 512345,
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_VENTA_ID = 512345,
        PROMESA_FECHA = "2026-10-20",
        PROMESA_MONTO_CENTAVOS = 20000L,
        CITA_FECHA = "2026-10-20",
        CITA_HORA = "17:00"
    )
}
