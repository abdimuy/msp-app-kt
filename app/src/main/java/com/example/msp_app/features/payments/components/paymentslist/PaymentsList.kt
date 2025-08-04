package com.example.msp_app.features.payments.components.paymentslist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.features.payments.components.paymentitem.PaymentItem
import com.example.msp_app.features.payments.components.paymentitem.PaymentItemVariant

@Composable
fun PaymentsList(
    payments: List<Payment>,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(2.dp))
        payments.forEach { payment ->
            PaymentItem(
                payment,
                variant = PaymentItemVariant.DEFAULT,
                navController = navController
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}