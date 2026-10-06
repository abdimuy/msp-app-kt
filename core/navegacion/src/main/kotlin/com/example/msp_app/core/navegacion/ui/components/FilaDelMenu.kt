package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu

/**
 * Una opción del menú. Alto MÍNIMO `touchTarget` (56 dp, spec §2.1.6): crece si
 * el texto envuelve a escala 2.0, nunca se trunca (spec §5). Activa: fondo
 * `brandTint`, texto `brand` (spec §2.1.5).
 */
@Composable
fun FilaDelMenu(
    etiqueta: String,
    icono: IconoDelMenu,
    activo: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contador: String? = null
) {
    val colores = MspTheme.colors
    val tinta = if (activo) colores.brand else colores.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MspTheme.spacing.touchTarget)
            .clip(MspTheme.shapes.control)
            .background(if (activo) colores.brandTint else colores.surface)
            .clickable(onClick = onClick)
            .semantics { selected = activo }
            .testTag(tag)
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        Icon(
            imageVector = icono.vector(),
            contentDescription = null,
            tint = if (activo) colores.brand else colores.onSurfaceMuted,
            modifier = Modifier.size(MspTheme.spacing.lg)
        )
        Text(
            text = etiqueta,
            style = if (activo) MspTheme.type.bodyStrong else MspTheme.type.body,
            color = tinta,
            modifier = Modifier.weight(1f)
        )
        if (contador != null) {
            Text(
                text = contador,
                style = MspTheme.type.caption,
                color = if (activo) colores.brand else colores.onSurfaceMuted
            )
        }
    }
}
