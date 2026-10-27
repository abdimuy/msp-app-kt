package com.example.msp_app.feature.visitas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono

/** Tag del aviso de pendientes, para las pruebas. */
const val AVISO_DE_PENDIENTES_TAG: String = "aviso_de_pendientes"

private val ALTO_TOCABLE = 48.dp

/**
 * Una línea de texto en Inicio: "N sin enviar · no cambies de teléfono"
 * (E-APP-096). Sin caja, sin botón, sin tarjeta: el área de toque llega a
 * [ALTO_TOCABLE] con relleno alrededor del texto, no pintando una superficie.
 * Con N = 0 no se pinta nada. Al tocarla, [onClick] lleva a los pendientes.
 *
 * Trae su propio [MspTheme] porque Inicio todavía no vive dentro de él (mismo
 * criterio que `TarjetaDeCercano`).
 */
@Composable
fun AvisoDePendientes(
    pendientes: PendientesDelTelefono,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val texto = pendientes.textoDelAviso() ?: return
    MspTheme(animateColors = false) {
        Text(
            text = texto,
            style = MspTheme.type.caption,
            color = MspTheme.colors.danger,
            modifier = modifier
                .testTag(AVISO_DE_PENDIENTES_TAG)
                .fillMaxWidth()
                .heightIn(min = ALTO_TOCABLE)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .wrapContentHeight()
        )
    }
}
