package com.example.msp_app.core.sync.tareas

import android.content.Context
import android.util.Log
import com.example.msp_app.core.avisos.AvisoInmediato
import com.example.msp_app.core.avisos.NotificadorDeVisitaAsignada
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.TareaZonaEntity
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import com.example.msp_app.core.sync.visitas.ResultadoSync
import com.example.msp_app.core.visitas.agenda.AgendaLocal
import com.example.msp_app.core.visitas.agenda.KillSwitchAgenda
import com.example.msp_app.core.visitas.agenda.RoomFuenteDeAgenda
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.TareasZonaSyncApi
import java.time.Instant

/**
 * Arma un [TareasZonaSyncManager] por corrida (plan visitas P3, Tarea 11).
 *
 * Igual que `VisitasZonaSyncProvider`: sin instancia cacheada (el servicio
 * Retrofit se crea en cada corrida; nada de `@Singleton`), la zona es la del
 * cobrador que la UI publicó en [CobranzaSyncProvider] y el interruptor es el
 * mismo de la bajada de la agenda (`config/visitas_compromisos.bajada`, apagado
 * por omisión): las tareas son parte de la misma agenda.
 *
 * `alAplicar` cuelga el aviso inmediato "Visita asignada" (Tarea 12,
 * E-APP-123): deriva el estado de las tareas recién guardadas con la agenda
 * local (si el cobrador ya visitó, no avisa) y deja pasar solo las pendientes
 * que `aviso_emitido` no tenga.
 */
object TareasZonaSyncProvider {

    fun crear(context: Context): TareasZonaSyncManager {
        val db = AppDatabase.getInstance(context.applicationContext)
        return TareasZonaSyncManager(
            api = V2ApiProvider.create(TareasZonaSyncApi::class.java),
            dao = db.tareaZonaDao(),
            zonaActual = { CobranzaSyncProvider.userContextFlow.value?.zona },
            killSwitch = { KillSwitchAgenda.bajada },
            alAplicar = avisoInmediato(context.applicationContext, db),
            telemetria = { r -> Log.i(TAG, "bajada de tareas: ${r.resumen()}") }
        )
    }

    private fun avisoInmediato(
        context: Context,
        db: AppDatabase
    ): suspend (List<TareaZonaEntity>) -> Unit {
        val notificador = NotificadorDeVisitaAsignada(context)
        val agenda = AgendaLocal(
            RoomFuenteDeAgenda(db.saleDao(), db.visitDao(), db.visitaZonaDao())
        )
        val aviso = AvisoInmediato(
            dao = db.avisosDao(),
            notificar = notificador::mostrar,
            permitido = notificador::permitido,
            ahora = { Instant.now() },
            hoy = { AgendaLocal.hoyDe(Instant.now()) },
            etiqueta = { t ->
                db.saleDao().getById(t.VENTA_ID)?.let { it.FOLIO.ifBlank { it.CLIENTE } }.orEmpty()
            },
            reportarPermiso = { Log.i(TAG, "permiso.notificaciones=$it") }
        )
        return { filas ->
            val hoy = AgendaLocal.hoyDe(Instant.now())
            aviso.alAplicar(filas, agenda.estadosDeTareas(filas, hoy))
        }
    }

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
