package com.example.msp_app.feature.pagos.domain

import java.time.Instant

/**
 * Los siete días de la semana tal como el servidor los manda (`LUNES`…`DOMINGO`,
 * sin acentos) y como la pantalla los escribe.
 *
 * El orden de declaración es el orden de la semana: lo usa el desempate de la
 * barra, igual que el servidor (`empate entre días: orden LUNES..DOMINGO`).
 */
enum class DiaDeLaSemana(val clave: String, val nombre: String, val abreviatura: String) {
    LUNES("LUNES", "Lunes", "Lun"),
    MARTES("MARTES", "Martes", "Mar"),
    MIERCOLES("MIERCOLES", "Miércoles", "Mié"),
    JUEVES("JUEVES", "Jueves", "Jue"),
    VIERNES("VIERNES", "Viernes", "Vie"),
    SABADO("SABADO", "Sábado", "Sáb"),
    DOMINGO("DOMINGO", "Domingo", "Dom");

    companion object {
        /** El día de la clave del servidor, o `null` si no es uno de los siete. */
        fun deLaClave(clave: String?): DiaDeLaSemana? {
            val limpia = clave?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.clave == limpia }
        }
    }
}

/** El día que el vendedor anotó al vender. NUNCA se calcula en el teléfono. */
sealed interface DiaAcordado {
    /** Día de la semana (`tipo = semana`). */
    data class DeLaSemana(val dia: DiaDeLaSemana) : DiaAcordado

    /** Día del mes, 1..31 (`tipo = mes`). */
    data class DelMes(val dia: Int) : DiaAcordado
}

/** Cuántos pagos de la ventana cayeron en [dia]. */
data class PagosDelDia(val dia: DiaDeLaSemana, val pagos: Int)

/**
 * La estimación del servidor con el historial de pagos de la venta.
 *
 * [principal] `null` = empate en el primer lugar. [dias] trae todos los días con
 * al menos un pago, de más a menos.
 */
data class EstimacionDelDia(
    val pagosConsiderados: Int,
    val principal: DiaDeLaSemana?,
    val dias: List<PagosDelDia>
)

/**
 * Lo que el servidor sabe del día de cobro de UNA venta, con la fecha en que lo
 * calculó. Al menos uno de [acordado] y [estimacion] viene lleno (el servidor no
 * manda ventas sin ninguno).
 */
data class DiaDeCobroDeLaVenta(
    val acordado: DiaAcordado?,
    val estimacion: EstimacionDelDia?,
    val calculadoEn: Instant?
)
