package com.example.msp_app.features.sales.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.components.CustomMap
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel

@Composable
fun SaleDetailsScreen(
    saleId: Int
) {
    val viewModel: SaleDetailsViewModel = viewModel()
    val state by viewModel.saleState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSaleDetails(saleId)
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            when (val result = state) {
                is ResultState.Idle -> {
                    Text("No hay datos")
                }

                is ResultState.Loading -> {
                    Text("Cargando...")
                }

                is ResultState.Error -> {
                    Text("Error: ${result.message}")
                }

                is ResultState.Success -> {
                    val sale = result.data
                    if (sale != null) {
                        SaleDetailsContent(sale = sale)
                    } else {
                        Text("No se encontró la venta")
                    }
                }
            }
        }
    }
}

@Composable
fun SaleDetailsContent(
    sale: Sale,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(850.dp)
        ) {
            CustomMap()

            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .align(Alignment.BottomCenter)
                    //.absoluteOffset(y = 20.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(vertical = 30.dp, horizontal = 16.dp)) {
                    Text(
                        text = sale.CLIENTE,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    Text(
                        text = sale.FOLIO,
                        color = Color.Blue,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    Row(modifier = Modifier.padding(vertical = 10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Field(label = "Fecha de venta:", value = sale.FECHA)
                            Field(label = "Teléfono:", value = sale.TELEFONO)
                            Field(label = "Total venta:", value = "$${sale.PRECIO_TOTAL.toInt()}")
                            Field(
                                label = "Precio de contado:",
                                value = "$${sale.PRECIO_DE_CONTADO.toInt()}"
                            )
                            Field(label = "Zona:", value = sale.ZONA_NOMBRE)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Field(label = "Parcialidad:", value = "$${sale.PARCIALIDAD}")
                            Field(label = "Frecuencia de pago:", value = sale.FREC_PAGO.toString())
                            Field(label = "Enganche:", value = "$${sale.ENGANCHE.toInt()}")
                            Field(
                                label = "Precio a ${sale.TIEMPO_A_CORTO_PLAZOMESES} mes(es):",
                                value = "$${sale.MONTO_A_CORTO_PLAZO.toInt()}"
                            )
                        }
                    }

                    Field(
                        label = "Dirección:",
                        value = "${sale.CALLE.replace("\n", " ")} ${
                            sale.CIUDAD.replace(
                                "\n",
                                " "
                            )
                        } ${sale.ESTADO}"
                    )
                    Field(label = "Notas:", value = sale.NOTAS)
                    Field(label = "Vendedores:", value = sale.VENDEDOR_1)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color.White, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "Productos",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Column {
                    
                }
            }
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .then(modifier)
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
