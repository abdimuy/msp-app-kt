package com.example.msp_app.features.home.components.homeheader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.R
import com.example.msp_app.ui.theme.ThemeController

@Composable
fun HomeHeader(
    userName: String?,
    onMenuClick: () -> Unit,
    onToggleTheme: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                backgroundColor,
                RoundedCornerShape(bottomEnd = 18.dp, bottomStart = 18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(130.dp)
                .weight(1f)

        ) {
            IconButton(onClick = onMenuClick, modifier = Modifier.offset(y = (-16).dp)) {
                Icon(Icons.Default.Menu, contentDescription = "Menú", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(0.dp))
            Column(modifier = Modifier.offset(y = (-16).dp)) {
                Text("Hola,", style = MaterialTheme.typography.titleSmall, color = Color.LightGray)
                Text(
                    userName ?: "-",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onToggleTheme, modifier = Modifier.offset(y = (-16).dp)) {
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter = painterResource(
                    id = if (ThemeController.isDarkMode)
                        R.drawable.light_mode_24px
                    else
                        R.drawable.dark_mode_24px
                ),
                contentDescription = "Toggle Theme",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}