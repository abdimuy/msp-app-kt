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
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.payments.components.weeklyreportcontent.WeeklyReportContent
import com.example.msp_app.features.payments.utils.ReportFormatters
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.visit.viewmodels.VisitsViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun WeeklyReportScreen(navController: NavController, viewModel: PaymentsViewModel = viewModel()) {
    val paymentsState by viewModel.paymentsByDateState.collectAsState()
    val forgivenessState by viewModel.forgivenessByDateState.collectAsState()

    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()

    val startIso = remember(userDataState) {
        val startDate = (userDataState as? ResultState.Success)?.data?.FECHA_CARGA_INICIAL
        val startInstant = startDate?.toDate()?.toInstant() ?: AppClock.System.now()
        AppTime.toWireFormat(startInstant)
    }

    // Display bound only (label "Del ... al DD/MM/yy" + PDF filename): today's date,
    // rendered in business zone by AppTime.formatIsoForDisplay downstream.
    val endIso = AppTime.toWireFormat(AppClock.System.now())

    // Query bound: EXCLUSIVE end of today in business zone, consistent with the half-open
    // DAO (`getPaymentsByDate`/`getVisitsByDate`/`getForgivenessByDate` now filter `< :end`).
    // A raw now() end would transiently UNDERCOUNT: a pago saved "now" and truncated to whole
    // seconds (Task 5b Cambio A) — e.g. `2026-04-15T18:30:00Z` — is NOT `< 2026-04-15T18:30:00.123Z`
    // under SQLite BINARY collation (`Z`=0x5A sorts after `.`=0x2E), so it would be excluded
    // from its own weekly report until the fraction rounds off. `startOfNextDay(today)`
    // includes all of today. Start bound (FECHA_CARGA_INICIAL) is left untouched.
    val queryEndIso = ReportFormatters.dateRangeFor(ReportFormatters.todayForReport()).endIso

    val visitsViewModel: VisitsViewModel = viewModel()
    val visitsState by visitsViewModel.visitsByDate.collectAsState()

    LaunchedEffect(startIso) {
        viewModel.getPaymentsByDate(startIso, queryEndIso, "WEEKLY_REPORT")
        visitsViewModel.getVisitsByDate(startIso, queryEndIso)
        viewModel.getForgivenessByDate(startIso, queryEndIso)
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
