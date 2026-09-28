package com.example.msp_app.workers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.sync.cobranza.RefrescoDelSaldoDeLaVenta
import com.example.msp_app.core.sync.cobranza.ReparacionDeCondonaciones
import com.example.msp_app.core.sync.cobranza.ResultadoDeLaReparacion
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import kotlinx.coroutines.CancellationException

/**
 * **La reparación ÚNICA de las condonaciones fantasma y los saldos negativos**
 * que dejó el defecto de la condonación (E-APP-029/E-APP-030), en cada teléfono
 * de la flota, al actualizar — sin tocar el schema de Room.
 *
 * Corre [ReparacionDeCondonaciones] y sólo cuando terminó COMPLETA marca la
 * bandera [BANDERA] en [PREFS]. Mientras no esté marcada, cada arranque la vuelve
 * a encolar (`enqueueReparacionDeCondonacionesSiFalta`, `KEEP`), y cada corrida
 * es idempotente: lo que una corrida anterior alcanzó a hacer no se repite ni se
 * deshace. Ante algo transitorio, `retry()` con el backoff del encolado, hasta
 * [MAX_INTENTOS]; ahí se marca la bandera y lo no resuelto queda en el log.
 */
class ReparacionDeCondonacionesWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val reparacion: ReparacionDeCondonaciones = ReparacionDeCondonaciones(
        pagosApi = V2ApiProvider.create(V2PaymentsApi::class.java),
        refresco = RefrescoDelSaldoDeLaVenta(
            api = V2ApiProvider.create(V2CobranzaApi::class.java),
            db = AppDatabase.getInstance(appContext)
        ),
        db = AppDatabase.getInstance(appContext)
    ),
    @VisibleForTesting
    internal val prefs: SharedPreferences = preferenciasDeLaReparacion(appContext)
) : CoroutineWorker(appContext, workerParams) {

    @Suppress("TooGenericExceptionCaught") // Room o red: cualquiera se reintenta
    override suspend fun doWork(): Result {
        if (prefs.getBoolean(BANDERA, false)) return Result.success()
        return try {
            when (reparacion.reparar()) {
                ResultadoDeLaReparacion.COMPLETA -> {
                    // `commit()` y no `apply()`: la bandera tiene que estar en
                    // disco antes de declarar el trabajo terminado.
                    prefs.edit().putBoolean(BANDERA, true).commit()
                    Log.i(TAG, "Reparación de condonaciones completa")
                    Result.success()
                }
                ResultadoDeLaReparacion.REINTENTAR -> if (runAttemptCount >= MAX_INTENTOS) {
                    // Tope: se da por terminada. Lo no resuelto se quedó como
                    // estaba —nada se escribió sin confirmación del servidor— y
                    // queda en el log.
                    prefs.edit().putBoolean(BANDERA, true).commit()
                    Log.e(TAG, "Tope de intentos; sin resolver: ${reparacion.ultimosPendientes}")
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Exception) {
            Log.w(TAG, "Reparación de condonaciones: fallo; reintentando", fallo)
            Result.retry()
        }
    }

    companion object {
        const val PREFS = "reparaciones"
        const val BANDERA = "reparacion_condonaciones_v1"
        const val NOMBRE_UNICO = "reparacion_condonaciones_v1"

        /**
         * Tope de intentos del trabajo (como `RefrescarSaldoDeVentaWorker`): un id
         * que falla para siempre no deja la reparación pendiente para siempre.
         */
        const val MAX_INTENTOS = 10
        private const val TAG = "ReparacionCondonaciones"

        fun preferenciasDeLaReparacion(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
