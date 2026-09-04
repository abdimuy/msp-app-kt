package com.example.msp_app.feature.visitas.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.visitas.domain.model.BloqueoDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import java.time.LocalDate

/**
 * Las reglas de la captura. Dominio PURO: cero Compose, cero Room, cero
 * `LocalDate.now()` — el "hoy" entra por parámetro, desde `AppClock`.
 *
 * Es el **único** lugar que decide si una visita se puede guardar. La pantalla
 * apaga el CTA con esto y el caso de uso lo vuelve a evaluar antes de escribir:
 * dos cinturones, una sola función, así que no pueden discrepar.
 */
object ReglasDeLaVisita {

    /**
     * Todo lo que impide guardar [captura], en el orden en que la pantalla lo
     * muestra. Lista vacía = se puede guardar.
     */
    fun bloqueosDe(captura: CapturaDeVisita, hoy: LocalDate): List<BloqueoDeLaVisita> {
        val resultado = captura.resultado ?: return listOf(BloqueoDeLaVisita.SIN_RESULTADO)
        return when (resultado) {
            ResultadoDeVisita.PROMETIO -> bloqueosDeLaPromesa(captura, hoy)
            ResultadoDeVisita.CITA -> bloqueosDeLaCita(captura)
            // "No estaba", "vuelvo" y "se negó" se sostienen con la sola
            // etiqueta: no prometen nada ni difieren nada.
            ResultadoDeVisita.NO_ESTABA,
            ResultadoDeVisita.VISITE_VUELVO,
            ResultadoDeVisita.SE_NEGO -> emptyList()
        }
    }

    /** ¿Se puede guardar? Azúcar sobre [bloqueosDe], para que nadie lo recalcule. */
    fun sePuedeGuardar(captura: CapturaDeVisita, hoy: LocalDate): Boolean =
        bloqueosDe(captura, hoy).isEmpty()

    private fun bloqueosDeLaPromesa(
        captura: CapturaDeVisita,
        hoy: LocalDate
    ): List<BloqueoDeLaVisita> = buildList {
        val fecha = captura.fechaPromesa
        when {
            fecha == null -> add(BloqueoDeLaVisita.PROMESA_SIN_FECHA)
            // El borde exacto: `hoy` pasa, `hoy - 1` no.
            fecha.isBefore(hoy) -> add(BloqueoDeLaVisita.PROMESA_EN_EL_PASADO)
        }
        val monto = captura.montoPrometido
        if (monto != null && monto <= Money.ZERO) add(BloqueoDeLaVisita.PROMESA_SIN_MONTO)
    }

    private fun bloqueosDeLaCita(captura: CapturaDeVisita): List<BloqueoDeLaVisita> =
        if (captura.fechaCita == null) listOf(BloqueoDeLaVisita.CITA_SIN_DIA) else emptyList()
}
