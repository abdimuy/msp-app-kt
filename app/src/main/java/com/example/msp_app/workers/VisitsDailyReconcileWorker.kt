package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.BuildConfig
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ConciliacionDeCompromisos
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ConciliacionInversa
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.sync.pendingwork.di.VisitsReconcileEntryPoint
import com.example.msp_app.core.sync.visitas.ConciliacionDeAgendaProvider
import com.example.msp_app.core.sync.visitas.ResultadoConciliacionDeAgenda
import com.example.msp_app.core.sync.visitas.ResultadoInventario
import com.example.msp_app.core.sync.visitas.VisitasZonaSyncProvider
import com.example.msp_app.core.sync.visitas.conteos
import com.example.msp_app.core.visitas.capacidades.CapacidadesDeVisitas
import com.example.msp_app.core.visitas.envio.FirestoreModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
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
 * **Envío legacy** (`BuildConfig.VISITAS_USE_V2 = false`, flavor devserver):
 * tampoco corre, ni el barrido ni la parte by-ids (E-VIS-031). El envío legacy
 * nunca estampa `SUBIDA_EN`, así que el barrido devolvería la misma legada con
 * foto en cada corrida y al abrir la app, y el worker legacy la reenviaría sin
 * la foto. La marca de corrida no se anota: la corrida al abrir la app se
 * encola y no hace nada.
 *
 * La marca de [UltimaConciliacionDeVisitas] se anota solo si la corrida decidió
 * todo lo que leyó; si no, la siguiente apertura de la app la reintenta.
 */
class VisitsDailyReconcileWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val modo: () -> ModoEnvioVisitas = { FirestoreModoEnvioVisitas.actual },
    /** E-VIS-031: con el envío legacy (`VISITAS_USE_V2 = false`) no corre. */
    @VisibleForTesting
    internal val useV2: Boolean = BuildConfig.VISITAS_USE_V2,
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
    internal val clock: AppClock = AppClock.System,
    /**
     * Telemetría `visitas_conciliacion` (Tarea 14; H14): todos los conteos de
     * la corrida, incluida la alerta §12-1 `visitas.reconcilia.devuelta_a_pendiente`.
     * `TelemetriaDeVisitas.emitir` nunca lanza; igual va aislada.
     */
    @VisibleForTesting
    internal val telemetria: (String, Map<String, Any>) -> Unit = TelemetriaDeVisitas::emitir,
    /**
     * Inventario de la agenda bajada (P2 Tarea 18, E-APP-109). `null` = no
     * corrió: su interruptor es el de la bajada (`config/visitas_compromisos`),
     * no el del envío.
     */
    @VisibleForTesting
    internal val inventariar: suspend () -> ResultadoInventario? = {
        VisitasZonaSyncProvider.inventariar(appContext, clock.now())
    },
    /**
     * Conciliación directa de la agenda (P2 Tarea 18, E-APP-131). `null` = no
     * corrió (interruptor de la bajada apagado o sin zona). No usa la bandeja
     * de dinero: su reintento es la siguiente corrida (E-VIS-116).
     */
    @VisibleForTesting
    internal val conciliarAgenda: suspend () -> ResultadoConciliacionDeAgenda? = {
        ConciliacionDeAgendaProvider.conciliar(appContext, clock.now())
    },
    /**
     * Conciliación inversa del compromiso (P2 Tarea 18, E-APP-132). `null` = no
     * corrió: el servidor no manda compromisos (contrato < 3 o bandera apagada),
     * y entonces ninguna visita puede estar "incompleta" por eso.
     */
    @VisibleForTesting
    internal val conciliarCompromisos: suspend () -> ConciliacionDeCompromisos? = {
        if (CapacidadesDeVisitas.de(appContext).actual().mandaCompromisos()) {
            EntryPointAccessors.fromApplication(
                appContext.applicationContext,
                VisitsReconcileEntryPoint::class.java
            ).reconcileCompromisosEntregadosUseCase().execute()
        } else {
            null
        }
    }
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!useV2) {
            // E-VIS-031: el envío legacy nunca estampa SUBIDA_EN; el barrido
            // devolvería la misma legada con foto en cada corrida, para siempre.
            Log.i(TAG, "envio legacy: la reconciliacion inversa no corre")
            return Result.success()
        }
        var decidida = false
        if (modo() == ModoEnvioVisitas.V2_ACTUAL) {
            Log.i(TAG, "modo v2_actual: la reconciliacion inversa no corre")
        } else {
            decidida = conciliacionInversa()
            decidida = conciliacionDeCompromisos() && decidida
        }
        inventarioDeLaAgenda()
        val agendaFallo = conciliacionDeLaAgenda()
        // Reintento propio (sin bandeja de dinero): si algo no se decidió la marca
        // no se anota y la siguiente apertura de la app lo vuelve a intentar.
        if (decidida && !agendaFallo) anotarCorrida(clock.now())
        return Result.success()
    }

    /**
     * Conciliación directa de la agenda (E-APP-131). Devuelve `true` si FALLÓ
     * (no si omitió a propósito): una falla impide anotar la corrida. Nunca
     * tumba la corrida.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun conciliacionDeLaAgenda(): Boolean {
        val r = try {
            conciliarAgenda() ?: return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "conciliacion de la agenda fallo de forma inesperada", e)
            return true
        }
        Log.i(TAG, "conciliacion de la agenda: $r")
        try {
            telemetria(
                TelemetriaDeVisitas.EVENTO_CONCILIACION_AGENDA,
                TelemetriaDeVisitas.conciliacionAgenda(r.conteos())
            )
        } catch (e: Exception) {
            Log.w(TAG, "telemetria de la conciliacion de la agenda fallo; la corrida sigue", e)
        }
        return r is ResultadoConciliacionDeAgenda.Fallida
    }

    /**
     * Conciliación inversa del compromiso (E-APP-132). Devuelve `true` si decidió
     * todo lo que leyó (o no corrió). Reencola las pendientes si devolvió alguna.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun conciliacionDeCompromisos(): Boolean {
        val r = try {
            conciliarCompromisos() ?: return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "conciliacion del compromiso fallo de forma inesperada", e)
            return false
        }
        Log.i(TAG, "conciliacion del compromiso: $r")
        try {
            telemetria(
                TelemetriaDeVisitas.EVENTO_CONCILIACION_COMPROMISOS,
                TelemetriaDeVisitas.conciliacionCompromisos(r)
            )
        } catch (e: Exception) {
            Log.w(TAG, "telemetria del compromiso fallo; la corrida sigue", e)
        }
        if (r.devueltas > 0) reencolarPendientes()
        return r.tandasFallidas == 0 && !r.ilegibles && r.devolucionesFallidas == 0
    }

    /**
     * El modo del envío no lo apaga: la agenda bajada se rige por su propio
     * interruptor, que [inventariar] ya consulta. Nunca tumba la corrida.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun inventarioDeLaAgenda() {
        val r = try {
            inventariar() ?: return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "inventario de la agenda fallo de forma inesperada", e)
            return
        }
        Log.i(TAG, "inventario de la agenda: $r")
        try {
            telemetria(
                TelemetriaDeVisitas.EVENTO_INVENTARIO,
                TelemetriaDeVisitas.inventario(r.conteos())
            )
        } catch (e: Exception) {
            Log.w(TAG, "telemetria del inventario fallo; la corrida sigue", e)
        }
    }

    /** `true` si la corrida decidió todo lo que leyó (para anotarla). */
    private suspend fun conciliacionInversa(): Boolean {
        return try {
            val r = conciliar()
            Log.i(
                TAG,
                "conciliacion revisadas=${r.revisadas} faltantes=${r.faltantes} " +
                    "confirmadas=${r.confirmadas} tandas_fallidas=${r.tandasFallidas} " +
                    "ilegibles=${r.entregadasIlegibles}"
            )
            reportar(r)
            // E-APP-079: las devueltas por una foto sin enviar también tienen que salir hoy.
            if (r.faltantes > 0 || r.devueltasPorFoto > 0) reencolarPendientes()
            r.tandasFallidas == 0 && !r.entregadasIlegibles
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Defensa: el use case ya reporta y absorbe lo esperable; esto
            // cubre un escape inesperado (p. ej. el grafo de Hilt).
            Log.w(TAG, "reconciliacion inversa de visitas fallo de forma inesperada", e)
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reportar(r: ConciliacionInversa) {
        try {
            telemetria(TelemetriaDeVisitas.EVENTO_CONCILIACION, TelemetriaDeVisitas.conciliacion(r))
        } catch (e: Exception) {
            Log.w(TAG, "telemetria de la conciliacion fallo; la corrida sigue", e)
        }
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
