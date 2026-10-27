package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.BuildConfig
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.UbicacionTardiaBody
import com.example.msp_app.data.api.services.visits.V2UbicacionTardiaApi
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * E-APP-129: lleva al servidor la ubicación que el teléfono obtuvo DESPUÉS de
 * subir la visita en 0,0 (E-APP-127). Lo manda el servidor del cobrador a la
 * oficina: el mapa de desempeño usa esa ubicación.
 *
 * ## Reglas (decisión del coordinador 2026-10-27)
 * - **Si el teléfono no tiene ubicación** (`Visit.LAT/LNG` siguen en 0,0) **no se
 *   manda nada**.
 * - Nunca duplica ni reabre la visita: es un `PATCH` por UUID que solo cambia
 *   LAT/LNG en el servidor (E-VIS-114). Este trabajo no toca `Visit` en Room ni
 *   la marca de entrega, ni `sales`.
 * - Idempotente: la fila de Room se lee en cada intento; repetirlo no cambia
 *   nada. El 0,0 nunca se manda.
 *
 * ## Qué responde cada código
 * - 200: listo. 404: la visita aún no llega al servidor (el envío va en camino
 *   y sale con la ubicación de Room): se reintenta unas veces y se suelta.
 * - 422 y demás 4xx: el servidor la rechazó; reintentar no cambia nada, se suelta.
 * - Red, 5xx, 408/429, 401/403: se reintenta (WorkManager, con red).
 */
class UbicacionTardiaDeVisitaWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val api: V2UbicacionTardiaApi =
        V2ApiProvider.create(V2UbicacionTardiaApi::class.java),
    @VisibleForTesting
    internal val visitDao: VisitDao = AppDatabase.getInstance(appContext).visitDao(),
    @VisibleForTesting
    internal val useV2: Boolean = BuildConfig.VISITAS_USE_V2,
    @VisibleForTesting
    internal val maxAttempts: Int = MAX_INTENTOS
) : CoroutineWorker(appContext, workerParams) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val id = inputData.getString(CLAVE_VISIT_ID) ?: return Result.failure()
        if (!useV2) return Result.success()
        val visita = visitDao.findVisitById(id) ?: return Result.success()
        // Sin ubicación en el teléfono no se manda nada.
        if (visita.LAT == 0.0 && visita.LNG == 0.0) return Result.success()
        return try {
            api.aplicarUbicacion(id, UbicacionTardiaBody(visita.LAT, visita.LNG))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            tras(e.code())
        } catch (e: IOException) {
            Log.w(TAG, "REINTENTA red: ubicacion de la visita $id", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "REINTENTA error_inesperado: ubicacion de la visita $id", e)
            Result.retry()
        }
    }

    private fun tras(codigo: Int): Result = when {
        codigo == HTTP_NO_ENCONTRADA ->
            // El envío de la visita todavía no llegó. No es un fallo: ese envío
            // sale con la ubicación de Room. Se reintenta unas veces por si el
            // PATCH se adelantó, y se suelta.
            if (runAttemptCount < maxAttempts) Result.retry() else Result.success()
        codigo in REINTENTABLES || codigo >= HTTP_ERROR_SERVIDOR -> Result.retry()
        else -> Result.success()
    }

    companion object {
        private const val TAG = "UbicacionTardiaVisita"
        const val CLAVE_VISIT_ID = "visit_id"
        const val MAX_INTENTOS = 8
        private const val HTTP_NO_ENCONTRADA = 404
        private const val HTTP_ERROR_SERVIDOR = 500
        private val REINTENTABLES = setOf(401, 403, 408, 429)
    }
}
