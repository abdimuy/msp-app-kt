package com.example.msp_app.core.common.cobranza.domain

import java.time.LocalDate
import java.time.LocalTime

/**
 * Cómo se trata un estado en la lista (antes en feature/pagos EstadoCuentaUi.kt;
 * movido aquí para que los casos compartidos lo prueben, E-APP-070).
 */
enum class TratoDelEstado { PAGADO, PARCIAL, REGRESAS, DIFERIDO, ESCALAR, CITA, NADIE, SIN_TRABAJAR }

object TratoDeEstado {
    /** Una promesa sin fecha y una cita sin hora se tratan como REGRESAS: nada sostiene sacarlas de la ruta. */
    fun de(estado: EstadoCuenta, fechaPromesa: LocalDate?, horaCita: LocalTime?): TratoDelEstado =
        when (estado) {
            EstadoCuenta.PAGO -> TratoDelEstado.PAGADO
            EstadoCuenta.ABONO_PARCIAL -> TratoDelEstado.PARCIAL
            EstadoCuenta.VISITE_VUELVO -> TratoDelEstado.REGRESAS
            EstadoCuenta.PROMETIO_PROXIMA ->
                if (fechaPromesa == null) TratoDelEstado.REGRESAS else TratoDelEstado.DIFERIDO
            EstadoCuenta.SE_NEGO -> TratoDelEstado.ESCALAR
            EstadoCuenta.CITA_A_UNA_HORA -> if (horaCita == null) TratoDelEstado.REGRESAS else TratoDelEstado.CITA
            EstadoCuenta.NO_ESTABA -> TratoDelEstado.NADIE
            EstadoCuenta.SIN_TOCAR -> TratoDelEstado.SIN_TRABAJAR
        }
}
