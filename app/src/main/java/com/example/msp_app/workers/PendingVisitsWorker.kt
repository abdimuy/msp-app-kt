package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.BuildConfig
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.api.services.visits.toCrearVisitaBody
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.local.entities.VisitEntity
import com.example.msp_app.data.models.visit.toDomain
import com.example.msp_app.features.visit.upload.domain.VisitUploadClassifier
import com.example.msp_app.features.visit.upload.domain.VisitUploadDecision
import java.io.IOException
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
    internal val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
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
            val response = v2Api.crearVisita(
                idempotencyKey = visit.ID,
                body = visit.toCrearVisitaBody()
            )
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
