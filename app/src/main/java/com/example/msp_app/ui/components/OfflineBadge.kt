package com.example.msp_app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WifiOff
import com.composables.icons.lucide.TriangleAlert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge que indica modo offline
 *
 * @param isVisible Si el badge es visible
 * @param modifier Modifier
 * @param isExpired Si los datos están expirados (muestra advertencia)
 * @param compact Modo compacto (solo icono)
 */
@Composable
fun OfflineBadge(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    isExpired: Boolean = false,
    compact: Boolean = false
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val backgroundColor = if (isExpired) {
            Color.Red.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        }

        val contentColor = if (isExpired) {
            Color.Red
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpired) Lucide.TriangleAlert else Lucide.WifiOff,
                contentDescription = if (isExpired) "Datos desactualizados" else "Modo offline",
                modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                tint = contentColor
            )

            if (!compact) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isExpired) "DESACTUALIZADO" else "OFFLINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
