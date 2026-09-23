package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.BuildConfig
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.api.services.visits.toCrearVisitaBody
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.toDomain
import com.example.msp_app.data.visitas.PartesDeComprobantesDeVisita
import com.example.msp_app.data.visitas.partesDeComprobantesDeVisita
import com.example.msp_app.features.visit.upload.domain.VisitUploadClassifier
import com.example.msp_app.features.visit.upload.domain.VisitUploadDecision
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Uploads one durably-stored pending visita to the backend and, on confirmation
 * that the server holds it, flips `GUARDADO_EN_MICROSIP` so the retry cohort
 * stops re-enqueuing it.
 *
 * The v2 path targets msp-api's `POST /v2/visitas` (idempotent by `id`). Its
 * robustness rule: a visita is marked "done" ONLY when the server is known to
 * hold it — a 2xx, or a 4xx which the cobranza failed-intent capture
 * middleware guarantees is persisted server-side for desk correction. A
 * network failure is never marked done, so a device that alone holds a
 * visita keeps retrying instead of dropping it. Idempotency by `id` makes a
 * resend safe (a duplicate resolves to the same stored visita, 2xx).
 *
 * ## The comprobantes ride in the SAME request (Task 23)
 *
 * When the visita carries photos the upload switches to the multipart shape of
 * `POST /v2/visitas` and the photos travel inside the very same request as the
 * visita — atomic server-side, no staging bucket, no orphan blobs. `SUBIDA_EN`
 * is stamped only on the images that actually travelled, and only after the
 * server accepted them. A failure to READ them is never treated as "there are
 * none": it retries without uploading, so the visita's id is not burned without
 * its evidence.
 *
 * The legacy path is preserved unchanged for prod until a prod Go host exists;
 * [useV2] (from `BuildConfig.VISITAS_USE_V2`) selects between them. There is no
 * dual send — two backends would risk a double insert.
 *
 * The injected seams (defaulted to production) let unit tests drive the worker
 * without WorkManager, Firebase, or a real network.
 */
class PendingVisitsWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val visitsStore: VisitsLocalDataSource = VisitsLocalDataSource(appContext),
    @VisibleForTesting
    internal val v2Api: V2VisitsApi = V2ApiProvider.create(V2VisitsApi::class.java),
    @VisibleForTesting
    internal val legacyApi: VisitsApi = ApiProvider.create(VisitsApi::class.java),
    @VisibleForTesting
    internal val useV2: Boolean = BuildConfig.VISITAS_USE_V2,
    @VisibleForTesting
    internal val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    @VisibleForTesting
    internal val imagenes: VisitImageDao =
        AppDatabase.getInstance(appContext).visitImageDao(),
    @VisibleForTesting
    internal val clock: AppClock = AppClock.System
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("visit_id")
            ?: return Result.failure().also {
                Log.e(TAG, "No se proporcionó visit_id")
            }

        val visit = visitsStore.getVisitById(id)
            ?: return Result.failure().also {
                Log.e(TAG, "Visita no encontrada: $id")
            }

        return if (useV2) uploadV2(visit) else uploadLegacy(visit)
    }

    /**
     * v2 upload. Classifies the outcome so we only mark GUARDADO_EN_MICROSIP
     * when the server is known to hold the visita. See [VisitUploadClassifier].
     */
    private suspend fun uploadV2(visit: VisitEntity): Result {
        return try {
            // Un fallo de LECTURA no se traga: ver `comprobantesDe`.
            val comprobantes = comprobantesDe(visit)
                ?: return Result.retry().also {
                    Log.w(
                        TAG,
                        "Visita ${visit.ID}: no se pudieron leer los comprobantes; " +
                            "NO se sube la visita para no quemar su id sin la evidencia"
                    )
                }
            val response = enviar(visit, comprobantes)
            markUploaded(comprobantes)
            markDone(visit.ID)
            Log.i(TAG, "Visita aplicada en v2: ${visit.ID} (server=${response.id})")
            Result.success()
        } catch (e: HttpException) {
            when (VisitUploadClassifier.classifyHttpCode(e.code())) {
                VisitUploadDecision.DONE -> {
                    // 4xx captured server-side → el desk la corrige; el teléfono terminó.
                    markDone(visit.ID)
                    Log.w(
                        TAG,
                        "Visita ${visit.ID} rechazada (${e.code()}); capturada server-side, marcada lista"
                    )
                    Result.success()
                }

                VisitUploadDecision.RETRY -> {
                    Log.w(TAG, "Visita ${visit.ID}: HTTP ${e.code()} transitorio, reintentando")
                    Result.retry()
                }

                VisitUploadDecision.RETRY_THEN_DONE -> {
                    // runAttemptCount is 0 on the first run; +1 counts this attempt.
                    if (runAttemptCount + 1 >= maxAttempts) {
                        markDone(visit.ID)
                        Log.w(
                            TAG,
                            "Visita ${visit.ID}: 5xx tras ${runAttemptCount + 1} intentos; " +
                                "ya capturada server-side, marcada lista"
                        )
                        Result.success()
                    } else {
                        Log.w(TAG, "Visita ${visit.ID}: HTTP ${e.code()} server, reintentando")
                        Result.retry()
                    }
                }

                VisitUploadDecision.RETRY_THEN_FAIL -> {
                    // Pure-retry code (401/408/425/429) with NO server-side
                    // custody guarantee — unlike RETRY_THEN_DONE, the cap
                    // here must NEVER mark done. runAttemptCount is 0 on the
                    // first run; +1 counts this attempt.
                    if (runAttemptCount + 1 >= maxAttempts) {
                        Log.w(
                            TAG,
                            "Visita ${visit.ID}: HTTP ${e.code()} tras ${runAttemptCount + 1} " +
                                "intentos sin custodia confirmada; se detiene este job de " +
                                "WorkManager (no se marca lista, sigue pendiente para " +
                                "VisitsPendingSynchronizer)"
                        )
                        Result.failure()
                    } else {
                        Log.w(TAG, "Visita ${visit.ID}: HTTP ${e.code()} transitorio, reintentando")
                        Result.retry()
                    }
                }
            }
        } catch (e: IOException) {
            // El server no la vio: jamás marcar lista. El teléfono la conserva.
            Log.w(TAG, "Visita ${visit.ID}: error de red, reintentando", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Visita ${visit.ID}: error inesperado, reintentando", e)
            Result.retry()
        }
    }

    /**
     * Manda la visita en el formato que le toca.
     *
     * **JSON cuando no lleva fotos, multipart cuando sí.** Las dos formas llegan
     * al mismo handler, la misma validación y la misma garantía de idempotencia
     * (`CrearVisita`, `handlers.go`), así que la elección no cambia el resultado
     * — cambia el radio de daño. El JSON es el camino que ya corre en producción
     * y el que los teléfonos viejos de la flota siguen usando; mandar multipart
     * también para la visita sin fotos cambiaría el formato de cable del 100% de
     * las visitas a cambio de nada. Con esto, una visita sin foto viaja
     * exactamente como viajaba.
     *
     * Ojo con la frontera: `comprobantes.partes` vacío puede significar "no hay
     * fotos" o "las había y todas se omitieron" (archivo ausente, tipo no
     * permitido). Las dos merecen el mismo trato: la visita sube igual, porque
     * la foto no la bloquea, y las filas omitidas se quedan pendientes en vez de
     * estamparse.
     */
    private suspend fun enviar(visit: VisitEntity, comprobantes: PartesDeComprobantesDeVisita) =
        if (comprobantes.partes.isEmpty()) {
            v2Api.crearVisita(idempotencyKey = visit.ID, body = visit.toCrearVisitaBody())
        } else {
            val json = Gson().toJson(visit.toCrearVisitaBody())
            v2Api.crearVisitaConImagenes(
                idempotencyKey = visit.ID,
                datos = json.toRequestBody("application/json".toMediaTypeOrNull()),
                imagenes = comprobantes.partes
            )
        }

    /**
     * Los comprobantes pendientes de [visit], listos para viajar — o `null` si
     * **la lectura falló**, que es una cosa distinta de "no hay ninguno".
     *
     * ## Por qué un fallo de lectura NO sube la visita
     *
     * Tentador: subirla sin fotos y no bloquear el trabajo de campo. Es una
     * trampa, y es la que la Task 22 tuvo que arreglar del lado del dinero. Si
     * la visita sube sin evidencia, **quema su `id`**: el reintento cae en el
     * replay idempotente del servidor —que devuelve la visita existente y no
     * vuelve a mirar las fotos que ya tenía— y, peor, el camino de éxito estampa
     * `SUBIDA_EN` y **borra el archivo local**. Un error de lectura pasajero
     * acabaría en un comprobante que nunca llegó al servidor y ya no existe en
     * el teléfono.
     *
     * La visita no se pierde: se reintenta con su misma clave, exactamente como
     * ante un `IOException`. Lo que se protege es que la visita y su evidencia
     * viajen **juntas o en otro intento**, nunca a medias.
     *
     * Ojo con la distinción, que es la mitad del arreglo: una imagen **omitida**
     * (sin archivo, o de tipo no permitido) NO es un fallo de lectura. Ahí sí se
     * sube la visita —la foto no la bloquea— y la fila se queda pendiente en vez
     * de estamparse. Confundir las dos convierte una condición benigna en un
     * bloqueo permanente.
     */
    private suspend fun comprobantesDe(visit: VisitEntity): PartesDeComprobantesDeVisita? = try {
        val pendientes = imagenes.getPendientesDe(visit.ID)
        partesDeComprobantesDeVisita(pendientes).also {
            if (it.omitidas > 0) {
                Log.w(
                    TAG,
                    "Visita ${visit.ID}: ${it.omitidas} comprobante(s) omitido(s) " +
                        "(archivo ausente o tipo no permitido)"
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Visita ${visit.ID}: no se pudieron leer los comprobantes", e)
        null
    }

    /**
     * Estampa `SUBIDA_EN` **solo en las que viajaron**, y borra su archivo.
     *
     * Marcar por visita —o marcar lo que había en la base al empezar— estamparía
     * también las que se omitieron por no tener archivo, y esas tienen que
     * quedarse pendientes para que se vean. El archivo local se va porque ya
     * cumplió: el servidor tiene la foto, y en un teléfono de gama baja cada
     * comprobante retenido son cientos de KB que no vuelven.
     *
     * Best-effort de punta a punta: una entrega que ya tuvo éxito no se puede
     * tumbar por no poder escribir un timestamp.
     */
    private suspend fun markUploaded(comprobantes: PartesDeComprobantesDeVisita) {
        if (comprobantes.enviadas.isEmpty()) return
        val subidaEn = AppTime.toWireFormat(clock.now())
        comprobantes.enviadas.forEach { imagen ->
            try {
                imagenes.marcarSubida(imagen.ID, subidaEn)
                File(imagen.URI).delete()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cerrar el comprobante ${imagen.ID}", e)
            }
        }
    }

    /** Legacy upload (prod). Unchanged behaviour: any error retries blindly. */
    private suspend fun uploadLegacy(visit: VisitEntity): Result {
        return try {
            Log.d(TAG, "Enviando visita (legacy): ${visit.ID}")
            legacyApi.saveVisit(visit.toDomain())
            markDone(visit.ID)
            Log.d(TAG, "Visita marcada como enviada (legacy): ${visit.ID}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar visita ${visit.ID} (legacy)", e)
            Result.retry()
        }
    }

    private suspend fun markDone(visitId: String) {
        visitsStore.changeVisitStatus(visitId, true)
    }

    companion object {
        private const val TAG = "PendingVisitsWorker"

        /** Max attempts before a server-side 5xx is treated as captured/done. */
        const val DEFAULT_MAX_ATTEMPTS = 10
    }
}
