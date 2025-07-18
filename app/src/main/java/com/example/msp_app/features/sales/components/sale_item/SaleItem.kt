package com.example.msp_app.features.sales.components.sale_item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.data.models.sale.toSale
import com.example.msp_app.features.payments.components.newpaymentdialog.NewPaymentDialog
import com.example.msp_app.features.sales.components.primarysaleitem.PrimarySaleItem
import com.example.msp_app.features.sales.components.secondarysaleitem.SecondarySaleItem
import com.example.msp_app.features.visit.components.NewVisitDialog
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

enum class SaleItemVariant {
    DEFAULT,
    SECONDARY,
}

@Composable
fun SaleItem(
    sale: SaleWithProducts,
    onClick: () -> Unit = {},
    variant: SaleItemVariant = SaleItemVariant.DEFAULT,
    distanceToCurrentLocation: Double = 0.0,
    navController: NavController
) {
    val progress = ((sale.PRECIO_TOTAL - sale.SALDO_REST) / sale.PRECIO_TOTAL).toFloat()
    val parsedDate = OffsetDateTime.parse(sale.FECHA)
    val dateFormatted = parsedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    fun onAgregarVisita() {
        // Implementar la lógica para agregar una visita
    }

    val menuExpanded = remember { mutableStateOf(false) }
    val showPaymentDialog = remember { mutableStateOf(false) }
    val showVisitDialog = remember { mutableStateOf(false) }

    val openMenu: () -> Unit = { menuExpanded.value = true }
    val closeMenu: () -> Unit = { menuExpanded.value = false }

    val openPaymentDialog: () -> Unit = { showPaymentDialog.value = true }
    val closePaymentDialog: () -> Unit = { showPaymentDialog.value = false }

    val openVisitDialog: () -> Unit = { showVisitDialog.value = true }
    val closeVisitDialog: () -> Unit = { showVisitDialog.value = false }

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
                showPaymentDialog = showPaymentDialog.value,
                openVisitDialog = openVisitDialog,
                closeVisitDialog = closeVisitDialog,
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
                showPaymentDialog = showPaymentDialog.value,
                distanceToCurrentLocation = distanceToCurrentLocation,
                openVisitDialog = openVisitDialog,
                closeVisitDialog = closeVisitDialog,
            )
        }
    }



    if (showPaymentDialog.value) {
        NewPaymentDialog(
            show = true,
            onDismissRequest = { showPaymentDialog.value = false },
            sale = sale.toSale(),
            suggestedPayment = sale.PARCIALIDAD,
            navController = navController
        )
    }

    if (showVisitDialog.value) {
        NewVisitDialog(
            show = true,
            onDismissRequest = { showVisitDialog.value = false },
            sale = sale.toSale(),
            navController = navController
        )
    }
}
