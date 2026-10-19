package com.example.msp_app.workers

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.diagnosticoenvio.EnvioDiagnostico
import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.visitas.capacidades.Capacidades
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.CapacidadesDTO
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitaDetalleDTO
import com.example.msp_app.data.api.services.visits.VisitaExistenteDTO
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.Visit
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/*
 * Fixtures compartidas por las pruebas del worker de visitas
 * (PendingVisitsWorkerModoV2ActualTest —antes PendingVisitsWorkerV2Test—,
 * PendingVisitsWorkerV3Test y MedicionesEnvioVisitasTest). Movidas tal cual
 * desde PendingVisitsWorkerV2Test (plan visitas P1, Tarea 2); el único cambio
 * es que el corredor recibe `context`, `imagenes` y `clock` como parámetros
 * en vez de leerlos de la clase de prueba.
 */

// ─── fixtures ──────────────────────────────────────────────────────────────

internal fun pendingVisit(id: String = "visita-001", guardado: Int = 0) = VisitEntity(
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

// ─── fakes ─────────────────────────────────────────────────────────────────

/**
 * Fake de [V2VisitsApi] con las DOS formas de cable.
 *
 * `crearVisitaConImagenes` grita por defecto: una visita sin fotos que
 * llegara por multipart sería un cambio de contrato silencioso para toda la
 * flota, y este fake lo convierte en un rojo en vez de en un 201.
 */
internal fun fakeV2Api(
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

    // El camino de hoy (v2) no consulta existencia; el v3 (Tarea 11) sí, y
    // sus pruebas pasan su propio fake. Aquí, un GET inesperado es un rojo.
    override suspend fun obtenerVisita(id: String): VisitaDetalleDTO =
        throw AssertionError("no se esperaba GET")

    // Contrato de visitas 3 (P2 Tarea 15): el worker de subida no negocia ni usa
    // by-ids con detalle. Si algún día lo hiciera, este fake lo grita.
    override suspend fun capacidades(): CapacidadesDTO =
        throw AssertionError("PendingVisitsWorker no debe consultar capacidades")

    override suspend fun visitasExistentesPorIdsDetalle(
        ids: String,
        detalle: Int
    ): List<VisitaExistenteDTO> =
        throw AssertionError("PendingVisitsWorker no debe llamar by-ids con detalle")
}

internal fun happyApi(): V2VisitsApi = fakeV2Api { _, _ -> VisitaDTO(id = "visita-001") }

internal fun throwingLegacyApi(): VisitsApi = object : VisitsApi {
    override suspend fun saveVisit(visit: Visit) {
        throw AssertionError("legacy saveVisit must not be called in v2 tests")
    }
}

internal fun httpError(code: Int, body: String = "{}"): HttpException = HttpException(
    Response.error<VisitaDTO>(
        code,
        body.toResponseBody("application/json".toMediaTypeOrNull())
    )
)

/** Un error HTTP con una cabecera de respuesta (p. ej. `X-Intent-Captured`). */
internal fun httpErrorConCabecera(
    code: Int,
    nombre: String,
    valor: String,
    body: String = "{}"
): HttpException {
    val crudo = okhttp3.Response.Builder()
        .code(code)
        .message("x")
        .protocol(Protocol.HTTP_1_1)
        .request(Request.Builder().url("http://x/v2/visitas").build())
        .header(nombre, valor)
        .build()
    return HttpException(
        Response.error<VisitaDTO>(
            body.toResponseBody("application/json".toMediaTypeOrNull()),
            crudo
        )
    )
}

/** Respuesta del POST con el id dado (el resto, de [pendingVisit]). */
internal fun visitaDto(id: String?) = VisitaDTO(
    id = id,
    cliente_id = 11486,
    cobrador_id = 200,
    cobrador = "Ramirez Ortiz, Fernando",
    tipo_visita = "SIN_PAGO",
    zona_cliente_id = 21552,
    fecha = "2026-06-01T09:30:00Z"
)

/** Respuesta de `GET /v2/visitas/{id}` con el id y los `imagen_ids` dados. */
internal fun visitaDetalle(id: String?, imagenIds: List<String>?) = VisitaDetalleDTO(
    id = id,
    cliente_id = 11486,
    cobrador_id = 200,
    cobrador = "Ramirez Ortiz, Fernando",
    forma_cobro_id = 0,
    lat = 0.0,
    lng = 0.0,
    nota = "El cliente pidio pasar la proxima semana",
    tipo_visita = "SIN_PAGO",
    zona_cliente_id = 21552,
    impte_docto_cc_id = 5000,
    fecha = "2026-06-01T09:30:00Z",
    created_at = "2026-06-01T09:31:00Z",
    imagen_ids = imagenIds
)

// ─── worker runner ───────────────────────────────────────────────────────────

/**
 * Construye el worker real con sus costuras. [modo] es obligatorio a
 * propósito: el kill-switch arranca en V3, así que una prueba que no lo diga
 * mediría un camino que no eligió (plan visitas P1, Tarea 11).
 */
@Suppress("LongParameterList") // seams de test
internal fun construirPendingVisitsWorker(
    context: Context,
    imagenes: VisitImageDao,
    clock: AppClock,
    modo: ModoEnvioVisitas,
    visitId: String? = "visita-001",
    api: V2VisitsApi = happyApi(),
    legacyApi: VisitsApi = throwingLegacyApi(),
    useV2: Boolean = true,
    maxAttempts: Int = 10,
    runAttemptCount: Int = 0,
    visitDao: VisitDao? = null,
    diagnostico: EnvioDiagnosticoSink? = null,
    capacidades: Capacidades = Capacidades.CONTRATO_2
): PendingVisitsWorker {
    val inputBuilder = Data.Builder()
    if (visitId != null) inputBuilder.putString("visit_id", visitId)

    return TestListenableWorkerBuilder<PendingVisitsWorker>(
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
                clock = clock,
                modo = { modo },
                visitDao = visitDao ?: AppDatabase.getInstance(appContext).visitDao(),
                diagnostico = diagnostico ?: EnvioDiagnostico.sink,
                capacidades = { capacidades }
            )
        })
        .build() as PendingVisitsWorker
}

/** Corre [construirPendingVisitsWorker] hasta el final. */
@Suppress("LongParameterList") // seams de test
internal fun runPendingVisitsWorker(
    context: Context,
    imagenes: VisitImageDao,
    clock: AppClock,
    modo: ModoEnvioVisitas,
    visitId: String? = "visita-001",
    api: V2VisitsApi = happyApi(),
    legacyApi: VisitsApi = throwingLegacyApi(),
    useV2: Boolean = true,
    maxAttempts: Int = 10,
    runAttemptCount: Int = 0,
    visitDao: VisitDao? = null,
    diagnostico: EnvioDiagnosticoSink? = null,
    capacidades: Capacidades = Capacidades.CONTRATO_2
): ListenableWorker.Result {
    val worker = construirPendingVisitsWorker(
        context,
        imagenes,
        clock,
        modo,
        visitId,
        api,
        legacyApi,
        useV2,
        maxAttempts,
        runAttemptCount,
        visitDao,
        diagnostico,
        capacidades
    )
    return runBlocking { worker.doWork() }
}
