package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.BuildConfig
import com.example.msp_app.core.avisos.encolarReprogramacionDeAvisos
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.diagnosticoenvio.EstadoDeEnvioDeVisitas
import com.example.msp_app.core.sync.pendingwork.data.visits.VisitsReconcileTriggerProvider
import com.example.msp_app.core.sync.tareas.TareasZonaSyncProvider
import com.example.msp_app.core.sync.visitas.VisitasZonaSyncProvider
import com.example.msp_app.core.visitas.capacidades.CapacidadesDeVisitas
import com.example.msp_app.core.visitas.envio.FirestoreModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.workmanager.enqueuePendingVisitsWorker
import kotlinx.coroutines.CancellationException

/**
 * Runs the visitas reconciler's app-open and periodic triggers outside the UI
 * lifecycle — same pattern as [CobranzaReconcileWorker]: plain
 * `(Context, WorkerParameters)` constructor (`HiltWorkerFactory` falls back to
 * it, see `HiltWorkerFactoryFallbackTest`), the real dependency resolved
 * **inside** `doWork()` via a process-wide provider rather than frozen at
 * construction — [VisitsReconcileTriggerProvider.get] re-resolves
 * `ReconcileVisitsUseCase` from the Hilt graph on every actual run, keeping
 * the baseURL kill-switch reachable.
 *
 * [enqueueVisitsReconcileNowWorker] and [enqueueVisitsReconcilePeriodicWorker]
 * (`WorkManagerUtils.kt`) both target THIS worker class and both route through
 * [VisitsReconcileTriggerProvider]'s single process-wide
 * `TriggerVisitsReconciliationUseCase`, so a periodic tick landing while the
 * app-open run is still in flight (or vice versa) is a guard skip, not a
 * second reconciliation — see that class's KDoc for the mechanism and
 * `TriggerVisitsReconciliationUseCaseTest` for the proof.
 *
 * Always [Result.success], never [Result.retry]: [TriggerVisitsReconciliationUseCase
 * .execute] never throws (every internal failure is caught and reported via
 * `SyncErrorReporter`, idempotently — a failed chunk just leaves its ids
 * pending for the next trigger to re-ask about). Asking WorkManager to also
 * retry on top would only race that next trigger for no benefit.
 */
class VisitsReconcileWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val pendientes: suspend () -> List<String> = { pendientesPorOmision(appContext) },
    @VisibleForTesting
    internal val encolar: (String) -> Unit = { enqueuePendingVisitsWorker(appContext, it) },
    @VisibleForTesting
    internal val trigger: suspend () -> Unit = {
        VisitsReconcileTriggerProvider.get(appContext).execute()
    },
    /**
     * Negociación del contrato de visitas (E-APP-097, spec §5.2.1): pregunta
     * `GET /v2/visitas/capacidades` si pasaron 6 h. El servicio se crea por
     * corrida (kill-switch de baseURL: nada de `@Singleton`).
     */
    @VisibleForTesting
    internal val refrescarCapacidades: suspend () -> Unit = {
        CapacidadesDeVisitas.de(appContext).refrescarSiToca {
            V2ApiProvider.create(V2VisitsApi::class.java)
        }
    },
    /**
     * Si el relleno del compromiso corre (spec §5.2.5): el envío v3 en uso y el
     * servidor con `contrato >= 3 && compromisos`. En el modo de reversa
     * (`v2_actual`) el worker reenviaría a ciegas cada visita confirmada.
     */
    @VisibleForTesting
    internal val rellenoActivo: () -> Boolean = {
        BuildConfig.VISITAS_USE_V2 &&
            FirestoreModoEnvioVisitas.actual == ModoEnvioVisitas.V3 &&
            CapacidadesDeVisitas.de(appContext).actual().mandaCompromisos()
    },
    @VisibleForTesting
    internal val compromisosPorRellenar: suspend () -> List<String> = {
        AppDatabase.getInstance(appContext).visitDao().compromisosPorRellenar()
    },
    /**
     * Telemetría `visitas_estado` (plan visitas P1, Tarea 14; H14): solo
     * conteos y antigüedades, lista blanca de `TelemetriaDeVisitas`. Solo
     * reporta: no devuelve nada y no cambia el resultado.
     */
    @VisibleForTesting
    internal val reportarEstado: suspend () -> Unit = {
        EstadoDeEnvioDeVisitas.de(appContext).emitir()
    },
    /**
     * Bajada de la agenda de la zona a `visita_zona` (plan visitas P2, Tarea
     * 16; E-APP-101). Apagada por omisión (`config/visitas_compromisos`); nunca
     * escribe `Visit` (I8). El manager se arma por corrida.
     */
    @VisibleForTesting
    internal val bajarAgenda: suspend () -> Unit = {
        VisitasZonaSyncProvider.sincronizar(appContext)
    },
    /**
     * Bajada de las tareas de oficina de la zona a `tarea_zona` (plan visitas
     * P3, Tarea 11; E-APP-122). Cada 15 min, justo después de la agenda y con
     * el mismo interruptor. El teléfono SIEMPRE hace pull: el push FCM solo
     * despierta (spec §6.4).
     */
    @VisibleForTesting
    internal val bajarTareas: suspend () -> Unit = {
        TareasZonaSyncProvider.sincronizar(appContext)
    },
    /** Tras cada sync de agenda y tareas, reconstruye las alarmas (P3 T13, E-APP-124). */
    @VisibleForTesting
    internal val reprogramarAvisos: () -> Unit = { encolarReprogramacionDeAvisos(appContext) }
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        reencolarPendientes()
        refrescarCapacidadesSinRiesgo()
        encolarRelleno()
        bajarAgendaSinRiesgo()
        bajarTareasSinRiesgo()
        reprogramarAvisosSinRiesgo()
        reconciliar()
        reportarEstadoSinRiesgo()
        return Result.success()
    }

    /** Después de reconciliar, para que los conteos ya digan lo que el servidor confirmó. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun reportarEstadoSinRiesgo() {
        try {
            reportarEstado()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "telemetria de visitas fallo; la corrida sigue", e)
        }
    }

    private suspend fun reconciliar() {
        try {
            trigger()
        } catch (e: Exception) {
            // Defensa: el guard y `ReconcileVisitsUseCase` ya reportan y
            // absorben todo lo esperable (ver KDoc de clase); esto solo cubre
            // un escape genuinamente inesperado. `:app` es legacy y queda
            // fuera de la norma de errores (Ruling I) — Log.w es lo mismo que
            // usa `CobranzaReconcileWorker` al lado.
            Log.w(TAG, "trigger de reconciliacion de visitas fallo de forma inesperada", e)
        }
    }

    /**
     * E-APP-072 (plan visitas P1, Tarea 12; cierra H3, spec §4.2-6): en cada
     * corrida —al abrir la app (`enqueueVisitsReconcileNowWorker`, desde
     * `VisitsReconcileObserver`) y cada 15 min (`enqueueVisitsReconcilePeriodicWorker`)—
     * reencola **todas** las pendientes, sin tope, en orden `FECHA ASC, ID ASC`.
     * `enqueuePendingVisitsWorker` usa `KEEP` y nombre único por id: una visita
     * que ya tiene su trabajo en cola no se duplica. La red recuperada la cubre
     * la restricción `CONNECTED` de cada trabajo encolado.
     *
     * Va ANTES del [trigger] y aislado: una lectura o un encolado que falle no
     * impide reconciliar; lo reintenta el siguiente periodo.
     */
    private suspend fun reencolarPendientes() {
        try {
            pendientes().forEach(encolar)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(
                TAG,
                "reencolado de visitas pendientes falló; lo reintenta el siguiente periodo",
                e
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun refrescarCapacidadesSinRiesgo() {
        try {
            refrescarCapacidades()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refresco de capacidades de visitas falló; se conserva la vigente", e)
        }
    }

    /**
     * Relleno del compromiso (plan visitas P2, Tarea 15; spec §5.2.5): con la
     * capacidad recién refrescada, encola por id —mismo nombre único y `KEEP`
     * que el reencolado— cada visita con promesa o cita sin prueba ni
     * resguardo. El worker decide qué viaja. Aislado como el reencolado.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun encolarRelleno() {
        try {
            if (rellenoActivo()) compromisosPorRellenar().forEach(encolar)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "relleno de compromisos falló; lo reintenta el siguiente periodo", e)
        }
    }

    /**
     * Después del reencolado: lo propio pendiente ya va camino al servidor
     * antes de bajar la agenda. Aislado: una bajada que falle no impide
     * reconciliar; la siguiente corrida retoma desde la última página aplicada.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun bajarAgendaSinRiesgo() {
        try {
            bajarAgenda()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "bajada de la agenda de visitas falló; la corrida sigue", e)
        }
    }

    /**
     * Después de la agenda y aislada de ella: una bajada de tareas que falle no
     * impide reconciliar ni la bajada de la agenda de la siguiente corrida.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun bajarTareasSinRiesgo() {
        try {
            bajarTareas()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "bajada de tareas de la zona falló; la corrida sigue", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reprogramarAvisosSinRiesgo() {
        try {
            reprogramarAvisos()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "reprogramacion de alarmas fallo; la corrida sigue", e)
        }
    }

    companion object {
        private const val TAG = "VisitsReconcileWorker"

        /**
         * La lectura por omisión de [pendientes]: todas, `FECHA ASC, ID ASC`, sin
         * tope (E-APP-072). Con nombre para que `VisitasParaElBarridoTest` ejerza
         * esta misma lectura y no una copia.
         */
        @VisibleForTesting
        internal suspend fun pendientesPorOmision(context: Context): List<String> =
            AppDatabase.getInstance(context).visitDao().pendientesOrdenadas()
    }
}
