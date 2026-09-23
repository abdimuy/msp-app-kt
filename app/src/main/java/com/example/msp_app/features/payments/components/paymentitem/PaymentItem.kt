package com.example.msp_app.features.payments.components.paymentitem

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.models.PaymentMethod
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.ui.theme.ThemeController

enum class PaymentItemVariant {
    DEFAULT,
    COMPACT
}

/** `testTag` del "⋯" de la fila de pago. */
const val PAYMENT_ITEM_MENU_TAG: String = "payment_item_menu"

/**
 * Una fila de pago.
 *
 * ## Los dos destinos, y por qué son dos (Task 21)
 *
 * La fila es un **pago**, así que tocarla entra a **su venta**
 * ([onClick] por defecto). El "⋯ → ver cliente" entra a la **persona**: es la
 * puerta al cliente desde el mapa de rutas y desde los pagos del día, donde el
 * contexto es "quién vive aquí", no "cuál mueble".
 *
 * Antes ese menú decía "Ver cliente" y navegaba al detalle de **venta** legado
 * (`sales/sale_details/{DOCTO_CC_ACR_ID}`): la etiqueta y el destino no
 * coincidían. Ahora coinciden.
 *
 * El componente ya no recibe un `NavController`: recibe las dos acciones. Así el
 * destino se decide en un solo lugar ([com.example.msp_app.navigation.DestinosDeCobranza])
 * y esta fila se prueba sin grafo de navegación.
 */
@SuppressLint("DefaultLocale")
@Composable
fun PaymentItem(
    payment: Payment,
    variant: PaymentItemVariant = PaymentItemVariant.DEFAULT,
    onVerCliente: () -> Unit,
    onClick: () -> Unit = {}
) {
    val isDark = ThemeController.isDarkMode
    val menuExpanded = remember { mutableStateOf(false) }

    val paymentMethod = PaymentMethod.fromId(payment.FORMA_COBRO_ID).label.uppercase()

    if (variant == PaymentItemVariant.DEFAULT) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = AppTime.formatIsoForDisplay(
                        payment.FECHA_HORA_PAGO,
                        "dd/MM/yyyy hh:mm a"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = payment.NOMBRE_CLIENTE,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(
                    modifier = Modifier.height(0.dp)
                )
                Text(
                    text = paymentMethod,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = payment.IMPORTE.toCurrency(noDecimals = true),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Más opciones",
                    modifier = Modifier
                        .size(30.dp)
                        .padding(start = 8.dp)
                        .testTag(PAYMENT_ITEM_MENU_TAG)
                        .clickable { menuExpanded.value = true }
                )
                DropdownMenu(
                    expanded = menuExpanded.value,
                    onDismissRequest = { menuExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Ver cliente") },
                        onClick = {
                            menuExpanded.value = false
                            onVerCliente()
                        }
                    )
                }
            }
        }
    }

    if (variant == PaymentItemVariant.COMPACT) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onClick() }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = AppTime.formatIsoForDisplay(
                        payment.FECHA_HORA_PAGO,
                        "dd/MM/yyyy hh:mm a"
                    ),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 8.sp
                )
                Text(
                    text = payment.NOMBRE_CLIENTE,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 8.sp
                )
                Text(
                    text = paymentMethod,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 8.sp
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = payment.IMPORTE.toCurrency(noDecimals = true),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()

            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Más opciones",
                    modifier = Modifier
                        .size(30.dp)
                        .padding(start = 8.dp)
                        .testTag(PAYMENT_ITEM_MENU_TAG)
                        .clickable { menuExpanded.value = true }
                )
                DropdownMenu(
                    expanded = menuExpanded.value,
                    onDismissRequest = { menuExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Ver cliente") },
                        onClick = {
                            menuExpanded.value = false
                            onVerCliente()
                        }
                    )
                }
            }
        }
    }
}
