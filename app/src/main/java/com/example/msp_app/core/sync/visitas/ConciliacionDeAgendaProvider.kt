package com.example.msp_app.core.sync.visitas

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import com.example.msp_app.core.visitas.agenda.AgendaLocal
import com.example.msp_app.core.visitas.agenda.KillSwitchAgenda
import com.example.msp_app.core.visitas.agenda.RoomFuenteDeAgenda
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.VisitasAgendaApi
import java.time.Instant

/**
 * Arma la conciliación directa por corrida (plan visitas P2, Tarea 18;
 * E-APP-131). Es el primer consumidor de producción de
 * [AgendaLocal]: la lee con [RoomFuenteDeAgenda], que solo hace SELECT.
 *
 * Sin instancia cacheada: el servicio Retrofit se crea en cada corrida (nada de
 * `@Singleton` sobre servicios con baseURL). Mismo interruptor y misma zona que
 * la bajada y el inventario: apagada la bajada no hay agenda que conciliar.
 * `null` = no corrió (interruptor apagado o sin zona).
 */
object ConciliacionDeAgendaProvider {

    suspend fun conciliar(
        context: Context,
        ahora: Instant = Instant.now()
    ): ResultadoConciliacionDeAgenda? {
        if (!KillSwitchAgenda.bajada) return null
        val zona = CobranzaSyncProvider.userContextFlow.value?.zona ?: return null
        val db = AppDatabase.getInstance(context.applicationContext)
        return ConciliacionDeAgenda(
            api = V2ApiProvider.create(VisitasAgendaApi::class.java),
            agenda = AgendaLocal(
                RoomFuenteDeAgenda(db.saleDao(), db.visitDao(), db.visitaZonaDao())
            )
        ).conciliar(zona, ahora)
    }
}
