package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod

private val METHOD_PILL_VERTICAL_PADDING = 2.dp

/**
 * Pill de método de cobro (mockup `.m`): color + texto, NUNCA solo color — "Efectivo" en
 * verde `statusPaid`/`statusPaidTint`, "Transfer." en `brand`/`brandTint` (los únicos dos
 * que trae el mockup). Cheque/Otro no están en el mockup (parked, `PaymentMethod` los
 * clasifica aparte, ver su KDoc) pero SÍ pueden llegar del dominio, así que tienen un
 * matiz propio en vez de dejar el `when` no exhaustivo; Condonado es puramente defensivo —
 * la guarda anti-fuga de `ReportAggregator` ya excluye ese método de la lista de pagos.
 *
 * `internal` (no `private`): lo consume [DetailList] (`PaymentRow`, otro archivo) — se separó
 * a este archivo propio para no cruzar el umbral `TooManyFunctions` de detekt en `DetailList.kt`
 * (mismo criterio que separó `ReportSheetContent` de `ReportSheets`).
 *
 * `MspStatusChip` (`:core:designsystem`) NO se reutiliza aquí a propósito: su paleta de
 * [com.example.msp_app.core.designsystem.component.ChipStatus] no tiene un matiz "brand"
 * (el que necesita Transferencia) y su ícono obligatorio se apartaría del `.m` del mockup
 * (solo color + texto, sin ícono) — mismo criterio que distingue el dot de [DuoTiles] del
 * `MspStatusChip`.
 */
@Composable
internal fun MethodPill(method: PaymentMethod, modifier: Modifier = Modifier) {
    Text(
        text = method.pillLabel(),
        style = MspTheme.type.captionStrong,
        color = method.pillContentColor(),
        modifier = modifier
            .clip(MspTheme.shapes.chip)
            .background(method.pillTintColor())
            .padding(horizontal = MspTheme.spacing.sm, vertical = METHOD_PILL_VERTICAL_PADDING)
    )
}

private fun PaymentMethod.pillLabel(): String = when (this) {
    PaymentMethod.EFECTIVO -> "Efectivo"
    PaymentMethod.TRANSFERENCIA -> "Transfer."
    PaymentMethod.CHEQUE -> "Cheque"
    PaymentMethod.CONDONACION -> "Condonado"
    PaymentMethod.OTRO -> "Otro"
}

@Composable
private fun PaymentMethod.pillContentColor(): Color = when (this) {
    PaymentMethod.EFECTIVO -> MspTheme.colors.statusPaid
    PaymentMethod.TRANSFERENCIA -> MspTheme.colors.brand
    PaymentMethod.CHEQUE -> MspTheme.colors.statusInfo
    PaymentMethod.CONDONACION -> MspTheme.colors.promise
    PaymentMethod.OTRO -> MspTheme.colors.onSurfaceMuted
}

@Composable
private fun PaymentMethod.pillTintColor(): Color = when (this) {
    PaymentMethod.EFECTIVO -> MspTheme.colors.statusPaidTint
    PaymentMethod.TRANSFERENCIA -> MspTheme.colors.brandTint
    PaymentMethod.CHEQUE -> MspTheme.colors.statusInfoTint
    PaymentMethod.CONDONACION -> MspTheme.colors.promiseTint
    PaymentMethod.OTRO -> MspTheme.colors.progressTrack
}
