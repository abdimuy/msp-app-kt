package com.example.msp_app.features.sales.components.primarysaleitem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.R
import com.example.msp_app.components.badges.AlertBadge
import com.example.msp_app.components.badges.BadgesType
import com.example.msp_app.data.models.sale.EstadoCobranza
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.ui.theme.ThemeController
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PrimarySaleItem(
    sale: SaleWithProducts,
    onClick: () -> Unit = {},
    date: String = sale.FECHA,
    openMenu: () -> Unit = {},
    closeMenu: () -> Unit = {},
    showMenu: Boolean = false,
    openPaymentDialog: () -> Unit = {},
    closePaymentDialog: () -> Unit = {},
    showPaymentDialog: Boolean = false,
    onAddVisit: () -> Unit = {},
    progress: Float = 0f,
    openVisitDialog: () -> Unit = {},
    closeVisitDialog: () -> Unit = {},
) {
    val isDark = ThemeController.isDarkMode

    val isNew = remember(sale.SALDO_REST, sale.TOTAL_IMPORTE, sale.ENGANCHE) {
        sale.SALDO_REST == sale.PRECIO_TOTAL - sale.ENGANCHE
    }

    val formattedDiaCobranza = runCatching {
        ZonedDateTime
            .parse(sale.DIA_TEMPORAL_COBRANZA, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd MMM HH:mm"))
    }.getOrDefault(sale.DIA_TEMPORAL_COBRANZA).uppercase()

    val latePayments = sale.NUM_PAGOS_ATRASADOS
    val message = when {
        latePayments < 1 -> "No tiene atrasa."
        latePayments < 5 -> "$latePayments pag atrasa"
        else -> "$latePayments pag atrasa"
    }
    val badgeType = when {
        latePayments < 1 -> BadgesType.Success
        latePayments < 5 -> BadgesType.Warning
        else -> BadgesType.Danger
    }

    val formattedFechaUltPago = sale.FECHA_ULT_PAGO
        .takeIf { !it.isNullOrEmpty() }
        ?.let { iso ->
            runCatching {
                ZonedDateTime
                    .parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .format(
                        DateTimeFormatter.ofPattern(
                            "dd MMM yy",
                            Locale.getDefault()
                        )
                    )
            }.getOrNull()
        } ?: "Sin Fecha"

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
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(45.dp)
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
                            Image(
                                painter = painterResource(id = R.drawable.horizontal_rule_24px),
                                contentDescription = "Pendiente",
                                modifier = Modifier.size(24.dp),
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
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        if (isNew) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFF4CAF50),
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Nuevo",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
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
                    }
                    Text(
                        text = "${sale.FOLIO} - $date",
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
                            .clickable { openMenu() },
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { closeMenu() },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Agregar pago") },
                            onClick = {
                                closeMenu()
                                openPaymentDialog()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Agregar visita") },
                            onClick = {
                                closeMenu()
                                openVisitDialog()
                            }
                        )
                    }
                }
            }
            Text(
                text = "${sale.CALLE.replace("\n", " ")} ${
                    sale.CIUDAD.replace(
                        "\n",
                        " "
                    )
                } ${sale.ESTADO}",
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Text(
                text = sale.PRODUCTOS,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sale.DIA_TEMPORAL_COBRANZA.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFFF9800),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                ) {
                                    append("Visitar: ")
                                }
                                withStyle(
                                    style = SpanStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                ) {
                                    append(formattedDiaCobranza)
                                }
                            }
                        )
                    }
                } else {
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
                }
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

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlertBadge(
                    message = message,
                    type = badgeType,
                    padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                )

                if (formattedFechaUltPago != "Sin Fecha") {
                    AlertBadge(
                        message = "Ult Pag $formattedFechaUltPago",
                        type = BadgesType.Primary,
                        padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}