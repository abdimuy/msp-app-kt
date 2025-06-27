package com.example.msp_app.features.home.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.features.sales.viewmodels.SalesViewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import java.time.ZoneOffset
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val systemUiController = rememberSystemUiController()
    val primary = MaterialTheme.colorScheme.primary
    SideEffect {
        systemUiController.setStatusBarColor(color = primary)
    }

    val authViewModel = LocalAuthViewModel.current
    authViewModel.currentUser.collectAsState().value
    authViewModel.userData.collectAsState().value

    val salesViewModel: SalesViewModel = viewModel()
    val salesState by salesViewModel.salesState.collectAsState()

    val isDark = isSystemInDarkTheme()

    val userDataState by authViewModel.userData.collectAsState()

    val userData = when (userDataState) {
        is ResultState.Success -> (userDataState as ResultState.Success<User?>).data
        else -> null
    }

    val startDate = DateUtils.formatIsoDate(
        iso = userData?.FECHA_CARGA_INICIAL?.toDate()?.toInstant()?.atZone(ZoneOffset.UTC)
            .toString(),
        pattern = "EEE. dd/MM/yyyy hh:mm a",
        locale = Locale("es", "MX")
    )

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            content = { innerPadding ->

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(bottomEnd = 18.dp, bottomStart = 18.dp)
                            )
                            .height(130.dp)
                            .fillMaxWidth()
                    ) {
                        IconButton(onClick = openDrawer, modifier = Modifier.offset(y = (-16).dp)) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menú",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(0.dp))

                        Column(modifier = Modifier.offset(y = (-16).dp)) {
                            Text(
                                text = "Hola,",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.LightGray
                            )
                            Text(
                                text = userData?.NOMBRE ?: "Usuario no encontrado",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()

                    ) {
                        OutlinedCard(
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isDark) Color.Gray else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .background(
                                    MaterialTheme.colorScheme.background,
                                    RoundedCornerShape(16.dp)
                                )
                                .offset(y = (-40).dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 20.dp, horizontal = 16.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1.1f)
                                    ) {
                                        PaymentInfoCollector(
                                            label = "Total Cobrado (Hoy)",
                                            value = "$8"
                                        )
                                        PaymentInfoCollector(
                                            label = "Total cobrado (semanal)",
                                            value = "$450"
                                        )
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(0.9f),
                                    ) {
                                        PaymentInfoCollector(
                                            label = "Pagos (Hoy)",
                                            value = "0",
                                            horizontalAlignment = Alignment.End
                                        )
                                        PaymentInfoCollector(
                                            label = "Pagos (semanal)",
                                            value = "3",
                                            horizontalAlignment = Alignment.End
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(100.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(
                                                0xFFF06846
                                            )
                                        ),
                                        elevation = CardDefaults.cardElevation(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Porcentaje (Cuentas)",
                                                color = Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .padding(top = 8.dp),
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = "1.01%",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 22.sp,
                                                color = Color.White,
                                                modifier = Modifier.offset(y = 10.dp)
                                            )
                                        }
                                    }

                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(100.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(
                                                0xFF56DA6A
                                            )
                                        ),
                                        elevation = CardDefaults.cardElevation(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Cntas. cobradas",
                                                color = Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .padding(top = 8.dp),
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = buildAnnotatedString {
                                                    withStyle(style = SpanStyle(color = Color.White)) {
                                                        append("3")
                                                    }
                                                    withStyle(
                                                        style = SpanStyle(
                                                            color = Color.White.copy(
                                                                alpha = 0.5f
                                                            )
                                                        )
                                                    ) {
                                                        append("/296")
                                                    }
                                                },
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.offset(y = 10.dp)
                                            )
                                        }
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(
                                            0xFF56DA6A
                                        )
                                    ),
                                    elevation = CardDefaults.cardElevation(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Porcentaje (Cobro)",
                                            color = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = 8.dp),
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "1.01%",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp,
                                            color = Color.White,
                                            modifier = Modifier.offset(y = 10.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = buildAnnotatedString {
                                        append("Inicio de semana: ")
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(startDate)
                                        }
                                    },
                                    fontSize = 16.sp,
                                )
                            }
                        }

                        OutlinedCard(
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isDark) Color.Gray else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .height(90.dp)
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("VISITAS SIN ENVIAR")
                                    }
                                    append("\nNO HAY VISITAS SIN ENVIAR")
                                },
                                modifier = Modifier.padding(16.dp),
                            )

                        }

                        Spacer(Modifier.height(20.dp))
                        OutlinedCard(
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isDark) Color.Gray else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .height(90.dp)
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("PAGOS SIN ENVIAR")
                                    }
                                    append("\nNO HAY PAGOS SIN ENVIAR")
                                },
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))

                        Buttons(text = "Descargar ventas", onClick = { salesViewModel.syncSales() })

                        when (salesState) {
                            is ResultState.Idle -> {
                                Text("Presiona el botón para descargar ventas")
                            }

                            is ResultState.Loading -> CircularProgressIndicator()

                            is ResultState.Success -> Text("Ventas descargadas: ${(salesState as ResultState.Success<List<*>>).data.size}")
                            is ResultState.Error -> Text("Error: ${(salesState as ResultState.Error).message}")
                        }

                        Buttons(text = "Enviar Pagos Pendientes", onClick = { salesViewModel })

                        Buttons(text = "Reenviar todos los pagos", onClick = { salesViewModel })

                        Buttons(text = "Cerrar sesión", onClick = { salesViewModel })

                        Buttons(text = "Inicializar semana de Cobro", onClick = { salesViewModel })
                    }
                }
            },
        )
    }
}

@Composable
fun Buttons(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 50.dp,
    cornerRadius: Dp = 10.dp
) {
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth(0.92f)
            .size(size)
            .padding(4.dp),
        shape = RoundedCornerShape(cornerRadius)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        )
    }
}

@Composable
fun PaymentInfoCollector(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}