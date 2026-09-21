package com.example.msp_app.feature.ventacorreccion.domain.port

/**
 * Reloj epoch-millis para el candado de corrección — aislado de
 * [com.example.msp_app.core.common.time.AppClock] porque `local_sale.CLAIMED_AT` y los
 * `*LeaseMs` del DAO ([com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases])
 * trabajan en `Long` epoch millis, no en `Instant`. Implementación de producción:
 * [com.example.msp_app.feature.ventacorreccion.data.AppClockRelojPort]. Nunca reloj real en
 * pruebas — usar `FakeReloj` (envuelve
 * [com.example.msp_app.core.testing.time.FakeClock]).
 */
interface RelojPort {
    fun ahoraEpochMillis(): Long
}
