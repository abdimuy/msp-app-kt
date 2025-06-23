package com.example.msp_app.features.sales.components.saleactionssection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.msp_app.R
import com.example.msp_app.features.sales.components.SaleActionButton

@Composable
fun SaleActionSection() {
    Column(
        modifier = Modifier.fillMaxWidth(0.92f),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SaleActionButton(
            text = "AGREGAR PAGO",
            backgroundColor = MaterialTheme.colorScheme.primary,
            iconRes = R.drawable.money,
            onClick = {}
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SaleActionButton(
                text = "AGREGAR CONDONACIÓN",
                backgroundColor = Color(0xFFD32F2F),
                iconRes = R.drawable.checklist,
                modifier = Modifier.weight(0.3f),
                onClick = { }
            )

            SaleActionButton(
                text = "AGREGAR VISITA",
                backgroundColor = Color(0xFF388E3C),
                iconRes = R.drawable.visita,
                modifier = Modifier.weight(0.3f),
                onClick = { }
            )
        }
    }
}