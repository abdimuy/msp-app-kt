package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.ResultadoEstadoCuenta
import com.example.msp_app.core.common.money.Money
import java.time.LocalDate
import java.time.LocalTime

/**
 * El estado del periodo de UNA venta, listo para `application/` y `ui/`.
 *
 * Es [ResultadoEstadoCuenta] con sus montos envueltos en [Money]: el enum
 * [estado] se copia **verbatim** del catálogo de ocho de la Task 14 —esta clase
 * no decide ningún estado y no tiene una sola rama que lo elija— y los
 * `BigDecimal` pelados de `cobranza/domain` se quedan de aquel lado de la
 * frontera (REGLA DE DINERO, `global-constraints.md`).
 *
 * ## Por qué [fechaPromesa] viaja hasta la pantalla
 *
 * Porque [EstadoCuenta.PROMETIO_PROXIMA] **sin fecha no puede pintarse como
 * diferido**. `PIDE_REAGENDAR` mapea a ese estado por la tabla del plan pero no
 * trae fecha ni monto, y "no cae esta semana" es una afirmación que sin fecha
 * nada sostiene — es el único camino del sistema que puede decirle a un
 * cobrador que deje de trabajar una puerta. La pantalla ramifica sobre este
 * campo, que el dominio ya devuelve; no re-deriva el estado (ver
 * `ui/EstadoCuentaUi.kt`).
 */
data class EstadoDelPeriodo(
    val estado: EstadoCuenta,
    val abonoDelPeriodo: Money,
    val parcialidad: Money,
    val fechaPromesa: LocalDate? = null,
    val montoPrometido: Money? = null,
    /**
     * El DÍA de la cita. Viaja hasta la pantalla por la MISMA razón que
     * [fechaPromesa]: los segmentos preguntan si algo cae HOY, y una cita sin
     * día no puede contestar que sí. "Está dentro del periodo, luego es hoy"
     * pondría la cita del lunes en la lista del jueves.
     */
    val fechaCita: LocalDate? = null,
    val horaCita: LocalTime? = null
) {
    companion object {
        /**
         * Envuelve un [ResultadoEstadoCuenta] del catálogo. **Única** vía de
         * construcción desde el dominio de cobranza: el `Money.of(...)` de la
         * frontera vive aquí y en los adaptadores, en ningún otro lugar.
         */
        fun de(resultado: ResultadoEstadoCuenta): EstadoDelPeriodo = EstadoDelPeriodo(
            estado = resultado.estado,
            abonoDelPeriodo = Money.of(resultado.abonoVentana),
            parcialidad = Money.of(resultado.parcialidad),
            fechaPromesa = resultado.fechaPromesa,
            montoPrometido = resultado.montoPrometido?.let(Money::of),
            fechaCita = resultado.fechaCita,
            horaCita = resultado.horaCita
        )

        /**
         * El estado de una venta que el periodo no alcanzó a tocar — la base del
         * catálogo. Se usa cuando la derivación no trajo entrada para una venta
         * (no debería pasar: `derivar` devuelve una por cuenta), y cuando no hay
         * ventana de cobro porque `FECHA_CARGA_INICIAL` todavía no se conoce.
         */
        fun sinTocar(parcialidad: Money): EstadoDelPeriodo = EstadoDelPeriodo(
            estado = EstadoCuenta.SIN_TOCAR,
            abonoDelPeriodo = Money.ZERO,
            parcialidad = parcialidad
        )
    }
}
