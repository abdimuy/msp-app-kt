package com.example.msp_app.features.sales.components.saleclientsettlement

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    val PRECIO_DE_CONTADO: Double,
    val MONTO_A_CORTO_PLAZO: Double,
    val PRECIO_TOTAL: Double,
    val SALDO_REST: Double,
    val FECHA: String
)

data class PagoResultado(
    val monto: Double,
    val etiqueta: String,
    val fechaValidez: String
)

@Composable
fun SaleClienteSettlement(sale: Sale) {

    val settlement = Settlement(
        PRECIO_DE_CONTADO = sale.PRECIO_DE_CONTADO,
        MONTO_A_CORTO_PLAZO = sale.MONTO_A_CORTO_PLAZO,
        PRECIO_TOTAL = sale.PRECIO_TOTAL,
        SALDO_REST = sale.SALDO_REST,
        FECHA = sale.FECHA
    )

    val resultado = calcularPagoResultado(settlement)

    if (resultado.monto != 0.0) {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Hoy liquida con",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = resultado.monto.toCurrency(noDecimals = true),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = resultado.etiqueta,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    HorizontalDivider(thickness = 1.dp, color = Color.White)
                    Text(
                        text = "Válido hasta ${resultado.fechaValidez}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
    }
}

fun calcularPagoResultado(settlement: Settlement): PagoResultado {
    if (settlement.PRECIO_DE_CONTADO == 0.0) {
        return PagoResultado(
            monto = 0.0,
            etiqueta = "No disponible",
            fechaValidez = "-"
        )
    }

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val saleDate = LocalDate.parse(settlement.FECHA, formatter)

    val tiempoTranscurrido = ChronoUnit.MONTHS.between(
        saleDate.withDayOfMonth(saleDate.lengthOfMonth()),
        LocalDate.now().minusDays(5)
    ) + 1

    val interesesCorto = (settlement.MONTO_A_CORTO_PLAZO - settlement.PRECIO_DE_CONTADO) / 4
    val interesesLargo = (settlement.PRECIO_TOTAL - settlement.MONTO_A_CORTO_PLAZO) / 7
    val totalAbonado = settlement.PRECIO_TOTAL - settlement.SALDO_REST

    val monto = when {
        tiempoTranscurrido <= 1 -> settlement.PRECIO_DE_CONTADO
        tiempoTranscurrido <= 3 -> settlement.PRECIO_DE_CONTADO + tiempoTranscurrido * interesesCorto
        tiempoTranscurrido in 4..5 -> settlement.MONTO_A_CORTO_PLAZO
        tiempoTranscurrido <= 12 -> settlement.MONTO_A_CORTO_PLAZO + (tiempoTranscurrido - 4) * interesesLargo
        else -> settlement.PRECIO_TOTAL
    } - totalAbonado

    val etiqueta = when {
        tiempoTranscurrido <= 1 -> "Precio de contado"
        tiempoTranscurrido <= 3 -> "Precio a $tiempoTranscurrido meses"
        tiempoTranscurrido in 4..5 -> "Precio a $tiempoTranscurrido meses"
        tiempoTranscurrido <= 12 -> "Precio a ${tiempoTranscurrido - 4} meses"
        else -> "Precio total"
    }

    val fechaValida = saleDate.plusMonths(tiempoTranscurrido).plusDays(14)

    val fechaFormateada = DateUtils.formatIsoDate(
        iso = fechaValida.toString(),
        pattern = "dd/MM/yyyy",
        locale = Locale("es", "MX")
    )

    return PagoResultado(monto, etiqueta, fechaFormateada)
}