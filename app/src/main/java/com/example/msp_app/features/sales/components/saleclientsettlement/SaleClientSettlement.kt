package com.example.msp_app.features.sales.components.saleclientsettlement

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.sale.Sale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Settlement(
    val cashPrice: Double,
    val shortTermAmount: Double,
    val totalPrice: Double,
    val remainingBalance: Double,
    val date: String
)

data class PaymentResults(
    val amount: Double,
    val category: String,
    val validUntil: String
)

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
                .height(210.dp)
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(8.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.bg_gradient
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                        text = "Válido hasta ${result.validUntil}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(top = 0.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

fun calculatePaymentResult(settlement: Settlement): PaymentResults {
    if (settlement.cashPrice == 0.0) {
        return PaymentResults(
            amount = 0.0,
            category = "No disponible",
            validUntil = "-"
        )
    }

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val saleDate = LocalDate.parse(settlement.date, formatter)

    val elapsedMonths = ChronoUnit.MONTHS.between(
        saleDate.withDayOfMonth(saleDate.lengthOfMonth()),
        LocalDate.now().minusDays(5)
    ) + 1

    val shortTermInteres = (settlement.shortTermAmount - settlement.cashPrice) / 4
    val longTermInterest = (settlement.totalPrice - settlement.shortTermAmount) / 7
    val totalPaid = settlement.totalPrice - settlement.remainingBalance

    val amount = when {
        elapsedMonths <= 1 -> settlement.cashPrice
        elapsedMonths <= 3 -> settlement.cashPrice + elapsedMonths * shortTermInteres
        elapsedMonths in 4..5 -> settlement.shortTermAmount
        elapsedMonths <= 12 -> settlement.shortTermAmount + (elapsedMonths - 4) * longTermInterest
        else -> settlement.totalPrice
    } - totalPaid

    val category = when {
        elapsedMonths <= 1 -> "Precio de contado"
        elapsedMonths <= 12 -> "Precio a $elapsedMonths meses"
        else -> "Precio total"
    }

    val validDate = saleDate.plusMonths(elapsedMonths).plusDays(14)

    val formattedDate = DateUtils.formatIsoDate(
        iso = validDate.toString(),
        pattern = "dd/MM/yyyy",
        locale = Locale("es", "MX")
    )

    return PaymentResults(amount, category, formattedDate)
}