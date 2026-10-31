package com.example.msp_app.data.visitas

import android.content.Context
import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion
import com.example.msp_app.core.common.location.captura.TomadorDeUbicacion
import com.example.msp_app.core.ubicacion.UbicacionAnticipada
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort

/**
 * Implementación real de [UbicacionPort] sobre el tomador anticipado (E-APP-148, K6).
 *
 * Contesta, SIN suspender ni esperar, la mejor ubicación que el tomador ya tiene y que la regla de
 * aceptación (≤ 30 m, no simulada, fresca) da por buena; si no hay, `null`. Ya no pide
 * `getCurrentLocation` a Play Services: la ubicación que llega DESPUÉS la escribe el tomador en
 * cuanto la acepta (`UbicacionDeCaptura`), y el worker retiene el envío hasta 30 s.
 *
 * **Quién escribe qué, sin que se pisen:** la visita nace con la ubicación de ANTES dentro de la
 * transacción del lote; este puerto devuelve esa misma ubicación para que `anotarUbicacion` la
 * confirme (un UPDATE que solo toca filas en 0,0, así que no escribe dos veces) y encole el PATCH
 * tardío de E-APP-129 cuando corresponda. Sin ubicación al guardar, el tomador la escribe después,
 * también solo si sigue en 0,0.
 *
 * **Contesta `null`, no lanza**, con permiso negado o GPS apagado: el tomador ya lo informa y avisa
 * al cobrador (E-APP-144). El caso de uso reporta ese `null` con su código de telemetría y
 * registra la visita igual (en 0,0 si el POST exige el campo).
 */
class UbicacionDeVisitaAdapter(
    private val tomador: TomadorDeUbicacion
) : UbicacionPort {

    constructor(context: Context) : this(UbicacionAnticipada.tomador(context))

    override suspend fun ubicacionActual(): UbicacionDeLaVisita? =
        (tomador.mejorUbicacionAntesDeGuardar() as? DecisionDeUbicacion.AceptarAntes)
            ?.fix
            ?.let { UbicacionDeLaVisita(lat = it.lat, lng = it.lon) }
}
