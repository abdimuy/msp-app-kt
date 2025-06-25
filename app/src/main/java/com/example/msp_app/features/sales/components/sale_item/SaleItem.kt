package com.example.msp_app.features.sales.components.sale_item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.data.models.sale.EstadoCobranza
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.payments.components.newpaymentdialog.NewPaymentDialog
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SaleItem(
    sale: Sale,
    onClick: () -> Unit
) {
    val progress = ((sale.PRECIO_TOTAL - sale.SALDO_REST) / sale.PRECIO_TOTAL).toFloat()
    val parsedDate = OffsetDateTime.parse(sale.FECHA)
    val dateFormatted = parsedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    fun onAgregarVisita() {
        // Implementar la lógica para agregar una visita
    }

    val isDark = isSystemInDarkTheme()
    val menuExpanded = remember { mutableStateOf(false) }
    val showPaymentDialog = remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(
            defaultElevation =
                if (!isDark) 8.dp else 0.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border =
            if (!isDark) null else BorderStroke(
                width = 1.dp,
                color = Color.DarkGray
            ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (sale.ESTADO_COBRANZA == EstadoCobranza.PAGADO) Color(0xFF4CAF50)
                            else if (sale.ESTADO_COBRANZA == EstadoCobranza.PENDIENTE) Color(
                                0xFF9E9E9E
                            )
                            else if (sale.ESTADO_COBRANZA == EstadoCobranza.NO_PAGADO) Color(
                                0xFFF44336
                            )
                            else if (sale.ESTADO_COBRANZA == EstadoCobranza.VOLVER_VISITAR) Color(
                                0xFFFF9800
                            )
                            else Color(0xFF9E9E9E),
                            shape = MaterialTheme.shapes.small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (sale.ESTADO_COBRANZA) {
                        EstadoCobranza.PENDIENTE -> {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Pendiente",
                                tint = Color.White,
                            )
                        }

                        EstadoCobranza.PAGADO -> {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Pagado",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        EstadoCobranza.NO_PAGADO ->
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "No Pagado",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )

                        EstadoCobranza.VOLVER_VISITAR -> {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Volver a Visitar",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        else -> {
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(sale.CLIENTE + " ")
                            }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${sale.FOLIO} - $dateFormatted",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .height(48.dp)

                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        modifier = Modifier
                            .size(30.dp)
                            .padding(start = 8.dp)
                            .clickable { menuExpanded.value = true }
                    )
                    DropdownMenu(
                        expanded = menuExpanded.value,
                        onDismissRequest = { menuExpanded.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Agregar pago") },
                            onClick = {
                                menuExpanded.value = false
                                showPaymentDialog.value = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Agregar visita") },
                            onClick = {
                                menuExpanded.value = false
                                onAgregarVisita()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${sale.CALLE.replace("\n", " ")} ${
                    sale.CIUDAD.replace(
                        "\n",
                        " "
                    )
                } ${sale.ESTADO}", maxLines = 2, overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(fontSize = 16.sp)) {
                            append("Abonado: ")
                        }
                        withStyle(
                            style = SpanStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(text = "$${(sale.PRECIO_TOTAL - sale.SALDO_REST).toInt()}")
                        }
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(fontSize = 16.sp)) {
                            append("Saldo: ")
                        }
                        withStyle(
                            style = SpanStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(text = "$${sale.SALDO_REST.toInt()}")
                        }
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                )
            }
        }
    }

    if (showPaymentDialog.value) {
        NewPaymentDialog(
            show = true,
            onDismissRequest = { showPaymentDialog.value = false },
            sale = sale
        )
    }
}
