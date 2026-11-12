package com.example.msp_app.feature.pagos.domain

import java.util.Locale

/** Cómo se rotula el día acordado en la esquina de la franja. */
enum class EtiquetaDelAcordado { COINCIDE, DISTINTO, AL_VENDER }

/**
 * Una etiqueta redondeada de la fila "Sáb 5": [rotulo] es la abreviatura del día y
 * [pagos] cuántos de los pagos de la ventana cayeron ese día. [principal] = va en
 * verde fuerte; las demás, solo con borde.
 */
data class EtiquetaDeDia(val rotulo: String, val pagos: Int, val principal: Boolean)

/**
 * Lo que la franja "Día de cobro" pinta, ya decidido. La arma [franjaDeCobro]; la
 * pantalla solo lo dibuja.
 *
 * [etiqueta] `null` = sin etiqueta del acordado; [etiquetaTexto] es su texto.
 * [dias] vacío = sin fila de etiquetas ni texto; [pagosConsiderados] es el N de
 * "Pagos por día, de sus últimos N".
 */
data class FranjaDeCobro(
    val etiqueta: EtiquetaDelAcordado?,
    val etiquetaTexto: String?,
    val diaGrande: String,
    val dias: List<EtiquetaDeDia>,
    val pagosConsiderados: Int? = null
)

/** Un día solo lleva etiqueta con al menos tantos pagos. */
private const val PAGOS_MINIMOS_PARA_ETIQUETA = 2

/** Máximo de etiquetas de día. */
private const val MAXIMO_DE_ETIQUETAS = 3

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
 * | acordado / estimación | etiqueta | día grande | etiquetas de día |
 * |---|---|---|---|
 * | = principal | `✓ Acordado` | principal | sí |
 * | ≠ principal | `Acordó sábado` (naranja) | principal | sí |
 * | solo acordado | `Acordado al vender` | el acordado | no |
 * | solo estimación | ninguna | principal | sí |
 * | empate + acordado | `Acordado al vender` | el acordado | sí, ninguna en verde |
 * | empate sin acordado | ninguna | `Distintos días` | sí, ninguna en verde |
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
    dias = emptyList()
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
        dias = etiquetasDeLosDias(estimacion, hayPrincipal = true),
        pagosConsiderados = estimacion.pagosConsiderados
    )
}

private fun conEmpate(acordado: DiaAcordado?, estimacion: EstimacionDelDia) = FranjaDeCobro(
    etiqueta = acordado?.let { EtiquetaDelAcordado.AL_VENDER },
    etiquetaTexto = acordado?.let { "Acordado al vender" },
    diaGrande = acordado?.textoGrande() ?: DISTINTOS_DIAS,
    dias = etiquetasDeLosDias(estimacion, hayPrincipal = false),
    pagosConsiderados = estimacion.pagosConsiderados
)

/**
 * Hasta [MAXIMO_DE_ETIQUETAS] días con al menos [PAGOS_MINIMOS_PARA_ETIQUETA] pagos,
 * de más a menos (empate: orden de la semana); la primera es la principal solo si
 * [hayPrincipal]. Sin "otros": los días sueltos no llevan etiqueta.
 */
internal fun etiquetasDeLosDias(
    estimacion: EstimacionDelDia,
    hayPrincipal: Boolean
): List<EtiquetaDeDia> = estimacion.dias
    .filter { it.pagos >= PAGOS_MINIMOS_PARA_ETIQUETA }
    .sortedWith(compareByDescending<PagosDelDia> { it.pagos }.thenBy { it.dia.ordinal })
    .take(MAXIMO_DE_ETIQUETAS)
    .mapIndexed { i, d -> EtiquetaDeDia(d.dia.abreviatura, d.pagos, i == 0 && hayPrincipal) }
