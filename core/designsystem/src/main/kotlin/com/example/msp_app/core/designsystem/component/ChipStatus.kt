package com.example.msp_app.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.msp_app.core.designsystem.theme.MspColors

/**
 * Estado semántico de un [MspStatusChip]. Cada valor codifica un estado de
 * cobranza con **tres** portadores redundantes de significado — color de
 * contenido, tint de fondo e ícono — nunca el color solo (regla dura de
 * accesibilidad, spec §2.1 / §5, ver [MspStatusChip]).
 */
enum class ChipStatus {
    /** Pagado — verde + check. */
    Paid,

    /** Parcial — ámbar + círculo mitad-lleno. */
    Partial,

    /** Vencido — rojo + warning. */
    Overdue,

    /** Pendiente — neutro + círculo vacío. */
    Pending,

    /** Promesa de pago — violeta + reloj. */
    Promise
}

/**
 * Color de contenido (texto + ícono) del estado, resuelto desde [MspColors]
 * (fuente única de color). Cada estado tiene su propio matiz semántico,
 * independiente de la marca.
 */
internal fun ChipStatus.contentColor(colors: MspColors): Color = when (this) {
    ChipStatus.Paid -> colors.statusPaid
    ChipStatus.Partial -> colors.statusPartial
    ChipStatus.Overdue -> colors.statusOverdue
    ChipStatus.Pending -> colors.statusPending
    ChipStatus.Promise -> colors.promise
}

/** Tint de fondo (pill) del estado, resuelto desde [MspColors]. */
internal fun ChipStatus.tintColor(colors: MspColors): Color = when (this) {
    ChipStatus.Paid -> colors.statusPaidTint
    ChipStatus.Partial -> colors.statusPartialTint
    ChipStatus.Overdue -> colors.statusOverdueTint
    ChipStatus.Pending -> colors.statusPendingTint
    ChipStatus.Promise -> colors.promiseTint
}

/** Ícono por defecto del estado (de [MspIcons]) — el tercer portador. */
internal fun ChipStatus.icon(): ImageVector = when (this) {
    ChipStatus.Paid -> MspIcons.Paid
    ChipStatus.Partial -> MspIcons.Partial
    ChipStatus.Overdue -> MspIcons.Overdue
    ChipStatus.Pending -> MspIcons.Pending
    ChipStatus.Promise -> MspIcons.Promise
}
