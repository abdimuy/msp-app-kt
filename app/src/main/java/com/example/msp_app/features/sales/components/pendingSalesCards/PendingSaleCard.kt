package com.example.msp_app.features.sales.components.pendingSalesCards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.entities.LocalSaleEntity

@Composable
fun PendingSalesCards(
    isDark: Boolean,
    pendingSalesState: ResultState<List<LocalSaleEntity>>
) {
    OutlinedCard(
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.dp, if (isDark) Color.Gray else Color.Transparent),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .background(Color.White, RoundedCornerShape(16.dp))
            .height(90.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "VENTAS SIN ENVIAR",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            when (pendingSalesState) {
                is ResultState.Loading -> Text("Cargando ventas pendientes...")
                is ResultState.Error -> Text(
                    "Error: ${pendingSalesState.message}",
                    color = Color.Red
                )

                is ResultState.Success -> {
                    val pending = pendingSalesState.data
                    Text(
                        if (pending.isEmpty())
                            "No hay ventas pendientes"
                        else
                            "Ventas pendientes: ${pending.size}"
                    )
                }

                else -> {}
            }
        }
    }
}
