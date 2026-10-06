package com.example.msp_app.data.menu

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.dao.cobranzasync.CobranzaSyncStateDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.port.EstadoDeEnvioPort
import com.example.msp_app.core.sync.cobranza.CobranzaSyncManager
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * La tarjeta de envío del menú lateral (spec §4): pendientes de pagos y de
 * ventas + hace cuánto hubo contacto SANO con el servidor. [pulso] re-evalúa los
 * minutos cada 60 s aunque Room no cambie.
 */
class EstadoDeEnvioAdapter(
    private val paymentDao: PaymentDao,
    private val localSaleDao: LocalSaleDao,
    private val syncStateDao: CobranzaSyncStateDao,
    private val clock: AppClock,
    private val pulso: Flow<Unit> = pulsoCadaMinuto()
) : EstadoDeEnvioPort {

    override fun estado(): Flow<EstadoDeEnvio> = combine(
        paymentDao.observarPendientesDeSubir(),
        localSaleDao.observarSinSubir(),
        syncStateDao.observarRecursos(RECURSOS),
        pulso
    ) { pagos, ventas, estados, _ ->
        EstadoDeEnvio(
            pagosPendientes = pagos,
            ventasPendientes = ventas,
            ultimoEnvioHaceMinutos = minutosDesdeElUltimoContacto(estados, clock.now())
        )
    }

    companion object {
        val RECURSOS: List<String> =
            listOf(CobranzaSyncManager.RESOURCE_VENTAS, CobranzaSyncManager.RESOURCE_PAGOS)
    }
}

private const val UN_MINUTO_MS = 60_000L

internal fun pulsoCadaMinuto(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(UN_MINUTO_MS)
    }
}

/**
 * Minutos desde el contacto sano MÁS VIEJO de los dos recursos, o `null` cuando
 * no hay un dato honesto. `recordError` también escribe `LAST_SYNCED_AT`
 * (`CobranzaSyncManager.kt:286-295`), por eso una fila con `LAST_ERROR` anula el
 * tiempo (decisión D7 del plan).
 */
internal fun minutosDesdeElUltimoContacto(
    estados: List<CobranzaSyncStateEntity>,
    ahora: Instant
): Long? {
    if (estados.map { it.RESOURCE }.toSet() != EstadoDeEnvioAdapter.RECURSOS.toSet()) return null
    if (estados.any { it.LAST_ERROR != null }) return null
    val instantes = estados.map { estado ->
        try {
            Instant.parse(estado.LAST_SYNCED_AT)
        } catch (ilegible: DateTimeParseException) {
            return null
        }
    }
    val minutos = Duration.between(instantes.min(), ahora).toMinutes()
    return minutos.takeIf { it >= 0 }
}
