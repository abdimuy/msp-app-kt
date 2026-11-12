package com.example.msp_app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.diadecobro.DiaDeCobroApi
import com.example.msp_app.data.diadecobro.DescargarDiaDeCobro
import com.example.msp_app.data.diadecobro.ResultadoDeDiaDeCobro
import com.example.msp_app.data.diadecobro.almacenDelDiaDeCobro
import com.example.msp_app.data.diadecobro.telemetriaDelDiaDeCobro
import com.example.msp_app.data.diadecobro.zonaDelDiaDeCobro

/**
 * Baja el día de cobro de la zona del cobrador, **una vez al día**, fuera del
 * sync de pagos y saldos (no comparte worker, cursor, mutex ni canal con él).
 *
 * La zona sale de [KEY_ZONA] (la manda la apertura de la app) o, para el
 * periódico, del contexto del cobrador ya vivo o de la última zona guardada. Sin
 * ninguna, no hay nada que bajar y termina bien.
 *
 * El `DiaDeCobroApi` se resuelve **dentro** de `doWork()`: congelarlo al
 * construir el worker rompería el kill-switch de la baseURL.
 */
class DiaDeCobroWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val telemetria = telemetriaDelDiaDeCobro(applicationContext)
        val almacen = almacenDelDiaDeCobro(applicationContext, telemetria)
        val zona = zonaDelDiaDeCobro(
            actual = CobranzaSyncProvider.userContextFlow.value?.zona,
            delTrabajo = inputData.getInt(KEY_ZONA, 0),
            guardada = almacen.leer()?.zona
        ) ?: return Result.success()
        val descargar = DescargarDiaDeCobro(
            V2ApiProvider.create(DiaDeCobroApi::class.java),
            almacen,
            telemetry = telemetria
        )
        return when (descargar(zona)) {
            is ResultadoDeDiaDeCobro.Fallo ->
                // Lo guardado se queda. Tres intentos y se espera al siguiente día.
                if (runAttemptCount < MAXIMO_DE_INTENTOS) Result.retry() else Result.success()

            else -> Result.success()
        }
    }

    companion object {
        const val KEY_ZONA = "zona"
        private const val MAXIMO_DE_INTENTOS = 3
    }
}
