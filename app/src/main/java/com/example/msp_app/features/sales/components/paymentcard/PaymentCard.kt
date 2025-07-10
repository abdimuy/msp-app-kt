package com.example.msp_app.features.sales.components.paymentcard

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.R
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import java.time.ZoneOffset

@Composable
fun PaymentCard(
    payment: Payment,
    navController: NavController,
    isFirstPayment: Boolean = false
) {
    val authViewModel: AuthViewModel = viewModel()
    val userState by authViewModel.userData.collectAsState()

    val user = (userState as? ResultState.Success<User?>)?.data

    val dateInitial = user
        ?.FECHA_CARGA_INICIAL
        ?.toDate()
        ?.toInstant()
        ?.atZone(ZoneOffset.UTC)
        ?.toLocalDateTime()
        ?.let { DateUtils.getIsoDateTime(it) }
        ?: DateUtils.getIsoDateTime()

    val bgRes = if (
        DateUtils.isAfterIso(
            payment.FECHA_HORA_PAGO,
            dateInitial
        )
    ) {
        R.drawable.bg_gradient_success
    } else {
        R.drawable.bg_gradient
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(70.dp)
            .then(
                if (isFirstPayment) Modifier.clickable {
                    navController.navigate("payment_ticket/${payment.ID}")
                } else Modifier
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = bgRes
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.7f)
                ) {
                    Text(
                        payment.COBRADOR,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        DateUtils.formatIsoDate(
                            payment.FECHA_HORA_PAGO,
                            pattern = "EE dd/MM/yyyy hh:mm a",
                        ).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(0.3f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "$${payment.IMPORTE.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (payment.GUARDADO_EN_MICROSIP) "ENVIADO" else "PENDIENTE",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}