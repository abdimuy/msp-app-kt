package com.example.msp_app.workers

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.Visit
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/*
 * Fixtures compartidas por las pruebas del worker de visitas
 * (PendingVisitsWorkerV2Test y MedicionesEnvioVisitasTest). Movidas tal cual
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

// ─── worker runner ───────────────────────────────────────────────────────────

@Suppress("LongParameterList") // seams de test; el DAO de fotos es el septimo.
internal fun runPendingVisitsWorker(
    context: Context,
    imagenes: VisitImageDao,
    clock: AppClock,
    visitId: String? = "visita-001",
    api: V2VisitsApi = happyApi(),
    legacyApi: VisitsApi = throwingLegacyApi(),
    useV2: Boolean = true,
    maxAttempts: Int = 10,
    runAttemptCount: Int = 0
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
