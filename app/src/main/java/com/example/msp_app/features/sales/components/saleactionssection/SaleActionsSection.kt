package com.example.msp_app.features.sales.components.saleactionssection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.forgiveness.components.NewForgivenessDialog
import com.example.msp_app.navigation.DestinosDeCobranza
import com.example.msp_app.navigation.Screen

/**
 * El bloque de acciones del detalle de venta legado.
 *
 * ## "Agregar Visita" navega, ya no abre un diálogo (Task 21)
 *
 * El `NewVisitDialog` quedó retirado: embebía la fecha de la cita en el texto
 * libre de `NOTA`, y por eso el mismo hecho de campo ("pidió reagendar") caía en
 * *vencidos* por esta puerta y en *hoy* por la pantalla nueva. El botón lleva
 * ahora al destino de la Task 19, que escribe la promesa y la cita en columnas
 * reales.
 *
 * ## "Agregar Pago" también navega (Task 21, ronda 1)
 *
 * El `NewPaymentDialog` quedó retirado. Los dos capturadores escribían la MISMA
 * tabla por el MISMO `PaymentFactory`, pero divergían en las dos direcciones y
 * el legado perdía en todas las que importan: su clave de idempotencia era un
 * `remember { UUID.randomUUID() }` —muere al rotar o al morir el proceso, y el
 * reintento cobra dos veces—, su "atomicidad" era un `@Transaction` inerte fuera
 * de un `@Dao` —un fallo entre las dos escrituras dejaba el pago contado con el
 * saldo intacto— y sus errores morían en un `printStackTrace()`. El botón lleva
 * ahora a `pagos/abono/{ventaId}`, que persiste la clave y el guard en su
 * `SavedStateHandle` y escribe dentro de `db.withTransaction`.
 *
 * El argumento es el **`DOCTO_CC_ACR_ID`** —la llave primaria de `sales`, que es
 * lo que `SaleDao.getById` filtra—, no el `DOCTO_CC_ID` del crédito ni el
 * `CLIENTE_ID`.
 *
 * **La condonación tiene DOS puertas**, no una (corrige E-APP-044): este bloque
 * del detalle legado y `ForgivenessScreen`, que montan el detalle de venta y el
 * de cliente nuevos. Las dos usan el mismo `NewForgivenessDialog`, que desde el
 * 2026-09-27 escribe por `RegistroDeCondonacion` (una transacción que relee el
 * saldo y no deja condonar más de lo que se debe) y avisa por `onGuardada`
 * **sólo cuando la escritura terminó**. Aquí la condonación guardada sigue
 * yendo al ticket legado, como antes; lo único nuevo es que ya no se navega sin
 * saber si se escribió (E-APP-047).
 */
@Composable
fun SaleActionSection(sale: Sale, navController: NavController) {
    var openForgivenessDialog by remember { mutableStateOf(false) }

    NewForgivenessDialog(
        show = openForgivenessDialog,
        onDismissRequest = { openForgivenessDialog = false },
        onGuardada = { pagoId ->
            navController.irAlTicketDesdeElDetalleLegado(pagoId)
            openForgivenessDialog = false
        },
        sale = sale
    )

    Column(
        modifier = Modifier.fillMaxWidth(0.92f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Primary action
        Button(
            onClick = {
                navController.navigate(DestinosDeCobranza.abonoDeUnaVenta(sale))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Agregar Pago",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { openForgivenessDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F)
                )
            ) {
                Text(
                    text = "Condonación",
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Button(
                onClick = {
                    navController.navigate(DestinosDeCobranza.visitaDeUnaVenta(sale))
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Agregar Visita",
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * La salida de una condonación guardada **desde el detalle legado**: empuja el
 * ticket ([Screen.PaymentTicket]) encima del detalle, que es donde vive el
 * diálogo. No saca nada de la pila —aquí no hay pantalla de condonación que
 * quitar—, así que atrás desde el ticket vuelve al detalle. Es lo que esta
 * puerta siempre hizo; `internal` sólo para medirlo sobre un grafo real.
 */
internal fun NavController.irAlTicketDesdeElDetalleLegado(pagoId: String) {
    navigate(Screen.PaymentTicket.createRoute(pagoId))
}
