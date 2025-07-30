package com.example.msp_app.features.payments.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.payments.components.weeklyreport.WeeklyReportContent
import com.example.msp_app.features.payments.utils.ReportFormatters
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.visit.viewmodels.VisitsViewModel
import java.time.temporal.ChronoUnit


@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun WeeklyReportScreen(
    navController: NavController,
    viewModel: PaymentsViewModel = viewModel()
) {
    val paymentsState by viewModel.paymentsByDateState.collectAsState()
    val forgivenessState by viewModel.forgivenessByDateState.collectAsState()

    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()

    val startIso = remember(userDataState) {
        val startDate = (userDataState as? ResultState.Success)?.data?.FECHA_CARGA_INICIAL
        DateUtils.parseDateToIso(startDate?.toDate())
    } ?: DateUtils.parseDateToIso(null)

    val endIso = DateUtils.addToIsoDate(
        DateUtils.addToIsoDate(startIso, 6, ChronoUnit.DAYS),
        -1, ChronoUnit.SECONDS
    )
    val visitsViewModel: VisitsViewModel = viewModel()
    val visitsState by visitsViewModel.visitsByDate.collectAsState()

    LaunchedEffect(startIso) {
        viewModel.getPaymentsByDate(startIso, endIso)
        visitsViewModel.getVisitsByDate(startIso, endIso)
        viewModel.getForgivenessByDate(startIso, endIso)
    }

    val visitTextData = ReportFormatters.formatVisitsTextList(
        (visitsState as? ResultState.Success)?.data ?: emptyList()
    )

    DrawerContainer(
        navController = navController
    ) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = openDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menú")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Reporte Semanal", style = MaterialTheme.typography.titleLarge)
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
            ) {
                WeeklyReportContent(
                    paymentsState = paymentsState,
                    forgivenessState = forgivenessState,
                    visitTextData = visitTextData,
                    startIso = startIso,
                    endIso = endIso,
                    navController = navController,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
