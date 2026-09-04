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
     * Cuántos días adelante puede caer un compromiso. **Un año.**
     *
     * El número no es redondo por gusto: la cobranza corre por semana y los
     * plazos de venta de esta cartera se miden en 15-24 abonos semanales, así
     * que un año está cómodamente más allá de cualquier compromiso real y no le
     * quita al cobrador ni un caso de campo. Del otro lado, la retención de la
     * poda conserva la fila noventa días **después** de la fecha del compromiso
     * (`VisitsLocalDataSource.RETENCION_DE_COMPROMISOS_DIAS`), así que este
     * techo es también el que acota la vida de una fila a unos quince meses en
     * vez de dejarla clavada por décadas cuando alguien teclea mal el año.
     */
    const val HORIZONTE_DIAS: Long = 365

    /** El último día que un compromiso puede tomar, contando desde [hoy]. */
    fun ultimoDiaValido(hoy: LocalDate): LocalDate = hoy.plusDays(HORIZONTE_DIAS)

    /**
     * Todo lo que impide guardar [captura], en el orden en que la pantalla lo
     * muestra. Lista vacía = se puede guardar.
     */
    fun bloqueosDe(captura: CapturaDeVisita, hoy: LocalDate): List<BloqueoDeLaVisita> {
        val resultado = captura.resultado ?: return listOf(BloqueoDeLaVisita.SIN_RESULTADO)
        return when (resultado) {
            ResultadoDeVisita.PROMETIO -> bloqueosDeLaPromesa(captura, hoy)
            ResultadoDeVisita.CITA -> bloqueosDeLaCita(captura, hoy)
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
        if (fecha == null) {
            add(
                BloqueoDeLaVisita.PROMESA_SIN_FECHA
            )
        } else {
            addAll(deLaFecha(fecha, hoy))
        }
        val monto = captura.montoPrometido
        if (monto != null && monto <= Money.ZERO) add(BloqueoDeLaVisita.PROMESA_SIN_MONTO)
    }

    /**
     * La cita se topa por los DOS lados, igual que la promesa.
     *
     * El día importa más aquí de lo que parece: una cita fechada hacia atrás más
     * allá de la ventana de retención se poda en la primera sincronización, o
     * sea que se pierde exactamente el registro que esa ventana existe para
     * conservar.
     */
    private fun bloqueosDeLaCita(
        captura: CapturaDeVisita,
        hoy: LocalDate
    ): List<BloqueoDeLaVisita> {
        val fecha = captura.fechaCita ?: return listOf(BloqueoDeLaVisita.CITA_SIN_DIA)
        return deLaFecha(fecha, hoy)
    }

    /**
     * La ÚNICA regla sobre la fecha de un compromiso, compartida por la promesa
     * y por la cita: **ni hacia atrás ni más allá del horizonte**. Los bordes
     * son exactos — `hoy` pasa, `hoy - 1` no; `hoy + 365` pasa, `hoy + 366` no.
     */
    private fun deLaFecha(fecha: LocalDate, hoy: LocalDate): List<BloqueoDeLaVisita> = when {
        fecha.isBefore(hoy) -> listOf(BloqueoDeLaVisita.COMPROMISO_EN_EL_PASADO)
        fecha.isAfter(ultimoDiaValido(hoy)) -> listOf(BloqueoDeLaVisita.COMPROMISO_MUY_LEJANO)
        else -> emptyList()
    }
}
