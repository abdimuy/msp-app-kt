package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowRightLeft
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.FileCheck
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Lucide
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod

private val METHOD_PILL_VERTICAL_PADDING = 2.dp

// 38dp — mismo footprint EXACTO que el avatar de iniciales que reemplaza ([MspInitialsAvatar],
// default 38dp), no el ~40dp aproximado del brief: mantener el ancho idéntico evita reabrir el
// presupuesto de layout de `DetailList.PaymentRow`/`ReportSheets.SheetRow`, ya afinado para el
// gate duro `MoneyNoTruncationTest` ("el dinero reflowea, no se trunca").
private val METHOD_TILE_DEFAULT_SIZE = 38.dp
private val METHOD_TILE_ICON_SIZE = 20.dp

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

/**
 * Tile de método de cobro (fix de dispositivo, Task 1): reemplaza el avatar de iniciales
 * decorativo de las filas de pago (`DetailList.PaymentRow`, `ReportSheets.SheetRow`) — el
 * nombre del cliente ya va en texto al lado, así que unas iniciales repetían esa misma
 * información; el MÉTODO de cobro (efectivo/transferencia/…) sí es dato nuevo en esa
 * posición. Cuadrado redondeado `shapes.control` (12dp, mismo radio que [MspInitialsAvatar])
 * tintado con el MISMO par color/tint que [MethodPill] ya usa para su pill de texto — una
 * sola fuente de verdad de color por método, no dos paletas divergentes.
 */
@Composable
internal fun MethodTile(
    method: PaymentMethod,
    modifier: Modifier = Modifier,
    size: Dp = METHOD_TILE_DEFAULT_SIZE
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MspTheme.shapes.control)
            .background(method.pillTintColor()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = method.tileIcon(),
            contentDescription = method.pillLabel(),
            tint = method.pillContentColor(),
            modifier = Modifier.size(METHOD_TILE_ICON_SIZE)
        )
    }
}

/**
 * Ícono Lucide por método (Task 1 del brief): efectivo -> billete, transferencia -> flechas
 * cruzadas — los dos únicos pedidos explícitamente. Cheque/Condonado/Otro no están en el
 * mockup (mismo parked de [pillTintColor]) — se eligió el Lucide más cercano al concepto:
 * [com.composables.icons.lucide.FileCheck] (documento validado) para cheque,
 * [com.composables.icons.lucide.HandCoins] (condonar = "regalar" el saldo) para condonado, y
 * [com.composables.icons.lucide.Coins] (genérico de dinero) para cualquier otro método.
 */
private fun PaymentMethod.tileIcon(): ImageVector = when (this) {
    PaymentMethod.EFECTIVO -> Lucide.Banknote
    PaymentMethod.TRANSFERENCIA -> Lucide.ArrowRightLeft
    PaymentMethod.CHEQUE -> Lucide.FileCheck
    PaymentMethod.CONDONACION -> Lucide.HandCoins
    PaymentMethod.OTRO -> Lucide.Coins
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
