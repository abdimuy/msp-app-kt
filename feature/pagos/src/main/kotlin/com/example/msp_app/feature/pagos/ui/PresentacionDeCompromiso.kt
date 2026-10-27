package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.pagos.domain.model.CompromisoDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDeCompromiso
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.TipoDeCompromiso
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * Cómo se pinta el compromiso vigente de una venta (plan visitas P2, Tarea 20;
 * spec §5.7 B, C, E, F; E-APP-115). Funciones puras: el estado ya viene
 * derivado y aquí solo se rotula, como en el escritorio (T21). "Hoy" solo
 * decide si el día se escribe "Hoy".
 *
 *  - F: una cita sin hora es una cita con su día ("Vie 23 oct, sin hora"),
 *    trato CITA (violeta); nunca REGRESAS ni "Sin hora, regresas".
 *  - B: con abonos, el chip de dinero sigue y el compromiso va debajo.
 *  - C: sin abonos, "Primer abono $X" sigue y el compromiso va debajo.
 *  - E: el detalle de la venta tiene su renglón.
 *  - `cerrada_sin_cumplir` y la reemplazada (sin entrada) no se pintan.
 *
 * Los casos por estado × hora/monto × abonos están en
 * `src/test/resources/presentacion-compromisos/`.
 */

/** Lo que la pantalla pinta de un compromiso. Mayúscula inicial, sin punto. */
@Immutable
data class CompromisoEnPantalla(
    val titulo: String,
    val detalle: String,
    val trato: TratoDelEstado,
    val icono: ImageVector
)

/** Lo que ocupa el primer lugar de la tarjeta: el chip de estado o "Primer abono". */
enum class PiezaPrincipal { ESTADO, PRIMER_ABONO }

/** Las piezas de estado de la tarjeta de una venta en la lista. */
data class PiezasDeLaTarjeta(val principal: PiezaPrincipal, val compromiso: CompromisoEnPantalla?)

/** El día como se lee en la ruta: "vie 23 oct" (luego con mayúscula), con el mismo mes que el resto de la app. */
private val DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", BUSINESS_LOCALE)
private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", BUSINESS_LOCALE)
private const val CENTAVOS = 2

/** El compromiso [c] para la pantalla, o `null` si no se pinta (sin compromiso o cerrado). */
fun presentacionDe(
    c: CompromisoDeLaVenta?,
    hoy: LocalDate,
    montosOcultos: Boolean = false
): CompromisoEnPantalla? {
    if (c == null || c.estado == EstadoDeCompromiso.CERRADA_SIN_CUMPLIR) return null
    // Mayúscula inicial (textos, decisión del dueño del 2026-10-20): "Vie 23 oct".
    val dia = if (c.dia == hoy) {
        "Hoy"
    } else {
        DIA.format(c.dia).replaceFirstChar {
            it.titlecase(BUSINESS_LOCALE)
        }
    }
    return when (c.tipo) {
        TipoDeCompromiso.CITA -> cita(c, dia)
        TipoDeCompromiso.PROMESA -> promesa(c, dia, montosOcultos)
    }
}

/**
 * La tarjeta de la lista: el dinero (o "Primer abono") manda, y el compromiso
 * va además, salvo que el chip de [estadoDelChip] ya diga ese mismo compromiso
 * ([yaLoDiceElEstado]): repetirlo debajo es ruido (textos minimalistas, §7).
 * "Primer abono" no dice nada del compromiso, así que ahí siempre se pinta.
 */
fun piezasDeLaTarjeta(
    conAbonos: Boolean,
    compromiso: CompromisoDeLaVenta?,
    hoy: LocalDate,
    montosOcultos: Boolean = false,
    estadoDelChip: EstadoDelPeriodo? = null
): PiezasDeLaTarjeta {
    val principal = if (conAbonos) PiezaPrincipal.ESTADO else PiezaPrincipal.PRIMER_ABONO
    val repetido = principal == PiezaPrincipal.ESTADO && compromiso != null &&
        yaLoDiceElEstado(estadoDelChip, compromiso)
    return PiezasDeLaTarjeta(
        principal,
        if (repetido) null else presentacionDe(compromiso, hoy, montosOcultos)
    )
}

