package com.example.msp_app.navigation

import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.visitas.domain.port.VisitaImpresaPort
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Puente al grafo de Hilt para [navegarAlTicketLegadoDeLaVisita], que corre
 * desde un callback de navegación (`onRegistrada`, ver `DestinosDeCobranzaGraph`)
 * y no desde un sitio inyectado por Hilt — mismo patrón que
 * `com.example.msp_app.core.sync.pendingwork.di.VisitsReconcileEntryPoint` y
 * `CobranzaSyncProvider.TelemetryEntryPoint`, ambos con el razonamiento
 * completo de por qué `EntryPointAccessors` en vez de `@Inject`.
 *
 * [VisitaImpresaPort] es el MISMO puerto que ya lee `TicketDeVisitaScreen`
 * (`:feature:visitas`, el ticket nuevo) para armar su ticket — reusarlo aquí
 * evita escribir un segundo lector de `VisitEntity` para el mismo dato, y ya
 * viene con su propia prueba (`RoomVisitaImpresaAdapterTest`).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TicketLegadoDeVisitaEntryPoint {
    fun visitaImpresaPort(): VisitaImpresaPort
    fun telemetry(): Telemetry
}
