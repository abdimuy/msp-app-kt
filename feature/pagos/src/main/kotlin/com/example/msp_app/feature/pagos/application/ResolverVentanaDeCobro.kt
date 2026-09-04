package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.pagos.domain.port.PeriodoDeCobroPort
import java.time.LocalDate
import javax.inject.Inject

/**
 * La ventana `[inicio de periodo, ahora]` sobre la que se deriva el catálogo de
 * ocho.
 *
 * Junta las DOS piezas que siempre viajan juntas —el inicio del periodo y el
 * reloj— para que ningún llamador pueda tomar una sin la otra, ni alcanzar
 * `Instant.now()` por su cuenta: `AppClock` es la única fuente de "ahora" del
 * repo, y un borde de periodo es justo donde alguien intenta saltársela.
 *
 * Devuelve `null` cuando `FECHA_CARGA_INICIAL` todavía no se conoce. No se
 * inventa una ventana (defecto D5): sin ventana no hay derivación, y quien
 * recibe el `null` lo dice — [DerivarEstadoDelPeriodo] emite
 * [PagosTelemetria.CODE_PERIODO_DESCONOCIDO].
 */
class ResolverVentanaDeCobro @Inject constructor(
    private val periodoDeCobroPort: PeriodoDeCobroPort,
    private val clock: AppClock
) {

    suspend operator fun invoke(): VentanaCobro? =
        periodoDeCobroPort.inicioDelPeriodo()?.let { VentanaCobro.desdeInicioSemana(it, clock) }

    /**
     * "Hoy" en la zona de negocio, del MISMO reloj que cierra la ventana.
     *
     * Los segmentos de la lista (Task 17) preguntan si una promesa cae hoy o ya
     * se pasó, y esa pregunta tiene que usar el mismo "ahora" que derivó el
     * periodo. Vive aquí por la razón por la que existe esta clase: para que
     * nadie pueda tomar una de las dos piezas sin la otra, ni alcanzar
     * `LocalDate.now()` por su cuenta.
     */
    fun hoy(): LocalDate = AppTime.todayInBusinessZone(clock)
}
