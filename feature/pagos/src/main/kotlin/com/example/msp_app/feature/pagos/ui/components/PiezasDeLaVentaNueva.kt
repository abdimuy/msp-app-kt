package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.CuotaDeLaVenta
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Lo que pinta [RenglonDeVenta] en una venta SIN ningún abono (decisión del
 * dueño, 2026-10-05, mock "Decidido · v3"): aquí y no en `PiezasDeLaLista.kt`
 * porque meterlo ahí empujaba ese archivo a 11 funciones declaradas —el tope
 * de `TooManyFunctions`— y partir por "qué pinta cada slot del renglón" es una
 * costura tan válida como partir por tipo de pieza, que es como ya está
 * particionado el resto del paquete (`ControlSegmentado.kt`,
 * `OcultosDelSegmentado.kt`, …).
 *
 * `internal` y no `private`: [ChipDePrimerAbono] y [TextoDeVendida] los llama
 * `RenglonDeVenta` desde el otro archivo, y [textoVendida] lo fija un test
 * unitario sin montar Compose — mismo criterio que ya usa
 * [ControlSegmentado].
 */
@Composable
internal fun ChipDePrimerAbono(cuota: CuotaDeLaVenta, montosOcultos: Boolean) {
    val brand = MspTheme.colors.brand
    Box(
        modifier = Modifier
            .border(BORDE_DEL_CHIP_DE_PRIMER_ABONO, brand, MspTheme.shapes.chip)
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
    ) {
        Text(
            text = PREFIJO_PRIMER_ABONO +
                if (montosOcultos) MASKED_MONEY else formatMoneyMxn(cuota.esperado.amount),
            style = MspTheme.type.captionStrong,
            color = brand,
            maxLines = 1,
            // En el `Text`, no en el `Box` que lo envuelve: es el mismo patrón que
            // `DISTANCIA_DEL_CLIENTE_TAG` — el tag va sobre el nodo que de verdad
            // tiene el texto, para que `assertTextEquals` lo lea sin depender de
            // cómo Compose decida fusionar (o no) la semántica del envolvente.
            modifier = Modifier.testTag(CHIP_DE_PRIMER_ABONO_TAG)
        )
    }
}

/**
 * "Vendida hoy" · "Vendida ayer" · "Vendida hace N días" · "Vendida 14 ene
 * 2026": lo que remplaza a `PastillaDeAtrasos` en una venta sin ningún abono
 * (decisión del dueño, 2026-10-05). No hay atraso que contar —nadie ha venido
 * a cobrar todavía—, así que el dato útil es cuándo se vendió, no cuándo se
 * cobró.
 *
 * Texto apagado ([MspTheme.colors.onSurfaceMuted]), no un chip: a diferencia
 * de [ChipDePrimerAbono] esto no es algo que la pantalla "espera" — es un
 * hecho pasado, y los hechos pasados de esta tarjeta ya se pintan así (ver
 * "Últ. pago" en `EncabezadoDeCliente`).
 *
 * No pinta nada si [fechaVenta] es `null`: no hay fecha que decir, y una
 * ausencia no es un hueco en blanco que valga la pena reservar.
 */
@Composable
internal fun TextoDeVendida(fechaVenta: LocalDate?, hoy: LocalDate) {
    val texto = textoVendida(fechaVenta, hoy) ?: return
    Text(
        text = texto,
        style = MspTheme.type.caption,
        color = MspTheme.colors.onSurfaceMuted,
        maxLines = 1,
        modifier = Modifier.testTag(TEXTO_DE_VENDIDA_TAG)
    )
}

/**
 * El núcleo puro de [TextoDeVendida]: dos fechas, cero reloj, cero `@Composable`.
 *
 * **Escalón propio, y no [com.example.msp_app.core.common.time.TiempoRelativo]
 * a propósito** (decisión del dueño, 2026-10-05): aquella pasa a "semanas" a
 * partir del día 7 porque ahí vive la cobranza; ésta habla de la venta, no del
 * cobro, y el dueño pidió el conteo exacto en días corridos hasta
 * [DIAS_MAX_EN_NUMERO] — "hace 22 días" ubica la venta en el mes igual de bien
 * que "hace 3 semanas", y sin la palabra nueva que aprender. Más allá de ese
 * tope el número deja de ser legible de un vistazo y se cae a la fecha
 * completa.
 */
internal fun textoVendida(fechaVenta: LocalDate?, hoy: LocalDate): String? {
    if (fechaVenta == null) return null
    val dias = ChronoUnit.DAYS.between(fechaVenta, hoy)
    return VENDIDA_PREFIJO + when {
        dias <= 0 -> "hoy"
        dias == 1L -> "ayer"
        dias <= DIAS_MAX_EN_NUMERO -> "hace $dias días"
        else -> fechaVenta.format(FECHA_COMPLETA_DE_VENTA)
    }
}

/** `testTag` del chip "Primer abono $X" — sólo en una venta sin abonos. */
const val CHIP_DE_PRIMER_ABONO_TAG: String = "pagos_primer_abono"

/** `testTag` de "Vendida …" — sólo en una venta sin abonos. */
const val TEXTO_DE_VENDIDA_TAG: String = "pagos_texto_vendida"

private val BORDE_DEL_CHIP_DE_PRIMER_ABONO = 1.5.dp

private const val PREFIJO_PRIMER_ABONO = "Primer abono "

private const val VENDIDA_PREFIJO = "Vendida "

/** El tope de días corridos antes de caer a la fecha completa. Ver [textoVendida]. */
private const val DIAS_MAX_EN_NUMERO = 30L

private val FECHA_COMPLETA_DE_VENTA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", BUSINESS_LOCALE)
