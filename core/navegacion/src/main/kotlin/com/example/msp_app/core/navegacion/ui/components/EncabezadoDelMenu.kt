package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.application.TextoDelEnvio
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.ui.ENVIO_DEL_MENU_TAG

/**
 * La cabecera fija del menú. Sin campo de rol (ruling R4 del plan: el dueño
 * decidió no mostrarlo). [cabecera].nombreDeZona se pinta tal cual llega si no
 * es `null`; si es `null` no se pinta nada — ni un respaldo "Zona N" (ruling
 * R3). Los botones de tema y de ocultar/mostrar dinero (ruling R2, decisión del
 * dueño) llegan por parámetro para que la hoja siga siendo pura.
 */
@Composable
internal fun EncabezadoDelMenu(
    cabecera: CabeceraDelMenu,
    temaOscuro: Boolean,
    montosOcultos: Boolean,
    onAlternarTema: () -> Unit,
    onAlternarMontos: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
        ) {
            MspInitialsAvatar(initials = cabecera.iniciales, size = MspTheme.spacing.touchTarget)
            Column(Modifier.weight(1f)) {
                if (cabecera.nombre.isNotBlank()) {
                    Text(
                        text = cabecera.nombre,
                        style = MspTheme.type.name,
                        color = MspTheme.colors.onSurface
                    )
                }
                cabecera.nombreDeZona?.let { zona ->
                    Text(
                        text = zona,
                        style = MspTheme.type.caption,
                        color = MspTheme.colors.onSurfaceMuted
                    )
                }
            }
            MspPrivacyEyeToggle(
                masked = montosOcultos,
                onToggle = onAlternarMontos,
                modifier = Modifier.size(MspTheme.spacing.touchTarget)
            )
            MspThemeToggle(
                darkTheme = temaOscuro,
                onToggle = onAlternarTema,
                modifier = Modifier.size(MspTheme.spacing.touchTarget)
            )
        }
        cabecera.envio?.let { TarjetaDeEnvio(it) }
    }
}

/** El punto nunca va solo: siempre con su texto (spec §5, regla de `MspStatusChip`). */
@Composable
private fun TarjetaDeEnvio(envio: EstadoDeEnvio) {
    val punto = if (envio.estado == EstadoDeEnvio.Estado.TODO_ENVIADO) {
        MspTheme.colors.statusPaid
    } else {
        MspTheme.colors.statusPartial
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.tile)
            .background(MspTheme.colors.surface2)
            .testTag(ENVIO_DEL_MENU_TAG)
            .padding(MspTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Box(
            Modifier
                .size(MspTheme.spacing.sm)
                .clip(MspTheme.shapes.chip)
                .background(punto)
        )
        Text(
            text = TextoDelEnvio.completo(envio),
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurface
        )
    }
}
