package com.example.msp_app.components.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BadgesType {
    Success, Warning, Danger, Primary
}

@Composable
fun AlertBadge(
    message: String,
    type: BadgesType,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (type) {
        BadgesType.Success -> Color(0xFFDFF5E1) to Color(0xFF1B5E20)
        BadgesType.Warning -> Color(0xFFFDF1CB) to Color(0xFFEF6C00)
        BadgesType.Danger -> Color(0xFFFFEBEE) to Color(0xFFA11818)
        BadgesType.Primary -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
    }

    Box(
        modifier = modifier
            .padding(8.dp)
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = message,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

