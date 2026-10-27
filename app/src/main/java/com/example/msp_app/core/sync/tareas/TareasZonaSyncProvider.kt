package com.example.msp_app.core.sync.tareas

import android.content.Context
import android.util.Log
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import com.example.msp_app.core.sync.visitas.ResultadoSync
import com.example.msp_app.core.visitas.agenda.KillSwitchAgenda
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.TareasZonaSyncApi

/**
 * Arma un [TareasZonaSyncManager] por corrida (plan visitas P3, Tarea 11).
 *
 * Igual que `VisitasZonaSyncProvider`: sin instancia cacheada (el servicio
 * Retrofit se crea en cada corrida; nada de `@Singleton`), la zona es la del
 * cobrador que la UI publicó en [CobranzaSyncProvider] y el interruptor es el
 * mismo de la bajada de la agenda (`config/visitas_compromisos.bajada`, apagado
 * por omisión): las tareas son parte de la misma agenda.
 *
 * `alAplicar` queda vacío hasta la Tarea 12, que cuelga aquí el aviso
 * inmediato "Visita asignada".
 */
object TareasZonaSyncProvider {

    fun crear(context: Context): TareasZonaSyncManager = TareasZonaSyncManager(
        api = V2ApiProvider.create(TareasZonaSyncApi::class.java),
        dao = AppDatabase.getInstance(context.applicationContext).tareaZonaDao(),
        zonaActual = { CobranzaSyncProvider.userContextFlow.value?.zona },
        killSwitch = { KillSwitchAgenda.bajada },
        telemetria = { r -> Log.i(TAG, "bajada de tareas: ${r.resumen()}") }
    )

    /**
     * Una corrida; nunca lanza salvo cancelación. Con el interruptor apagado ni
     * siquiera arma el servicio ni abre la base.
     */
    suspend fun sincronizar(context: Context): ResultadoSync {
        if (!KillSwitchAgenda.bajada) return ResultadoSync.Apagado
        return crear(context).sincronizar()
    }

    private const val TAG = "TareasZonaSync"
}

/** Solo conteos y banderas: nada de ids, notas ni datos del cliente en el log. */
private fun ResultadoSync.resumen(): String = when (this) {
    ResultadoSync.Apagado -> "apagada"
    ResultadoSync.SinZona -> "sin zona"
    is ResultadoSync.Ok ->
        "ok paginas=$paginas aplicadas=$aplicadas descartadas=$descartadas " +
            "replay=$replayPorEpoch cambio_zona=$cambioDeZona"
    is ResultadoSync.Error -> "error ${causa::class.simpleName}"
}
