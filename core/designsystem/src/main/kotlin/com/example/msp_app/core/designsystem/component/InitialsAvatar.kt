package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.designsystem.theme.Manrope
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * Avatar de iniciales de las filas de pago/cliente: cuadro redondeado
 * `shapes.control` (12dp) con fondo `brandTint` e [initials] en `brand`,
 * Manrope ExtraBold 13sp centrado — 1:1 del mockup (`.ava`: 38×38, radius 12,
 * `background:var(--tint)`, `color:var(--brand)`, `font-weight:800`).
 *
 * Recibe las [initials] ya calculadas (p. ej. `"ML"` para "Minerva López"):
 * el cálculo nombre → iniciales es responsabilidad del llamador/piloto (YAGNI:
 * el design system no conoce el modelo de cliente).
 */
@Composable
fun MspInitialsAvatar(initials: String, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MspTheme.shapes.control)
            .background(MspTheme.colors.brandTint),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            ),
            color = MspTheme.colors.brand
        )
    }
}
