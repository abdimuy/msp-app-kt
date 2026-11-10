package com.example.msp_app.feature.usodedatos

import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.datos.ClasificadorDeServicio
import com.example.msp_app.core.datos.UsoDelDia
import java.time.LocalDate

data class FilaDeServicio(val nombre: String, val bytes: Long)

/** Un periodo (hoy / últimos 7 días) ya sumado y listo para pintar. */
data class PeriodoDeUso(
    val total: Long,
    val movil: Long,
    val wifi: Long,
    val abierta: Long,
    val segundoPlano: Long,
    val servicios: List<FilaDeServicio>,
    /** Hay total pero ningún desglose por servicio (p. ej. el teléfono no dio el detalle). */
    val sinDetalle: Boolean
)

data class UsoDeDatosUiState(
    val cargando: Boolean = true,
    val hoy: PeriodoDeUso? = null,
    val semana: PeriodoDeUso? = null
) {
    /** Ni un byte en los 7 días: se pinta "Todavía no hay datos". */
    val vacio: Boolean get() = !cargando && hoy == null && semana == null
}

/** 1 MB = 1 000 000 B (como cuentan las compañías), un decimal, separador de miles. */
object FormatoDeMegas {
    private const val MEGA = 1_000_000.0
    fun mb(bytes: Long): String =
        String.format(BUSINESS_LOCALE, "%,.1f MB", maxOf(0L, bytes) / MEGA)
}

object ConstructorDeResumen {
    private const val DIAS_DE_LA_SEMANA = 7

    fun construir(dias: List<UsoDelDia>, hoy: LocalDate): UsoDeDatosUiState {
        val desde = hoy.minusDays((DIAS_DE_LA_SEMANA - 1).toLong()).toString()
        val hastaInclusive = hoy.toString()
        val semana = dias.filter { it.fecha in desde..hastaInclusive && it.tieneDatos }
        if (semana.isEmpty()) return UsoDeDatosUiState(cargando = false)
        val deHoy = semana.filter { it.fecha == hastaInclusive }
        return UsoDeDatosUiState(
            cargando = false,
            hoy = periodo(deHoy),
            semana = periodo(semana)
        )
    }

    private fun periodo(dias: List<UsoDelDia>): PeriodoDeUso {
        val total = dias.sumOf { it.total }
        val endpoints = dias.flatMap { it.endpoints.entries }
        val porServicio = endpoints.groupBy { ClasificadorDeServicio.servicioDe(it.key) }
            .map { (nombre, l) -> FilaDeServicio(nombre, l.sumOf { it.value.rx + it.value.tx }) }
            .filter { it.bytes > 0 }
            .toMutableList()
        val desglose = dias.sumOf { it.desglose }
        val sinDetalle = total > 0 && porServicio.isEmpty()
        if (!sinDetalle && total - desglose > 0) {
            porServicio += FilaDeServicio(ClasificadorDeServicio.MAPA_FIREBASE_Y_OTROS, total - desglose)
        }
        return PeriodoDeUso(
            total = total,
            movil = dias.sumOf { it.movil },
            wifi = dias.sumOf { it.wifi },
            abierta = dias.sumOf { it.primerPlano },
            segundoPlano = dias.sumOf { it.segundoPlano },
            servicios = porServicio.sortedByDescending { it.bytes },
            sinDetalle = sinDetalle
        )
    }
}
