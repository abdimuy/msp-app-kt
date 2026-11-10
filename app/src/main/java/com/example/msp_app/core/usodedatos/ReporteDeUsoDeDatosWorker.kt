package com.example.msp_app.core.usodedatos

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.msp_app.core.datos.EmisorDeReporte
import com.example.msp_app.core.datos.ReporteDeUsoDeDatos
import com.example.msp_app.core.datos.UsoDeDatos
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.utils.Constants
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Reporte diario del uso de datos a la oficina (E-APP-174). Una vez al día, con red,
 * manda el resumen del día ANTERIOR por `RemoteLogger` (`app_logs`, módulo
 * [MODULO], acción [ACCION]). Idempotente: ver `ReporteDeUsoDeDatos`.
 */
class ReporteDeUsoDeDatosWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result = try {
        val servicio = UsoDeDatos.servicio(applicationContext)
        servicio.guardarAhora() // muestrea ya: el total del día queda al corriente
        ReporteDeUsoDeDatos(
            servicio,
            emisorPorRemoteLogger(applicationContext),
            Constants.APP_VERSION
        )
            .enviarPendientes()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Medición: si falla, mañana se reintenta; no vale la pena un backoff agresivo.
        Result.success()
    }

    companion object {
        const val MODULO = "USO_DATOS"
        const val ACCION = "REPORTE_DIARIO"
        const val NOMBRE_UNICO = "reporte_uso_de_datos"

        /** `KEEP`: una sola programación aunque se llame en cada arranque. */
        fun encolar(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReporteDeUsoDeDatosWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NOMBRE_UNICO, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/**
 * `RemoteLogger.log` encola en Firestore (que guarda sin red y sube después), así que
 * llamarlo cuenta como entregado. El sobre ya trae userId/userEmail/appVersion; aquí
 * solo viaja la lista blanca de `ConstructorDeReporte`.
 */
internal fun emisorPorRemoteLogger(context: Context): EmisorDeReporte = EmisorDeReporte { datos ->
    RemoteLogger.getInstance(context).info(
        module = ReporteDeUsoDeDatosWorker.MODULO,
        action = ReporteDeUsoDeDatosWorker.ACCION,
        message = "Uso de datos ${datos["fecha"]}",
        data = datos
    )
    true
}
