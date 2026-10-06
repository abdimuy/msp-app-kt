package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.core.navegacion.ui.FILA_DEL_MENU_TAG
import com.example.msp_app.core.navegacion.ui.SECCION_DEL_MENU_TAG

/**
 * Encabezado plegable + sus opciones. Plegar es instantáneo, sin animación: nada
 * salta (principio 12) y no hay nada que cortar con movimiento reducido.
 */
@Composable
internal fun SeccionPlegable(
    seccion: SeccionDelMenu,
    abierta: Boolean,
    habilitada: Boolean,
    itemActivoId: String?,
    onToggle: () -> Unit,
    onElegir: (ElementoDelMenu) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MspTheme.spacing.touchTarget)
                .clip(MspTheme.shapes.control)
                .clickable(enabled = habilitada, onClick = onToggle)
                .semantics { stateDescription = if (abierta) "Abierta" else "Plegada" }
                .testTag(SECCION_DEL_MENU_TAG + seccion.id)
                .padding(horizontal = MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = seccion.titulo.uppercase(BUSINESS_LOCALE),
                style = MspTheme.type.sectionLabel,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (abierta) Lucide.ChevronDown else Lucide.ChevronRight,
                contentDescription = null,
                tint = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(MspTheme.spacing.lg)
            )
        }
        if (abierta) {
            seccion.items.forEach { elemento ->
                FilaDelMenu(
                    etiqueta = elemento.etiqueta,
                    icono = elemento.icono,
                    activo = elemento.id == itemActivoId,
                    tag = FILA_DEL_MENU_TAG + elemento.id,
                    onClick = { onElegir(elemento) },
                    contador = elemento.contador
                )
            }
        }
    }
}
