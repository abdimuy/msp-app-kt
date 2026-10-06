package com.example.msp_app.feature.configuracion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.example.msp_app.core.designsystem.theme.MspTheme

const val CERRAR_SESION_TAG: String = "configuracion_cerrar_sesion"

/**
 * "Cerrar sesión" con confirmación, movido desde el cajón legado (spec §2.1.4).
 * El diálogo es el de `DrawerContainer` reubicado con sus mismos textos, no
 * reescrito.
 */
@Composable
fun SesionSection(onCerrarSesion: () -> Unit, modifier: Modifier = Modifier) {
    var confirmando by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = "Sesión",
            style = MspTheme.type.sectionHeader,
            color = MspTheme.colors.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MspTheme.spacing.sm)
                .heightIn(min = MspTheme.spacing.touchTarget)
                .clip(MspTheme.shapes.control)
                .background(MspTheme.colors.surface)
                .clickable { confirmando = true }
                .testTag(CERRAR_SESION_TAG)
                .padding(horizontal = MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cerrar sesión",
                style = MspTheme.type.bodyStrong,
                color = MspTheme.colors.danger
            )
        }
    }
    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro de que deseas cerrar sesión?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = false
                    onCerrarSesion()
                }) { Text("Sí") }
            },
            dismissButton = {
                TextButton(onClick = { confirmando = false }) { Text("No") }
            }
        )
    }
}
