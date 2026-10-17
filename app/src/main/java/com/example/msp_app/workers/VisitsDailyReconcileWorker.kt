package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ConciliacionInversa
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.sync.pendingwork.di.VisitsReconcileEntryPoint
import com.example.msp_app.core.visitas.envio.FirestoreModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.UltimaConciliacionDeVisitas
import com.example.msp_app.workmanager.enqueuePendingVisitsWorker
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import kotlinx.coroutines.CancellationException

/**
 * Reconciliación inversa diaria de visitas (capa 5; plan visitas P1, Tarea 13;
 * spec §4.6-1; E-VIS-009): corre [com.example.msp_app.core.common.sync
 * .pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase] y, si devolvió
 * visitas a pendiente, reencola **todas** las pendientes en orden con `KEEP`
 * (`enqueuePendingVisitsWorker`: nombre único por id, sin amplificación).
 *
 * Mismo patrón que [VisitsReconcileWorker]: constructor `(Context,
 * WorkerParameters)` con costuras `@JvmOverloads`, el use case se resuelve del
 * grafo de Hilt **dentro** de `doWork()` en cada corrida (sin `@Singleton`: el
 * kill-switch de baseURL tiene que alcanzarlo), y siempre [Result.success]:
 * el use case reporta todo lo que falla y lo no decidido se pregunta en la
 * siguiente corrida; pedirle a WorkManager que además reintente solo
 * competiría con ella. Sin tope de reintentos.
 *
 * **Kill-switch** (`config/visitas_envio`, Tarea 10): en `V2_ACTUAL` no hace
 * nada. Ese modo devuelve el envío a la conducta de antes de P1, y antes de P1
 * nada devolvía visitas soltadas a la cola.
 *
 * La marca de [UltimaConciliacionDeVisitas] se anota solo si la corrida decidió
 * todo lo que leyó; si no, la siguiente apertura de la app la reintenta.
 */
class VisitsDailyReconcileWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val modo: () -> ModoEnvioVisitas = { FirestoreModoEnvioVisitas.actual },
    @VisibleForTesting
    internal val conciliar: suspend () -> ConciliacionInversa = {
        EntryPointAccessors.fromApplication(
            appContext.applicationContext,
            VisitsReconcileEntryPoint::class.java
        ).reconcileDeliveredVisitsUseCase().execute()
    },
    @VisibleForTesting
    internal val pendientes: suspend () -> List<String> = {
        AppDatabase.getInstance(appContext).visitDao().pendientesOrdenadas()
    },
    @VisibleForTesting
    internal val encolar: (String) -> Unit = { enqueuePendingVisitsWorker(appContext, it) },
    @VisibleForTesting
    internal val anotarCorrida: (
        Instant
    ) -> Unit = { UltimaConciliacionDeVisitas.anotar(appContext, it) },
    @VisibleForTesting
    internal val clock: AppClock = AppClock.System
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (modo() == ModoEnvioVisitas.V2_ACTUAL) {
            Log.i(TAG, "modo v2_actual: la reconciliacion inversa no corre")
            return Result.success()
        }
        try {
            val r = conciliar()
            Log.i(
                TAG,
                "conciliacion revisadas=${r.revisadas} faltantes=${r.faltantes} " +
                    "confirmadas=${r.confirmadas} tandas_fallidas=${r.tandasFallidas} " +
                    "ilegibles=${r.entregadasIlegibles}"
            )
            if (r.faltantes > 0) reencolarPendientes()
            if (r.tandasFallidas == 0 && !r.entregadasIlegibles) anotarCorrida(clock.now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Defensa: el use case ya reporta y absorbe lo esperable; esto
            // cubre un escape inesperado (p. ej. el grafo de Hilt).
            Log.w(TAG, "reconciliacion inversa de visitas fallo de forma inesperada", e)
        }
        return Result.success()
    }

    private suspend fun reencolarPendientes() {
        try {
            pendientes().forEach(encolar)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // El reconciliador de cada 15 min (Tarea 12) también las reencola.
            Log.w(TAG, "reencolado tras la reconciliacion inversa fallo", e)
        }
    }

    companion object {
        private const val TAG = "VisitsDailyReconcile"
    }
}
