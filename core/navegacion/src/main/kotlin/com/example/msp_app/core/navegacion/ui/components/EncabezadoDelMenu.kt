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
import androidx.compose.ui.text.style.TextOverflow
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.application.TextoDelEnvio
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.ui.ENVIO_DEL_MENU_TAG
import com.example.msp_app.core.navegacion.ui.NOMBRE_DEL_MENU_TAG

/**
 * La cabecera fija del menú. Sin campo de rol (ruling R4 del plan: el dueño
 * decidió no mostrarlo). [cabecera].nombreDeZona se pinta tal cual llega si no
 * es `null`; si es `null` no se pinta nada — ni un respaldo "Zona N" (ruling
 * R3). Los botones de tema y de ocultar/mostrar dinero (ruling R2, decisión del
 * dueño) llegan por parámetro para que la hoja siga siendo pura.
 *
 * ## Una fila, con los botones chicos (decisión del dueño, 2026-10-07)
 *
 * Antes los dos botones eran cajas de 56 dp en una fila propia, encima del avatar
 * (ruling R11). El dueño lo vio en su Galaxy A25 y pidió que no ocuparan una fila:
 * ahora van a la derecha del nombre, **sin caja** (`boxed = false`: sólo el glifo,
 * regla "acción pequeña sin caja"), cada uno con [ALTO_DE_LA_OPCION] de área tocable
 * invisible. Para que al nombre no le falte ancho, el avatar baja a 40 dp y la
 * orilla derecha se reduce a `xs`: el área tocable de los botones ya deja su propio
 * margen visual alrededor del glifo. El nombre y la zona recortan con "…".
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
            .padding(
                start = MspTheme.spacing.md,
                end = MspTheme.spacing.xs,
                top = MspTheme.spacing.sm,
                bottom = MspTheme.spacing.xs
            ),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MspInitialsAvatar(
                initials = cabecera.iniciales,
                size = MspTheme.spacing.touchTarget - MspTheme.spacing.md
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = MspTheme.spacing.sm + MspTheme.spacing.xs)
                    .testTag(NOMBRE_DEL_MENU_TAG)
            ) {
                if (cabecera.nombre.isNotBlank()) {
                    Text(
                        text = cabecera.nombre,
                        style = MspTheme.type.name,
                        color = MspTheme.colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                cabecera.nombreDeZona?.let { zona ->
                    Text(
                        text = zona,
                        style = MspTheme.type.caption,
                        color = MspTheme.colors.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MspPrivacyEyeToggle(
                masked = montosOcultos,
                onToggle = onAlternarMontos,
                boxed = false,
                modifier = Modifier.size(ALTO_DE_LA_OPCION)
            )
            MspThemeToggle(
                darkTheme = temaOscuro,
                onToggle = onAlternarTema,
                boxed = false,
                modifier = Modifier.size(ALTO_DE_LA_OPCION)
            )
        }
        cabecera.envio?.let {
            // La orilla derecha del encabezado es `xs` por los botones; la tarjeta
            // recupera el margen `md` para quedar simétrica.
            TarjetaDeEnvio(it, Modifier.padding(end = MspTheme.spacing.md - MspTheme.spacing.xs))
        }
    }
}

/** El punto nunca va solo: siempre con su texto (spec §5, regla de `MspStatusChip`). */
@Composable
private fun TarjetaDeEnvio(envio: EstadoDeEnvio, modifier: Modifier = Modifier) {
    val punto = if (envio.estado == EstadoDeEnvio.Estado.TODO_ENVIADO) {
        MspTheme.colors.statusPaid
    } else {
        MspTheme.colors.statusPartial
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.tile)
            .background(MspTheme.colors.surface2)
            .testTag(ENVIO_DEL_MENU_TAG)
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm),
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
