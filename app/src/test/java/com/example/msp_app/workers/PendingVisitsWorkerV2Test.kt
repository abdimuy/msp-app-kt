package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.Visit
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response

/**
 * Unit tests for the v2 upload path of [PendingVisitsWorker].
 *
 * Room is backed by the in-memory DB from [RoomTestBase]; the network is a
 * fake [V2VisitsApi]. The worker is built directly (no WorkManager, no
 * Firebase — the worker logs via android.util.Log only). Mirrors
 * [PendingPaymentsWorkerV2Test].
 *
 * The invariant under test is the robustness rule: the visita is marked
 * GUARDADO_EN_MICROSIP=1 ONLY when the server is known to hold it (2xx, or a
 * captured 4xx), never on a network failure.
 */
class PendingVisitsWorkerV2Test : RoomTestBase() {

    /** El instante que el reloj falso da a `SUBIDA_EN`. */
    private val subidaEn: Instant = Instant.parse("2026-09-04T18:00:00Z")

    private lateinit var context: Context
    private lateinit var visitsStore: VisitsLocalDataSource

    @get:Rule
    val carpeta: TemporaryFolder = TemporaryFolder()

    @Before
    fun setUpWorker() {
        context = ApplicationProvider.getApplicationContext()
        visitsStore = VisitsLocalDataSource(context)
    }

    // ─── fixtures ──────────────────────────────────────────────────────────────

