package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.ui.EdicionDeLaFicha

/**
 * La hoja que edita la ficha. **No renderiza nada con [edicion] en `null`**,
 * igual que `PrintSheet` de `:feature:collectionReport`: la hoja la manda el
 * estado, no un `remember` local que se perdería en la primera recomposición
 * rara.
 *
 * El cuerpo vive en [CuerpoDeLaFicha], fuera de este envoltorio, para que
 * Roborazzi lo pueda capturar — `captureRoboImage` toma la ventana raíz y no el
 * `Popup` donde Material monta la hoja.
 *
 * **No dupica pantalla:** editar la ficha ocurre encima del detalle de cliente,
 * que es donde el cobrador ya está parado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDeLaFicha(
    edicion: EdicionDeLaFicha?,
    onCerrar: () -> Unit,
    onSenal: (SenalDeFicha) -> Unit,
    onNota: (String) -> Unit,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (edicion == null) return
    // El `SheetState` se crea aquí dentro y no llega por parámetro: es un tipo
    // experimental de Material 3, y ponerlo en la firma obligaría a cada
    // llamador —incluidos los goldens— a repetir el `@OptIn`.
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MspTheme.colors.surface,
        contentColor = MspTheme.colors.onSurface
    ) {
        CuerpoDeLaFicha(
            senales = edicion.senales,
            nota = edicion.nota,
            guardando = edicion.guardando,
            fallo = edicion.fallo,
            onSenal = onSenal,
            onNota = onNota,
            onGuardar = onGuardar
        )
    }
}
