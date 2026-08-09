package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspBentoTile
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.TileUi

/**
 * Duo Efectivo/Transferencia del tablero (mockup `.duo`): dos [MspBentoTile] a partes
 * iguales. El dot de color de cada tile (`.tk .dot` — un `Box` simple dentro de
 * [MspBentoTile], NO un `MspStatusChip`, ver el gotcha del brief de esta tarea) distingue
 * el método: verde `statusPaid` para Efectivo, `brand` para Transferencia — la misma
 * paleta que usa el method pill de [DetailList] para esos dos métodos.
 *
 * [masked] enmascara el monto de AMBOS tiles (regla de privacidad del piloto, propagada
 * tal cual a [MspBentoTile]). `onEfectivoClick`/`onTransferenciaClick` solo informan el
 * toque — el cableado real a `CollectionReportViewModel.openSheet` vive en
 * `CollectionReportScreen`; este componente no conoce el `ViewModel` ni `SheetKind`, mismo
 * criterio que `HeroSection.onClick`.
 */
@Composable
fun DuoTiles(
    efectivo: TileUi,
    transferencia: TileUi,
    masked: Boolean,
    onEfectivoClick: () -> Unit,
    onTransferenciaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspBentoTile(
            dotColor = MspTheme.colors.statusPaid,
            label = efectivo.label,
            amount = efectivo.amount.amount,
            subLine = "${efectivo.count} pagos",
            masked = masked,
            onClick = onEfectivoClick,
            modifier = Modifier.weight(1f)
        )
        MspBentoTile(
            dotColor = MspTheme.colors.brand,
            label = transferencia.label,
            amount = transferencia.amount.amount,
            subLine = "${transferencia.count} pagos",
            masked = masked,
            onClick = onTransferenciaClick,
            modifier = Modifier.weight(1f)
        )
    }
}
