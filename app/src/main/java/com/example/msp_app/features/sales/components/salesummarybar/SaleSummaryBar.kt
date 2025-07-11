package com.example.msp_app.features.sales.components.salesummarybar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.R

@Composable
fun SaleSummaryBar(
    balance: String,
    percentagePaid: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoItem(
                iconRes = R.drawable.money,
                value = balance,
                label = "Saldo",
                iconModifier = Modifier.size(32.dp)
            )

            InfoItem(
                iconRes = R.drawable.percentage,
                value = percentagePaid,
                label = "Porc. pagado",
                iconModifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun InfoItem(
    iconRes: Int,
    value: String,
    label: String,
    iconModifier: Modifier = Modifier.size(24.dp)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(45.dp)
                .background(color = Color(0xFF5D8EFF), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = Color.White,
                modifier = iconModifier
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}