    private fun pendingVisit(id: String = "visita-001", guardado: Int = 0) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Ramirez Ortiz, Fernando",
        COBRADOR_ID = 200,
        FECHA = "2026-06-01T09:30:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        NOTA = "El cliente pidio pasar la proxima semana",
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21552,
        IMPTE_DOCTO_CC_ID = 5000,
        GUARDADO_EN_MICROSIP = guardado
    )

    private suspend fun seed(visit: VisitEntity) = visitsStore.saveVisit(visit)

    /**
     * Siembra una fila de comprobante **con su archivo de verdad en disco**: el
     * armador del multipart hace `File(URI).exists()`, así que una fila sin
     * archivo no probaría el camino feliz sino el de omisión.
     */
    private suspend fun sembrarImagen(
        id: String,
        orden: Int = 0,
        visitaId: String = "visita-001",
        mime: String = "image/jpeg",
        descripcion: String? = null,
        subidaEn: String? = null
    ): File {
        val archivo = File(carpeta.root, "$id.jpg")
        archivo.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
        db.visitImageDao().insertAll(
            listOf(
                VisitImageEntity(
                    ID = id,
                    VISITA_ID = visitaId,
                    URI = archivo.absolutePath,
                    MIME = mime,
                    DESCRIPCION = descripcion,
                    ORDEN = orden,
                    CREADA_EN = "2026-09-04T17:00:00Z",
                    SUBIDA_EN = subidaEn
                )
            )
        )
        return archivo
    }

    /** Una fila cuyo archivo NO existe: la que el armador tiene que omitir. */
    private suspend fun sembrarImagenSinArchivo(id: String, orden: Int) {
        db.visitImageDao().insertAll(
            listOf(
                VisitImageEntity(
                    ID = id,
                    VISITA_ID = "visita-001",
                    URI = File(carpeta.root, "$id-borrado.jpg").absolutePath,
                    MIME = "image/jpeg",
                    ORDEN = orden,
                    CREADA_EN = "2026-09-04T17:00:00Z"
                )
            )
        )
    }

    private fun MultipartBody.Part.nombre(): String? = headers?.get("Content-Disposition")
        ?.substringAfter("name=\"", "")
        ?.substringBefore('"')

    private fun MultipartBody.Part.texto(): String = Buffer().also { body.writeTo(it) }.readUtf8()

    private fun RequestBody.texto(): String = Buffer().also { writeTo(it) }.readUtf8()

    private suspend fun guardadoFlag(id: String): Int =
        visitsStore.getVisitById(id).GUARDADO_EN_MICROSIP

    // ─── fakes ─────────────────────────────────────────────────────────────────

    /**
     * Fake de [V2VisitsApi] con las DOS formas de cable.
     *
     * `crearVisitaConImagenes` grita por defecto: una visita sin fotos que
     * llegara por multipart sería un cambio de contrato silencioso para toda la
     * flota, y este fake lo convierte en un rojo en vez de en un 201.
     */
    private fun fakeV2Api(
        multipart: suspend (
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ) -> VisitaDTO = { _, _, _ ->
            throw AssertionError("una visita sin fotos debe viajar como JSON, no como multipart")
        },
        crear: suspend (idempotencyKey: String, body: CrearVisitaBody) -> VisitaDTO
    ): V2VisitsApi = object : V2VisitsApi {
        override suspend fun crearVisita(idempotencyKey: String, body: CrearVisitaBody): VisitaDTO =
            crear(idempotencyKey, body)

        override suspend fun crearVisitaConImagenes(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): VisitaDTO = multipart(idempotencyKey, datos, imagenes)

        // El worker de subida nunca consulta by-ids — eso es del reconciliador
        // (Task 10). Si algun cambio futuro lo hiciera, este fake lo grita en
        // vez de responder algo plausible y esconder el acoplamiento nuevo.
        override suspend fun visitasExistentesPorIds(ids: String): List<String> =
            throw AssertionError("PendingVisitsWorker no debe llamar by-ids")
    }

    private fun happyApi(): V2VisitsApi = fakeV2Api { _, _ -> VisitaDTO(id = "visita-001") }

    private fun throwingLegacyApi(): VisitsApi = object : VisitsApi {
        override suspend fun saveVisit(visit: Visit) {
            throw AssertionError("legacy saveVisit must not be called in v2 tests")
        }
    }

    private fun httpError(code: Int, body: String = "{}"): HttpException = HttpException(
        Response.error<VisitaDTO>(
            code,
            body.toResponseBody("application/json".toMediaTypeOrNull())
        )
    )

    // ─── worker runner ───────────────────────────────────────────────────────────

    @Suppress("LongParameterList") // seams de test; el DAO de fotos es el septimo.
    private fun buildAndRunWorker(
        visitId: String? = "visita-001",
        api: V2VisitsApi = happyApi(),
        legacyApi: VisitsApi = throwingLegacyApi(),
        useV2: Boolean = true,
        maxAttempts: Int = 10,
        runAttemptCount: Int = 0,
        imagenes: VisitImageDao = db.visitImageDao(),
        clock: AppClock = FakeClock(subidaEn)
    ): ListenableWorker.Result {
        val inputBuilder = Data.Builder()
        if (visitId != null) inputBuilder.putString("visit_id", visitId)

        val worker = TestListenableWorkerBuilder<PendingVisitsWorker>(
            context,
            inputBuilder.build()
        )
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = PendingVisitsWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    visitsStore = VisitsLocalDataSource(appContext),
                    v2Api = api,
                    legacyApi = legacyApi,
                    useV2 = useV2,
                    maxAttempts = maxAttempts,
                    imagenes = imagenes,
                    clock = clock
                )
            })
            .build()

        var result: ListenableWorker.Result = ListenableWorker.Result.failure()
        runBlocking { result = (worker as PendingVisitsWorker).doWork() }
        return result
    }

    // ─── tests ────────────────────────────────────────────────────────────────

    @Test
    fun v2_happy_path_marks_guardado() = runTest {
        seed(pendingVisit())

        var callCount = 0
        var capturedKey: String? = null
        val api = fakeV2Api { key, _ ->
            callCount++
            capturedKey = key
            VisitaDTO(id = "visita-001")
        }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            "GUARDADO_EN_MICROSIP must flip to 1 after 2xx",
            1,
            guardadoFlag("visita-001")
        )
        assertEquals("crearVisita must be called exactly once", 1, callCount)
        assertEquals("Idempotency-Key must equal the visita ID", "visita-001", capturedKey)
    }

    @Test
    fun v2_duplicate_200_is_idempotent_and_marks_done() = runTest {
        seed(pendingVisit())

        var callCount = 0
        val api = fakeV2Api { _, _ ->
            callCount++
            VisitaDTO(id = "visita-001")
        }

        // Two independent worker runs (e.g. a forced retry). The server dedupes
        // by body.id; both must succeed with no double-adverse effect.
        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))

        assertEquals(1, guardadoFlag("visita-001"))
        assertEquals("worker resends on each run; server is the dedupe authority", 2, callCount)
    }

    @Test
    fun v2_422_validation_marks_done_because_captured_server_side() = runTest {
        seed(pendingVisit())

        val api = fakeV2Api { _, _ ->
            throw httpError(
                422,
                """{"code":"visita_cliente_no_encontrado","detail":"el cliente no existe"}"""
            )
        }

        val result = buildAndRunWorker(api = api)

        assertEquals(
            "A 422 is captured as a failed-intent server-side; the phone is done",
            ListenableWorker.Result.success(),
            result
        )
        assertEquals(
            "GUARDADO must flip to 1 — resolution lives desk-side",
            1,
            guardadoFlag("visita-001")
        )
    }

    @Test
    fun v2_403_marks_done() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(403) }

        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertEquals(1, guardadoFlag("visita-001"))
    }

    @Test
    fun v2_401_returns_retry_and_keeps_pending() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(401) }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals("a 401 blip must not mark the visita done", 0, guardadoFlag("visita-001"))
    }

    @Test
    fun v2_409_marks_done_at_first_attempt_because_idempotent_lookup_confirms_it() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(409) }

        // runAttemptCount defaults to 0 — this is attempt 1, nowhere near any
        // cap. A 409 (ErrVisitaYaExiste) must be treated as success on the
        // very first try because registrar_visita.go resolves the collision
        // by ID and returns the SAME visita already stored server-side.
        val result = buildAndRunWorker(api = api)

        assertEquals(
            "409 is idempotent success (server already holds this visita by id), not a retry",
            ListenableWorker.Result.success(),
            result
        )
        assertEquals(1, guardadoFlag("visita-001"))
    }

    /**
     * Robustez suprema: the pure-RETRY set with NO server-side custody
     * guarantee (401 token blip, 408/425/429 gateway/rate-limiter backoff)
     * must stop retrying at the exact attempt cap — not "eventually", not
     * one attempt early, not one attempt late — and must NEVER mark
     * GUARDADO_EN_MICROSIP, unlike the RETRY_THEN_DONE 5xx cap: there is no
     * proof of custody, so a capped visita must stay pending for
     * VisitsPendingSynchronizer to pick up again on the next session.
     */
    @Test
    fun v2_pure_retry_codes_stop_asking_for_retry_exactly_at_the_cap() = runTest {
        val maxAttempts = 3

        listOf(401, 408, 425, 429).forEach { code ->
            val visitId = "visita-$code"
            seed(pendingVisit(id = visitId))
            val api = fakeV2Api { _, _ -> throw httpError(code) }

            // Below the cap (attempt 1 of 3, runAttemptCount=0): keep retrying.
            val belowCap = buildAndRunWorker(
                visitId = visitId,
                api = api,
                maxAttempts = maxAttempts,
                runAttemptCount = 0
            )
            assertEquals(
                "HTTP $code below the cap must keep retrying",
                ListenableWorker.Result.retry(),
                belowCap
            )
            assertEquals(
                "HTTP $code below the cap must not mark the visita done",
                0,
                guardadoFlag(visitId)
            )

            // Exactly at the cap (attempt 3 of 3, runAttemptCount=2): stop
            // retrying, but do NOT mark done — no custody guarantee.
            val atCap = buildAndRunWorker(
                visitId = visitId,
                api = api,
                maxAttempts = maxAttempts,
                runAttemptCount = maxAttempts - 1
            )
            assertEquals(
                "HTTP $code at the exact cap must stop asking for a retry",
                ListenableWorker.Result.failure(),
                atCap
            )
            assertEquals(
                "HTTP $code at the cap must stay pending, not be marked done",
                0,
                guardadoFlag(visitId)
            )

            // One attempt past the cap (runAttemptCount=maxAttempts): still
            // capped, still pending — never reopens into retry.
            val pastCap = buildAndRunWorker(
                visitId = visitId,
                api = api,
                maxAttempts = maxAttempts,
                runAttemptCount = maxAttempts
            )
            assertEquals(
                "HTTP $code past the cap must remain capped, not resume retrying",
                ListenableWorker.Result.failure(),
                pastCap
            )
            assertEquals(
                "HTTP $code past the cap must still stay pending, not be marked done",
                0,
                guardadoFlag(visitId)
            )
        }
    }

    @Test
    fun v2_5xx_below_cap_returns_retry() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(500) }

        val result = buildAndRunWorker(api = api, maxAttempts = 3, runAttemptCount = 0)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(
            "5xx below the cap must keep retrying, not mark done",
            0,
            guardadoFlag("visita-001")
        )
    }

    @Test
    fun v2_5xx_at_cap_marks_done() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw httpError(503) }

        // maxAttempts=3, runAttemptCount=2 → this is the 3rd (final) attempt.
        val result = buildAndRunWorker(api = api, maxAttempts = 3, runAttemptCount = 2)

        assertEquals(
            "at the attempt cap a server 5xx (captured server-side) is marked done",
            ListenableWorker.Result.success(),
            result
        )
        assertEquals(1, guardadoFlag("visita-001"))
    }

    @Test
    fun v2_network_error_returns_retry_and_never_marks_done() = runTest {
        seed(pendingVisit())
        val api = fakeV2Api { _, _ -> throw IOException("connection reset") }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(
            "a network failure must NEVER mark the visita done",
            0,
            guardadoFlag("visita-001")
        )
    }

    @Test
    fun missing_visit_id_returns_failure() = runTest {
        val result = buildAndRunWorker(visitId = null)
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun visit_not_found_returns_failure() = runTest {
        // Nothing seeded.
        val result = buildAndRunWorker(visitId = "does-not-exist")
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun legacy_path_used_when_useV2_false() = runTest {
        seed(pendingVisit())

        var legacyCalled = false
        val legacy = object : VisitsApi {
            override suspend fun saveVisit(visit: Visit) {
                legacyCalled = true
            }
        }
        // v2 api throws so the test fails loudly if the legacy gate is wrong.
        val v2 =
            fakeV2Api { _, _ -> throw AssertionError("v2 must not be called when useV2=false") }

        val result = buildAndRunWorker(api = v2, legacyApi = legacy, useV2 = false)

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("legacy saveVisit must be called when useV2=false", legacyCalled)
        assertEquals(1, guardadoFlag("visita-001"))
    }

    @Test
    fun v2_success_response_id_is_optional() = runTest {
        seed(pendingVisit())
        // Server returns an empty body → Gson leaves id null; worker must not care.
        val api = fakeV2Api { _, _ -> VisitaDTO() }

        assertEquals(ListenableWorker.Result.success(), buildAndRunWorker(api = api))
        assertEquals(1, guardadoFlag("visita-001"))
    }

    // ─── los comprobantes (Task 23) ──────────────────────────────────────────

    /**
     * **La compatibilidad de flota, medida del lado del cliente.** Una visita
     * sin fotos sigue viajando como JSON, exactamente como antes de esta tarea.
     *
     * El fake de `crearVisitaConImagenes` lanza por defecto, así que si el
     * worker mandara multipart "porque da igual", esta prueba se pone roja en
     * vez de pasar en verde con un cambio de contrato para todos los teléfonos.
     */
    @Test
    fun v2_una_visita_sin_fotos_viaja_como_json() = runTest {
        seed(pendingVisit())

        var llamadasJson = 0
        val api = fakeV2Api { _, _ ->
            llamadasJson++
            VisitaDTO(id = "visita-001")
        }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("una visita sin fotos viaja como JSON", 1, llamadasJson)
    }

    /**
     * Con fotos, el request es multipart y lleva las partes que el servidor
     * espera: `imagen` + su `id_<n>` **posicional**.
     */
    @Test
    fun v2_envia_los_comprobantes_pendientes() = runTest {
        seed(pendingVisit())
        sembrarImagen("IMG-1", orden = 0)

        var partes: List<MultipartBody.Part> = emptyList()
        var clave: String? = null
        var datos: String? = null
        val api = fakeV2Api(
            multipart = { key, cuerpo, imagenes ->
                clave = key
                datos = cuerpo.texto()
                partes = imagenes
                VisitaDTO(id = "visita-001")
            }
        ) { _, _ -> throw AssertionError("con fotos la visita debe viajar como multipart") }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("imagen", "id_0"), partes.map { it.nombre() })
        assertEquals("IMG-1", partes[1].texto())
        assertEquals("la Idempotency-Key es el id de la VISITA", "visita-001", clave)
        assertTrue(
            "el campo datos lleva el mismo documento JSON de siempre",
            datos.orEmpty().contains("\"id\":\"visita-001\"")
        )
    }

    /**
     * **El test que el plan pide aparte: reintentar la misma visita con foto no
     * crea una segunda.**
     *
     * Del lado del servidor eso lo garantiza el UUID de la visita más el
     * pre-flight de `RegistrarVisitaConImagenes`, que solo guarda las imágenes
     * cuyo id todavía no tiene. Lo que el TELÉFONO tiene que aportar es la mitad
     * que el servidor no puede inventar: **el mismo `id_<n>` en los dos
     * intentos**. Si el teléfono acuñara uno nuevo, la clave de storage sería
     * otra y el servidor guardaría la foto por segunda vez.
     */
    @Test
    fun v2_el_reintento_manda_el_mismo_id_de_imagen() = runTest {
        seed(pendingVisit())
        sembrarImagen("IMG-1", orden = 0)

        val idsPorIntento = mutableListOf<List<String>>()
        var intentos = 0
        val api = fakeV2Api(
            multipart = { _, _, imagenes ->
                intentos++
                idsPorIntento += imagenes.filter { it.nombre()?.startsWith("id_") == true }
                    .map { it.texto() }
                if (intentos == 1) throw IOException("sin senal") else VisitaDTO(id = "visita-001")
            }
        ) { _, _ -> throw AssertionError("con fotos la visita debe viajar como multipart") }

        val primero = buildAndRunWorker(api = api)
        val segundo = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.retry(), primero)
        assertEquals(ListenableWorker.Result.success(), segundo)
        assertEquals(
            "los dos intentos mandan el MISMO id de imagen",
            listOf(listOf("IMG-1"), listOf("IMG-1")),
            idsPorIntento
        )
        assertEquals("una sola visita, ya marcada", 1, guardadoFlag("visita-001"))
    }

    /** Una imagen ya subida no se reenvía: `SUBIDA_EN` es lo que la excluye. */
    @Test
    fun v2_no_reenvia_una_imagen_ya_subida() = runTest {
        seed(pendingVisit())
        sembrarImagen("IMG-YA", orden = 0, subidaEn = "2026-09-03T10:00:00Z")
        sembrarImagen("IMG-NUEVA", orden = 1)

        var ids: List<String> = emptyList()
        val api = fakeV2Api(
            multipart = { _, _, imagenes ->
                ids = imagenes.filter { it.nombre()?.startsWith("id_") == true }.map { it.texto() }
                VisitaDTO(id = "visita-001")
            }
        ) { _, _ -> throw AssertionError("con fotos la visita debe viajar como multipart") }

        buildAndRunWorker(api = api)

        assertEquals(listOf("IMG-NUEVA"), ids)
    }

    /**
     * `SUBIDA_EN` marca **solo lo que viajó**. La que se omitió por no tener
     * archivo se queda pendiente, para que se vea.
     */
    @Test
    fun v2_marca_subida_solo_lo_que_viajo() = runTest {
        seed(pendingVisit())
        val archivo = sembrarImagen("IMG-VIAJA", orden = 0)
        sembrarImagenSinArchivo("IMG-NO-VIAJA", orden = 1)

        buildAndRunWorker(
            api = fakeV2Api(
                multipart = { _, _, _ -> VisitaDTO(id = "visita-001") }
            ) { _, _ -> throw AssertionError("con fotos la visita debe viajar como multipart") }
        )

        val filas = db.visitImageDao().getByVisitaId("visita-001").associateBy { it.ID }
        assertEquals(
            "la que viajo queda estampada",
            "2026-09-04T18:00:00Z",
            filas.getValue("IMG-VIAJA").SUBIDA_EN
        )
        assertNull("la que no viajo sigue pendiente", filas.getValue("IMG-NO-VIAJA").SUBIDA_EN)
        assertTrue("el archivo entregado se borra del telefono", !archivo.exists())
    }

    /**
     * El `n` de `id_<n>` cuenta **partes agregadas**, no filas miradas. Con el
     * índice de la fila, la segunda imagen que sí viaja saldría como `id_2`
     * siendo la segunda parte `imagen` — y el servidor parea por posición.
     */
    @Test
    fun v2_una_imagen_sin_archivo_no_corre_los_ids_de_las_demas() = runTest {
        seed(pendingVisit())
        sembrarImagen("IMG-1", orden = 0)
        sembrarImagenSinArchivo("IMG-FANTASMA", orden = 1)
        sembrarImagen("IMG-3", orden = 2)

        var nombres: List<String?> = emptyList()
        val api = fakeV2Api(
            multipart = { _, _, imagenes ->
                nombres = imagenes.map { it.nombre() }
                VisitaDTO(id = "visita-001")
            }
        ) { _, _ -> throw AssertionError("con fotos la visita debe viajar como multipart") }

        buildAndRunWorker(api = api)

        assertEquals(listOf("imagen", "id_0", "imagen", "id_1"), nombres)
    }

    /**
     * Una imagen **omitida** no es un fallo de lectura: la visita sube igual —la
     * foto no la bloquea— y la fila se queda pendiente en vez de estamparse.
     *
     * Es la mitad de la frontera que la Task 22 tuvo que reponer: sin ella, el
     * arreglo del fallo de lectura convierte una condición benigna en un bloqueo
     * permanente.
     */
    @Test
    fun v2_una_imagen_omitida_no_impide_subir() = runTest {
        seed(pendingVisit())
        sembrarImagenSinArchivo("IMG-FANTASMA", orden = 0)

        var llamadasJson = 0
        val api = fakeV2Api { _, _ ->
            llamadasJson++
            VisitaDTO(id = "visita-001")
        }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("sin partes que mandar, vuelve al JSON de siempre", 1, llamadasJson)
        assertEquals("la visita se marca lista", 1, guardadoFlag("visita-001"))
        assertNull(
            "la fila omitida NO se estampa",
            db.visitImageDao().getByVisitaId("visita-001").single().SUBIDA_EN
        )
    }

    /**
     * **Un fallo de LECTURA no sube la visita.** Es el segundo defecto que la
     * Task 22 dejó en el camino, y entra por la misma puerta acá.
     *
     * Si la visita subiera sin evidencia, quemaría su `id`: el reintento cae en
     * el replay del servidor —que devuelve la visita existente sin volver a
     * mirar fotos— y el camino de éxito estampa `SUBIDA_EN` y **borra el archivo
     * local**. Un error pasajero acabaría en un comprobante que nunca llegó al
     * servidor y ya no existe en el teléfono.
     */
    @Test
    fun v2_si_la_lectura_de_comprobantes_falla_no_se_sube_la_visita() = runTest {
        seed(pendingVisit())
        val archivo = sembrarImagen("IMG-1", orden = 0)

        var llamadas = 0
        val api = fakeV2Api(
            multipart = { _, _, _ ->
                llamadas++
                VisitaDTO(id = "visita-001")
            }
        ) { _, _ ->
            llamadas++
            VisitaDTO(id = "visita-001")
        }

        val result = buildAndRunWorker(api = api, imagenes = DaoDeImagenesQueRevienta(db))

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals("no se llama al servidor: el id no se quema", 0, llamadas)
        assertEquals("la visita sigue pendiente", 0, guardadoFlag("visita-001"))
        assertTrue("el archivo local sigue ahi", archivo.exists())
    }

    /**
     * **Control positivo del anterior.** El mismo montaje con el DAO real SÍ
     * sube, así que el `retry` de arriba no puede ser un worker que dejó de
     * funcionar por otra razón.
     */
    @Test
    fun v2_control_positivo_con_el_dao_real_si_sube() = runTest {
        seed(pendingVisit())
        sembrarImagen("IMG-1", orden = 0)

        var llamadas = 0
        val api = fakeV2Api(
            multipart = { _, _, _ ->
                llamadas++
                VisitaDTO(id = "visita-001")
            }
        ) { _, _ -> throw AssertionError("con fotos la visita debe viajar como multipart") }

        val result = buildAndRunWorker(api = api)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, llamadas)
    }

    /**
     * DAO que revienta al LEER los pendientes y funciona para todo lo demás:
     * así el fallo que se prueba es el de lectura y no "la base entera caída".
     */
    private class DaoDeImagenesQueRevienta(private val db: AppDatabase) : VisitImageDao {
        override suspend fun insertAll(imagenes: List<VisitImageEntity>) =
            db.visitImageDao().insertAll(imagenes)

        override suspend fun getByVisitaId(visitaId: String): List<VisitImageEntity> =
            db.visitImageDao().getByVisitaId(visitaId)

        override suspend fun getPendientesDe(visitaId: String): List<VisitImageEntity> =
            throw IllegalStateException("no se pudo leer visita_imagenes")

        override suspend fun marcarSubida(imagenId: String, subidaEn: String) =
            db.visitImageDao().marcarSubida(imagenId, subidaEn)

        override suspend fun rutasVivas(): List<String> = db.visitImageDao().rutasVivas()

        override suspend fun huerfanasAnterioresA(limite: String): List<VisitImageEntity> =
            db.visitImageDao().huerfanasAnterioresA(limite)

        override suspend fun eliminar(imagenId: String) = db.visitImageDao().eliminar(imagenId)
    }
}
