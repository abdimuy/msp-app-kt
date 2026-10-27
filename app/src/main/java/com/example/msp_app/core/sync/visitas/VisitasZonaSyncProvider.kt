package com.example.msp_app.core.sync.visitas

import android.content.Context
import android.util.Log
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import com.example.msp_app.core.visitas.agenda.KillSwitchAgenda
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.VisitasZonaSyncApi
import java.time.Instant

/**
 * Arma un [VisitasZonaSyncManager] por corrida (plan visitas P2, Tarea 16).
 *
 * No hay instancia cacheada: el servicio Retrofit se crea en cada corrida, como
 * los demás servicios con baseURL (nada de `@Singleton`). Lo que serializa dos
 * corridas simultáneas (el periódico y el aviso SSE) es el candado del proceso
 * que el manager trae por omisión.
 *
 * - La zona es la del cobrador que la UI publicó en [CobranzaSyncProvider]
 *   (solo se lee). Sin UI todavía, no hay zona y la corrida no baja nada.
 * - El interruptor es `config/visitas_compromisos.bajada`, apagado por omisión.
 */
object VisitasZonaSyncProvider {

    fun crear(context: Context): VisitasZonaSyncManager = VisitasZonaSyncManager(
        api = V2ApiProvider.create(VisitasZonaSyncApi::class.java),
        dao = AppDatabase.getInstance(context.applicationContext).visitaZonaDao(),
        zonaActual = { CobranzaSyncProvider.userContextFlow.value?.zona },
        killSwitch = { KillSwitchAgenda.bajada },
        telemetria = { r ->
            Log.i(TAG, "bajada de visitas: ${r.resumen()}")
            CobranzaSyncProvider.userContextFlow.value?.zona?.let {
                reportarBajada(
                    detector,
                    it,
                    r
                )
            }
        }
    )

    /**
     * Memoria del proceso para `visitas_sync.cursor_stalled` (E-APP-110): el
     * manager se arma por corrida, así que la posición de cierre de la corrida
     * anterior tiene que vivir aquí.
     */
    private val detector = DetectorDeCursorAtorado()

    /**
     * Inventario de la agenda (Tarea 18, E-APP-109), con el MISMO interruptor
     * y la MISMA zona que la bajada: apagada la bajada, no se toca
     * `visita_zona`. `null` = no corrió (interruptor apagado o sin zona).
     */
    suspend fun inventariar(
        context: Context,
        ahora: Instant = AppClock.System.now()
    ): ResultadoInventario? {
        if (!KillSwitchAgenda.bajada) return null
        val zona = CobranzaSyncProvider.userContextFlow.value?.zona ?: return null
        return VisitasZonaReconciler(
            api = V2ApiProvider.create(VisitasZonaSyncApi::class.java),
            dao = AppDatabase.getInstance(context.applicationContext).visitaZonaDao()
        ).reconciliar(zona, VisitasZonaSyncManager.desdeDeLaVentana(ahora))
    }

    /**
     * Una corrida; nunca lanza salvo cancelación (ver [VisitasZonaSyncManager]).
     * Con el interruptor apagado ni siquiera arma el servicio ni abre la base.
     */
    suspend fun sincronizar(context: Context): ResultadoSync {
        if (!KillSwitchAgenda.bajada) return ResultadoSync.Apagado
        return crear(context).sincronizar()
    }

    private const val TAG = "VisitasZonaSync"
}

/** Solo conteos y banderas: nada de ids ni datos del cliente en el log. */
private fun ResultadoSync.resumen(): String = when (this) {
    ResultadoSync.Apagado -> "apagada"
    ResultadoSync.SinZona -> "sin zona"
    is ResultadoSync.Ok ->
        "ok paginas=$paginas aplicadas=$aplicadas descartadas=$descartadas " +
            "replay=$replayPorEpoch cambio_zona=$cambioDeZona"
    is ResultadoSync.Error -> "error ${causa::class.simpleName}"
}
