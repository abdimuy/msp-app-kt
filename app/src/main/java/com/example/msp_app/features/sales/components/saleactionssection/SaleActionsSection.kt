package com.example.msp_app.features.sales.components.saleactionssection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.R
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.payments.components.newpaymentdialog.NewPaymentDialog
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.sales.components.SaleActionButton
import com.example.msp_app.features.visit.components.NewVisitDialog

@Composable
fun SaleActionSection(sale: Sale) {
    val viewModel: PaymentsViewModel = viewModel()
    val paymentsBySaleIdState by viewModel.paymentsBySaleIdState.collectAsState()

    var open by remember { mutableStateOf(false) }
    var openVisitDialog by remember { mutableStateOf(false) }

    val paymentAmounts: List<Int> = if (paymentsBySaleIdState is ResultState.Success) {
        (paymentsBySaleIdState as ResultState.Success).data.map { it.IMPORTE.toInt() }.distinct()
    } else {
        emptyList()
    }

    fun toggleDialog() {
        open = !open
    }

    fun closeDialog() {
        open = false
    }

    Column(
        modifier = Modifier.fillMaxWidth(0.92f),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NewPaymentDialog(
            open,
            onDismissRequest = { closeDialog() },
            suggestions = paymentAmounts,
            suggestedPayment = sale.PARCIALIDAD,
            sale
        )
        SaleActionButton(
            text = "AGREGAR PAGO",
            backgroundColor = MaterialTheme.colorScheme.primary,
            iconRes = R.drawable.money,
            onClick = { toggleDialog() },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SaleActionButton(
                text = "AGREGAR CONDONACIÓN",
                backgroundColor = Color(0xFFD32F2F),
                iconRes = R.drawable.checklist,
                modifier = Modifier.weight(0.3f),
                onClick = { }
            )

            NewVisitDialog(
                show = openVisitDialog,
                onDismissRequest = { openVisitDialog = false },
                sale
            )
            SaleActionButton(
                text = "AGREGAR VISITA",
                backgroundColor = Color(0xFF388E3C),
                iconRes = R.drawable.visita,
                modifier = Modifier.weight(0.3f),
                onClick = { openVisitDialog = true }
            )
        }
    }
}
