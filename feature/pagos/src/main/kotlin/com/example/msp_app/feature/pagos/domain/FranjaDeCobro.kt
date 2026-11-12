package com.example.msp_app.feature.pagos.domain

import java.util.Locale

/** Cómo se rotula el día acordado en la esquina de la franja. */
enum class EtiquetaDelAcordado { COINCIDE, DISTINTO, AL_VENDER }

/** El tramo de la barra repartida. */
enum class ColorDelTramo { FUERTE, SUAVE, OTROS }

/** Un tramo de la barra: su peso es proporcional a [pagos]. */
data class TramoDeLaBarra(val rotulo: String, val pagos: Int, val color: ColorDelTramo)

/**
 * Lo que la franja "Día de cobro" pinta, ya decidido. La arma [franjaDeCobro]; la
 * pantalla solo lo dibuja.
 *
 * [etiqueta] `null` = sin etiqueta; [etiquetaTexto] es su texto. [tramos] vacío =
 * sin barra.
 */
data class FranjaDeCobro(
    val etiqueta: EtiquetaDelAcordado?,
    val etiquetaTexto: String?,
    val diaGrande: String,
    val tramos: List<TramoDeLaBarra>
)

/** Un día solo entra a la barra con al menos tantos pagos. */
private const val PAGOS_MINIMOS_PARA_TRAMO = 2

/** Máximo de tramos con nombre en la barra. */
private const val MAXIMO_DE_TRAMOS = 3

/** Rótulo del tramo gris con el resto de los pagos. */
const val ROTULO_OTROS: String = "otros"

/** Texto grande cuando hay empate y nadie acordó un día. */
const val DISTINTOS_DIAS: String = "Distintos días"

private fun DiaAcordado.textoGrande(): String = when (this) {
    is DiaAcordado.DeLaSemana -> dia.nombre
    is DiaAcordado.DelMes -> "Día $dia"
}

private fun DiaAcordado.textoMinuscula(): String = when (this) {
    is DiaAcordado.DeLaSemana -> dia.nombre.lowercase(Locale.ROOT)
    is DiaAcordado.DelMes -> "día $dia"
}

/**
 * **La regla de la franja** (spec del día de cobro §7). Pura: sin reloj, sin red.
 *
 * | acordado / estimación | etiqueta | día grande | barra |
 * |---|---|---|---|
 * | = principal | `✓ Acordado` | principal | sí |
 * | ≠ principal | `Acordó sábado` (naranja) | principal | sí |
 * | solo acordado | `Acordado al vender` | el acordado | no |
 * | solo estimación | ninguna | principal | sí |
 * | empate + acordado | `Acordado al vender` | el acordado | sí, sin tramo fuerte |
 * | empate sin acordado | ninguna | `Distintos días` | sí, sin tramo fuerte |
 * | nada | `null`: la franja no aparece | | |
 *
 * Una estimación sin días no cuenta como estimación: no hay nada que repartir ni
 * que nombrar.
 */
fun franjaDeCobro(dia: DiaDeCobroDeLaVenta?): FranjaDeCobro? {
    if (dia == null) return null
    val acordado = dia.acordado
    val estimacion = dia.estimacion?.takeIf { it.dias.isNotEmpty() }
    return when {
        estimacion == null -> acordado?.let { soloAcordado(it) }
        estimacion.principal != null -> conGanador(acordado, estimacion, estimacion.principal)
        else -> conEmpate(acordado, estimacion)
    }
}

private fun soloAcordado(acordado: DiaAcordado) = FranjaDeCobro(
    etiqueta = EtiquetaDelAcordado.AL_VENDER,
    etiquetaTexto = "Acordado al vender",
    diaGrande = acordado.textoGrande(),
    tramos = emptyList()
)

private fun conGanador(
    acordado: DiaAcordado?,
    estimacion: EstimacionDelDia,
    principal: DiaDeLaSemana
): FranjaDeCobro {
    val coincide = acordado is DiaAcordado.DeLaSemana && acordado.dia == principal
    val (etiqueta, texto) = when {
        acordado == null -> null to null
        coincide -> EtiquetaDelAcordado.COINCIDE to "✓ Acordado"
        else -> EtiquetaDelAcordado.DISTINTO to "Acordó ${acordado.textoMinuscula()}"
    }
    return FranjaDeCobro(
        etiqueta = etiqueta,
        etiquetaTexto = texto,
        diaGrande = principal.nombre,
        tramos = tramosDeLaBarra(estimacion, hayFuerte = true)
    )
}

private fun conEmpate(acordado: DiaAcordado?, estimacion: EstimacionDelDia) = FranjaDeCobro(
    etiqueta = acordado?.let { EtiquetaDelAcordado.AL_VENDER },
    etiquetaTexto = acordado?.let { "Acordado al vender" },
    diaGrande = acordado?.textoGrande() ?: DISTINTOS_DIAS,
    tramos = tramosDeLaBarra(estimacion, hayFuerte = false)
)

/**
 * Hasta [MAXIMO_DE_TRAMOS] días con al menos [PAGOS_MINIMOS_PARA_TRAMO] pagos, de
 * más a menos (empate: orden de la semana); el primero FUERTE si [hayFuerte], el
 * resto SUAVE; y un tramo gris con lo que sobra, solo si sobra algo.
 */
internal fun tramosDeLaBarra(
    estimacion: EstimacionDelDia,
    hayFuerte: Boolean
): List<TramoDeLaBarra> {
    val ordenados = estimacion.dias
        .filter { it.pagos > 0 }
        .sortedWith(compareByDescending<PagosDelDia> { it.pagos }.thenBy { it.dia.ordinal })
    val conNombre = ordenados.filter { it.pagos >= PAGOS_MINIMOS_PARA_TRAMO }.take(MAXIMO_DE_TRAMOS)
    val tramos = conNombre.mapIndexed { i, d ->
        TramoDeLaBarra(
            rotulo = d.dia.abreviatura,
            pagos = d.pagos,
            color = if (i == 0 && hayFuerte) ColorDelTramo.FUERTE else ColorDelTramo.SUAVE
        )
    }
    val otros = ordenados.sumOf { it.pagos } - conNombre.sumOf { it.pagos }
    return if (otros > 0) tramos + TramoDeLaBarra(ROTULO_OTROS, otros, ColorDelTramo.OTROS) else tramos
}
