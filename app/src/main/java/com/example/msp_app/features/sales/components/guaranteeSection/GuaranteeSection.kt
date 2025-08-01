package com.example.msp_app.features.sales.components.guaranteeSection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.guarantees.screens.viewmodels.GuaranteesViewModel
import com.example.msp_app.navigation.Screen

@Composable
fun GuaranteeSection(
    sale: Sale,
    navController: NavController,
) {
    val guaranteesViewModel: GuaranteesViewModel = viewModel()
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sale.DOCTO_CC_ID) {
        guaranteesViewModel.loadGuaranteeBySaleId(sale.DOCTO_CC_ID)
    }

    val guarantee by guaranteesViewModel.guaranteeBySale.collectAsState()

    LaunchedEffect(guarantee?.ID) {
        guarantee?.ID?.let { id ->
            guaranteesViewModel.loadEventsByGuaranteeId(id.toString())
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        guarantee?.let {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF198754)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Garantía Activa",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Se encuentra una garantía actualmente activa.",
                        fontSize = 18.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = it.ESTADO,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when (it.ESTADO) {
                "NOTIFICADO" -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .height(40.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            "Recolectar producto del cliente",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {},
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .height(40.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            "Imprimir aviso de garantía",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                "LISTO_PARA_ENTREGAR" -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { guaranteesViewModel.onEntregarProducto(it) },
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .height(40.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            "Entregar producto al cliente",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        } ?: TextButton(
            onClick = { navController.navigate(Screen.Guarantee.createRoute(sale.DOCTO_CC_ID.toString())) },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                .height(40.dp)
                .fillMaxWidth()
        ) {
            Text(
                "INICIAR GARANTIA",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
    
    if (showConfirmDialog && guarantee != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Confirmar Recolección",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("¿Está seguro de que desea recolectar el producto del cliente?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        guaranteesViewModel.onRecolectarProductoClick(guarantee!!)
                        showConfirmDialog = false
                    }
                ) {
                    Text("Sí, recolectar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}