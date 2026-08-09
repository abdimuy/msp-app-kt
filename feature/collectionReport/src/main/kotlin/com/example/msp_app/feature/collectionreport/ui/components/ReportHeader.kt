package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme

/** Icon-surface del menú — mismo 40dp que `MspPrivacyEyeToggle`/`MspThemeToggle` (kollect §7.2). */
private val MENU_BUTTON_SIZE = 40.dp

/**
 * Header propio del reporte (mockup `.hdr`, NO un `TopAppBar` M3): ícono de menú (abre el
 * drawer contenedor — la navegación real la cablea Task 10) + bloque título "Cobranza" +
 * subtítulo "Reporte · [cobrador]" + los dos toggles del design system (privacidad, tema).
 *
 * El ícono de menú usa [MspCard] (no `MspSurface`, `internal` fuera de
 * `:core:designsystem`) del mismo tamaño/forma que los toggles vecinos, con
 * `Icons.Filled.Menu` (`material-icons-core`, mismo criterio anti-`material-icons-extended`
 * que `MspIcons` del design system).
 */
@Composable
fun ReportHeader(
    cobrador: String,
    masked: Boolean,
    darkTheme: Boolean,
    onMenuClick: () -> Unit,
    onPrivacyToggle: () -> Unit,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspCard(
            modifier = Modifier.size(MENU_BUTTON_SIZE),
            shape = MspTheme.shapes.control,
            onClick = onMenuClick
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = null,
                    tint = MspTheme.colors.onSurfaceMuted
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Cobranza",
                style = MspTheme.type.greeting,
                color = MspTheme.colors.onSurface
            )
            Text(
                text = "Reporte · $cobrador",
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
        }

        MspPrivacyEyeToggle(masked = masked, onToggle = onPrivacyToggle)
        MspThemeToggle(darkTheme = darkTheme, onToggle = onThemeToggle)
    }
}
