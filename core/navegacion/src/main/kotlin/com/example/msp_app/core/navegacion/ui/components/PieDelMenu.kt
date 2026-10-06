package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu
import com.example.msp_app.core.navegacion.ui.FILA_DEL_MENU_TAG
import com.example.msp_app.core.navegacion.ui.ID_CONFIGURACION

/**
 * Pie fijo (spec §2.1.4): Configuración + versión. [aviso] es la ranura del
 * banner de actualización de `:app` (decisión D12 del plan). [version] llega ya
 * armada porque `BuildConfig` sólo existe en `:app` (spec §3.3).
 */
@Composable
fun PieDelMenu(version: String, onConfiguracion: () -> Unit, aviso: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.sm)
    ) {
        aviso()
        FilaDelMenu(
            etiqueta = "Configuración",
            icono = IconoDelMenu.CONFIGURACION,
            activo = false,
            tag = FILA_DEL_MENU_TAG + ID_CONFIGURACION,
            onClick = onConfiguracion
        )
        Text(
            text = version,
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = MspTheme.spacing.sm)
        )
    }
}