/**
 * El renglón del detalle de la venta: el compromiso, salvo que el estado en
 * grande ([estado]) ya lo diga ([yaLoDiceElEstado]).
 */
fun renglonDelDetalle(
    estado: EstadoDelPeriodo,
    compromiso: CompromisoDeLaVenta?,
    hoy: LocalDate,
    montosOcultos: Boolean = false
): CompromisoEnPantalla? = if (compromiso != null && yaLoDiceElEstado(estado, compromiso)) {
    null
} else {
    presentacionDe(
        compromiso,
        hoy,
        montosOcultos
    )
}

/**
 * ¿El estado de la semana ya pinta este MISMO compromiso? Solo cuando el
 * compromiso sigue vigente (el chip dice lo mismo: "Prometió $500 el 4 sept",
 * "Cita 4 sept 16:30") y es del mismo día —y la misma hora, si es cita—. Una
 * promesa incumplida o cumplida agrega el desenlace, y un chip sin día
 * ("Cita sin hora") no dice cuándo: en esos casos la línea sí va.
 */
fun yaLoDiceElEstado(estado: EstadoDelPeriodo?, compromiso: CompromisoDeLaVenta): Boolean {
    if (estado == null) return false
    val vigente = compromiso.estado == EstadoDeCompromiso.VIGENTE_FUTURA ||
        compromiso.estado == EstadoDeCompromiso.VIGENTE_HOY
    if (!vigente) return false
    return when (compromiso.tipo) {
        TipoDeCompromiso.PROMESA ->
            estado.estado == EstadoCuenta.PROMETIO_PROXIMA && estado.fechaPromesa == compromiso.dia
        TipoDeCompromiso.CITA ->
            estado.estado == EstadoCuenta.CITA_A_UNA_HORA && estado.fechaCita == compromiso.dia &&
                estado.horaCita == compromiso.hora
    }
}

private fun cita(c: CompromisoDeLaVenta, dia: String): CompromisoEnPantalla {
    val hora = c.hora?.let { HORA.format(it) }
    val conHora = dia + ", " + (hora ?: "sin hora")
    return when (c.estado) {
        EstadoDeCompromiso.CUMPLIDA, EstadoDeCompromiso.CITA_CUMPLIDA -> CompromisoEnPantalla(
            "Cita cumplida",
            dia + hora?.let { ", $it" }.orEmpty(),
            TratoDelEstado.PAGADO,
            PagosIconos.Pago
        )
        EstadoDeCompromiso.INCUMPLIDA ->
            CompromisoEnPantalla(
                "Cita incumplida",
                conHora,
                TratoDelEstado.REGRESAS,
                PagosIconos.Vuelvo
            )
        else -> CompromisoEnPantalla(
            "Cita",
            conHora,
            TratoDelEstado.CITA,
            if (hora == null) PagosIconos.CitaSinHora else PagosIconos.Cita
        )
    }
}

private fun promesa(
    c: CompromisoDeLaVenta,
    dia: String,
    montosOcultos: Boolean
): CompromisoEnPantalla {
    val monto = c.montoCentavos?.let {
        if (montosOcultos) MASKED_MONEY else formatMoneyMxn(BigDecimal.valueOf(it, CENTAVOS))
    }
    val detalle = dia + monto?.let { ", $it" }.orEmpty()
    return when (c.estado) {
        EstadoDeCompromiso.CUMPLIDA, EstadoDeCompromiso.CITA_CUMPLIDA ->
            CompromisoEnPantalla(
                "Promesa cumplida",
                detalle,
                TratoDelEstado.PAGADO,
                PagosIconos.Pago
            )
        EstadoDeCompromiso.INCUMPLIDA ->
            CompromisoEnPantalla(
                "Promesa incumplida",
                detalle,
                TratoDelEstado.REGRESAS,
                PagosIconos.Vuelvo
            )
        else -> CompromisoEnPantalla(
            "Promesa",
            detalle,
            TratoDelEstado.DIFERIDO,
            PagosIconos.Prometio
        )
    }
}
