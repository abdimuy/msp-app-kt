package com.example.msp_app.features.sales.components.saleclientsettlement

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.R
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.domain.models.Settlement
import com.example.msp_app.features.sales.domain.models.calculatePaymentResult

@Composable
fun SaleClienteSettlement(sale: Sale) {
    val settlement = Settlement(
        cashPrice = sale.PRECIO_DE_CONTADO,
        shortTermAmount = sale.MONTO_A_CORTO_PLAZO,
        totalPrice = sale.PRECIO_TOTAL,
        remainingBalance = sale.SALDO_REST,
        date = sale.FECHA
    )

    val result = calculatePaymentResult(settlement)

    if (result.amount != 0.0 && sale.SALDO_REST != 0.0) {
        Card(
            modifier = Modifier
                .padding(12.dp)
                .heightIn(min = 210.dp)
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 210.dp)
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.bg_gradient
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hoy liquida con",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                    Text(
                        text = result.amount.toCurrency(noDecimals = true),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                    Text(
                        text = result.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                    HorizontalDivider(thickness = 1.dp, color = Color.White)
                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                    Text(
                        text = "Valido hasta ${result.validUntil}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(top = 0.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}
