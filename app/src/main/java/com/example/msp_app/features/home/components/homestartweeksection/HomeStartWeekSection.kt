package com.example.msp_app.features.home.components.homestartweeksection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.features.sales.viewmodels.SalesViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HomeStartWeekSection(
    startDate: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val salesViewModel: SalesViewModel = viewModel()
    val lastSyncDate = salesViewModel.getLastSyncDate()
    OutlinedCard(
        modifier = modifier.fillMaxWidth(0.92f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color.Gray else Color.LightGray
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = buildAnnotatedString {
                append("Inicio de semana: \n")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    if (startDate == "null") {
                        append("Cargando...")
                    } else {
                        append(startDate.uppercase())
                    }
                }
            },
            fontSize = 16.sp
        )
    }

    Spacer(Modifier.height(16.dp))

    Text(
        modifier = Modifier.padding(horizontal = 16.dp),
        text = "Última actualización de datos:\n ${
            if (lastSyncDate.isBlank())
                "No se ha sincronizado aún"
            else {
                val originalFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val parsed = originalFormat.parse(lastSyncDate)
                if (parsed != null) {
                    val targetFormat = SimpleDateFormat(
                        "dd/MM/yyyy hh:mm a", Locale.getDefault()
                    )
                    targetFormat.format(parsed)
                } else {
                    lastSyncDate
                }
            }
        }",
        fontSize = 14.sp,
        color = if (isDark) Color.LightGray else Color.DarkGray,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(16.dp))
}