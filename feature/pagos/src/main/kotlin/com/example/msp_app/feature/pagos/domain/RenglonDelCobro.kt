package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un renglón del grupo "El cobro": rótulo, valor y, si hace falta, su respaldo en chico. */
data class RenglonDelCobro(val rotulo: String, val valor: String, val nota: String? = null)

private val FECHA_Y_HORA_DEL_CALCULO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", BUSINESS_LOCALE)

private fun DiaDeLaSemana.enMinuscula(): String = nombre.lowercase(Locale.ROOT)

private fun String.conMayusculaInicial(): String =
    replaceFirstChar { it.titlecase(BUSINESS_LOCALE) }

/**
 * Los renglones del grupo **"El cobro"** de "Datos de la venta", en este orden y
 * cada uno **solo si hay con qué escribirlo**:
 *
 * - **Día acordado** — lo anotó el vendedor al vender.
 * - **Suele pagar** — el día con más pagos y "N de sus últimos M pagos". Con
 *   empate no hay día: "Distintos días".
 * - **Otros días** — los demás días con al menos un pago, cada uno con su número.
 * - **Calculado** — fecha y hora (de la oficina) en que el servidor lo calculó.
 *
 * Lista vacía = el grupo no se abre.
 */
fun datosDelCobro(dia: DiaDeCobroDeLaVenta?): List<RenglonDelCobro> {
    if (dia == null) return emptyList()
    val estimacion = dia.estimacion?.takeIf { it.dias.isNotEmpty() }
    val renglones = listOfNotNull(
        dia.acordado?.let { acordado ->
            RenglonDelCobro(
                "Día acordado",
                when (acordado) {
                    is DiaAcordado.DeLaSemana -> acordado.dia.nombre
                    is DiaAcordado.DelMes -> "Día ${acordado.dia}"
                },
                "lo anotó el vendedor al vender"
            )
        },
        estimacion?.let { sueleAPagar(it) },
        estimacion?.let { otrosDias(it) }
    )
    if (renglones.isEmpty()) return emptyList()
    val calculado = dia.calculadoEn?.let {
        RenglonDelCobro("Calculado", FECHA_Y_HORA_DEL_CALCULO.format(AppTime.toBusinessZoned(it)))
    }
    return renglones + listOfNotNull(calculado)
}

private fun sueleAPagar(e: EstimacionDelDia): RenglonDelCobro {
    val ventana = "de sus últimos ${e.pagosConsiderados} pagos"
    val principal = e.principal ?: return RenglonDelCobro("Suele pagar", DISTINTOS_DIAS, ventana)
    val pagos = e.dias.firstOrNull { it.dia == principal }?.pagos
    val nota = if (pagos != null) "$pagos $ventana" else ventana
    return RenglonDelCobro("Suele pagar", principal.nombre, nota)
}

private fun otrosDias(e: EstimacionDelDia): RenglonDelCobro? {
    val otros = e.dias
        .filter { it.dia != e.principal && it.pagos > 0 }
        .sortedWith(compareByDescending<PagosDelDia> { it.pagos }.thenBy { it.dia.ordinal })
    if (otros.isEmpty()) return null
    val iguales = otros.map { it.pagos }.distinct().size == 1
    return if (iguales) {
        val n = otros.first().pagos
        RenglonDelCobro(
            "Otros días",
            otros.joinToString(", ") { it.dia.enMinuscula() }.conMayusculaInicial(),
            if (n == 1) "1 pago cada uno" else "$n pagos cada uno"
        )
    } else {
        RenglonDelCobro(
            "Otros días",
            otros.joinToString(", ") { "${it.dia.enMinuscula()} ${it.pagos}" }.conMayusculaInicial()
        )
    }
}
