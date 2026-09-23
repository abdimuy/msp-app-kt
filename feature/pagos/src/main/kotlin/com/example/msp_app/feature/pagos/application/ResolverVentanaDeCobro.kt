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

    suspend operator fun invoke(): VentanaCobro? = resolver().ventana

    /**
     * La ventana **y** el "hoy" contra el que preguntan los segmentos de la
     * lista, resueltos con **una sola** lectura del reloj.
     *
     * Que sea una sola no es una optimización: con dos lecturas, una carga que
     * cruza la medianoche emparejaba una ventana que cierra el día N con un
     * `hoy` del día N+1, y entonces "prometió para hoy" y "el periodo se cerró
     * hoy" hablaban de días distintos dentro de la misma pantalla. Es la misma
     * razón por la que esta clase junta el inicio del periodo y el reloj: para
     * que nadie pueda tomar una de las piezas sin la otra.
     */
    suspend fun resolver(): PeriodoResuelto {
        val ahora = clock.now()
        val inicio = periodoDeCobroPort.inicioDelPeriodo()
        return PeriodoResuelto(
            ventana = inicio?.let { VentanaCobro(inicio = it, fin = ahora) },
            hoy = AppTime.toBusinessDate(ahora)
        )
    }
}

/**
 * El periodo resuelto en un solo viaje al reloj: la [ventana] (o `null` si
 * `FECHA_CARGA_INICIAL` todavía no se conoce) y el [hoy] de negocio que le
 * corresponde. Los dos salen del MISMO `Instant`.
 */
data class PeriodoResuelto(val ventana: VentanaCobro?, val hoy: LocalDate)
