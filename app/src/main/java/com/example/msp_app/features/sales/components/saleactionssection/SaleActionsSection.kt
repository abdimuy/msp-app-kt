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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.forgiveness.components.NewForgivenessDialog
import com.example.msp_app.features.payments.components.newpaymentdialog.NewPaymentDialog
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.visit.components.NewVisitDialog

@Composable
fun SaleActionSection(sale: Sale, navController: NavController) {
    val viewModel: PaymentsViewModel = viewModel()
    val paymentsBySaleIdState by viewModel.paymentsBySaleIdState.collectAsState()

    var open by remember { mutableStateOf(false) }
    var openVisitDialog by remember { mutableStateOf(false) }
    var openForgivenessDialog by remember { mutableStateOf(false) }

    val paymentAmounts: List<Int> = if (paymentsBySaleIdState is ResultState.Success) {
        (paymentsBySaleIdState as ResultState.Success).data.map { it.IMPORTE.toInt() }.distinct()
    } else {
        emptyList()
    }

    // Dialogs
    NewPaymentDialog(
        open,
        onDismissRequest = { open = false },
        suggestions = paymentAmounts,
        suggestedPayment = sale.PARCIALIDAD,
        sale,
        navController = navController
    )
    NewForgivenessDialog(
        show = openForgivenessDialog,
        onDismissRequest = { openForgivenessDialog = false },
        sale,
        navController = navController
    )
    NewVisitDialog(
        show = openVisitDialog,
        onDismissRequest = { openVisitDialog = false },
        sale = sale,
        navController = navController
    )

    Column(
        modifier = Modifier.fillMaxWidth(0.92f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Primary action
        Button(
            onClick = { open = true },
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
                onClick = { openVisitDialog = true },
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
