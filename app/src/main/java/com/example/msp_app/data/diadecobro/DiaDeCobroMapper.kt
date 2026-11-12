package com.example.msp_app.data.diadecobro

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.data.api.services.diadecobro.AcordadoDto
import com.example.msp_app.data.api.services.diadecobro.DiaDeCobroResponse
import com.example.msp_app.data.api.services.diadecobro.DiaDeCobroVentaDto
import com.example.msp_app.data.api.services.diadecobro.EstimacionDto
import com.example.msp_app.feature.pagos.domain.DiaAcordado
import com.example.msp_app.feature.pagos.domain.DiaDeCobroDeLaVenta
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana
import com.example.msp_app.feature.pagos.domain.EstimacionDelDia
import com.example.msp_app.feature.pagos.domain.PagosDelDia
import java.time.Instant

private const val PRIMER_DIA_DEL_MES = 1
private const val ULTIMO_DIA_DEL_MES = 31

/**
 * La venta [doctoCcId] de la respuesta, ya en dominio, o `null` si la respuesta
 * no la trae o no trae con qué pintar nada de ella.
 *
 * **Lo que no se entiende se descarta, no se adivina**: un día que no es uno de
 * los siete, un `tipo` desconocido o una estimación sin días valen `null`. Es
 * mejor no mostrar el dato que mostrar uno inventado (la estimación nunca se
 * presenta como hecho).
 */
fun DiaDeCobroResponse.deLaVenta(doctoCcId: Int): DiaDeCobroDeLaVenta? =
    ventas.orEmpty().firstOrNull { it.docto_cc_id == doctoCcId }
        ?.toDomain(calculadoEn())

/** Cuándo calculó el servidor, o `null` si no lo dijo o no se entiende. */
fun DiaDeCobroResponse.calculadoEn(): Instant? = AppTime.parseWireFormatOrNull(calculado_en)

internal fun DiaDeCobroVentaDto.toDomain(calculadoEn: Instant?): DiaDeCobroDeLaVenta? {
    val acordado = acordado?.toDomain()
    val estimacion = estimacion?.toDomain()
    if (acordado == null && estimacion == null) return null
    return DiaDeCobroDeLaVenta(acordado, estimacion, calculadoEn)
}

internal fun AcordadoDto.toDomain(): DiaAcordado? = when (tipo?.trim()?.lowercase()) {
    "semana" -> DiaDeLaSemana.deLaClave(runCatching { dia?.asString }.getOrNull())
        ?.let { DiaAcordado.DeLaSemana(it) }

    "mes" -> runCatching { dia?.asInt }.getOrNull()
        ?.takeIf { it in PRIMER_DIA_DEL_MES..ULTIMO_DIA_DEL_MES }
        ?.let { DiaAcordado.DelMes(it) }

    else -> null
}

internal fun EstimacionDto.toDomain(): EstimacionDelDia? {
    val considerados = pagos_considerados ?: return null
    val dias = dias.orEmpty().mapNotNull { d ->
        val dia = DiaDeLaSemana.deLaClave(d.dia) ?: return@mapNotNull null
        val pagos = d.pagos?.takeIf { it > 0 } ?: return@mapNotNull null
        PagosDelDia(dia, pagos)
    }
    if (dias.isEmpty()) return null
    val principalDicho = !principal.isNullOrBlank()
    val principalDia = DiaDeLaSemana.deLaClave(principal)
    // Un principal que SÍ vino pero no es un día no se vuelve "empate": la
    // estimación entera no se entiende y se descarta.
    if (principalDicho && principalDia == null) return null
    return EstimacionDelDia(considerados, principalDia, dias)
}
