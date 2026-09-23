package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.feature.pagos.domain.model.MesDePagos
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * El "riel" del mock: los pagos en orden descendente, **agrupados por mes y
 * visibles sin colapsar**.
 *
 * El mes interrumpe el riel con su nodo, su nombre y su subtotal; los pagos
 * cuelgan debajo. No hay estado de expandido/colapsado y no debe haberlo — la
 * decisión del `task-16-brief.md` es exactamente que nada quede escondido
 * detrás de un toque.
 *
 * Dominio PURO. El mes se calcula en la zona de negocio de `AppTime`: un pago
 * de las 23:30 del 31 de agosto pertenece a agosto en Tehuacán, no a
 * septiembre en UTC.
 */
object RielDePagos {

    /**
     * Agrupa [pagos] por mes de negocio, más reciente primero, y dentro de cada
     * mes los pagos también del más reciente al más viejo.
     */
    fun de(pagos: List<PagoDelHistorial>): List<MesDePagos> = pagos
        .groupBy { YearMonth.from(AppTime.toBusinessDate(it.fecha)) }
        .toSortedMap(compareByDescending { it })
        .map { (mes, delMes) ->
            MesDePagos(
                mes = mes,
                nombre = nombreDe(mes),
                subtotal = Money.sum(delMes.map { it.importe }),
                pagos = delMes.sortedByDescending { it.fecha }
            )
        }

    /**
     * Nombre del mes en minúsculas, es-MX ("agosto"). Minúsculas por la regla de
     * copy del plan, no por el mock — que lo pinta capitalizado.
     */
    fun nombreDe(mes: YearMonth): String =
        mes.month.getDisplayName(TextStyle.FULL, BUSINESS_LOCALE).lowercase(BUSINESS_LOCALE)
}
