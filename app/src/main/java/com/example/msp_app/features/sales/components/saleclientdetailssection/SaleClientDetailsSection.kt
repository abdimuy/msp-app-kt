package com.example.msp_app.features.sales.components.saleclientdetailssection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.components.infofield.InfoField
import com.example.msp_app.features.sales.components.salecontactactions.SaleContactActions
import com.example.msp_app.ui.theme.ThemeController

@Composable
fun SaleClientDetailsSection(sale: Sale, modifier: Modifier = Modifier) {
    val isDark = ThemeController.isDarkMode

    OutlinedCard(
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color.Gray else Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth(0.92f)
            .background(Color.White, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(vertical = 30.dp, horizontal = 16.dp)) {
            Text(
                text = sale.CLIENTE,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            Text(
                text = sale.FOLIO,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Row(modifier = Modifier.padding(vertical = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    InfoField(label = "Fecha de venta:", value = sale.FECHA)
                    InfoField(label = "Teléfono:", value = sale.TELEFONO)
                    InfoField(
                        label = "Total venta:",
                        value = "$${sale.PRECIO_TOTAL.toInt()}"
                    )
                    InfoField(
                        label = "Precio de contado:",
                        value = "$${sale.PRECIO_DE_CONTADO.toInt()}"
                    )
                    InfoField(label = "Zona:", value = sale.ZONA_NOMBRE)
                }
                Column(modifier = Modifier.weight(1f)) {
                    InfoField(label = "Parcialidad:", value = "$${sale.PARCIALIDAD}")
                    InfoField(
                        label = "Frecuencia de pago:",
                        value = sale.FREC_PAGO.toString()
                    )
                    InfoField(label = "Enganche:", value = "$${sale.ENGANCHE.toInt()}")
                    InfoField(
                        label = "Precio a ${sale.TIEMPO_A_CORTO_PLAZOMESES} mes(es):",
                        value = "$${sale.MONTO_A_CORTO_PLAZO.toInt()}"
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SaleContactActions(sale)
            }

            InfoField(
                label = "Dirección:",
                value = "${sale.CALLE.replace("\n", " ")} ${
                    sale.CIUDAD.replace(
                        "\n",
                        " "
                    )
                } ${sale.ESTADO}"
            )
            InfoField(
                label = "Aval o responsable",
                value = sale.AVAL_O_RESPONSABLE.toString()
            )
            InfoField(label = "Notas:", value = sale.NOTAS)
            InfoField(label = "Vendedores:", value = sale.VENDEDOR_1)
        }
    }
}