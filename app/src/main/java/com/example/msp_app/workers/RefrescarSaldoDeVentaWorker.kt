package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.sync.cobranza.RefrescoDelSaldoDeLaVenta
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Trabajo único por venta que deja su `SALDO_REST` en el del servidor
 * ([RefrescoDelSaldoDeLaVenta]). Lo encola `PendingPaymentsWorker` en dos
 * momentos: cuando el servidor rechaza una condonación por saldo, y cuando
 * anota el documento de un pago aplicado (abono o condonación) — lo que corrige
 * el saldo que un `mergeVentas` a media página pudo dejar inflado o desinflado.
 *
 * Va aparte de la subida a propósito: el pago ya se soltó, así que reintentar
 * esto **no vuelve a mandar el pago**. Sin señal o ante un fallo transitorio, `retry()` —con tope— con el
 * backoff y la restricción de red del encolado; un 4xx permanente o el tope,
 * `success()` sin tocar el saldo, para no matar la cadena (ver [siguientePaso])
 * ([com.example.msp_app.workmanager.enqueueRefrescoDeSaldo]); mientras tanto el
 * saldo local se queda donde lo dejó la captura, nunca por encima.
 */
class RefrescarSaldoDeVentaWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val refresco: RefrescoDelSaldoDeLaVenta = RefrescoDelSaldoDeLaVenta(
        api = V2ApiProvider.create(V2CobranzaApi::class.java),
        db = AppDatabase.getInstance(appContext)
    )
) : CoroutineWorker(appContext, workerParams) {

    @Suppress("TooGenericExceptionCaught") // red o Room: cualquiera se reintenta
    override suspend fun doWork(): Result {
        val zona = inputData.getInt(KEY_ZONA, SIN_VALOR)
        val cargo = inputData.getInt(KEY_CARGO, SIN_VALOR)
        if (zona == SIN_VALOR || cargo == SIN_VALOR) return Result.failure()
        return try {
            val resultado = refresco.refrescar(zona = zona, cargo = cargo)
            Log.i(TAG, "Saldo de la venta $cargo: $resultado")
            Result.success()
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Exception) {
            siguientePaso(cargo, fallo)
        }
    }

    /**
     * Qué hacer ante un fallo del refresco.
     *
     * - **Transitorio → `retry()`**: red, 5xx, el **404** —que puede venir de un
     *   túnel o proxy que nunca llegó al API, igual que en la subida
     *   (`UploadDecision.kt`, `PendingPaymentsWorkerV2Test.
     *   v2_404_del_tunel_se_reintenta_y_nunca_suelta`)— y los 4xx que el repo ya
     *   trata como señal de reintento (401, 408, 409, 425, 429).
     * - **Permanente, o transitorio que llegó al tope de [MAX_INTENTOS] →
     *   `success()` sin tocar el saldo**, con el error en el log. NO
     *   `failure()`: `enqueueRefrescoDeSaldo` encadena con `APPEND_OR_REPLACE`, y
     *   un eslabón FALLIDO deja fallidos a todos los que vienen detrás sin
     *   correrlos (medido: saldo 100 contra 300, fósil). Rendirse no infla nada
     *   —el saldo se queda donde estaba— y así el refresco de un pago posterior
     *   siempre puede correr.
     */
    private fun siguientePaso(cargo: Int, fallo: Exception): Result {
        val codigo = (fallo as? HttpException)?.code()
        val permanente = codigo != null && codigo in HTTP_4XX && codigo !in HTTP_4XX_DE_REINTENTO
        return when {
            permanente -> Result.success().also {
                Log.e(TAG, "Saldo de la venta $cargo: HTTP $codigo permanente; se deja como estaba")
            }
            runAttemptCount >= MAX_INTENTOS -> Result.success().also {
                Log.e(TAG, "Saldo de la venta $cargo: tope de intentos; se deja como estaba", fallo)
            }
            else -> Result.retry().also {
                Log.w(TAG, "Saldo de la venta $cargo: no se pudo refrescar; reintentando", fallo)
            }
        }
    }

    companion object {
        const val KEY_ZONA = "zona_cliente_id"
        const val KEY_CARGO = "cargo"

        /**
         * Tope de intentos. Con el backoff exponencial de 30 s del encolado, diez
         * intentos cubren varias horas sin señal.
         */
        const val MAX_INTENTOS = 10
        private val HTTP_4XX = 400..499
        private val HTTP_4XX_DE_REINTENTO = setOf(401, 404, 408, 409, 425, 429)
        private const val SIN_VALOR = -1
        private const val TAG = "RefrescarSaldoDeVenta"
    }
}
