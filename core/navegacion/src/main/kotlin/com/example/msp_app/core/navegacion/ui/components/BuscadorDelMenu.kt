package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.ui.BUSCADOR_DEL_MENU_TAG

@Composable
internal fun BuscadorDelMenu(consulta: String, onConsulta: (String) -> Unit) {
    BasicTextField(
        value = consulta,
        onValueChange = onConsulta,
        singleLine = true,
        textStyle = MspTheme.type.body.copy(color = MspTheme.colors.onSurface),
        cursorBrush = SolidColor(MspTheme.colors.brand),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md)
            .testTag(BUSCADOR_DEL_MENU_TAG),
        decorationBox = { campo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MspTheme.spacing.touchTarget)
                    .clip(MspTheme.shapes.field)
                    .background(MspTheme.colors.surface2)
                    .padding(horizontal = MspTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                Icon(
                    imageVector = Lucide.Search,
                    contentDescription = null,
                    tint = MspTheme.colors.onSurfaceMuted,
                    modifier = Modifier.size(MspTheme.spacing.lg)
                )
                Box(Modifier.weight(1f)) {
                    if (consulta.isEmpty()) {
                        Text(
                            "Buscar en el menú",
                            style = MspTheme.type.body,
                            color = MspTheme.colors.onSurfaceMuted
                        )
                    }
                    campo()
                }
            }
        }
    )
}
