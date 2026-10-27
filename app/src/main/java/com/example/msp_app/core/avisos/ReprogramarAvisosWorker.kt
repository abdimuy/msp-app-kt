package com.example.msp_app.core.avisos

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import com.example.msp_app.core.visitas.agenda.AgendaLocal
import com.example.msp_app.core.visitas.agenda.RoomFuenteDeAgenda
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import kotlin.coroutines.cancellation.CancellationException

/** Encola la reprogramación de alarmas; total: nunca propaga (plan P3, Tarea 13). */
@Suppress("TooGenericExceptionCaught")
fun encolarReprogramacionDeAvisos(context: Context) {
    try {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReprogramarAvisosWorker.NOMBRE_UNICO,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReprogramarAvisosWorker>().build()
        )
    } catch (e: Exception) {
        Log.w("ReprogramarAvisos", "no se pudo encolar la reprogramacion de alarmas", e)
    }
}

/**
 * Recalcula el plan desde la agenda local y lo proyecta a `AlarmManager`
 * (plan visitas P3, Tarea 13; E-APP-124). Siempre `success`: las alarmas son
 * comodidad, y el siguiente disparador (sync, captura, arranque) lo reintenta.
 * Sin zona conocida reconstruye las alarmas de la tabla sin recalcular.
 */
class ReprogramarAvisosWorker @JvmOverloads constructor(
    appContext: Context,
    params: WorkerParameters,
    @VisibleForTesting
    internal val reprogramar: suspend () -> Unit = { reprogramarPorOmision(appContext) }
) : CoroutineWorker(appContext, params) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        try {
            reprogramar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "la reprogramacion de alarmas fallo; la lista sigue mostrando todo", e)
        }
        return Result.success()
    }

    companion object {
        const val NOMBRE_UNICO = "reprogramar_avisos"
        private const val TAG = "ReprogramarAvisos"

        @VisibleForTesting
        internal suspend fun reprogramarPorOmision(context: Context) {
            proyectar(context)
            reportarAvisos(context)
        }

        /** E-APP-134: permisos y conteos a la telemetría; nunca lanza ni cambia lo programado. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun reportarAvisos(context: Context) {
            try {
                val programados = AppDatabase.getInstance(context).avisosDao().programados().size
                TelemetriaDeVisitas.emitir(
                    TelemetriaDeVisitas.EVENTO_AVISOS,
                    TelemetriaDeVisitas.avisos(
                        EstadoDePermisos.leer(context),
                        programados,
                        ContadorDeDisparos.de(context).ultimas24h(AppClock.System.now())
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "la telemetria de avisos no se pudo armar", e)
            }
        }

        private suspend fun proyectar(context: Context) {
            val db = AppDatabase.getInstance(context)
            val reprogramador = ReprogramadorDeAlarmas(
                context = context,
                dao = db.avisosDao(),
                killSwitch = { KillSwitchAvisos.alarmas },
                reportarExactas = { Log.i(TAG, "permiso.alarmas_exactas=$it") }
            )
            val zona = CobranzaSyncProvider.userContextFlow.value?.zona
            if (zona == null) {
                // Sin zona (p. ej. justo tras el reinicio) no se recalcula: se
                // reconstruyen las alarmas de la tabla tal cual.
                val ahora = AppClock.System.now()
                reprogramador.reprogramarTodo(
                    PlanDeAvisos.desdeTabla(db.avisosDao().programados(), ahora)
                )
                return
            }
            val agenda = AgendaLocal(
                RoomFuenteDeAgenda(db.saleDao(), db.visitDao(), db.visitaZonaDao())
            )
            val ahora = AppClock.System.now()
            val compromisos = agenda.derivarAhora(zona, ahora).values
            val tareas = db.tareaZonaDao().deZona(zona)
            val estados = agenda.estadosDeTareas(tareas, AgendaLocal.hoyDe(ahora))
            val plan = PlanDeAvisos.planear(
                compromisos = compromisos,
                tareas = tareas.mapNotNull { t -> estados[t.ID]?.let { t to it } },
                ahora = ahora,
                zona = BUSINESS_ZONE
            )
            reprogramador.reprogramarTodo(plan)
        }
    }
}
