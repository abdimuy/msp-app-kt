package com.example.msp_app.features.payments.components.sortingbuttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SortingButtons(
    onSortByName: () -> Unit,
    onSortByDate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onSortByName,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = "ORD. POR NOMBRE", color = Color.White)
        }

        Button(
            onClick = onSortByDate,
            modifier = Modifier.weight(1f)
        ) {
            Text("ORD. POR FECHA", color = Color.White)
        }
    }
}