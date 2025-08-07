package com.example.msp_app.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ModernSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animationDuration: Int = 5000
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "spinner")

        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    animationDuration,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        val primaryColor = MaterialTheme.colorScheme.primary

        // Círculo contenedor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    clip = false
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.9f),
                            primaryColor.copy(alpha = 0.7f),
                            primaryColor
                        ),
                        radius = (size.value / 2)
                    ),
                    shape = CircleShape
                )
        ) {
            // Aplicar clip circular a todo el contenido interno
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            ) {
                // Primera onda
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = (-size.value / 3).dp)
                        .rotate(rotation)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 1.2f),
                            shape = RoundedCornerShape((size.value * 0.45f).dp)
                        )
                )

                // Segunda onda
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = (-size.value / 3).dp)
                        .rotate(rotation)
                        .background(
                            Color.White.copy(alpha = 0.4f),
                            shape = RoundedCornerShape((size.value * 0.3f).dp)
                        )
                )
            }
        }
    }
}