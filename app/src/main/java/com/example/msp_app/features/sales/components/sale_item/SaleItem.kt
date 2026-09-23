package com.example.msp_app.features.sales.components.sale_item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import com.example.msp_app.core.common.location.SaleDistance
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.features.sales.components.primarysaleitem.PrimarySaleItem
import com.example.msp_app.features.sales.components.secondarysaleitem.SecondarySaleItem
import com.example.msp_app.navigation.DestinosDeCobranza
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

enum class SaleItemVariant {
    DEFAULT,
    SECONDARY
}

/**
 * La tarjeta de una venta dentro de una lista.
 *
 * ## "Agregar visita" ya no abre un diálogo (Task 21)
 *
 * El `NewVisitDialog` quedó **retirado**: escribía la fecha de la cita dentro
 * del texto libre de `NOTA`, así que "pidió reagendar" derivaba a `REGRESAS` y
 * caía en *vencidos*, mientras el mismo hecho capturado en la pantalla nueva
 * deriva a `DIFERIDO` y cae en *hoy*. El mismo hecho de campo en dos cubetas
 * distintas según qué UI abrió el cobrador.
 *
 * Ahora la acción **navega** al destino de la Task 19
 * (`visitas/registrar/{clienteId}?ventaId=…`), que escribe `PROMESA_FECHA`,
 * `PROMESA_MONTO_CENTAVOS` y `CITA_HORA` en columnas reales.
 *
 * ## "Agregar pago" tampoco abre un diálogo (Task 21, ronda 1)
 *
 * El `NewPaymentDialog` quedó **retirado**. Acuñaba su clave de idempotencia con
 * `remember { UUID.randomUUID() }` —que muere al rotar y al morir el proceso, y
 * ahí el reintento entra como un cobro nuevo— y escribía el pago y el descuento
 * del saldo por separado, porque su `@Transaction` está fuera de un `@Dao` y no
 * hace nada. La acción navega ahora a `pagos/abono/{ventaId}`, donde la clave y
 * el guard viven en el `SavedStateHandle` y las dos escrituras van dentro de
 * `db.withTransaction`.
 *
 * El argumento es el `DOCTO_CC_ACR_ID` de esta fila — [DestinosDeCobranza] lo
 * dice y lo prueba.
 */
@Composable
fun SaleItem(
    sale: SaleWithProducts,
    onClick: () -> Unit = {},
    variant: SaleItemVariant = SaleItemVariant.DEFAULT,
    distanceToCurrentLocation: SaleDistance = SaleDistance.Unknown,
    navController: NavController
) {
    val progress = ((sale.PRECIO_TOTAL - sale.SALDO_REST) / sale.PRECIO_TOTAL).toFloat()
    val parsedDate = OffsetDateTime.parse(sale.FECHA)
    val dateFormatted = parsedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    fun onAgregarVisita() {
        navController.navigate(DestinosDeCobranza.visitaDeUnaVenta(sale))
    }

    fun onAgregarAbono() {
        navController.navigate(DestinosDeCobranza.abonoDeUnaVenta(sale))
    }

    val menuExpanded = remember { mutableStateOf(false) }

    val openMenu: () -> Unit = { menuExpanded.value = true }
    val closeMenu: () -> Unit = { menuExpanded.value = false }

    // Las dos "aperturas de diálogo" son ahora navegaciones a los destinos de
    // las Tasks 18 y 19. Los cierres quedan en no-op y `showPaymentDialog` en
    // `false`: la pantalla se cierra sola al volver, y `Primary`/
    // `SecondarySaleItem` siguen recibiendo el mismo trío sin que haya que
    // tocarlos ni mover sus goldens.
    val openPaymentDialog: () -> Unit = { onAgregarAbono() }
    val closePaymentDialog: () -> Unit = {}
    val openVisitDialog: () -> Unit = { onAgregarVisita() }
    val closeVisitDialog: () -> Unit = {}

    when (variant) {
        SaleItemVariant.DEFAULT -> {
            PrimarySaleItem(
                sale = sale,
                onClick = onClick,
                onAddVisit = { onAgregarVisita() },
                progress = progress,
                date = dateFormatted,
                openMenu = openMenu,
                closeMenu = closeMenu,
                showMenu = menuExpanded.value,
                openPaymentDialog = openPaymentDialog,
                closePaymentDialog = closePaymentDialog,
                showPaymentDialog = false,
                openVisitDialog = openVisitDialog,
                closeVisitDialog = closeVisitDialog
            )
        }

        SaleItemVariant.SECONDARY -> {
            SecondarySaleItem(
                sale = sale,
                onClick = onClick,
                onAddVisit = { onAgregarVisita() },
                progress = progress,
                date = dateFormatted,
                openMenu = openMenu,
                closeMenu = closeMenu,
                showMenu = menuExpanded.value,
                openPaymentDialog = openPaymentDialog,
                closePaymentDialog = closePaymentDialog,
                showPaymentDialog = false,
                distanceToCurrentLocation = distanceToCurrentLocation,
                openVisitDialog = openVisitDialog,
                closeVisitDialog = closeVisitDialog
            )
        }
    }
}
