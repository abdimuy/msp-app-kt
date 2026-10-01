package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.logging.Logger
import com.example.msp_app.core.sync.cobranza.RefrescoDelSaldoDeLaVenta
import com.example.msp_app.core.sync.cobranza.ResolucionDeCapturasSueltas
import com.example.msp_app.core.sync.cobranza.ResumenDeLaResolucion
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import kotlinx.coroutines.CancellationException

/**
 * **Corre [ResolucionDeCapturasSueltas] en cada arranque con sesión** (E-APP-048).
 *
 * No lleva bandera de "una sola vez", a diferencia de
 * [ReparacionDeCondonacionesWorker]: además de reparar lo que dejó agosto, es la
 * red de seguridad para cualquier camino que vuelva a dejar una captura soltada
 * sin documento (p.ej. `RECONCILED_VIA_GET`, que suelta la fila sin guardar el
 * documento). Sin capturas sueltas, la corrida es una sola consulta a Room.
 *
 * Ante algo transitorio, `retry()` con el backoff del encolado hasta
 * [MAX_INTENTOS]; ahí termina y lo no resuelto queda en el log — nada se
 * escribió sin confirmación del servidor, y el siguiente arranque lo retoma.
 */
class ResolucionDeCapturasSueltasWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val resolucion: ResolucionDeCapturasSueltas = ResolucionDeCapturasSueltas(
        pagosApi = V2ApiProvider.create(V2PaymentsApi::class.java),
        refresco = RefrescoDelSaldoDeLaVenta(
            api = V2ApiProvider.create(V2CobranzaApi::class.java),
            db = AppDatabase.getInstance(appContext)
        ),
        db = AppDatabase.getInstance(appContext)
    ),
    @VisibleForTesting
    internal val telemetria: (ResumenDeLaResolucion) -> Unit = ::reportar
) : CoroutineWorker(appContext, workerParams) {

    @Suppress("TooGenericExceptionCaught") // Room o red: cualquiera se reintenta
    override suspend fun doWork(): Result = try {
        val resumen = resolucion.resolver()
        if (resumen.revisadas > 0) runCatching { telemetria(resumen) }
        when {
            resumen.completa -> Result.success()
            runAttemptCount >= MAX_INTENTOS -> {
                Log.e(TAG, "Tope de intentos; sin resolver: ${resumen.pendientes}")
                Result.success()
            }
            else -> Result.retry()
        }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Exception) {
        Log.w(TAG, "Resolución de capturas sueltas: fallo; reintentando", fallo)
        Result.retry()
    }

    companion object {
        const val NOMBRE_UNICO = "resolucion_capturas_sueltas"
        const val MAX_INTENTOS = 10
        private const val TAG = "CapturasSueltas"
        private const val MODULO = "COBRANZA"

        /**
         * Lo que oficina tiene que ver: cuántas se reconocieron y, con nivel de
         * error, las que el servidor no tiene (el cobrador trae ese dinero) y las
         * que se quedaron sin evidencia.
         */
        private fun reportar(resumen: ResumenDeLaResolucion) {
            val logger = Logger.get()
            logger.info(
                module = MODULO,
                action = "RESOLUCION_CAPTURAS_SUELTAS",
                message = "Capturas sueltas: ${resumen.reconocidas} reconocidas de ${resumen.revisadas}",
                data = mapOf(
                    "revisadas" to resumen.revisadas,
                    "reconocidas" to resumen.reconocidas,
                    "no_aplicadas" to resumen.noAplicadas,
                    "sin_evidencia" to resumen.sinEvidencia,
                    "pendientes" to resumen.pendientes.size
                )
            )
            if (resumen.detalleNoAplicadas.isNotEmpty()) {
                logger.error(
                    module = MODULO,
                    action = "CAPTURA_NO_APLICADA_EN_SERVIDOR",
                    message = "El servidor no tiene ${resumen.noAplicadas} captura(s) que el teléfono dio por subidas",
                    data = mapOf("capturas" to resumen.detalleNoAplicadas)
                )
            }
            if (resumen.detalleSinEvidencia.isNotEmpty()) {
                logger.warning(
                    module = MODULO,
                    action = "CAPTURA_SUELTA_SIN_EVIDENCIA",
                    message = "${resumen.sinEvidencia} captura(s) siguen restando sin prueba del servidor",
                    data = mapOf("capturas" to resumen.detalleSinEvidencia)
                )
            }
        }
    }
}
